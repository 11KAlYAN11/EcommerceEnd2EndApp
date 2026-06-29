# Phase 1 — Spring Boot Foundation

## Objective
Understand the Spring Boot runtime from the ground up and build the first working API endpoint.

---

## What We Built
| File | Purpose |
|---|---|
| `EcommerceApplication.java` | App entry point with `@SpringBootApplication` |
| `application.properties` | Base configuration (port, profiles, logging) |
| `application-dev.properties` | Dev-specific overrides |
| `common/response/ApiResponse.java` | Standard response wrapper for ALL endpoints |
| `health/HealthStatus.java` | Response model for health data |
| `health/HealthService.java` | Business logic layer |
| `health/HealthController.java` | HTTP layer (`@RestController`) |

## API Endpoints Built
```
GET /api/health          → { success, message, data: { status, service, version, uptime } }
GET /api/health/db       → { success, message, data: { status, service } }
GET /api/health/memory   → { success, message, data: { status, service } }
GET /api/actuator/health → Spring's built-in health check
GET /api/actuator/info   → App metadata from properties
```

---

## Concepts Introduced

### `@SpringBootApplication` — Three Annotations in One
```java
@SpringBootApplication
=
@SpringBootConfiguration   // This class can define @Bean methods
+ @EnableAutoConfiguration // Auto-configure based on classpath JARs
+ @ComponentScan           // Scan this package tree for Spring-managed classes
```

### Auto-Configuration
Spring Boot reads your `pom.xml` and auto-configures:
```
spring-boot-starter-web added?
  → Auto-create embedded Tomcat on port 8080
  → Auto-configure DispatcherServlet
  → Auto-configure Jackson for JSON

spring-boot-starter-data-jpa added?
  → Auto-configure Hibernate
  → Auto-configure HikariCP connection pool
  → Auto-configure @Transactional support
```
Zero XML. Zero manual server setup.

### IoC (Inversion of Control)
```
Without IoC (tight coupling):
  class OrderService {
      PaymentService ps = new PaymentService(); // YOU create it
  }

With IoC (loose coupling):
  class OrderService {
      OrderService(PaymentService ps) { ... } // SPRING creates & injects it
  }
```
Control of object creation is INVERTED from your code to the framework.

### DI (Dependency Injection) — Three Ways, One Winner
```java
// ❌ Field Injection — hidden dependencies, can't test
@Autowired private PaymentService ps;

// ❌ Setter Injection — mutable, optional only
@Autowired public void setPs(PaymentService ps) { ... }

// ✅ Constructor Injection — immutable, testable, explicit
public OrderService(PaymentService ps) { this.ps = ps; }
```
Always use constructor injection. `final` field = thread-safe + fail-fast.

### What Is a Bean?
Any object that Spring creates and manages in its IoC Container (ApplicationContext).

```java
@Component    // Generic bean — utility classes
@Service      // Business logic layer
@Repository   // Data access layer (adds DB exception translation)
@Controller   // HTTP request handler (returns views)
@RestController // HTTP + @ResponseBody (returns JSON) ← use this for REST APIs
```
All four are identical to Spring internally. They're semantic markers for humans.

### MVC Pattern
```
HTTP Request
    ↓
CONTROLLER    — receives HTTP, validates input, delegates to service
    ↓
SERVICE       — business logic, orchestrates operations, pure Java
    ↓
REPOSITORY    — data access only, talks to DB
    ↓
DATABASE
```
Each layer has ONE reason to change. Controller changes when API contract changes. Service changes when business rules change. Repository changes when DB schema changes.

### `ResponseEntity<T>`
```java
// Without ResponseEntity → always 200 OK
return body;

// With ResponseEntity → full control
return ResponseEntity.ok(body);                          // 200
return ResponseEntity.status(HttpStatus.CREATED).body(b); // 201
return ResponseEntity.status(HttpStatus.NOT_FOUND).build(); // 404
```
REST APIs communicate intent through HTTP status codes. Using them correctly is part of the contract.

### Maven Build Lifecycle
```
mvn clean          → delete /target
mvn compile        → .java → .class
mvn test           → run tests
mvn package        → create fat JAR
mvn spring-boot:run → compile + start app
```

### Spring Profiles
```properties
spring.profiles.active=dev
```
Loads `application.properties` + `application-dev.properties`. Dev gets verbose logging and error details. Prod gets minimal logging and no stack traces in responses. Same codebase, different behavior.

---

## Common Bugs in This Phase

| Bug | Cause | Fix |
|---|---|---|
| `@ComponentScan` not finding beans | Bean class is in a different package tree than `@SpringBootApplication` | Move main class to root package |
| Port 8080 already in use | Another app is running | `server.port=8081` or kill the other process |
| `context-path` not working | Using wrong URL | All URLs prefixed: `localhost:8080/api/health` not `/health` |
| 404 on all endpoints | `@RestController` missing or wrong package | Check class is under the root package |

---

## Interview Questions

**Q: What does `@SpringBootApplication` do?**
> Combines `@SpringBootConfiguration` (this is a config class), `@EnableAutoConfiguration` (auto-configure from classpath), and `@ComponentScan` (find all beans in this package tree). It bootstraps the entire IoC container and embedded server in one annotation.

**Q: What is the difference between `@Controller` and `@RestController`?**
> `@RestController` = `@Controller` + `@ResponseBody`. The `@ResponseBody` tells Spring to write the return value directly to the HTTP response body as JSON (via Jackson), rather than resolving a view name. Always use `@RestController` for REST APIs.

**Q: Why is constructor injection preferred over field injection?**
> Constructor injection produces `final` (immutable) fields, makes dependencies explicit and visible in the signature, allows easy unit testing (pass a mock to the constructor), and fails at startup if a dependency is missing rather than failing at runtime with NPE.

**Q: What is a Spring Bean? What is the default scope?**
> A Bean is any object managed by the Spring IoC container. The default scope is **singleton** — one instance per ApplicationContext, shared everywhere. This is why service classes must be stateless (no mutable instance fields holding request-specific data).

**Q: What is `ResponseEntity` and when do you use it?**
> `ResponseEntity` represents the full HTTP response: status code, headers, and body. Without it, Spring returns 200 OK always. With it you return 201 for created, 404 for not found, 503 for service unavailable. Correct status codes are part of the REST contract.

---

## MFAQ

**Why do we need `ApiResponse<T>` wrapper?**
Without it every endpoint returns a different shape — sometimes a string, sometimes an object, sometimes an array. The frontend can't build a consistent client. With `ApiResponse`, every endpoint always returns `{ success, message, data, timestamp }`. The frontend always reads `response.data`.

**What is `@Slf4j`?**
Lombok annotation that injects a `Logger log = LoggerFactory.getLogger(ThisClass.class)`. You write `log.info("...")`, `log.debug("...")`, `log.error("...")`. No boilerplate.

**What is DevTools and why `optional=true`?**
DevTools provides hot-reload (restart on code change during dev). `optional=true` means it won't be transitively pulled into other projects that depend on this one. `scope=runtime` means it's not on the compilation classpath — it can't be accidentally used in code.
