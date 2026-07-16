# ShopEase — Deployment Problems & Solutions

All issues hit during the AWS deployment of this Spring Boot + React app on EC2 + RDS + ElastiCache + ALB + CloudFront.

---

## 1. Swagger double-prefix `/api/api/v3/api-docs` → 403

**Problem:** Swagger UI returned 403 because `springdoc.swagger-ui.url` was set to `/api/v3/api-docs`. SpringDoc auto-prepends the context-path (`/api`), resulting in `/api/api/v3/api-docs`.

**Fix:** Set `springdoc.swagger-ui.url=/v3/api-docs` (no context-path prefix).

**Rule:** SpringDoc always prepends `server.servlet.context-path` to this value. Never include it manually.

---

## 2. Docker startup: `Could not resolve placeholder 'ALLOWED_ORIGINS'`

**Problem:** `application-prod.properties` had `${ALLOWED_ORIGINS}` with no default. Docker container failed to start because the env var wasn't passed.

**Fix:** Added defaults: `${ALLOWED_ORIGINS:http://localhost:5173}`. Moved all env vars to `/home/ec2-user/shopease.env` on EC2 and used `--env-file` in `docker run`.

**Rule:** Always set a fallback in `${VAR:default}` for optional config. Use an env file on EC2 instead of 15 `-e KEY=VAL` flags.

---

## 3. RDS password rejected — `@` not allowed

**Problem:** Password `ShopEase@2024` was rejected by RDS. The `@` symbol breaks JDBC URL parsing.

**Fix:** Used `ShopEase2024Prod` (no special chars in password).

**Rule:** Avoid `@`, `?`, `&`, `#` in DB passwords — they're URL delimiters.

---

## 4. EC2 port 8080 not bound to host (`0.0.0.0`)

**Problem:** `docker compose ps` showed `8080/tcp` without `0.0.0.0:` prefix. Container wasn't reachable from ALB.

**Fix:** Stopped a local IntelliJ instance holding port 8080. Container restarted and bound correctly.

**Rule:** If a port shows without `0.0.0.0:` in Docker, something else owns it. Run `netstat -tlnp | grep 8080`.

---

## 5. Git Bash path mangling for AWS CLI

**Problem:** AWS CLI commands with paths like `/api/actuator/health` got converted to `C:/Program Files/Git/api/actuator/health` by Git Bash's POSIX path conversion.

**Fix:** Prefix commands with `MSYS_NO_PATHCONV=1`.

**Rule:** Always use `MSYS_NO_PATHCONV=1` before any AWS CLI command that has a path argument on Windows Git Bash.

---

## 6. Mixed content error (HTTPS CloudFront → HTTP ALB)

**Problem:** Browser blocked API calls because the React app (HTTPS via CloudFront) was calling the ALB directly over HTTP.

**Fix:** Added ALB as a second CloudFront origin. Set path pattern `/api/*` → forward to ALB. Updated `VITE_API_URL` to the CloudFront domain. No SSL cert needed on ALB.

**Architecture:**
```
Browser → CloudFront (HTTPS) → /api/* → ALB (HTTP) → EC2
                              → /*    → S3 (static)
```

---

## 7. GitHub Actions SSH `no key found`

**Problem:** PEM key pasted into GitHub secret without the header/footer lines (`-----BEGIN RSA PRIVATE KEY-----`). SSH action returned `no key found`.

**Fix:** Paste the FULL `.pem` file content including the header and footer lines.

**Rule:** The entire PEM file content is the secret — headers included.

---

## 8. `@WebMvcTest` returning 403 on all endpoints

**Problem:** Tests used `@WebMvcTest` but Spring loaded default security instead of the custom `SecurityConfig`. All endpoints returned 403.

**Fix:** Added `@Import({SecurityConfig.class, JwtAuthFilter.class})` to the test class.

**Rule:** `@WebMvcTest` only auto-loads `@Controller` beans. Security config is `@Configuration` — it must be explicitly imported.

---

## 9. `@WebMvcTest` — "JPA metamodel must not be empty"

**Problem:** `@EnableJpaAuditing` on the main application class triggers JPA context initialization inside `@WebMvcTest`, which has no JPA layer.

**Fix:** Added `@MockBean JpaMetamodelMappingContext jpaMetamodelMappingContext` to the test.

**Rule:** Any `@Enable*` annotation on `@SpringBootApplication` that touches JPA will bleed into web slice tests. Always mock `JpaMetamodelMappingContext`.

---

## 10. Redis serialization 500 — `LocalDateTime not supported`

**Problem:** `GenericJackson2JsonRedisSerializer` used a default `ObjectMapper` without `JavaTimeModule`. Caching any DTO with `LocalDateTime` field threw:
```
InvalidDefinitionException: Java 8 date/time type LocalDateTime not supported by default
```
Hidden locally because local Redis was down → app fell back to `ConcurrentMapCacheManager` (in-memory, no serialization).

**Fix (attempt 1 — failed):** Added `JavaTimeModule` + `DefaultTyping.NON_FINAL` to a custom `ObjectMapper`.

**Why it still failed:** `DefaultTyping.NON_FINAL` does NOT wrap root-level `List` objects. `List<CategoryResponse>` was stored as `[[elem1],[elem2]]` (no outer type wrapper), but deserialization expected `["java.util.ArrayList",[...]]`. Result: crash on every cache read.

**Fix (final):** Switched to `JdkSerializationRedisSerializer`. Made all cached DTOs implement `Serializable`. Java's own serialization handles `LocalDateTime`, `BigDecimal`, and `List<T>` without any Jackson type-wrapping quirks.

**Rule:** For Spring Boot Redis caching with complex DTOs, `JdkSerializationRedisSerializer` is simpler and more reliable than fighting Jackson's `DefaultTyping`. Use it unless you need human-readable JSON in Redis.

---

## 11. Stale Redis entries crash new serializer

**Problem:** After switching serializer (from broken Jackson config to working JDK serializer), old Redis entries written in the old format cause deserialization crashes on read.

**Fix:** `FLUSHALL` on ElastiCache after every serializer change.

```bash
ssh -i ~/key.pem ec2-user@<EC2-IP> \
  "docker run --rm redis:7-alpine redis-cli -h <REDIS-HOST> -p 6379 FLUSHALL"
```

**Rule:** Any change to the Redis serializer = mandatory cache flush before restarting the app.

---

## 12. Hibernate 6 JPQL null-parameter binding bug

**Problem:** Spring Boot 3.x uses Hibernate 6. The JPQL pattern:
```sql
AND (:keyword IS NULL OR field LIKE :keyword)
```
throws `IllegalArgumentException` — Hibernate 6 cannot determine the binding type for a parameter used in both `IS NULL` and `LIKE` in the same expression.

**Fix:** Replaced the `@Query` method with `JpaSpecificationExecutor`. Built a `ProductSpec` class using the Criteria API with null-safe predicates.

**Rule:** In Hibernate 6, avoid `(:param IS NULL OR ...)` patterns in JPQL. Use `JpaSpecificationExecutor` + `Specification` for dynamic queries.

---

## 13. `@Transactional(readOnly=true)` blocking INSERT

**Problem:** `CartService.getCart()` was marked `readOnly=true`. For new users with no cart, `getOrCreateCart()` tries to `INSERT` a new cart row inside a read-only transaction. Spring/Hibernate silently rejects the write → 500.

**Fix:** Changed `getCart()` to `@Transactional` (without `readOnly`).

**Rule:** `readOnly=true` is for pure read paths. Any method that may write (including "get or create" patterns) needs a writable transaction.

---

## 14. CloudFront stripping query strings

**Problem:** Search `GET /api/search/products?q=denim` returned all 15 products instead of 1. Direct EC2 call worked. CloudFront `/api/*` behavior had no `OriginRequestPolicy` set, so query strings were stripped before forwarding to ALB.

**Fix:** Added AWS managed `AllViewer` origin request policy (`b689b0a8-53d0-40ab-baf2-68738e2966ac`) to the `/api/*` cache behavior. This forwards all headers, cookies, and query strings to the origin.

**Rule:** CloudFront `CachingDisabled` policy controls TTL only — it does NOT control what gets forwarded. Always set an `OriginRequestPolicy` on API cache behaviors.

---

## 15. Lombok `@Builder` ignores field initializers

**Problem:** `Product.java` had `private boolean active = true`. With Lombok `@Builder`, the field initializer is ignored — builder-created objects get `active = false` (primitive default). Products created by `DataSeeder` were inactive.

**Fix:** Add `@Builder.Default` to any field with a non-zero/non-null default that must be respected by the builder:
```java
@Builder.Default
private boolean active = true;
```

**Rule:** Lombok `@Builder` never uses field initializers unless `@Builder.Default` is present. Always check boolean/default fields in builder-heavy code.

---

## 16. Test admin credentials in production

**Problem:** `DataSeeder` hardcoded `admin@test.com / Admin@123`. These credentials end up in production.

**Fix:** Read from environment variables with local-only defaults:
```java
@Value("${seed.admin.email:admin@test.com}")
private String adminEmail;

@Value("${seed.admin.password:Admin@123}")
private String adminPassword;
```
Set real values in `/home/ec2-user/shopease.env` on EC2.

**Rule:** Seed credentials must come from env vars in production. Hardcoded test credentials in code = security incident waiting to happen.

---

## Quick Reference — Common Deployment Gotchas

| Symptom | Likely Cause | Fix |
|---|---|---|
| 500 on any cached endpoint | Redis serializer mismatch | Flush Redis + redeploy |
| Search returns all results | CloudFront stripping query strings | Add `AllViewer` origin request policy |
| 403 on all test endpoints | Missing `@Import` in `@WebMvcTest` | Import `SecurityConfig` + `JwtAuthFilter` |
| "JPA metamodel must not be empty" in tests | `@EnableJpaAuditing` bleeds into web slice | `@MockBean JpaMetamodelMappingContext` |
| Container not reachable from ALB | Port not bound to `0.0.0.0` | Check if another process owns the port |
| AWS CLI path garbled on Windows | Git Bash POSIX conversion | `MSYS_NO_PATHCONV=1` prefix |
| `Could not resolve placeholder` on startup | Missing env var, no default | Add `:default` in `${VAR:default}` |
| Hibernate 6 JPQL crash with nullable params | `IS NULL OR LIKE` pattern | Use `JpaSpecificationExecutor` + `Specification` |
| SSH "no key found" | PEM pasted without header/footer | Include full PEM including `-----BEGIN...-----` lines |
| Search/cart works locally, 500 in prod | Feature works but hits real Redis | LocalDateTime in DTO needs `JavaTimeModule` or `Serializable` |
