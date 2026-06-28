# EcommerceEnd2End — Production-Grade E-commerce Backend

## Phase 0: Project Planning

---

## What Are We Building?

A production-grade e-commerce backend API built with Java 17 and Spring Boot 4.x.
This system powers the core business operations of an online store — from user registration
and product browsing to cart management, order placement, payments, and notifications.

The frontend (React/Angular/Mobile) communicates with this backend via REST APIs.

---

## Business Context

Every company selling online needs:

| Business Problem | Technical Solution |
|---|---|
| Users need accounts | Auth Module (JWT) |
| Products need a catalogue | Product Module |
| Users select items | Cart Module |
| Users buy items | Order Module |
| Money changes hands | Payment Module |
| Users get confirmations | Notification Module (Kafka) |
| System slows under load | Redis Cache |
| DB becomes bottleneck | Indexing + Connection Pooling |

---

## Functional Requirements

**User:**
- Register with email and password
- Login and receive a JWT token
- Browse and search products with filtering and pagination
- Add and remove products from cart
- Place an order from cart contents
- Pay for an order
- View order history and status
- Cancel an order (only before shipping)
- Leave a review on purchased products

**Admin:**
- Add, update, and delete products and categories
- View and update order status (Processing → Shipped → Delivered)
- View sales reports

**System:**
- Send email confirmation on order placement
- Send email on order status changes
- Automatically release inventory if payment fails

---

## Non-Functional Requirements

| Attribute | Target |
|---|---|
| API Response Time | < 200ms for 95th percentile |
| Concurrent Users | 1,000+ |
| Availability | 99.9% uptime |
| Auth | JWT stateless tokens |
| Password Storage | BCrypt hashed |
| Data Safety | Transactional order + payment operations |
| Scalability | Horizontally scalable, stateless |

---

## Architecture

```
CLIENT LAYER
  React App / Mobile / Postman

        │ HTTPS
        ▼

SPRING BOOT API (port 8080)
  ┌────────────┐   ┌────────────┐   ┌────────────────┐
  │Controllers │ → │  Services  │ → │  Repositories  │
  └────────────┘   └────────────┘   └────────────────┘
  ┌────────────┐   ┌────────────┐   ┌────────────────┐
  │  Security  │   │   Kafka    │   │  Redis Cache   │
  └────────────┘   └────────────┘   └────────────────┘

        │
        ▼

PostgreSQL (primary data store)
Redis (cache layer)
```

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.1.0 |
| Build Tool | Maven |
| Database | PostgreSQL |
| ORM | Spring Data JPA (Hibernate) |
| Auth | Spring Security + JWT |
| Cache | Redis |
| Messaging | Apache Kafka |
| API Docs | Swagger (OpenAPI 3) |
| Testing | JUnit 5 + Mockito |
| Containerization | Docker |
| Monitoring | Prometheus + Grafana |
| Deployment | Railway (backend) + Vercel (frontend) |

---

## Folder Structure (Package by Feature)

```
src/main/java/com/ecommerce/
├── EcommerceApplication.java
├── config/                    ← Cross-cutting configuration
│   ├── SecurityConfig.java
│   ├── RedisConfig.java
│   └── KafkaConfig.java
├── common/                    ← Shared utilities
│   ├── exception/
│   ├── response/
│   └── util/
├── user/
├── auth/
├── product/
├── category/
├── cart/
├── order/
├── payment/
├── notification/
└── review/
```

---

## Git Strategy

**Branching Model:** GitHub Flow

- `main` is always deployable
- All new work branches from `main`
- Features merged via Pull Request
- Branch naming: `feature/phase-X-description` or `fix/short-description`

**Commit Standard:** Conventional Commits

```
feat(auth): add JWT token generation
fix(cart): prevent negative quantity
test(order): add order placement unit tests
```

---

## Project Roadmap

| Phase | Topic | Status |
|---|---|---|
| 0 | Project Planning | ✅ Done |
| 1 | Spring Boot Foundation + Health API | 🔄 Next |
| 2 | Database Design | ⬜ |
| 3 | Authentication (JWT) | ⬜ |
| 4 | Product Module | ⬜ |
| 5 | Cart | ⬜ |
| 6 | Orders | ⬜ |
| 7 | Payments | ⬜ |
| 8 | Caching (Redis) | ⬜ |
| 9 | Async Processing (Kafka) | ⬜ |
| 10 | Notifications | ⬜ |
| 11 | Logging | ⬜ |
| 12 | Monitoring | ⬜ |
| 13 | Docker | ⬜ |
| 14 | Deployment | ⬜ |
| 15 | Load Balancing | ⬜ |
| 16 | Microservices | ⬜ |
| 17 | Fault Tolerance | ⬜ |
| 18 | System Design | ⬜ |
| 19 | Performance | ⬜ |
| 20 | Production Readiness | ⬜ |
