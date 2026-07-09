# Architecture Decision Record — Backend (Spring Boot)

**Project**: ShopEase E-Commerce API  
**Status**: Accepted  
**Date**: 2026-07-09  
**Scope**: All backend architectural decisions across Phases 0–15

---

## Index of Decisions

| # | Decision | Phase |
|---|---|---|
| B-01 | Monolith-first architecture | 0 |
| B-02 | Package-by-feature over package-by-layer | 0 |
| B-03 | PostgreSQL as primary data store | 1–2 |
| B-04 | JWT for stateless authentication | 3 |
| B-05 | DTO pattern (Request / Response objects) | 4 |
| B-06 | Soft delete for Products and Categories | 4 |
| B-07 | Price snapshot on OrderItem | 6 |
| B-08 | @Transactional wrapping order + payment | 6–7 |
| B-09 | Idempotent cart add | 5 |
| B-10 | Redis as optional cache layer | 8 |
| B-11 | @Async for email notifications | 11 |
| B-12 | MDC correlation ID for distributed tracing readiness | 14 |
| B-13 | Rate limiting via servlet filter | 15 |
| B-14 | Multi-stage Dockerfile | 15 |
| B-15 | H2 for tests, PostgreSQL for dev/prod | 13 |

---

## B-01 — Monolith-First Architecture

**Context**  
Microservices are often assumed to be "the right way." But Netflix, Uber, and Amazon all started as monoliths and split only after hitting specific scaling bottlenecks that justified the operational complexity. This project is built by a 1-3 person team with a well-understood e-commerce domain.

**Decision**  
Build a well-structured monolith. Clean internal module boundaries (package-by-feature) mean it can be extracted into microservices at Phase 16+ by lifting each package into its own Spring Boot app.

**Why not microservices from day 1?**  
Microservices require: API gateway, service discovery, distributed tracing, inter-service auth, network partition handling, and per-service deployments — all before writing a single line of business logic. That complexity would eliminate all learning bandwidth.

**Trade-offs accepted**  
- Cannot scale individual modules independently  
- A JVM crash takes down all features  
- Tech stack is fixed (no mixing Go + Java + Python per service)

---

## B-02 — Package-by-Feature over Package-by-Layer

**Context**  
Two dominant ways to organise a Java project:

```
Package-by-layer (common but bad)    Package-by-feature (chosen)
─────────────────────────────────    ─────────────────────────────
controllers/                         auth/
  ProductController.java               AuthController.java
  CartController.java                  AuthService.java
services/                              JwtService.java
  ProductService.java              product/
  CartService.java                   ProductController.java
repositories/                          ProductService.java
  ProductRepository.java               ProductRepository.java
  CartRepository.java                  ProductRequest.java
                                       ProductResponse.java
```

**Decision**  
Package-by-feature. Every feature owns its controller, service, repository, and DTOs in one package.

**Why**  
- Opening `order/` shows everything about orders — no context-switching between 4 folders  
- Deleting a feature = deleting one folder. Package-by-layer leaves orphaned classes everywhere  
- Microservice extraction = copy the package folder into a new project  
- Cohesion is high, coupling is explicit (cross-package imports are visible)

**Trade-offs accepted**  
- Slight friction: shared utilities (`common/`, `config/`) still exist as cross-cutting concerns  
- Developers unfamiliar with the pattern may wonder where to put truly shared code

---

## B-03 — PostgreSQL as Primary Data Store

**Context**  
E-commerce data is inherently relational: Users → Orders → OrderItems → Products → Categories. We need foreign key enforcement, multi-table ACID transactions, complex aggregation queries (revenue by day, top customers), and pagination on large catalogs.

**Decision**  
PostgreSQL 15 as the sole primary database via Spring Data JPA (Hibernate). All monetary values use `NUMERIC(19,4)` mapped to Java `BigDecimal` — never `float` or `double` (floating-point cannot represent exact cents).

**Schema managed by**  
- `ddl-auto=update` in dev — Hibernate auto-creates and alters tables  
- `ddl-auto=validate` in prod — Hibernate only checks; never modifies (prevents accidental data loss)

**Why not MySQL?**  
PostgreSQL has superior `JSONB`, `ARRAY` types, full-text search, and window functions. Identical for this use case but better for future capability. PostgreSQL is also the default on Railway, Supabase, AWS RDS, and GCP Cloud SQL.

**Why not MongoDB?**  
E-commerce relationships are relational by nature. Embedding orders inside user documents causes update anomalies. Cross-collection transactions exist but are clunky. ACID guarantees across multiple collections are weaker.

**Trade-offs accepted**  
- Single PostgreSQL instance is the write bottleneck (sharding / read replicas needed at scale)  
- Schema migrations in prod require Flyway/Liquibase (Phase 16 concern)  
- `ddl-auto=update` is risky if used carelessly in prod — enforced by profile separation

---

## B-04 — JWT for Stateless Authentication

**Context**  
Every protected endpoint needs to know who is calling and what role they have. Two approaches: session-based (server stores state) or token-based (client holds signed token, server just verifies signature).

**Decision**  
HMAC-SHA256 JWT (JJWT 0.12.6). Token contains: `sub` (email), `roles` array, `iat`, `exp` (24h). Stored in the client's `localStorage`. Validated on every request by `JwtAuthFilter extends OncePerRequestFilter` before the request reaches any controller.

**Filter chain order**
```
Request
 → SecurityHeadersFilter   (adds HSTS, X-Frame-Options, etc.)
 → RateLimitFilter          (sliding window per IP)
 → JwtAuthFilter            (extracts + validates token, populates SecurityContext)
 → Spring Security checks   (@PreAuthorize, hasRole())
 → Controller
```

**Why not session + Redis?**  
Sessions require a shared session store for horizontal scaling. That adds a DB round-trip per request and a Redis dependency for a feature that JWT solves without state. JWT is the industry default for REST APIs.

**Why not OAuth2 / Keycloak?**  
Hides JWT internals that this project explicitly teaches. Adds an external dependency and a network hop. Appropriate for production systems with multiple client apps; unnecessary here.

**Token revocation caveat**  
A valid token cannot be revoked until expiry (24h). If a user must be locked out immediately, a Redis token denylist is needed. Accepted for this project — mitigated by short expiry.

**Trade-off accepted**  
`localStorage` is readable by JavaScript — XSS risk. In production, deliver the token in an `httpOnly` cookie to prevent script access.

---

## B-05 — DTO Pattern (Request / Response Objects)

**Context**  
JPA entities are mutable, carry Hibernate proxies, have lazy relationships, and often contain fields that should never be exposed (password hash, internal flags). Returning entities directly from controllers causes:  
- `LazyInitializationException` (Hibernate tries to load lazy relationships after session closes)  
- Overfetching (password hash, audit timestamps sent to browser)  
- Tight coupling (changing the entity shape breaks the API contract)

**Decision**  
Every controller boundary uses DTOs:
- `*Request.java` — inbound (validated with `@Valid`, `@NotBlank`, `@Min`)  
- `*Response.java` — outbound (explicit field selection, computed fields like `categoryName`)

All responses wrapped in a standard envelope:
```json
{
  "success": true,
  "message": "Product fetched",
  "data": { ... },
  "timestamp": "2026-07-09T10:00:00"
}
```

**Why**  
- Decouples API contract from DB schema — can change entity without breaking clients  
- Prevents accidental field exposure  
- Makes validation explicit and co-located with the DTO class  
- Consistent error shape: frontend always checks `response.success`

**Trade-off accepted**  
More classes to maintain. Mitigated by keeping DTOs simple (records in Java 17+ would be ideal).

---

## B-06 — Soft Delete for Products and Categories

**Context**  
When a product is deleted, orders that reference it must remain queryable (order history, invoices, returns). A hard `DELETE` would break every `OrderItem` foreign key or force `ON DELETE SET NULL`, losing product name and price from past orders.

**Decision**  
Products and Categories have an `active` boolean column. Delete operations set `active = false`. All queries filter `WHERE active = true`. The product data remains in the database forever, allowing order history to display it accurately.

**Why not CASCADE DELETE?**  
Cascade delete would destroy order history — a legal and business requirement. Soft delete preserves referential integrity.

**Why not a separate archive table?**  
Adds schema complexity and join overhead for a simple requirement.

**Trade-offs accepted**  
- DB grows over time (soft-deleted records accumulate)  
- Must remember to add `active = true` filter to every query — enforced via `@Where(clause = "active = true")` Hibernate annotation or explicit JPQL

---

## B-07 — Price Snapshot on OrderItem

**Context**  
Product prices change over time. If `OrderItem` just stores a `productId`, an order placed today for ₹999 would show ₹1,499 tomorrow after a price update — wrong for receipts, tax calculations, and refunds.

**Decision**  
`OrderItem` stores `priceAtPurchase` (copied from `product.price` at the moment of order creation). This field never changes after the order is placed, regardless of future product price changes.

```java
orderItem.setPriceAtPurchase(product.getPrice()); // snapshot taken here
```

**Why**  
This is the industry-standard pattern for any financial transaction. Price is a business fact that must be immutable once recorded.

**Trade-offs accepted**  
- Slight data duplication (price in both `products` and `order_items`)  
- `priceAtPurchase` can drift from current product price — intentional by design

---

## B-08 — @Transactional for Order + Payment Atomicity

**Context**  
Placing an order involves 4 operations: validate stock → decrement stock → create order → clear cart. If step 3 fails (DB error), steps 1-2 have already run — stock is permanently wrong and the user has no order. This is a classic partial-failure problem.

**Decision**  
`OrderService.placeOrder()` and `PaymentService.processPayment()` are annotated `@Transactional`. The entire sequence is a single DB transaction — either all operations commit or all roll back.

```
OrderService.placeOrder() [@Transactional]
  ├── validate stock          (SELECT)
  ├── decrement stockQuantity (UPDATE)
  ├── create Order + items    (INSERT)
  └── clear cart              (DELETE)
  ── SUCCESS → COMMIT / FAILURE → ROLLBACK (stock restored automatically)
```

**Isolation level**  
Default `READ_COMMITTED` — prevents dirty reads; phantom reads possible but acceptable for this scale. Race conditions on stock at very high concurrency would require `SERIALIZABLE` or optimistic locking (`@Version`).

**Why @Transactional and not manual commit?**  
Spring's `@Transactional` is AOP-based — Hibernate/JDBC transaction lifecycle is managed transparently. Manual commits are error-prone and verbose.

**Trade-offs accepted**  
- Long transactions hold DB locks — keep `@Transactional` methods short, avoid external HTTP calls inside  
- `@Transactional` on `private` methods doesn't work (Spring AOP proxy limitation) — must call through the proxy (via another bean or `self` injection)

---

## B-09 — Idempotent Cart Add

**Context**  
If the user clicks "Add to Cart" twice, or the frontend retries due to a network glitch, a naive implementation creates two `CartItem` rows for the same product. The cart now shows "iPhone × 2" but with two separate rows — confusing and hard to manage.

**Decision**  
`CartService.addItem()` checks for an existing `CartItem` for the same `(cart, product)` pair:
- **Exists** → increment quantity  
- **Not exists** → create new `CartItem` with quantity 1

```java
cartItemRepo.findByCartAndProduct(cart, product)
    .ifPresentOrElse(
        item -> item.setQuantity(item.getQuantity() + qty),
        () -> cartItemRepo.save(new CartItem(cart, product, qty, product.getPrice()))
    );
```

**Why**  
Idempotency is a core property of well-designed APIs. The same logical operation (add product X to cart) produces the same outcome no matter how many times it's called — critical for retry-safe clients.

**Trade-offs accepted**  
Requires a unique constraint on `(cart_id, product_id)` in the DB as a safety net against race conditions.

---

## B-10 — Redis as Optional Cache Layer

**Context**  
Product catalog reads are high-frequency and low-change. Fetching all products or a single product detail hits PostgreSQL every time, even when the data hasn't changed in hours. Under load, this creates unnecessary DB pressure.

**Decision**  
Spring Cache (`@Cacheable`, `@CacheEvict`) backed by Redis 7. Key cache entries:
- `product::{id}` — product detail, TTL 10 minutes  
- `products::all` — product list, TTL 5 minutes  
- `categories::all` — category list, TTL 30 minutes

Cache is evicted on every `createProduct`, `updateProduct`, `deleteProduct` call via `@CacheEvict`.

**Graceful degradation**  
If Redis is unreachable, the app falls back to direct PostgreSQL queries. No crash, no error — just slower. This is configured via `spring.cache.type=none` fallback and try/catch around cache operations.

**Why not in-process cache (Caffeine / Guava)?**  
An in-process cache is per-JVM — if 3 app instances run behind a load balancer, each has its own cache with potentially different stale data. Redis is shared across all instances.

**Trade-offs accepted**  
- Cache invalidation is hard — must maintain `@CacheEvict` discipline wherever data changes  
- Redis is another infrastructure component to manage and monitor  
- Stale data possible within TTL window (acceptable for product catalogs; not acceptable for cart/order)

---

## B-11 — @Async for Email Notifications

**Context**  
Sending an email via SMTP takes 200-2000ms depending on the mail server. If `OrderService.placeOrder()` calls `NotificationService.sendOrderConfirmation()` synchronously, the user waits 2 seconds for their "Order placed" API response — just for email delivery.

**Decision**  
`NotificationService` methods are annotated `@Async`. Spring executes them in a separate thread pool (configured in `AsyncConfig`: 5 core, 20 max, 100 queue). The order placement API returns immediately; email is sent in the background.

```
POST /orders → OrderService.placeOrder() → returns 201 in ~50ms
                      └→ [background thread] NotificationService.sendOrderEmail() → ~1500ms
```

**Why not Kafka for this?**  
Kafka adds broker infrastructure, producer/consumer config, offset management, and retry logic — for sending one email. `@Async` achieves the same decoupling with zero additional infrastructure. Kafka becomes justified at Phase 17 when event sourcing and guaranteed delivery matter.

**Trade-offs accepted**  
- If the app crashes between order creation and email send, the email is lost (no retry)  
- Background thread errors are invisible to the user — must be logged and alerted on  
- Mitigated by: wrapping email body in try/catch with `@Slf4j` error logging

---

## B-12 — MDC Correlation ID for Distributed Tracing Readiness

**Context**  
When a request fails in production, the logs show hundreds of concurrent request lines interleaved. It's impossible to isolate which log lines belong to the failing request without a per-request identifier.

**Decision**  
`SecurityHeadersFilter` (which runs on every request) generates a UUID `X-Correlation-Id` and stores it in `MDC` (Mapped Diagnostic Context — a per-thread key-value store injected into every log line by Logback/SLF4J).

```java
String correlationId = UUID.randomUUID().toString();
MDC.put("correlationId", correlationId);
response.setHeader("X-Correlation-Id", correlationId);
```

Log pattern: `%d{ISO8601} [%X{correlationId}] %-5level %logger{36} - %msg%n`

Every log line for a request now carries the same UUID. Support can grep for one ID and see the entire request lifecycle.

**Also**: the correlation ID is returned in the response header — frontend can include it in bug reports.

**Trade-offs accepted**  
- UUID adds ~8 bytes to every log line (negligible)  
- MDC must be cleared after the request (`MDC.clear()` in finally block) — otherwise thread pool reuse leaks the ID to the next request

---

## B-13 — Rate Limiting via Servlet Filter

**Context**  
Without rate limiting, a single malicious client can flood the login endpoint with brute-force password attempts, or flood the product search with thousands of requests per second, degrading service for all users.

**Decision**  
`RateLimitFilter` implements a sliding window per IP address using an in-memory `ConcurrentHashMap<String, Deque<Long>>`. Default: 100 requests per IP per 60 seconds. Returns `429 Too Many Requests` with a `Retry-After` header on breach.

Configurable limits per endpoint group (auth endpoints are tighter: 10/min).

**Why in-process and not API gateway?**  
API gateway (nginx, AWS API Gateway) is the right place for rate limiting in production. In-process is chosen here to teach the sliding window algorithm and to avoid external infrastructure dependencies. The filter is written to be replaceable with a Redis-backed distributed implementation.

**Trade-offs accepted**  
- Per-JVM state: if 3 app instances run behind a load balancer, a client can make 100 req/min per instance (300 total). Redis-backed rate limiting fixes this but adds complexity  
- In-memory map grows with unique IP count — must be bounded (evict old entries on access)

---

## B-14 — Multi-Stage Dockerfile

**Context**  
A naive `FROM eclipse-temurin:17 COPY target/*.jar app.jar` Docker image is ~400MB and contains the full JDK (compiler, debugging tools) — a large attack surface for a production container that only needs to run a JAR.

**Decision**  
Two-stage build:
```dockerfile
# Stage 1: BUILD — full JDK + Maven, compiles the JAR
FROM eclipse-temurin:17-jdk-alpine AS build
COPY . .
RUN mvn -q package -DskipTests

# Stage 2: RUN — minimal JRE only, copies JAR from stage 1
FROM eclipse-temurin:17-jre-alpine
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Final image size: ~180MB (vs ~420MB naive). JDK tools not present — cannot `javac`, cannot attach debugger.

**Trade-offs accepted**  
- Slightly more complex Dockerfile  
- Layer caching: Maven dependency download is slow if `pom.xml` changes; mitigated by copying `pom.xml` and running `mvn dependency:go-offline` before copying source

---

## B-15 — H2 for Tests, PostgreSQL for Dev/Prod

**Context**  
Integration tests that run against a real PostgreSQL require: a running PostgreSQL, a test database, credentials, and cleanup after each test. This makes CI setup complex and test runs slow.

**Decision**  
`application-test.properties` configures H2 in-memory with `MODE=PostgreSQL` (H2 can emulate PostgreSQL SQL dialect). Tests annotated `@SpringBootTest` pick up the test profile automatically.

Unit tests (`@WebMvcTest`, `@DataJpaTest`) mock the service layer — no DB needed at all.

**File separation**:
```
application.properties          ← shared config
application-dev.properties      ← PostgreSQL dev DB, ddl-auto=update
application-prod.properties     ← PostgreSQL prod DB, ddl-auto=validate
application-test.properties     ← H2 in-memory, ddl-auto=create-drop
```

**Naming convention enforced**:
| File naming | Plugin | Phase | Command |
|---|---|---|---|
| `*Test.java` | maven-surefire-plugin | `test` | `mvn test` |
| `*IT.java` | maven-failsafe-plugin | `verify` | `mvn verify` |

**Trade-offs accepted**  
- H2 SQL dialect is not 100% identical to PostgreSQL — PostgreSQL-specific features (JSONB, array operators, advisory locks) can't be tested with H2. Those require a real PostgreSQL (Testcontainers, Phase 16 concern)
