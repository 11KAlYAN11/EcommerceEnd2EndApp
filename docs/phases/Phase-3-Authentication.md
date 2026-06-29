# Phase 3 — Authentication (JWT + Spring Security)

## Objective
Secure the API with stateless JWT-based authentication and role-based authorization.

---

## What We Built
| File | Purpose |
|---|---|
| `common/util/JwtUtil.java` | Generate, validate, extract claims from JWT |
| `config/JwtAuthFilter.java` | Filter that runs on every request, validates token |
| `config/SecurityConfig.java` | Defines which endpoints are public vs protected |
| `user/UserDetailsServiceImpl.java` | Bridges our `User` entity with Spring Security |
| `auth/AuthService.java` | Register and Login business logic |
| `auth/AuthController.java` | HTTP endpoints for auth |
| `common/exception/GlobalExceptionHandler.java` | Centralized error handling |
| `config/DevController.java` | Dev-only: promote user to ADMIN |

## API Endpoints Built
```
POST /api/auth/register   → create account, return JWT
POST /api/auth/login      → validate credentials, return JWT
POST /api/dev/make-admin  → (dev only) promote email to ROLE_ADMIN
GET  /api/dev/users       → (dev only) list all users
```

---

## Concepts Introduced

### Why JWT? — The Stateless Problem
```
Traditional session-based auth (stateful):
  User logs in → server creates session → stores in memory
  Server must store session for every logged-in user
  Problem: scale to 3 servers → user hits server 2 → no session → logged out
  Solution: sticky sessions, session DB → complexity

JWT auth (stateless):
  User logs in → server creates JWT → sends to client
  Server stores NOTHING — the token carries all info
  User sends JWT with every request
  ANY server can validate the token (just needs the secret key)
  → Horizontally scalable with zero shared state
```

### JWT Structure — Anatomy of a Token
```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbGljZUB0ZXN0LmNvbSIsInJvbGVzIjpbIlJPTEVfVVNFUiJdfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c

HEADER  .  PAYLOAD  .  SIGNATURE

Header:  { "alg": "HS256", "typ": "JWT" }
Payload: { "sub": "alice@test.com", "roles": ["ROLE_USER"], "iat": 1234, "exp": 1234+86400 }
Signature: HMAC-SHA256(base64(header) + "." + base64(payload), SECRET_KEY)

Why is it secure?
  If attacker changes payload (e.g., adds "ROLE_ADMIN"):
  → Signature verification fails → token rejected
  The secret key is only on the server → attacker can't re-sign
```

### Request Flow With JWT
```
Client: POST /api/auth/login { email, password }
          ↓
Server: AuthenticationManager.authenticate(email, password)
          → DaoAuthenticationProvider loads user from DB
          → BCryptPasswordEncoder.matches(rawPassword, hashedPassword)
          → If match: authentication succeeds
          ↓
Server: JwtUtil.generateToken(userDetails) → JWT string
          ↓
Client receives: { token: "eyJ..." }
Client stores token (localStorage or cookie)

—— Future requests ——

Client: GET /api/orders  Authorization: Bearer eyJ...
          ↓
JwtAuthFilter (runs before every controller):
  1. Extract "Bearer eyJ..." from Authorization header
  2. jwtUtil.extractUsername(jwt) → "alice@test.com"
  3. userDetailsService.loadUserByUsername("alice@test.com")
  4. jwtUtil.isTokenValid(jwt, userDetails)
  5. If valid: SecurityContextHolder.setAuthentication(...)
          ↓
OrderController sees authenticated request
@AuthenticationPrincipal UserDetails → alice@test.com
```

### BCrypt — Why Passwords Are Never Stored Plain
```
Registration:
  User sends: "password123"
  BCrypt hash: "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
  Stored in DB: the hash (never the plain text)

Login:
  User sends: "password123"
  BCrypt.matches("password123", storedHash) → true/false
  BCrypt internally: re-hash with same salt → compare
  Even if DB is breached → can't reverse hash to get "password123"

Why BCrypt (not MD5/SHA1)?
  BCrypt is intentionally SLOW (work factor 10 = 1024 iterations)
  MD5: crack 1 billion passwords/second
  BCrypt: crack ~100 passwords/second
  Brute force becomes impractical
```

### Spring Security Filter Chain
```
Every HTTP request passes through a chain of filters:

CorsFilter → CsrfFilter → JwtAuthFilter → AuthorizationFilter → Controller

Our JwtAuthFilter:
  - Runs BEFORE Spring's built-in UsernamePasswordAuthenticationFilter
  - Validates JWT → sets Authentication in SecurityContextHolder
  - If no token: passes through (SecurityContext stays empty)
  - AuthorizationFilter then checks: is SecurityContext authenticated?
    If yes and endpoint requires auth → allowed
    If no → 401 Unauthorized
    If yes but wrong role → 403 Forbidden
```

### `@EnableMethodSecurity` + `@PreAuthorize`
```java
// On the class or method level in service/controller:
@PreAuthorize("hasRole('ADMIN')")
public void deleteProduct(Long id) { ... }

// Spring evaluates this BEFORE the method runs
// hasRole('ADMIN') checks: does SecurityContext have 'ROLE_ADMIN' authority?
// If not → AccessDeniedException → 403 Forbidden
```

### `UserDetailsService` — The Bridge
```
Spring Security doesn't know about our User entity.
It works with the UserDetails interface.
UserDetailsServiceImpl.loadUserByUsername(email):
  1. Load User from our DB by email
  2. Map User.roles → Set<GrantedAuthority>
  3. Wrap in Spring's User object (implements UserDetails)
  4. Return to Spring Security for password comparison and role checks
```

### `@RestControllerAdvice` — Global Exception Handler
```
Without it: Spring returns HTML error page or raw exception JSON
With it:    Every exception routes through our handler → standard ApiResponse

@ExceptionHandler(BadCredentialsException.class)
→ Returns: { success: false, message: "Invalid email or password" } + 401

@ExceptionHandler(MethodArgumentNotValidException.class)
→ Returns: { success: false, message: "Validation failed",
             data: { email: "Invalid format", password: "Min 6 chars" } } + 400
```

---

## Public vs Protected Endpoints
```
PUBLIC (no token needed):
  /api/auth/**         → register, login
  /api/health/**       → health checks
  /api/actuator/**     → actuator endpoints
  /api/dev/**          → dev bootstrap (dev profile only)
  GET /api/products/** → anyone can browse products
  GET /api/categories/** → anyone can browse categories

AUTHENTICATED (any valid JWT):
  POST /api/cart/**
  GET  /api/orders/**
  POST /api/orders/**
  GET  /api/payments/**

ADMIN ONLY (JWT with ROLE_ADMIN):
  POST   /api/products    → create product
  PUT    /api/products/** → update product
  DELETE /api/products/** → delete product
  POST   /api/categories  → create category
  PATCH  /api/orders/**/status → update order status
```

---

## How to Become ADMIN (Dev Flow)
```
Step 1: POST /api/auth/register → register alice@test.com
Step 2: POST /api/dev/make-admin?email=alice@test.com
Step 3: POST /api/auth/login → get FRESH token (old token has old roles)
Step 4: Use the new token — now has ROLE_ADMIN
```

---

## Common Bugs

| Bug | Cause | Fix |
|---|---|---|
| 401 on all requests | No `Authorization: Bearer <token>` header | Add header in Postman |
| 403 on admin endpoints | User has ROLE_USER only | Use `/dev/make-admin` + re-login |
| 403 after getting new role | Old token used — roles baked into JWT at generation time | Login again to get fresh token |
| Token expired | `jwt.expiration=86400000` (24h) passed | Login again |
| `UsernameNotFoundException` | Email not found in DB during login | Register first |
| 403 on `GET /products` | SecurityConfig didn't allow public GET | Fixed: GET products/categories are now public |

---

## Interview Questions

**Q: What is the difference between authentication and authorization?**
> Authentication = verifying WHO you are (login with email/password → get token). Authorization = verifying WHAT you're allowed to do (ROLE_ADMIN can delete products, ROLE_USER cannot). Authentication must succeed before authorization is checked.

**Q: Why is JWT stateless? What are the trade-offs?**
> JWT is stateless because the server stores nothing — all info is in the token itself. Any server can validate it. Trade-off: you can't invalidate a JWT before it expires (no server-side session to delete). Solution: short expiry (15 min) + refresh tokens, or maintain a token blacklist in Redis.

**Q: What is BCrypt and why is it better than MD5/SHA1 for passwords?**
> BCrypt is a key derivation function designed to be slow (work factor controls iterations). MD5/SHA1 are fast hash functions — a GPU can crack billions per second. BCrypt is intentionally slow (~100/second per GPU), making brute force impractical. It also includes a random salt, so identical passwords produce different hashes.

**Q: What happens if someone modifies the JWT payload?**
> The signature becomes invalid. The server re-computes `HMAC-SHA256(header.payload, secretKey)` and compares it with the signature in the token. Tampering with the payload changes the hash — they don't match → token rejected → 401.

**Q: What is `@PreAuthorize` and when does it run?**
> It's a Spring Security AOP annotation. Spring wraps the method in a proxy. Before delegating to the real method, the proxy checks the SpEL expression (e.g., `hasRole('ADMIN')`). If the check fails, it throws `AccessDeniedException` → 403, without the method body ever executing.

**Q: What is CSRF and why do we disable it?**
> CSRF (Cross-Site Request Forgery) attacks use a victim's browser session cookie to make unauthorized requests. We disable CSRF because our API is stateless — we use JWT in headers, not session cookies. There are no cookies to hijack. CSRF protection is irrelevant for token-based APIs.

---

## MFAQ

**Why does my token work for GET but not POST on the same endpoint?**
Different HTTP methods can have different security rules. `GET /products` is public. `POST /products` requires ROLE_ADMIN. Check the SecurityConfig and `@PreAuthorize` annotations.

**My `@PreAuthorize` isn't blocking anything — why?**
`@EnableMethodSecurity` must be on the config class. Without it, `@PreAuthorize` annotations are silently ignored and all methods are accessible.

**Why re-login after being made ADMIN?**
Roles are baked into the JWT at generation time. The old JWT still says ROLE_USER. After being promoted, login again to generate a new JWT that includes ROLE_ADMIN in its payload.

**What is `OncePerRequestFilter`?**
A Spring filter guaranteed to execute exactly once per HTTP request. Without it, in complex dispatch scenarios (forwards, includes), a filter might run multiple times. `JwtAuthFilter` extends this to ensure we parse the token exactly once per request.
