# ShopEase — End-to-End E-Commerce Platform

A production-grade e-commerce application built from scratch across **15 backend phases** and **8 frontend phases**, covering real-world engineering patterns from JWT auth to Redis caching, observability, production hardening, and a full React SPA.

---

## What This Is

ShopEase is a full-stack teaching project designed to take a developer from zero to interview-ready on Spring Boot + React. Every decision has a documented reason. Every pattern appears because a real production problem demanded it.

**Not a tutorial clone.** Built to the same standards you'd apply in a professional codebase: DTOs, transactional integrity, soft deletes, correlation IDs, rate limiting, Dockerfile, Prometheus metrics, and an ADR trail.

---

## Architecture Overview

```
┌────────────────────────────────────────────────────────────────┐
│                         CLIENT LAYER                           │
│   React 18 + Vite (port 5173)  │  Postman / curl              │
└───────────────────────────────────────────┬────────────────────┘
                                            │ HTTP / JSON
                                            ▼
┌────────────────────────────────────────────────────────────────┐
│               Spring Boot 3.3 API  (port 8080, context /api)   │
│                                                                │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────────────────┐  │
│  │ Controllers │→ │  Services   │→ │    Repositories      │  │
│  │  (REST)     │  │ (business)  │  │  (Spring Data JPA)   │  │
│  └─────────────┘  └─────────────┘  └──────────────────────┘  │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────────────────┐  │
│  │  Security   │  │ Redis Cache │  │  Async Email (@Async) │  │
│  │ JWT Filter  │  │ @Cacheable  │  │  Gmail SMTP           │  │
│  └─────────────┘  └─────────────┘  └──────────────────────┘  │
│  ┌─────────────┐  ┌─────────────┐                             │
│  │  Rate Limit │  │  Prometheus │                             │
│  │  (Filter)   │  │  /actuator  │                             │
│  └─────────────┘  └─────────────┘                             │
└───────────────────────────────┬────────────────────────────────┘
                                │
              ┌─────────────────┴──────────────────┐
              ▼                                    ▼
   ┌────────────────────┐              ┌───────────────────┐
   │   PostgreSQL 15    │              │    Redis 7         │
   │  (primary store)   │              │  (cache + session) │
   └────────────────────┘              └───────────────────┘
```

---

## Technology Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 17 |
| Framework | Spring Boot | 3.3.0 |
| Build | Maven | 3.x |
| Database | PostgreSQL | 15 |
| ORM | Spring Data JPA / Hibernate | — |
| Auth | Spring Security + JWT (JJWT) | 0.12.6 |
| Cache | Redis | 7 |
| API Docs | SpringDoc OpenAPI (Swagger UI) | — |
| Testing | JUnit 5 + Mockito + MockMvc | — |
| Observability | Micrometer + Prometheus | — |
| Containerization | Docker (multi-stage) | — |
| Frontend | React 18 + Vite | — |
| HTTP Client | Axios | — |

---

## How to Run

### Prerequisites
- Java 17+
- Maven 3.x
- PostgreSQL 15 running on `localhost:5432`
- Redis running on `localhost:6379` (optional — app degrades gracefully)
- Node 18+ (frontend only)

### Backend

```bash
cd EcommerceEnd2End

# 1. Create the database
psql -U postgres -c "CREATE DATABASE ecommerce_dev;"

# 2. Run with dev profile (auto-creates tables via ddl-auto=update)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 3. API is live at:
#    http://localhost:8080/api/
#    Swagger UI: http://localhost:8080/api/swagger-ui.html
```

**Environment variables** (override defaults):
| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/ecommerce_dev` | Database URL |
| `DB_USERNAME` | `postgres` | DB user |
| `DB_PASSWORD` | `root` | DB password |
| `JWT_SECRET` | (insecure default) | Must override in prod |
| `ALLOWED_ORIGINS` | `http://localhost:*` | CORS allowed origins |

### Seed an Admin User (dev profile only)

```bash
# Register a normal user first via POST /api/auth/register
# Then promote them:
curl -X POST "http://localhost:8080/api/dev/make-admin?email=admin@test.com"
```

### Frontend

```bash
cd EcommerceEnd2End/frontend

npm install
npm run dev
# App at http://localhost:5173
```

**Default credentials (after seeding):**
| Role | Email | Password |
|---|---|---|
| Admin | `admin@test.com` | `admin123` |
| User | any registered email | your password |

---

## API Endpoint Reference

All endpoints prefixed with `/api`.

### Auth
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/auth/register` | Public | Register new user |
| POST | `/auth/login` | Public | Login → JWT token |

### Products & Categories
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/products` | Public | List all (paginated) |
| GET | `/products/{id}` | Public | Product detail |
| GET | `/products/category/{id}` | Public | Products by category |
| POST | `/products` | Admin | Create product |
| PUT | `/products/{id}` | Admin | Update product |
| DELETE | `/products/{id}` | Admin | Soft-delete product |
| GET | `/categories` | Public | List categories |
| POST | `/categories` | Admin | Create category |
| PUT | `/categories/{id}` | Admin | Update category |
| DELETE | `/categories/{id}` | Admin | Delete category |

### Search
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/search/products?q=&minPrice=&maxPrice=&categoryId=&page=&size=` | Public | Multi-field search with filters |

### Cart
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/cart` | User | Get current cart |
| POST | `/cart/items` | User | Add item (idempotent) |
| PUT | `/cart/items/{id}` | User | Update quantity |
| DELETE | `/cart/items/{id}` | User | Remove item |
| DELETE | `/cart` | User | Clear cart |

### Orders
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/orders` | User | Place order from cart |
| GET | `/orders` | User | My order history |
| GET | `/orders/{id}` | User | Order detail |
| POST | `/orders/{id}/cancel` | User | Cancel order |

### Payments
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/payments/process` | User | Process payment for order |
| GET | `/payments/{orderId}` | User | Payment status |
| POST | `/payments/{id}/confirm` | Admin | Confirm payment |
| POST | `/payments/{id}/fail` | Admin | Fail payment |
| POST | `/payments/{id}/refund` | Admin | Refund payment |

### Admin
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/admin/dashboard` | Admin | Stats (users, orders, revenue) |
| GET | `/admin/orders` | Admin | All orders (filterable by status) |
| PATCH | `/admin/orders/{id}/status` | Admin | Update order status |
| GET | `/admin/revenue` | Admin | Revenue report |
| GET | `/admin/top-customers` | Admin | Top customers by spend |

### File Upload
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/upload/image` | Admin | Upload image file |
| POST | `/upload/product/{id}/image` | Admin | Attach image to product |
| GET | `/files/{filename}` | Public | Serve uploaded file |

### Dev (dev profile only — not in prod)
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/dev/make-admin?email=` | Public | Promote user to admin |
| GET | `/dev/users` | Public | List all users |

### Observability
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/actuator/health` | Public | Health check |
| GET | `/actuator/prometheus` | Public | Prometheus metrics scrape |
| GET | `/actuator/metrics` | Public | All metric names |

---

## Project Phases

### Backend — `EcommerceEnd2End/docs/phases/`

| Phase | Topic | Doc | Status |
|---|---|---|---|
| 0 | Project Planning & Architecture | [Phase-0](EcommerceEnd2End/docs/phases/Phase-0-Project-Planning.md) | ✅ |
| 1 | Spring Boot Foundation + Health API | [Phase-1](EcommerceEnd2End/docs/phases/Phase-1-Spring-Boot-Foundation.md) | ✅ |
| 2 | Database Design (JPA Entities, Relationships) | [Phase-2](EcommerceEnd2End/docs/phases/Phase-2-Database-Design.md) | ✅ |
| 3 | JWT Authentication & Spring Security | [Phase-3](EcommerceEnd2End/docs/phases/Phase-3-Authentication.md) | ✅ |
| 4 | Products & Categories (DTOs, Pagination, Soft Delete) | [Phase-4](EcommerceEnd2End/docs/phases/Phase-4-Products-Categories.md) | ✅ |
| 5 | Shopping Cart (Idempotency, orphanRemoval) | [Phase-5](EcommerceEnd2End/docs/phases/Phase-5-Cart.md) | ✅ |
| 6 | Order Management (@Transactional, state machine) | [Phase-6](EcommerceEnd2End/docs/phases/Phase-6-Orders.md) | ✅ |
| 7 | Payment Processing (Idempotency, lifecycle) | [Phase-7](EcommerceEnd2End/docs/phases/Phase-7-Payments.md) | ✅ |
| 8 | Redis Caching (@Cacheable, @CacheEvict, TTL) | [Phase-8](EcommerceEnd2End/docs/phases/Phase-8-Redis-Caching.md) | ✅ |
| 9 | Full-Text Search (JPQL, dynamic filters) | [Phase-9](EcommerceEnd2End/docs/phases/Phase-9-Search.md) | ✅ |
| 10 | File Upload (MultipartFile, UUID, disk→S3) | [Phase-10](EcommerceEnd2End/docs/phases/Phase-10-File-Upload.md) | ✅ |
| 11 | Email Notifications (@Async, Thymeleaf, Gmail SMTP) | [Phase-11](EcommerceEnd2End/docs/phases/Phase-11-Email-Notifications.md) | ✅ |
| 12 | Advanced Querying (N+1 fix, JOIN FETCH, aggregates) | [Phase-12](EcommerceEnd2End/docs/phases/Phase-12-Advanced-Querying.md) | ✅ |
| 13 | Testing (JUnit 5, MockMvc, H2, @WebMvcTest) | [Phase-13](EcommerceEnd2End/docs/phases/Phase-13-Testing.md) | ✅ |
| 14 | Observability (MDC, Micrometer, Prometheus) | [Phase-14](EcommerceEnd2End/docs/phases/Phase-14-Observability.md) | ✅ |
| 15 | Production Hardening (Rate limiting, Docker, security headers) | [Phase-15](EcommerceEnd2End/docs/phases/Phase-15-Production-Hardening.md) | ✅ |

### Frontend — `EcommerceEnd2End/frontend/docs/phases/`

| Phase | Topic | Doc | Status |
|---|---|---|---|
| F-0 | Vite + React setup, folder structure, why React | [Phase-F0](EcommerceEnd2End/frontend/docs/phases/Phase-F0-Setup.md) | ✅ |
| F-1 | Auth (JWT storage, AuthContext, ProtectedRoute) | [Phase-F1](EcommerceEnd2End/frontend/docs/phases/Phase-F1-Auth.md) | ✅ |
| F-2 | Axios setup, interceptors, CORS | [Phase-F2](EcommerceEnd2End/frontend/docs/phases/Phase-F2-API-CORS.md) | ✅ |
| F-3 | Products (search, pagination, two-state pattern) | [Phase-F3](EcommerceEnd2End/frontend/docs/phases/Phase-F3-Products.md) | ✅ |
| F-4 | Cart (CartContext, optimistic vs pessimistic) | [Phase-F4](EcommerceEnd2End/frontend/docs/phases/Phase-F4-Cart.md) | ✅ |
| F-5 | Orders (useParams, status badge, ProtectedRoute) | [Phase-F5](EcommerceEnd2End/frontend/docs/phases/Phase-F5-Orders.md) | ✅ |
| F-6 | Payment (method selection, navigation state) | [Phase-F6](EcommerceEnd2End/frontend/docs/phases/Phase-F6-Payment.md) | ✅ |
| F-7 | Admin Panel (CRUD modals, AdminRoute, double guard) | [Phase-F7](EcommerceEnd2End/frontend/docs/phases/Phase-F7-Admin.md) | ✅ |

### Architecture Decision Records

| Scope | Doc |
|---|---|
| Backend ADRs — 15 decisions (B-01 to B-15) | [docs/adr/ADR-BACKEND.md](docs/adr/ADR-BACKEND.md) |
| Frontend ADRs — 10 decisions (F-01 to F-10) | [frontend/docs/adr/ADR-FRONTEND.md](frontend/docs/adr/ADR-FRONTEND.md) |

---

## Folder Structure

```
EcommerceEnd2End/                     ← root
├── README.md                         ← this file
├── EcommerceEnd2End/                 ← Spring Boot backend
│   ├── src/main/java/com/ecommerce/
│   │   ├── auth/                     ← JWT, login, register
│   │   ├── user/                     ← User entity + service
│   │   ├── product/                  ← Product CRUD
│   │   ├── category/                 ← Category CRUD
│   │   ├── cart/                     ← Cart management
│   │   ├── order/                    ← Order lifecycle
│   │   ├── payment/                  ← Payment processing
│   │   ├── search/                   ← Full-text search
│   │   ├── admin/                    ← Admin reports
│   │   ├── notification/             ← Async email
│   │   ├── upload/                   ← File upload
│   │   ├── config/                   ← Security, CORS, Redis
│   │   ├── common/                   ← ApiResponse, exceptions, MDC
│   │   └── dev/                      ← DevController (dev profile only)
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   ├── application-dev.properties
│   │   └── application-prod.properties
│   ├── docs/
│   │   ├── phases/                   ← 16 backend phase docs
│   │   ├── adr/ADR-BACKEND.md        ← all 15 backend architectural decisions
│   │   └── EcommerceAPI.postman_collection.json
│   └── Dockerfile
└── frontend/                         ← React 18 + Vite SPA
    ├── src/
    │   ├── api/                      ← Axios clients per domain
    │   ├── context/                  ← AuthContext, CartContext, ToastContext
    │   ├── components/               ← Navbar, Modal, Spinner, ProtectedRoute
    │   ├── pages/                    ← One folder per domain (products, cart, orders…)
    │   └── utils/                    ← formatPrice, statusBadgeClass, imgFallback
    ├── docs/
    │   ├── phases/                   ← 8 frontend phase docs
    │   └── adr/ADR-FRONTEND.md       ← all 10 frontend architectural decisions
    └── vite.config.js
```

---

## Key Patterns Used

| Pattern | Where | Why |
|---|---|---|
| DTO (Request/Response) | Every controller | Decouple API contract from JPA entities |
| Soft Delete | Product, Category | Preserve FK integrity, order history stays intact |
| Price Snapshot | OrderItem | `priceAtPurchase` never changes retroactively |
| Idempotent Cart Add | CartService | Add same product twice = update quantity, not duplicate row |
| @Transactional order+payment | OrderService, PaymentService | ACID guarantee — all or nothing |
| Context API (Auth, Cart, Toast) | React frontend | Shared state without Redux boilerplate |
| Two-state search | ProductListPage | `searchInput` (typed) vs `search` (submitted) — avoids per-keystroke API calls |
| Axios interceptor unwrap | api/axios.js | Unwraps `ApiResponse.data` once, every component gets clean payload |
| MDC correlation ID | SecurityHeadersFilter | Every log line tagged with request ID — traceable across services |
| Rate limiting | RateLimitFilter | Sliding window per IP — prevents brute force and API abuse |
| Multi-stage Docker | Dockerfile | Build stage (JDK 17) → run stage (JRE slim) — minimal attack surface |

---

## Postman Collection

Import [`EcommerceEnd2End/docs/EcommerceAPI.postman_collection.json`](EcommerceEnd2End/docs/EcommerceAPI.postman_collection.json) into Postman.

Set base URL variable `{{baseUrl}}` = `http://localhost:8080/api`.  
After login, copy the token into the collection-level `{{token}}` variable.
