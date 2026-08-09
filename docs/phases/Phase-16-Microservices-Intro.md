# Phase 16 — Microservices Intro: Service Decomposition

## Objective
Take the monolith apart, one bounded context at a time, without ever breaking the working system. This phase is a series of extractions (16.1, 16.2, ...) — each one takes a package out of the monolith and turns it into an independently buildable, independently runnable Spring Boot application, in strangler-fig order (safest/most-decoupled domain first, most-coupled domain last).

The monolith itself is untouched throughout — it stays at the repo root exactly as Phases 0–15 left it, still fully buildable and runnable, so it remains the reference implementation to compare each extracted service against.

---

## 16.1 — notification-service (first extraction)

### Why this one first
Every other domain (cart, order, payment...) is reachable by a browser and needs its own auth, its own DB, its own request/response contract. `notification` was the one domain in the whole monolith with **zero inbound HTTP API and zero owned database table** — Phase 11 built it as a pure `@Async` fire-and-forget helper called *from inside* other services. That makes it the cheapest possible place to first prove out "new pom, new Application class, new port, new Dockerfile" before doing it somewhere that actually has data-coupling risk.

### What We Built
| File | Purpose |
|---|---|
| `notification-service/pom.xml` | Standalone Maven project — same `spring-boot-starter-parent`, no dependency on the monolith's pom or any shared module |
| `NotificationServiceApplication.java` | New `@SpringBootApplication` entry point — this service's own JVM process |
| `config/AsyncConfig.java` | Identical thread pool to the monolith's (`core=2, max=5, queue=100, prefix=email-`) — the reasoning (ADR B-11) didn't change just because it moved |
| `EmailService.java` | Same 4 email methods, **adapted signatures** (see below) |
| `NotificationController.java` | New — REST endpoints so another service can trigger an email over HTTP |
| `dto/OrderConfirmationRequest.java`, `OrderItemLine.java`, `OrderCancellationRequest.java`, `WelcomeRequest.java`, `LowStockAlertRequest.java` | New — the DTOs that replace passing JPA entities across the boundary |
| `application.properties` | Own port `8081`, own context path `/notification` |
| `Dockerfile` | Same multi-stage build/run pattern as the monolith's |
| `templates/emails/*.html` | Same 3 Thymeleaf templates, field paths updated (see below) |

### The core problem this extraction forces you to solve: entity coupling → DTO coupling

In the monolith, `OrderService` called `emailService.sendOrderConfirmation(user, order)` **in the same JVM**, passing live Hibernate entities. `EmailService` could freely call `order.getItems()`, `order.getCreatedAt()`, `user.getFirstName()` because everything shared one process and one persistence context.

The moment `notification-service` becomes its own process, that's impossible:
- It has no `spring-boot-starter-data-jpa`, no Postgres/H2 driver — **no database connection at all**.
- It doesn't have the `Order`/`User`/`OrderItem` classes on its classpath — those entity definitions live in the monolith (and later, in order-service/user-service).

So every method's signature changed from "take an entity" to "take a flat DTO":

```java
// Before (monolith, same JVM):
emailService.sendOrderConfirmation(user, order);
// EmailService reaches into order.getItems(), user.getFirstName(), etc.

// After (separate process):
emailService.sendOrderConfirmation(new OrderConfirmationRequest(
    user.getEmail(), user.getFirstName(), order.getId(),
    order.getStatus().name(), order.getTotalPrice(),
    order.getItems().stream()
        .map(i -> new OrderItemLine(i.getProduct().getName(), i.getQuantity(), i.getPriceAtPurchase()))
        .toList(),
    order.getCreatedAt()
));
```

**This is the pattern for every future extraction, not just this one.** Wherever there used to be an in-process method call passing an entity, there is now an HTTP call (for now — an event, once Phase 17 introduces Kafka) passing a DTO. cart-service calling product-service for a price, order-service calling user-service for an address — same shape of change every time.

The Thymeleaf templates needed one small update to match: `item.product.name` (entity navigation) became `item.productName` (flat DTO field) in `order-confirmation.html`. Nothing else about the templates changed.

### Which database does it use?
**None.** This is worth stating explicitly because it's the exception, not the rule, going forward. `notification-service`'s `pom.xml` has no `spring-boot-starter-data-jpa` and no DB driver; `application.properties` has no `spring.datasource.*`. It is fully stateless — receive request → render template → send SMTP → done. There is no "notifications" table logging history (a real system might add one later, e.g. "has this user opened this email" — out of scope here).

This was only possible because `notification` was the one domain with zero owned tables in the ADR B-01 boundary map. Every extraction after this one (**user-service** next) owns real tables, so "what's the database story?" becomes an actual decision instead of a non-question — see Phase 16.2 for how that gets resolved.

### Auth
Deliberately **none** on `NotificationController`. These endpoints are called by other services (order-service, once it exists), not by a browser — there's no end user JWT to validate. Locking down service-to-service calls (a shared internal token, mTLS, or simply "not internet-routable") is a hardening step, not something the first extraction needs to solve. Flagged here so it isn't mistaken for an oversight later.

### How we verified it (not just "it compiles")
```bash
cd notification-service
../mvnw compile              # clean compile, zero errors
../mvnw package -DskipTests  # → target/notification-service-0.0.1-SNAPSHOT.jar (~26MB)
java -jar target/notification-service-0.0.1-SNAPSHOT.jar   # started standalone on :8081

curl http://localhost:8081/notification/actuator/health
# → {"status":"UP"}

curl -X POST http://localhost:8081/notification/api/notifications/welcome \
  -H "Content-Type: application/json" \
  -d '{"recipientEmail":"test@example.com","firstName":"Kalyan"}'
# → HTTP 202 Accepted
```
Log output after the POST:
```
INFO  ... Tomcat started on port 8081 (http) with context path '/notification'
INFO  ... Started NotificationServiceApplication in 4.363 seconds
ERROR ... [email-1] EmailService : Failed to send welcome email to test@example.com: Authentication failed
```
Three things this proves at once:
1. **202 immediately** — the controller returned before the email attempt even started (async is working).
2. **Thread named `email-1`** — the custom `ThreadPoolTaskExecutor` from `AsyncConfig` is the one actually running the task, not Spring's default `SimpleAsyncTaskExecutor`.
3. **Failure isolated** — the fake Gmail credentials caused an `Authentication failed` error that was caught and logged, not thrown — exactly the "email failure must never fail the caller" behavior from Phase 11, now proven to survive the move into a separate service.

### Common Bugs / Gotchas Hit During This Extraction
| Bug | Cause | Fix |
|---|---|---|
| `cd notification-service && ...` then next command "file not found" | Shell working directory persists across tool calls — a `cd` in one command carries into the next | Always confirm `pwd` before assuming you're at repo root again |
| None yet on the Spring side | — | This extraction had no Spring-specific surprises — the monolith's own `AsyncConfig`/`EmailService` design ported over cleanly once entities were replaced with DTOs |

---

## Interview Questions

**Q: Why extract the "notification" domain first instead of something like "user" or "product"?**
> Strangler-fig migrations should extract the least-coupled, lowest-risk piece first to prove the mechanics (build, deploy, verify) before tackling anything with real data dependencies. Notification had zero inbound callers other than internal method calls and zero owned database tables — the smallest possible blast radius if something went wrong.

**Q: Does every microservice need its own database?**
> No — only services that own persistent state need one. A purely stateless service (an email sender, a PDF renderer, a notification dispatcher, often an API gateway) has nothing to persist and needs no database at all. The "database per service" rule applies to services that own data, not to every service unconditionally.

**Q: What changes when a synchronous in-process call becomes a cross-service call?**
> The caller can no longer pass a live object graph (a Hibernate entity with lazy-loaded relationships tied to an open persistence context) — that context doesn't exist on the other side of a network call. The callee's public contract must become a flat, serializable DTO containing exactly the fields it needs, and the caller becomes responsible for assembling that DTO from its own data before making the call.

**Q: If this were re-done with Kafka (Phase 17) instead of REST, what would actually change?**
> Only the transport at the very edges: `NotificationController`'s REST endpoints would be replaced by a `@KafkaListener` consuming `OrderPlaced`/`OrderCancelled`/`UserRegistered` events, and callers would publish an event instead of issuing an HTTP POST. `EmailService` and every DTO underneath stay identical — the DTO *is* the event payload shape. This is why getting the DTO boundary right now matters even before messaging exists.

---

## MFAQ

**Why does `notification-service` still have `spring-boot-starter-validation` if nothing calls it from a browser?**
The DTOs (`@Email`, `@NotBlank`, `@Min`) still get validated on every request, because the caller is still an untrusted-enough boundary (a bug in order-service could send a malformed payload) — validation isn't only for end users, it's for any network boundary.

**Won't copying `AsyncConfig` and the templates verbatim cause drift from the monolith's copies over time?**
Yes, and that's expected and fine for now — the monolith's copy will eventually be deleted once every domain that emails things (auth, order) is extracted and pointed at this service instead. Until then, two copies temporarily existing is the normal, visible cost of a strangler-fig migration — not a mistake to "fix" by linking them together.

**What's next?**
Phase 16.2 — **user-service** (`auth` + `user` + `address`). This is the first extraction that owns real tables, so it's where "which database, and how many" becomes a real decision instead of a non-question.

---

## 16.2 — user-service (first extraction with real data + a real inter-service call)

### Why this one next
Notification proved the mechanics (own pom, own port, own Dockerfile) with zero data risk. `user` is the next-safest step up: it owns real tables, but nothing else in the monolith writes to `users`/`roles`/`addresses` — everything else only *reads* a user by ID. It's also foundational: every future service (cart, order, payment) needs to know who's calling, so identity has to exist before those can be extracted.

### The database decision
Chose: **same Postgres container/instance as the monolith, but a separate database** — `ecommerce_users`, created with `CREATE DATABASE ecommerce_users` against the existing container. Not a new schema inside the monolith's `ecommerce_dev` database (that would still allow accidental cross-service JOINs), and not a whole second Postgres container yet (that's the AWS-time upgrade). This is the deliberate middle step: fully isolated at the database level (no shared tables, no cross-database FK — Postgres doesn't even allow FKs across databases), cheap locally (one running Postgres process), and it graduates cleanly to a separate RDS instance later without any code change — only the `DB_URL` env var changes.

```bash
# One-time setup, run once against the existing Postgres:
psql -h localhost -U postgres -d postgres -c "CREATE DATABASE ecommerce_users"
```

### What We Built
| File | Purpose |
|---|---|
| `user-service/pom.xml` | Standalone Maven project, own parent, own DB driver |
| `UserServiceApplication.java` | New entry point, `@EnableJpaAuditing` (needed for `Auditable`'s `@CreatedDate`/`@LastModifiedDate`) |
| `user/User.java`, `Role.java`, `RoleRepository.java`, `UserRepository.java`, `UserDetailsServiceImpl.java` | Copied verbatim — fully self-contained, no cross-package monolith references |
| `address/Address.java`, `AddressRepository.java` | Copied verbatim — `Address` belongs to `User`, both now live in the same service |
| `auth/AuthController.java`, `dto/*` | Copied verbatim — the public contract (`POST /api/auth/register`, `POST /api/auth/login`) is unchanged |
| `auth/AuthService.java` | **Adapted** — see below |
| `notification/NotificationClient.java` | New — the piece that replaces the in-process email call |
| `config/{SecurityConfig, JwtAuthFilter, DevController, DataSeeder}` | Copied, trimmed to only what this service owns (no `/products`, `/categories` in `PUBLIC_URLS`; `DataSeeder` seeds only roles + demo users, not products) |
| `common/{audit, exception, response, util}` | Copied verbatim (same reasoning as notification-service's copies) |

### The second real adaptation: a synchronous cross-service call

The monolith's `AuthService.register()` called `emailService.sendWelcome(user)` **in the same JVM** — no network involved, entity in, nothing back. Now that `EmailService` lives inside `notification-service`, that call has to leave the process:

```java
// Before (monolith):
emailService.sendWelcome(user);   // in-process method call, takes the entity

// After (user-service -> notification-service, over HTTP):
notificationClient.sendWelcome(user.getEmail(), user.getFirstName());
```

`NotificationClient` wraps Spring's `RestClient` (the modern synchronous HTTP client, Spring Framework 6.1+/Boot 3.2+ — the direct replacement for the older `RestTemplate`, which you'll still see everywhere in existing codebases and interview questions). It does a `POST /api/notifications/welcome` against `notification-service`'s own `NotificationController` — the exact endpoint built in 16.1.

Same non-negotiable rule as Phase 11's `@Async` (ADR B-11), now one network hop further out: **if notification-service is down, registration must still succeed.** The call is wrapped in try/catch, logs on failure, and never propagates — the alternative (a hiccup in an unrelated service breaking your core registration flow) is the exact fragility a synchronous inter-service call introduces, and exactly the reason Phase 17 eventually replaces this call with a published event instead of an HTTP request.

One thing deliberately **not** carried over: `MetricsService.incrementRegistrations()`. Cross-service metrics aggregation (one Prometheus/Grafana view spanning every microservice) is a real, valid question — but it's infrastructure that should be solved once, consistently, not half-solved inside one service's business logic while extracting it. Deferred to when observability itself gets revisited, not dropped by oversight.

### JWT: still validated by looking the user up in the DB — for now
`JwtAuthFilter` in user-service is unchanged from the monolith: on every request it calls `userDetailsService.loadUserByUsername(username)`, i.e. a DB read, to rebuild the `UserDetails` and confirm the account is still enabled. That's correct and cheap **here**, because user-service owns the `users` table.

It will NOT be correct for the next services (cart, order, ...). Those services don't have a `users` table at all, so they can't do this lookup even if they wanted to — the JWT's `roles` claim (already embedded at token-issue time, see `JwtUtil.generateToken`) is the only identity information they'll have, and they'll need to trust it directly rather than calling back into user-service on every single request just to re-derive what the token already says. Flagging this now so the *next* extraction's `JwtAuthFilter` is a deliberate rewrite, not confusion about why it looks different from this one.

### How we verified it
```bash
# One-time: create the database (see above), then:
cd user-service && ../mvnw package -DskipTests
java -jar target/user-service-0.0.1-SNAPSHOT.jar     # :8082, alongside notification-service on :8081

curl -X POST http://localhost:8082/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Kalyan","lastName":"Test","email":"kalyan-test@example.com","password":"secret123"}'
# -> 201, real JWT returned

# user-service log:
#   INFO  AuthService : New user registered: kalyan-test@example.com
# notification-service log (proves the HTTP call actually landed):
#   ERROR [email-1] EmailService : Failed to send welcome email to kalyan-test@example.com: Authentication failed
#   (fails only on the fake Gmail creds -- same expected failure mode as 16.1)

# JWT gate check:
curl http://localhost:8082/api/some-protected-path                              # -> 403 (no token)
curl -H "Authorization: Bearer <token>" http://localhost:8082/api/some-protected-path  # passes auth,
#   log shows "Authenticated user: kalyan-test@example.com" BEFORE the 500 --
#   the 500 itself is NoResourceFoundException falling into the catch-all
#   Exception handler, a pre-existing monolith quirk (same GlobalExceptionHandler
#   code, unchanged), not something this extraction introduced.

curl http://localhost:8082/api/dev/users
# -> admin@test.com (ROLE_ADMIN), user@test.com (ROLE_USER), kalyan-test@example.com (ROLE_USER)
# proves DataSeeder ran correctly against the new ecommerce_users database
```

### Common Bugs / Gotchas Hit During This Extraction
| Bug | Cause | Fix |
|---|---|---|
| No `psql` client available locally | Postgres runs via a container/service with no CLI installed on the host | Used the `postgresql-42.7.3.jar` driver already cached in `~/.m2` to run `CREATE DATABASE` via a throwaway JDBC snippet — no new tooling installed |
| `NoResourceFoundException` -> 500 instead of 404 on an unmapped path | Pre-existing: `GlobalExceptionHandler`'s catch-all `@ExceptionHandler(Exception.class)` doesn't special-case Spring's resource-not-found exception | Not fixed here — identical behavior exists in the monolith today; noted rather than silently carried forward unexplained |

---

## Interview Questions (16.2)

**Q: Why put user-service's data in a new database inside the same Postgres container instead of a new schema, or a whole new container?**
> A new schema in the same database still shares one Postgres instance's failure domain and makes it *possible* (even if not intended) to write a cross-schema JOIN, silently re-coupling two services. A separate database on the same server gives real logical isolation — Postgres cannot FK or JOIN across databases — while deferring the operational cost of a second container/instance until it's actually justified (e.g. before a cloud deployment).

**Q: What's the risk of a synchronous HTTP call from user-service to notification-service on the registration path?**
> If notification-service is slow or down, the call either blocks the registration request or fails. It's wrapped in try/catch specifically so failure doesn't propagate — but the registration response is still, in principle, waiting on a second service's availability for that brief window. This latent coupling is exactly why event-driven communication (Phase 17) is preferred for this kind of "fire-and-forget, best-effort" work in a mature system — the caller shouldn't need the callee to be reachable at all.

**Q: Why does user-service's JwtAuthFilter still do a database lookup on every request, when the JWT already contains the roles claim?**
> Because user-service owns the `users` table, so re-checking `enabled` (has this account been disabled since the token was issued?) is a legitimate, cheap, same-database read. A service that does NOT own that table can't make this same choice — it would mean a network call to user-service on every single authenticated request just to answer a question the token can already answer well enough. That trade-off (freshness vs. an extra network hop per request) is exactly why the *next* service's filter needs to look different, not the same.

---

## MFAQ (16.2)

**Why does `DataSeeder` only seed roles and two demo users here, when the monolith's version also seeded products and categories?**
Because this service doesn't own products or categories — that seeding logic belongs in `product-service` once that extraction happens. Splitting `DataSeeder` along the same lines as the package split keeps each service's seed data scoped to what it actually owns.

**If user-service is down, can anything else in the system still function?**
Right now: yes, for anything that doesn't require login (there's nothing else built yet to test this against, but the shape of the answer matters) — existing JWTs already issued keep working anywhere else that validates them independently, since validation is just a signature+claims check, not a call back to user-service. This is the JWT/stateless-auth payoff (ADR B-04) showing up concretely for the first time in the split system.

**What's next?**
Phase 16.3 — **product-service** (`product` + `category` + `search` + `review`). Fully self-contained catalog data, no writes depend on user-service or anything else — the last "easy" extraction before cart-service, which will be the first to make an inter-service call to *fetch* data (product price/name) rather than just fire-and-forget notify.

---

## 16.3 — product-service (product + category + search + review)

### System so far

```mermaid
flowchart LR
    subgraph client [Client / Postman]
    end

    client -->|login/register| US[user-service :8082<br/>ecommerce_users DB]
    client -->|browse / admin CRUD| PS[product-service :8083<br/>ecommerce_products DB]
    US -->|POST welcome email<br/>fire-and-forget| NS[notification-service :8081<br/>no DB]

    PS -. same Redis,<br/>different DB index .-> R[(Redis)]

    style US fill:#2b6cb0,color:#fff
    style PS fill:#2f855a,color:#fff
    style NS fill:#b7791f,color:#fff
    style R fill:#555,color:#fff
```

Monolith (port 8080) still runs untouched alongside all three — nothing above replaces it yet.

### The one real fix this extraction needed: `Review.user`

```mermaid
flowchart LR
    subgraph before["Monolith (one DB)"]
        R1["Review"] -->|"@ManyToOne User"| U1["User (same DB)"]
    end
    subgraph after["Split (two DBs)"]
        R2["Review"] -->|"Long userId<br/>(no FK, no join)"| gap["✂ network boundary"]
        gap --> U2["User (user-service's DB)"]
    end
```

Everything else in `product`/`category`/`search` was self-contained and copied unchanged.

### The bigger lesson: two different JWT filters, on purpose

```mermaid
sequenceDiagram
    participant C as Client
    participant US as user-service
    participant PS as product-service

    C->>US: POST /api/auth/login
    US->>US: check password, load roles from its OWN DB
    US-->>C: JWT (sub=email, roles=[...])

    C->>PS: POST /api/categories (Bearer JWT)
    Note over PS: StatelessJwtAuthFilter<br/>verify signature + read roles<br/>FROM THE TOKEN — no DB call
    PS->>PS: @PreAuthorize("hasRole('ADMIN')")
    PS-->>C: 201 Created
```

| | user-service's `JwtAuthFilter` | product-service's `StatelessJwtAuthFilter` |
|---|---|---|
| Checks | DB (`loadUserByUsername`) | Token claims only |
| Why | It owns the `users` table — cheap, catches disabled accounts instantly | Owns no `users` table — can't check even if it wanted to |
| Cost | One DB read per request | Zero extra I/O per request |
| Staleness | None | Up to token expiry (24h) if an account gets disabled |

### Other decisions, briefly
- **DB**: `ecommerce_products`, same Postgres instance, separate database (same pattern as 16.2).
- **Redis**: shared instance with the monolith, but `spring.data.redis.database=1` — a different logical DB slot, so cache keys (`products::all`, `category::5`...) don't collide with the monolith's own cache on slot 0.
- **Redis down entirely?** App still starts and works — `management.health.redis.enabled=false` added, since caching here is an optimization (ADR B-10), not a hard dependency.

### Verified live (all three services running together)

```mermaid
flowchart TD
    A["POST /api/auth/login (user-service)<br/>admin@test.com"] -->|JWT with ROLE_ADMIN| B["POST /api/categories (product-service)"]
    B -->|201 Created| C["Electronics category created"]
    C --> D["POST /api/products (product-service)<br/>as ADMIN"]
    D -->|201 Created| E["iPhone 16 created"]
    F["Same request as ROLE_USER token"] -->|403 Forbidden| G["role check enforced, no DB call needed"]
    H["No token at all"] -->|200 OK on GET /products| I["public browsing still works"]
```

All five outcomes above were hit exactly as shown — 201 / 201 / 403 / 200, in one live run across three separate JVMs.

### What's next?
Phase 16.4 — **cart-service**. First service that needs to *fetch* data from another service mid-request (product price + name from product-service) rather than fire-and-forget notify — a different, harder integration shape than anything built so far.

---

## 16.4 — cart-service (first hard dependency on another service)

### Fire-and-forget vs. required fetch

```mermaid
flowchart TB
    subgraph t1["16.2 -- fire and forget"]
        A1[user-service] -->|"notify, don't wait"| B1[notification-service]
        B1x["notification-service down?<br/>registration still succeeds"]
    end
    subgraph t2["16.4 -- required fetch"]
        A2[cart-service] -->|"need price/stock NOW"| B2[product-service]
        B2x["product-service down?<br/>add-to-cart fails (503)"]
    end
    style t1 fill:#f7f8fa,stroke:#d8dde3
    style t2 fill:#f7f8fa,stroke:#d8dde3
```

This is the harder integration shape: cart can't decide anything about a product without asking product-service.

### Entity changes (same pattern as before, applied twice)

```mermaid
flowchart LR
    subgraph old["Monolith"]
        C1[Cart] -->|"@OneToOne User"| U[User]
        CI1[CartItem] -->|"@ManyToOne Product"| P[Product]
    end
    subgraph new["cart-service"]
        C2[Cart] -->|"userEmail (from JWT, no lookup)"| J["✂"]
        CI2[CartItem] -->|"productId + snapshot<br/>(name, price, image)"| J2["✂"]
    end
```

`userEmail` needed **no network call at all** — it's the JWT `sub` claim, already trusted statelessly (same pattern as 16.3). `productId` needed a real fetch.

### Read vs. write: only writes call product-service

```mermaid
sequenceDiagram
    participant C as Client
    participant CS as cart-service
    participant PS as product-service

    C->>CS: POST /api/cart/items {productId, qty}
    CS->>PS: GET /api/products/{id}
    PS-->>CS: name, price, stock, active
    CS->>CS: validate stock, save snapshot
    CS-->>C: 200, cart with item

    Note over C,CS: later, just viewing the cart...
    C->>CS: GET /api/cart
    Note over CS: NO call to product-service --<br/>reads the stored snapshot
    CS-->>C: 200, cart (price as of last add/update)
```

**Trade-off, stated plainly**: `GET /cart` is fast and works even if product-service is down, but the price shown can be stale until the next add/update touches that line. This is deliberate — Cart isn't a financial record like Order (whose price snapshot in ADR B-07 is permanent); this one just refreshes itself naturally every time the item is touched.

### Verified live (all 4 services)
`login → JWT` → `create category` → `create product` → `POST /cart/items (cart-service calls product-service)` → `GET /cart shows the fetched name+price` — all via [verify-services.sh](../../verify-services.sh) `flow` command, output:
```
OK: got JWT from user-service
OK: product-service accepted the token and created a category
OK: public GET /products works with no token at all
OK: cart-service fetched the product from product-service and added it
OK: GET /cart shows the product name/price snapshot pulled from product-service
```

### A script bug this caught (worth keeping as a lesson)
First `flow` run after adding cart-service failed with "product-service did not return a category id" — the script's own error message guessed "jwt.secret mismatch." **Wrong.** The real cause: the category name was a fixed literal (`"SmokeTestCategory"`), and Postgres persists across restarts — the 2nd run hit a real unique-constraint 409, which looks nothing like a JWT problem once you actually read the response body instead of trusting the first plausible-sounding guess. Fixed by suffixing every test run with a timestamp, and by printing the raw response on failure instead of guessing.

### What's next?
Phase 16.5 — **order-service** and **payment-service**. The most coupled domain left (touches user, product, cart, triggers payment + notification) — saved for last on purpose, per the original extraction order.

---

## 16.5 — order-service (the hard one)

### Everything it touches

```mermaid
flowchart TB
    C[Client] -->|POST /orders, Bearer JWT| OS[order-service :8085<br/>ecommerce_orders DB]
    OS -->|GET /cart, DELETE /cart<br/>same JWT forwarded| CS[cart-service]
    OS -->|GET /products/id<br/>PATCH /products/id/stock| PS[product-service]
    OS -->|GET /addresses/id or /default| US[user-service]
    OS -->|fire-and-forget| NS[notification-service]

    style OS fill:#c53030,color:#fff
```

One customer action now costs **4 network calls minimum** before the response returns. This is exactly why it was extracted last — every pattern used here (forward the JWT, fetch fresh, snapshot, fire-and-forget) was already proven individually on a simpler service first.

### Forwarding the token, not just trusting the claims

Cart-service and product-service both require auth on everything they expose to order-service. order-service doesn't have its own identity to call them as — it acts *as the customer*. So the filter stashes the raw incoming token in a `ThreadLocal` for the duration of the request, and every outgoing client call re-attaches it:

```mermaid
sequenceDiagram
    participant C as Client
    participant OS as order-service
    participant CS as cart-service
    participant PS as product-service

    C->>OS: POST /orders  (Bearer JWT)
    Note over OS: StatelessJwtAuthFilter validates it,<br/>stashes raw token in CurrentRequestToken
    OS->>CS: GET /cart  (same Bearer JWT)
    CS-->>OS: [{productId, quantity}, ...]
    loop each item
        OS->>PS: GET /products/{id}  (public, no token needed)
        PS-->>OS: fresh price, stock, name
        OS->>PS: PATCH /products/{id}/stock  (same Bearer JWT)
    end
    OS->>CS: DELETE /cart  (same Bearer JWT)
    OS-->>C: 201 Created
```

### The snapshot pattern, applied to the two fields the monolith's own comments predicted

```mermaid
flowchart LR
    subgraph before["Monolith"]
        O1[Order] -->|"@ManyToOne User"| U[User]
        O1 -->|"@ManyToOne Address"| A[Address]
        OI1[OrderItem] -->|"@ManyToOne Product"| P[Product]
        OI1 -->|"priceAtPurchase ✓ already snapshotted"| OI1
    end
    subgraph after["order-service"]
        O2[Order] -->|"userEmail (JWT, no lookup)"| j1["✂"]
        O2 -->|"shippingStreet/City/State/...<br/>(fetched + snapshotted at order time)"| j2["✂"]
        OI2[OrderItem] -->|"productId + productName (NEW snapshot)<br/>+ priceAtPurchase (already existed)"| j3["✂"]
    end
```

The monolith's `Order.java` comment literally said: *"Better approach (Phase 6 improvement): snapshot address fields directly on the order... For now, FK is fine for learning."* Splitting into services didn't just permit that fix — it required it. Same story for `OrderItem.productName`: the comment already said price-only snapshotting was incomplete. This is worth noticing: **the monolith's own documentation was predicting exactly what the distributed version would need**, three phases in advance.

### Two gaps stated plainly, not hidden

1. **No distributed transaction.** `@Transactional` on `placeOrder()` only covers this service's own `orders` table now. If it fails *after* product-service already decremented stock, that decrement does not roll back. The monolith had this all inside one DB transaction; the split version doesn't, and fixing it properly needs a saga/compensating-action pattern — Phase 17+ territory, not solvable with an annotation.
2. **Stock adjustment has no service identity.** `PATCH /products/{id}/stock` just requires "some valid JWT," forwarded from whatever customer is checking out — there's no way yet to say "only order-service may call this." A real system would use a service credential or mTLS here. Deferred on purpose, flagged clearly.

### A small but real ripple: JWT gained a `firstName` claim
order-service needs a first name for the confirmation email, but has no `users` table to look it up in. Rather than add a whole new call to user-service just for a name, `user-service`'s `JwtUtil.generateToken` now embeds `firstName` as a claim at login/register time — the same trade-off the `roles` claim already made (staleness until next login, in exchange for zero extra calls).

### Verified live (all 5 services)
Via `verify-services.sh flow`:
```
OK: order-service placed an order (id=2) -- pulled cart, fetched fresh prices, decremented stock
OK: product-service stock actually decremented (5 -> 3)
OK: cart-service's cart was cleared by order-service after placement
```
Also manually verified: cancelling the order restored stock exactly (3 → back to original), and notification-service's log showed the order-confirmation attempt landing (same expected Gmail-auth failure as every prior test — the pipeline works, only the fake credentials don't).

### What's next?
Phase 16.6 — **payment-service**. Needs a way to read AND advance order status (`PATCH /orders/{id}/confirm-payment` already added and reserved for it) — the last piece before this phase's services fully replace the monolith's core purchase flow.

---

## 16.6 — payment-service (last domain extraction)

```mermaid
flowchart LR
    C[Client] -->|initiate/confirm/fail| PAY[payment-service :8086<br/>ecommerce_payments DB]
    PAY -->|GET /orders/id -- ownership check<br/>PATCH /orders/id/confirm-payment| OS[order-service]
    style PAY fill:#805ad5,color:#fff
```

Smallest of the six services, and the last domain one. `Payment.order` (FK) became `orderId` (no FK) — same pattern as every prior step. The only new idea: `OrderClient.getOrder()` doesn't do its own ownership check — it just forwards the caller's JWT to order-service's existing `GET /orders/{id}`, which already filters by the token's email. A 404 from that call means "not yours, or doesn't exist" either way; payment-service doesn't need to know which.

**Same gap as order-service's stock adjustment, one level up**: `confirmPayment()` saves `Payment.status = COMPLETED` in its own DB, then makes a *separate* HTTP call to flip the order to `CONFIRMED`. If the process dies between those two steps, the payment shows completed while the order still shows pending — a real inconsistency window a saga/outbox pattern is meant to close (Phase 17+), not something fixed here.

**Verified live** — all 6 services, one command (`verify-services.sh flow`):
```
OK: payment-service initiated a payment for order 3 (fetched order total via order-service)
OK: payment-service confirmed the payment
OK: order-service's order flipped to CONFIRMED -- payment-service's write-back call worked
```
11 checks total, across 6 independently-running JVMs, from `login` through `place order` through `confirm payment` through `order status flips`.

## Domain extraction complete

```mermaid
flowchart TB
    N[notification :8081] 
    U[user :8082]
    P[product :8083]
    C[cart :8084]
    O[order :8085]
    PAY[payment :8086]
    style N fill:#2f9e5c,color:#fff
    style U fill:#2f9e5c,color:#fff
    style P fill:#2f9e5c,color:#fff
    style C fill:#2f9e5c,color:#fff
    style O fill:#2f9e5c,color:#fff
    style PAY fill:#2f9e5c,color:#fff
```

All 6 domains from the monolith are now independently-running services with their own databases. Still ahead, **not part of "done"**: an API Gateway (single entry point — right now each service is called on its own port directly), pointing the frontend at it, and only then retiring the monolith. See the dedicated migration write-up for the full picture end to end.

---

## 16.8 — closing the admin-dashboard gap (found after UI cutover)

After the frontend was pointed at the gateway (16.7), a real gap surfaced: the UI's Admin page called `/api/admin/dashboard` and friends — routes the monolith had, that nothing in the new stack replicated. Confirmed live: `404` through the gateway.

```mermaid
flowchart LR
    C[Admin UI] -->|GET /admin/dashboard| GW[gateway]
    GW -->|routes to| OS[order-service]
    OS -->|"revenue, orders-by-status,<br/>top-customers -- own data,<br/>no network call"| OS
    OS -->|GET /products/count| PS[product-service]
    OS -->|GET /users/count -- admin-gated| US[user-service]
    style OS fill:#c53030,color:#fff
```

**Why it lives on order-service, not a new admin-service**: 4 of the 5 routes (`revenue`, `top-customers`, `orders-by-status`, `filtered orders`, `update status`) are pure order-service data — the monolith's `AdminDashboardService` only needed `OrderRepository` for those, same as here. Only the dashboard *summary* needs two extra numbers (`totalProducts`, `totalUsers`), fetched from product-service/user-service the same required-fetch way every other cross-service read has worked all along. Standing up a whole separate service just to make 2 HTTP calls and re-host 4 endpoints that already belong to order-service would be over-engineering, not correctness.

**A second real bug found while building this**: `findByFilters` (admin's "all orders" view) used the same `(:status IS NULL OR o.status = :status)` JPQL shape the monolith's product search once used — and hit the *exact* documented Hibernate 6 / Postgres bug (`problems-overcomed.md` #12: "could not determine data type of parameter"). Fixed the same proven way: `JpaSpecificationExecutor` + a small `OrderSpec` class (mirrors product-service's `ProductSpec`), not JPQL.

**Verified live**, fully consistent across calls: dashboard showed `totalOrders: 14`, `ordersByStatus.PENDING: 8`; filtering `/admin/orders?status=PENDING` returned exactly 8; marking one order `DELIVERED` via `PATCH /admin/orders/{id}/status` made `totalRevenue` jump from `0` to that order's exact total on the next dashboard call.

**Also fixed in the same pass**: the frontend navbar's "API Docs" link was hardcoded to the monolith's Swagger UI (`:8080`, not running). None of the 7 new services have `springdoc-openapi` wired up yet — a real, separate piece of future work, not faked here. Link removed rather than left dead; the Postman collection is the working API reference until per-service (or gateway-aggregated) Swagger exists.

### What's next?
Monolith decommission is now the only item left on the original Phase 16 roadmap.

---

## 16.7 — api-gateway (single entry point)

```mermaid
flowchart LR
    C[Client] -->|":9000, one port"| GW[api-gateway]
    GW -->|"/api/auth/**, /api/dev/**, /api/addresses/**"| U[user :8082]
    GW -->|"/api/products/**, /api/categories/**, /api/search/**"| P[product :8083]
    GW -->|"/api/cart/**"| CT[cart :8084]
    GW -->|"/api/orders/**"| O[order :8085]
    GW -->|"/api/payments/**"| PAY[payment :8086]
    GW -->|"/api/notifications/**"| N[notification :8081]
    style GW fill:#2b6cb0,color:#fff
```

**No path rewriting needed anywhere.** Every service already exposes its business routes under `/api/...` (5 via `server.servlet.context-path=/api`, notification-service via its controller's own `@RequestMapping("/api/notifications")` despite having no context-path). Whatever path the client sends the gateway forwards byte-for-byte to the matching service.

**No JWT validation at the gateway — deliberately.** The pattern since product-service (16.3) has been: every service validates the token itself, independently. Making the gateway the one place that checks JWTs would quietly change that into "every service trusts whichever box sits in front of it," which is a different (weaker) security model. The gateway stays a dumb router; auth still depends only on the token, exactly as before.

**The one deliberate stack inconsistency**: `spring-cloud-starter-gateway` is WebFlux/Netty (reactive), while every other service here is Servlet/Tomcat (blocking). That's not an oversight — it's what "Spring Cloud Gateway" means by default. (Spring Cloud 2023.0.x also ships a blocking `spring-cloud-starter-gateway-mvc` variant, not used here, worth knowing it exists.)

**Verified live**: full purchase chain — login → create category → create product → add to cart → place order → trigger a notification — run entirely through `:9000`, never touching 8081–8086 directly. Every hop worked identically to calling each service's own port.

### What's next?
Frontend cutover — point `VITE_API_URL` at `:9000` instead of the monolith's `:8080` — then, only after that's confirmed working, decommission the monolith.
