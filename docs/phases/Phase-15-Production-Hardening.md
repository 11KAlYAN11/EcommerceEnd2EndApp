# Phase 15 — Production Hardening

## Objective
Harden the app for real-world deployment: rate limiting, security response headers, environment-specific configuration, Docker packaging, and graceful shutdown. These are the non-negotiables before any real traffic.

---

## What We Built

| File | Purpose |
|---|---|
| `security/RateLimitFilter.java` | Sliding window IP-based rate limiter (100 req/min per IP) |
| `security/SecurityHeadersFilter.java` | Browser security headers (HSTS, CSP, clickjacking, MIME sniff) |
| `common/exception/GlobalExceptionHandler.java` | Added AccessDeniedException handler (403 with ApiResponse) |
| `application-prod.properties` | Production-safe config: validate schema, no SQL logs, env vars |
| `Dockerfile` | Multi-stage build, non-root user, JVM container tuning |
| `docker-compose.yml` | App + PostgreSQL + Redis as one orchestrated environment |

---

## Concepts Introduced

### Rate Limiting — Sliding Window Algorithm

```
PROBLEM: Without rate limiting, one client can:
  - Brute-force passwords (1000 attempts/second)
  - Scrape your entire product catalog
  - Flood the DB connection pool → all users get 503

SLIDING WINDOW LOG ALGORITHM:
  Per IP: maintain a deque (double-ended queue) of request timestamps.

  On each request:
    1. Remove timestamps older than 60 seconds from front
    2. Add current timestamp to back
    3. If size > 100: reject with HTTP 429
    4. Else: pass through

  Example:
    14:00:00 → [00:00] (1 request)
    14:00:59 → [00:00, 00:01, ..., 00:59] (60 requests)
    14:01:01 → deque still has all 60, some expire, keeps sliding

  WHY SLIDING vs FIXED WINDOW?
    Fixed: "allow 100 per minute, reset at :00"
    Attack: 100 requests at 00:59 + 100 at 01:00 = 200 requests in 2 seconds
    Sliding: at any point in time, only 100 requests in last 60s → safe

PRODUCTION ALTERNATIVE:
  In-memory (our impl): state lost on restart, not shared across instances
  Redis-based (production): Bucket4j + Redis → shared across all app pods
  API Gateway (enterprise): AWS API Gateway, Kong, Nginx limit_req
```

### Security Response Headers

```
HTTP response headers that instruct the browser how to behave:

X-Content-Type-Options: nosniff
  Without: browser guesses file type from content (MIME sniffing)
  Attack: upload evil.gif that's actually JavaScript → browser executes it
  With nosniff: browser trusts Content-Type header, never guesses

X-Frame-Options: DENY
  Without: attacker puts your site in an invisible <iframe>
  Attack: "Click here to win!" button is actually on top of your "Delete account"
  → Clickjacking: user clicks your action while looking at attacker's page
  With DENY: browser refuses to render in any frame

Strict-Transport-Security (HSTS): max-age=31536000
  Without: user types "myapp.com" → browser tries HTTP first
  Attack: SSL stripping — attacker intercepts the HTTP→HTTPS redirect
  With HSTS: browser caches "always use HTTPS" for 1 year
  → No HTTP-first attempt ever → attacker can't intercept

Content-Security-Policy: default-src 'none'
  Without: attacker injects <script> tags → XSS → steals tokens
  With: browser only allows resources from approved sources
  Our API sends JSON only → "none" is correct (no HTML/scripts from server)

X-XSS-Protection: 1; mode=block
  Legacy browser XSS filter. Deprecated in modern browsers (use CSP instead).
  Setting it helps users on IE11, old mobile browsers.
```

### Multi-Stage Docker Build

```dockerfile
# Stage 1: Build — needs full JDK + Maven (~700MB)
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
COPY pom.xml .
RUN mvn dependency:go-offline          # cached separately from source
COPY src ./src
RUN mvn package -DskipTests

# Stage 2: Run — needs JRE only (~250MB, no build tools)
FROM eclipse-temurin:17-jre-alpine
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

# Result: 250MB image instead of 700MB
# No source code, no Maven, no JDK in the final image
# Less attack surface: attacker can't use Maven/javac inside the container

LAYER CACHING:
  Copy pom.xml → download deps → copy src → compile
  Deps rarely change → the "download" layer is cached across builds
  Only src changes → only the "compile" step re-runs
  Repeat build: seconds instead of minutes (dep download skipped)
```

### Non-Root Container User

```dockerfile
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# WHY NON-ROOT?
# If the app is compromised (RCE bug):
# - Root user: attacker has root → can read all files, install tools, pivot
# - appuser: attacker is unprivileged → can't install anything, limited filesystem
#
# In Kubernetes: PodSecurityPolicy/SecurityContext can enforce non-root
# Best practice: all production containers should run as non-root
```

### JVM Container Awareness

```
# Problem without flags:
# Container: 512MB RAM limit
# Host machine: 32GB RAM
# JVM (without container support) sees: 32GB → sets heap to 8GB
# Result: container is killed immediately (OOMKilled)

# Solution:
-XX:+UseContainerSupport      → JVM reads cgroup limits (not host total)
-XX:MaxRAMPercentage=75.0     → use 75% of container's 512MB = 384MB heap
                                 leave 25% for off-heap (JVM internals, metaspace)

# -Djava.security.egd=file:/dev/./urandom
# Problem: SecureRandom in containers blocks waiting for entropy
# /dev/random is blocking — rare in containers without hardware RNG
# /dev/urandom is non-blocking, cryptographically safe for most uses
# /dev/./ is a JVM path normalization trick to force urandom even on some JVMs
# that re-normalize /dev/urandom to /dev/random internally
```

### Docker Compose — Service Orchestration

```yaml
services:
  postgres:
    image: postgres:15-alpine
    healthcheck:             # readiness probe
      test: pg_isready ...
      
  app:
    depends_on:
      postgres:
        condition: service_healthy  # wait for ACTUAL readiness, not just started

# Without healthcheck condition:
# postgres starts (but not ready to accept connections)
# app starts 1s later, tries to connect → "Connection refused"
# → App fails to start on every docker compose up

# With service_healthy:
# App only starts AFTER postgres passes its healthcheck
# → Reliable startup order, every time
```

### Graceful Shutdown

```
# Without graceful shutdown:
# SIGTERM received → JVM exits immediately
# In-flight HTTP requests: dropped (user gets connection reset)
# Active DB transactions: rolled back (data may be lost)
# @Async tasks: cancelled mid-send (email half-sent)

# With graceful shutdown (application.properties):
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=30s

# Process:
# 1. SIGTERM received (from Docker, Kubernetes, OS)
# 2. Spring stops accepting NEW requests (load balancer drains)
# 3. Existing requests are allowed to complete (up to 30s timeout)
# 4. After all requests done (or timeout): JVM exits cleanly
# 5. Active DB transactions commit or rollback normally
# 6. Thread pools shutdown cleanly

# In Kubernetes: set terminationGracePeriodSeconds > 30s
# So K8s gives the app 30s to drain before force-killing it
```

### Production Profile — application-prod.properties

```
Key differences from dev:
  
  spring.jpa.hibernate.ddl-auto=validate
    validate: check entities match schema, fail fast if not
    Never use create/update in prod — would ALTER or drop tables

  All credentials from env vars — no defaults:
    spring.datasource.password=${DB_PASSWORD}  (no :-root fallback)
    App fails to start if not set → forces correct configuration

  spring.jpa.show-sql=false
    Dev: show SQL for learning and debugging
    Prod: SQL in logs = flood, schema leakage, 50MB/min at 100 req/s

  logging.level=WARN
    Dev: DEBUG for learning
    Prod: WARN only — fewer logs, faster disk I/O, easier to find real issues
```

### AccessDeniedException — 403 with ApiResponse

```java
// BEFORE (no handler):
// User with ROLE_USER calls DELETE /api/products/1 → @PreAuthorize fires
// → Spring Security throws AccessDeniedException
// → No handler → Spring's default: 403 with HTML error page
// → Client gets HTML instead of our standard JSON ApiResponse

// AFTER (with handler in GlobalExceptionHandler):
@ExceptionHandler(AccessDeniedException.class)
public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error("Access denied: insufficient permissions"));
}
// → Client gets: {"success":false,"message":"Access denied: ..."}
// Consistent API responses, even for security violations

// NOTE: 401 vs 403
// 401 Unauthorized: not authenticated (no token, expired token)
// 403 Forbidden:    authenticated but wrong role
// "Unauthorized" is a naming mistake in HTTP — it means "unauthenticated"
```

---

## Production Checklist

```
Before deploying:
✅ JWT_SECRET is a long random string (not the dev default)
✅ DB_PASSWORD, REDIS_PASSWORD set as env vars (not hardcoded)
✅ spring.profiles.active=prod (not dev)
✅ spring.jpa.hibernate.ddl-auto=validate (not update/create)
✅ spring.jpa.show-sql=false
✅ CORS restricted to your frontend domain (not *)
✅ All actuator endpoints secured or restricted
✅ Rate limiting active
✅ Graceful shutdown configured
✅ Docker HEALTHCHECK set
✅ Running as non-root user in container
✅ No sensitive data in docker-compose.yml (use .env file)
```

---

## Running with Docker

```bash
# 1. Build and start everything
docker compose up --build

# 2. Start only DB + Redis (run app in IntelliJ/Eclipse)
docker compose up postgres redis

# 3. View app logs
docker compose logs -f app

# 4. Stop everything
docker compose down

# 5. Wipe all data (careful!)
docker compose down -v

# 6. Create .env file for secrets (never commit this)
echo "DB_PASSWORD=yourpassword" >> .env
echo "JWT_SECRET=your-64-char-random-secret" >> .env
echo "MAIL_PASSWORD=your-app-password" >> .env
```

---

## Common Bugs

| Bug | Cause | Fix |
|---|---|---|
| App starts before DB is ready | No `condition: service_healthy` | Add healthcheck to postgres service + depends_on condition |
| Rate limit fires for load balancer | `getRemoteAddr()` returns proxy IP | Read `X-Forwarded-For` header |
| HSTS breaks HTTP dev | Header sent over HTTP | Only issue HSTS in prod (profile check) or accept it in dev |
| 403 returns HTML | No AccessDeniedException handler | Add to GlobalExceptionHandler |
| Schema validation fails on startup | Entity added but migration not run | Run Flyway migration first |
| Container OOMKilled | JVM uses host RAM (ignores container limit) | Add `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75` |
| Image has source code | Single-stage Docker build | Use multi-stage: build stage → run stage |

---

## Interview Questions

**Q: What is the difference between rate limiting at application level vs API gateway?**
> Application-level (our RateLimitFilter): in-memory, per-instance, no shared state across multiple app pods. Simple to add, resets on restart. API gateway (Kong, AWS API Gateway, nginx): centralized rate limiting across all instances, persisted in Redis, configurable without code changes, supports more strategies (token bucket, concurrent connections). In production with multiple app instances, always use a centralized solution — per-instance limits multiply: 3 instances × 100 req/min = 300 req/min effective limit per IP.

**Q: What is the difference between `ddl-auto=update` and `ddl-auto=validate` in production?**
> `update` makes Hibernate automatically ALTER tables to match entity changes — dangerous in production because it can make irreversible schema changes, and it doesn't handle all migrations (can't drop columns, rename safely). `validate` only checks that the existing schema matches entities — if it doesn't match, the app fails to start with a clear error. Schema changes in production should be managed by Flyway or Liquibase, not Hibernate auto-DDL.

**Q: What is graceful shutdown and why does it matter?**
> Graceful shutdown allows in-flight requests to complete before the JVM exits. Without it, a rolling deployment or pod restart drops requests mid-processing: active DB transactions are rolled back, HTTP responses are cut off, emails stop mid-send. With `server.shutdown=graceful`, Spring stops accepting new requests, then waits up to N seconds for existing requests to complete before exiting. In Kubernetes, `terminationGracePeriodSeconds` must be set higher than this timeout.

**Q: Why use multi-stage Docker builds?**
> The build stage needs JDK + Maven (~700MB). The runtime only needs JRE + the JAR (~250MB). Multi-stage: Stage 1 compiles using full JDK. Stage 2 copies only the JAR. The final image doesn't contain source code, Maven, or the JDK — smaller image (faster pulls), less attack surface (no build tools an attacker could use), fewer CVEs in the image.

**Q: What is the `X-Forwarded-For` header and why does it matter for rate limiting?**
> When requests pass through a load balancer or proxy, `request.getRemoteAddr()` returns the proxy's IP, not the client's. All requests appear to come from the same source — rate limiting would fire for ALL users after 100 requests. `X-Forwarded-For: 203.0.113.5, 10.0.0.1` carries the original client IP (leftmost value). Rate limiters must read this header to identify the real client. Security note: clients can spoof this header — configure your proxy to strip and re-set it.

---

## MFAQ

**Why separate `application-prod.properties` instead of environment-specific logic in Java?**
Properties files are the right layer for environment configuration. Java code has business logic — it shouldn't know or care whether it's running in dev vs prod. The Spring profile mechanism (`application-{profile}.properties`) is the designed solution. The same JAR runs in dev (loads dev properties) and prod (loads prod properties). Changing behavior per environment without recompiling is a 12-Factor App principle.

**Should I use Docker Compose in production?**
Docker Compose is great for local development and single-server setups. For production at scale: Kubernetes (K8s) for multi-node, auto-healing, rolling updates. AWS ECS/Fargate for managed containers without Kubernetes complexity. The concepts from docker-compose.yml (services, networks, healthchecks, env vars) map directly to Kubernetes manifests (Deployments, Services, probes, ConfigMaps/Secrets). Learning Compose first is the right path to K8s.

**What else should be hardened before real production?**
This phase covers the essentials. Advanced topics for Phase 19+:
- Database connection via SSL/TLS
- Secrets management (AWS Secrets Manager, HashiCorp Vault) instead of env vars
- Container image scanning (Trivy, Snyk) in CI/CD pipeline
- Web Application Firewall (AWS WAF) for SQL injection, XSS pattern matching
- DDoS protection (AWS Shield, Cloudflare)
- Penetration testing before launch
