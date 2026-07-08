# E-Commerce Backend — Phase-Wise Learning Index

> **Learning Philosophy**: Never skip reasoning. Every concept explained from first principles before code. Target: 3-5 years Java Spring Boot backend interview readiness.

---

## Quick Navigation

| Phase | Topic | Key Concepts | Status |
|---|---|---|---|
| [Phase 0](Phase-0-Project-Planning.md) | Project Planning | SDLC, Monolith vs Microservices, Package-by-feature, Git Flow | ✅ |
| [Phase 1](Phase-1-Spring-Boot-Foundation.md) | Spring Boot Foundation | IoC, DI, @SpringBootApplication, MVC, Beans, ResponseEntity | ✅ |
| [Phase 2](Phase-2-Database-Design.md) | Database Design | JPA, Hibernate, FetchType, CascadeType, BigDecimal, Soft Delete | ✅ |
| [Phase 3](Phase-3-Authentication.md) | JWT Authentication | JWT anatomy, BCrypt, Spring Security filter chain, @PreAuthorize | ✅ |
| [Phase 4](Phase-4-Products-Categories.md) | Products & Categories | DTOs, LazyInitializationException fix, Pagination, Soft Delete | ✅ |
| [Phase 5](Phase-5-Cart.md) | Shopping Cart | @AuthenticationPrincipal, Lazy creation, orphanRemoval, Idempotent add | ✅ |
| [Phase 6](Phase-6-Orders.md) | Order Management | @Transactional, ACID, Price snapshot, OrderStatus state machine | ✅ |
| [Phase 7](Phase-7-Payments.md) | Payment Processing | Idempotency, paymentReference, Webhook simulation, Payment lifecycle | ✅ |
| [Phase 8](Phase-8-Redis-Caching.md) | Caching (Redis) | Redis, @Cacheable, @CacheEvict, TTL, graceful fallback | ✅ |
| [Phase 9](Phase-9-Search.md) | Search | JPQL, multi-field search, price+category filters, dynamic query | ✅ |
| [Phase 10](Phase-10-File-Upload.md) | File Upload | MultipartFile, UUID naming, MIME validation, disk→S3 pattern | ✅ |
| [Phase 11](Phase-11-Email-Notifications.md) | Email & Notifications | @Async, thread pool, Thymeleaf, Gmail SMTP, MimeMessage | ✅ |
| [Phase 12](Phase-12-Advanced-Querying.md) | Advanced Querying | JOIN FETCH (N+1 fix), aggregates, COALESCE, dynamic filters, admin dashboard | ✅ |
| [Phase 13](Phase-13-Testing.md) | Testing | JUnit5, Mockito, @WebMvcTest, MockMvc, @WithMockUser, H2, ReflectionTestUtils | ✅ |
| [Phase 14](Phase-14-Observability.md) | Observability | MDC correlation ID, Micrometer, Prometheus, custom counters/gauges/timers | ✅ |
| [Phase 15](Phase-15-Production-Hardening.md) | Production Hardening | Rate limiting, security headers, multi-stage Docker, graceful shutdown, prod profile | ✅ |
| Phase 16 | Microservices Intro | Service decomposition, API Gateway, Service discovery | 🔜 |
| Phase 17 | Message Queues | Kafka / RabbitMQ, async order processing | 🔜 |
| Phase 18 | Event Sourcing | CQRS, Event store, eventual consistency | 🔜 |
| Phase 19 | Cloud Deployment | AWS/GCP, K8s basics, CI/CD pipeline | 🔜 |
| Phase 20 | Interview Prep | System design, common patterns, mock questions | 🔜 |

---

## Critical Bugs Fixed (Running Log)

| Bug | Phase | Cause | Fix |
|---|---|---|---|
| Spring Boot 4.1.0 → 3.3.0 downgrade | 1 | Too new, unstable APIs | Pinned to 3.3.0 LTS |
| `@MockitoBean` not found | 1 | Introduced in Spring Boot 3.4+ | Changed to `@MockBean` |
| `LazyInitializationException` on GET /categories | 4 | Returning entity → Jackson serializes lazy `parent` after session closed | Created `CategoryResponse` DTO, map inside `@Transactional` |
| 403 on GET /products and GET /categories | 4 | SecurityConfig had `anyRequest().authenticated()` | Added public GET access for products and categories |
| 403 on POST /categories (admin) | 4 | No way to promote user to ADMIN | Created `DevController` with `@Profile("dev")` and `/dev/make-admin` |
| `getOrCreateCart()` inaccessible | 5 | Method was package-private, OrderService is different package | Added `public` modifier |
| 403 returns Spring HTML error page | 15 | No `AccessDeniedException` handler in `GlobalExceptionHandler` | Added handler returning standard `ApiResponse` |
| `@MockBean` not found in tests | 13 | Spring Boot 3.4+ renamed to `@MockitoBean` | Pinned project to Spring Boot 3.3.0 where `@MockBean` works |

---

## Tech Stack Summary

```
Language:     Java 17
Framework:    Spring Boot 3.3.0
Database:     PostgreSQL 15 (dev: ecommerce_dev)
ORM:          Spring Data JPA + Hibernate
Security:     Spring Security + JWT (JJWT 0.12.6)
Build:        Maven
Docs:         SpringDoc OpenAPI (Swagger UI at /api/swagger-ui.html)
```

---

## Interview Concept Cheat Sheet

| Concept | One-Line Answer |
|---|---|
| IoC | Object creation is inverted to the framework, not your code |
| DI | Dependencies are injected (constructor) not created with `new` |
| Bean | Any Spring-managed object in the ApplicationContext |
| @Transactional | Wraps method in DB transaction — commit on success, rollback on RuntimeException |
| ACID | Atomicity, Consistency, Isolation, Durability — DB transaction guarantees |
| JWT | Stateless auth token — Header.Payload.Signature — server signs, client stores |
| BCrypt | Slow intentional hash — makes brute force impractical |
| LAZY loading | Relationship loaded only when accessed (session must be open) |
| DTO | Plain Java object for API contract — decouples entity from response shape |
| Soft Delete | `active=false` flag — hides from listings, preserves FK integrity |
| Idempotency | Same operation applied multiple times = same result as once |
| Price Snapshot | `priceAtPurchase` — historical record, never changes retroactively |
| orphanRemoval | Remove from collection in Java = DELETE row in DB |
| Pagination | Load subset of records — `Pageable`, `Page<T>`, `?page=0&size=20` |
| @PreAuthorize | AOP intercepts method — checks role before body executes |
| MDC | Per-thread key-value store — puts correlation ID in every log line |
| Micrometer | Vendor-neutral metrics facade — Counter, Gauge, Timer |
| Rate Limiting | Sliding window: max N requests per IP per time window |
| HSTS | HTTP header: browser caches "always use HTTPS" for N seconds |
| Multi-stage Docker | Build stage (JDK) → Run stage (JRE + JAR) → smaller, safer image |
| Graceful Shutdown | Finish in-flight requests before JVM exits |
| ddl-auto=validate | Hibernate checks schema matches entities — never auto-alter in prod |

---

## API Endpoint Summary

```
Health:     GET  /api/health, /api/health/db, /api/health/memory
Auth:       POST /api/auth/register, /api/auth/login
Dev:        POST /api/dev/make-admin?email=, GET /api/dev/users
Categories: CRUD /api/categories (GET public, write = ADMIN)
Products:   CRUD /api/products (GET public, write = ADMIN) + search + pagination
Cart:       GET/POST/PUT/DELETE /api/cart (authenticated)
Orders:     POST/GET /api/orders, DELETE cancel, PATCH status (ADMIN)
Payments:   POST/GET /api/payments, confirm/fail/refund
Search:     GET  /api/search/products?q=&minPrice=&maxPrice=&categoryId=
Upload:     POST /api/upload/image, /api/upload/product/{id}/image (ADMIN)
Admin:      GET  /api/admin/dashboard, /api/admin/revenue, /api/admin/top-customers
            GET  /api/admin/orders (filtered), PATCH /api/admin/orders/{id}/status
Actuator:   GET  /api/actuator/health, /api/actuator/prometheus, /api/actuator/metrics
```
