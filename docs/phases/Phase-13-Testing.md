# Phase 13 — Testing Strategy

## Objective
Build confidence in your code through automated tests. Learn the three testing layers (unit, web slice, integration), when to use each, and how to test a Spring Boot app without hitting real databases or external services.

---

## What We Built

| File | Type | What It Tests |
|---|---|---|
| `src/test/resources/application-test.properties` | Config | H2 in-memory DB, disabled mail/Redis for tests |
| `common/util/JwtUtilTest.java` | Unit | JWT generate, parse, validate, expiry |
| `auth/AuthServiceTest.java` | Unit | Register (success, conflict, role creation), login |
| `product/ProductServiceTest.java` | Unit | Get, create, soft delete, not found paths |
| `auth/AuthControllerTest.java` | Web slice | HTTP status codes, request validation, JSON shape |
| `product/ProductControllerTest.java` | Web slice | Public endpoints, 404 handling, auth enforcement |

---

## The Testing Pyramid

```
         ┌──────────────┐
         │  Integration │  Few — @SpringBootTest, full context
         │     Tests    │  Slow (10–30s), realistic, catch wiring bugs
         ├──────────────┤
         │  Web Slice   │  Some — @WebMvcTest, controllers + security
         │    Tests     │  Medium (1–3s), tests HTTP layer in isolation
         ├──────────────┤
         │  Unit Tests  │  Many — JUnit5 + Mockito, no Spring context
         │              │  Fast (<100ms), tests business logic in isolation
         └──────────────┘

Rule: test more at the bottom (cheap) and fewer at the top (expensive).
Most bugs live in business logic → unit tests catch them.
Integration tests verify the whole system wires correctly.
```

---

## Concepts Introduced

### @ExtendWith(MockitoExtension.class) vs @SpringBootTest

```
@ExtendWith(MockitoExtension.class):
  → Activates Mockito only
  → No Spring ApplicationContext created
  → @Mock fields are initialized
  → @InjectMocks creates real object with mocked dependencies
  → Startup time: < 100ms
  → Use for: service classes, utility classes

@SpringBootTest:
  → Starts full Spring ApplicationContext
  → Loads ALL beans: repositories, services, configs, security
  → Startup time: 5–30 seconds (full app start)
  → Use for: integration tests that verify components wire together
  → @SpringBootTest + @AutoConfigureMockMvc → full integration with HTTP

@WebMvcTest(Controller.class):
  → Loads ONLY web layer (controllers, filters, security)
  → Does NOT load @Service, @Repository beans (mock them with @MockBean)
  → Startup time: 1–3 seconds
  → Use for: controller tests (HTTP status, request validation, JSON shape)
```

### The Difference Between @Mock and @MockBean

```java
// @Mock — Mockito only, no Spring involved
@ExtendWith(MockitoExtension.class)
class ServiceTest {
    @Mock UserRepository userRepository; // pure Mockito mock
    @InjectMocks AuthService authService; // real AuthService, mocked deps injected
}

// @MockBean — Spring Test, registers mock in ApplicationContext
@WebMvcTest(AuthController.class)
class ControllerTest {
    @MockBean AuthService authService; // mock registered as Spring bean
    // Spring uses this mock wherever it would use the real AuthService
}

Rule:
  - In @ExtendWith(MockitoExtension.class) tests → use @Mock
  - In @WebMvcTest or @SpringBootTest tests → use @MockBean
```

### when().thenReturn() — Controlling Mock Behavior

```java
// Without setup: mockObject.anyMethod() returns null/0/false
// With setup: control exactly what the mock returns

when(userRepository.existsByEmail("alice@test.com")).thenReturn(true);
// → calling userRepository.existsByEmail("alice@test.com") returns true
// → any other email → still returns false (default mock behavior)

when(jwtUtil.generateToken(any())).thenReturn("fake-jwt");
// any() → matches any argument

when(userRepository.findById(99L)).thenThrow(new RuntimeException("DB error"));
// → simulate DB failure to test your error handling
```

### verify() — Assert Behavior, Not Just Return Values

```java
// Some logic doesn't have a return value — verify side effects instead

// Assert save() was called exactly once
verify(userRepository, times(1)).save(any(User.class));

// Assert delete() was NEVER called (soft delete — don't physical delete)
verify(productRepository, never()).delete(any());

// Assert email was sent
verify(emailService).sendWelcome(any(User.class));

// Without verify: even if the method has a bug and never calls save(),
// your test might still pass if you only assert the return value.
```

### MockMvc — Testing HTTP Without a Real Server

```java
mockMvc.perform(
    post("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request))
)
.andExpect(status().isCreated())                    // HTTP 201
.andExpect(jsonPath("$.success").value(true))       // response body
.andExpect(jsonPath("$.data.token").exists())       // field present
.andExpect(jsonPath("$.data.email").value("alice@test.com"));

// What MockMvc does:
// 1. Passes request through the full filter chain (JwtAuthFilter, CORS, etc.)
// 2. Routes to the controller via DispatcherServlet
// 3. Runs validation (@Valid)
// 4. Returns the response
// NO real TCP connection, no Tomcat. Pure in-process simulation.
```

### @WithMockUser — Injecting a Fake Authenticated User

```java
// Without @WithMockUser: SecurityContext is empty → 401 Unauthorized
// With @WithMockUser: Spring Security pretends this user is authenticated

@Test
@WithMockUser(roles = "ADMIN")
void adminEndpoint_returns200() { ... }  // SecurityContext has ROLE_ADMIN

@Test
@WithMockUser  // default: username="user", roles=["USER"]
void userEndpoint_returns200() { ... }

// How it works:
// @WithMockUser is processed before the test method runs.
// It creates a UsernamePasswordAuthenticationToken and sets it in SecurityContext.
// @PreAuthorize("hasRole('ADMIN')") checks SecurityContext → finds ADMIN role → passes.
// @PreAuthorize("hasRole('ADMIN')") checks SecurityContext → finds USER role → 403.
```

### ReflectionTestUtils — Setting Private @Value Fields

```java
// Problem: JwtUtil has private @Value fields:
//   @Value("${jwt.secret}") private String secret;
// In tests without Spring context, these are never set → null → NPE.

// Solution: ReflectionTestUtils.setField() bypasses access modifiers
JwtUtil jwtUtil = new JwtUtil();
ReflectionTestUtils.setField(jwtUtil, "secret", "test-secret-256-bits");
ReflectionTestUtils.setField(jwtUtil, "expiration", 3600000L);

// Only use in tests — never in production code.
// Clean alternative: create a constructor that accepts these values.
```

### H2 — In-Memory Database for Tests

```
PostgreSQL (dev/prod): persistent, requires installation, port 5432
H2 (tests): lives in JVM memory, auto-creates schema from @Entity, zero setup

application-test.properties:
  spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL
  spring.jpa.hibernate.ddl-auto=create-drop

MODE=PostgreSQL: H2 behaves like PostgreSQL for most queries.
create-drop: create schema at test start, drop at end → clean state each run.

When to use @SpringBootTest + H2:
  Integration tests that need to actually save/load from a database.
  Tests JPA entity relationships, cascade behavior, query correctness.
  Slower than unit tests but faster than hitting real PostgreSQL.
```

---

## Test Structure Patterns

```java
// Arrange-Act-Assert (AAA) — universal test structure
@Test
void methodName_scenario_expectedOutcome() {
    // Arrange — set up the test
    User user = new User(...);
    when(repository.findById(1L)).thenReturn(Optional.of(user));

    // Act — call the method under test
    UserResponse response = service.getUser(1L);

    // Assert — verify the outcome
    assertThat(response.getEmail()).isEqualTo("alice@test.com");
    verify(repository).findById(1L);
}
```

### Test Method Naming Convention

```
methodName_scenario_expectedOutcome

getProduct_notFound_throwsException       ✅ clear
getProduct_whenIdIsNullAndDbIsEmpty_...   ❌ too long
test1                                      ❌ meaningless
```

---

## Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=JwtUtilTest

# Run specific test method
mvn test -Dtest=AuthServiceTest#register_emailAlreadyExists_throwsConflict

# Run tests with the 'test' profile (loads application-test.properties)
mvn test -Dspring.profiles.active=test
```

---

## Common Bugs

| Bug | Cause | Fix |
|---|---|---|
| `NullPointerException` on mock | `@Mock` not initialized | Add `@ExtendWith(MockitoExtension.class)` |
| Test hits real database | Missing `application-test.properties` | Create test properties with H2 |
| `401 Unauthorized` in @WebMvcTest | Security context empty | Add `@WithMockUser` or make endpoint public |
| `UnnecessaryStubbingException` | `when()` set up but never called | Remove unused stubbing or use `lenient()` |
| `@MockBean` not found | Spring Boot 3.4+ changed to `@MockitoBean` | Pin to Spring Boot 3.3.x or use `@MockitoBean` |
| All tests share state | H2 not reset between tests | Use `create-drop` DDL or `@Transactional` to rollback |

---

## Interview Questions

**Q: What is the difference between @Mock and @MockBean?**
> `@Mock` is a Mockito annotation that creates a mock object — no Spring involvement. `@MockBean` is a Spring Test annotation that creates a mock AND registers it in the Spring ApplicationContext, replacing any real bean of that type. Use `@Mock` in pure unit tests (`@ExtendWith(MockitoExtension.class)`). Use `@MockBean` in Spring tests (`@WebMvcTest`, `@SpringBootTest`).

**Q: What does @WebMvcTest load? What does it NOT load?**
> `@WebMvcTest` loads the web layer only: controllers, filters, security config, `@ControllerAdvice`, Spring MVC infrastructure. It does NOT load `@Service`, `@Repository`, or `@Component` beans. Services needed by controllers must be `@MockBean`'d. Result: fast startup (< 3s) for testing HTTP concerns without database or business logic.

**Q: What is MockMvc and how does it differ from TestRestTemplate?**
> `MockMvc` simulates HTTP requests in-process — no real TCP connection, no Tomcat listening on a port. It routes requests through the full filter chain and DispatcherServlet. `TestRestTemplate` starts an actual embedded Tomcat and makes real HTTP calls. MockMvc is faster and used with `@WebMvcTest`. TestRestTemplate is used with `@SpringBootTest(webEnvironment = RANDOM_PORT)` for full integration tests.

**Q: Why would you test a soft-delete separately from a hard delete?**
> Soft delete sets `active=false` instead of calling `repository.delete()`. The test must verify: (1) `save()` was called (not `delete()`), (2) the entity's `active` field is `false`, (3) physical delete methods were never invoked. If you only assert the return value, you might miss a bug where the entity was physically deleted and the service returned a cached/stale response.

**Q: What is test coverage and how much is enough?**
> Test coverage measures what percentage of code lines are executed by tests. 100% coverage doesn't mean zero bugs — you can execute code without verifying correctness. For a Spring Boot backend: aim for 70–80% coverage on business logic (services), focus tests on edge cases and error paths, and don't write tests just to inflate coverage numbers. Controllers and repositories need fewer tests because frameworks test them implicitly.

---

## MFAQ

**Why do we need `application-test.properties`? Can't we just use `application.properties`?**
`application.properties` points to PostgreSQL and Gmail SMTP. Unit tests would fail trying to connect to a real database (no connection in CI/CD). Integration tests would send real emails. `application-test.properties` swaps: PostgreSQL → H2 (in-memory), Gmail → invalid host (emails silently fail). The test profile is activated automatically for tests via `@ActiveProfiles("test")` or `spring.profiles.active=test`.

**What's TestContainers and when would you use it instead of H2?**
TestContainers starts a real PostgreSQL (or any service) in a Docker container during tests. H2 in MODE=PostgreSQL is 95% compatible but can fail on complex JSONB, array types, or PostgreSQL-specific functions. TestContainers guarantees identical behavior to production. Trade-off: requires Docker installed, tests take 10–30s more on first run (container startup). Use H2 for speed, TestContainers when PostgreSQL-specific features matter.

**Should every method have a unit test?**
No. Private methods, getters/setters, and trivial delegation don't need tests. Test behavior, not implementation. Focus on: error paths (what happens when it fails), boundary conditions (empty list, null, max value), business rules (can't order if cart is empty, can't register duplicate email). A useful heuristic: if you've had a bug there before, add a test. If the code is copied from a framework call and has no logic, skip it.
