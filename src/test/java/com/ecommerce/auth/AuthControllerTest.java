package com.ecommerce.auth;

import com.ecommerce.auth.dto.AuthResponse;
import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.RegisterRequest;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.util.JwtUtil;
import com.ecommerce.observability.MetricsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import com.ecommerce.config.JwtAuthFilter;
import com.ecommerce.config.SecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WEB LAYER TEST for AuthController.
 *
 * @WebMvcTest(AuthController.class):
 *   Loads ONLY the web layer — controller, filters, security config.
 *   Does NOT load @Service, @Repository, @Component beans by default.
 *   Result: fast startup (< 2s), tests HTTP concerns without DB.
 *
 *   What IS loaded:
 *     - AuthController (the class under test)
 *     - SecurityConfig (with JwtAuthFilter)
 *     - GlobalExceptionHandler (@RestControllerAdvice)
 *     - Spring MVC infrastructure (DispatcherServlet, Jackson, etc.)
 *
 *   What is NOT loaded (must be @MockBean):
 *     - AuthService (business logic)
 *     - JwtUtil (used by JwtAuthFilter to parse tokens)
 *     - UserDetailsService (used by SecurityConfig to authenticate)
 *
 * MockMvc:
 *   Simulates HTTP requests without starting a real server.
 *   Tests the full request → filter chain → controller → response cycle.
 *   Much faster than starting Tomcat (TestRestTemplate / @SpringBootTest).
 *
 * @MockBean vs @Mock:
 *   @Mock (Mockito): just creates a mock object, no Spring involvement
 *   @MockBean (Spring): creates a mock AND registers it in the Spring
 *     ApplicationContext, replacing any real bean with that type.
 *   In @WebMvcTest: always use @MockBean, not @Mock.
 *
 * jsonPath("$.field"):
 *   JSONPath assertion on the response body.
 *   "$.data.token" = root > "data" field > "token" field
 *   "$.success" = root > "success" boolean
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "cors.allowed-origins=http://localhost:3000")
class AuthControllerTest {

    @Autowired MockMvc     mockMvc;
    @Autowired ObjectMapper objectMapper; // Jackson — serializes objects to JSON

    // The controller's real dependency — mocked so no DB/business logic runs
    @MockBean AuthService authService;

    // Security infrastructure — @WebMvcTest loads SecurityConfig which needs these
    @MockBean JwtUtil            jwtUtil;
    @MockBean UserDetailsService userDetailsService;

    // MetricsService uses Micrometer counters not available in web slice
    @MockBean MetricsService metricsService;

    // @EnableJpaAuditing on main class triggers JPA metamodel check in web slice — mock it
    @MockBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    // ── POST /auth/register ───────────────────────────────────────────────────

    @Test
    @DisplayName("POST /auth/register with valid body → 201 Created with token")
    void register_validRequest_returns201() throws Exception {
        // Arrange
        RegisterRequest req = buildRegisterRequest("alice@test.com", "Alice", "Smith", "pass123");
        AuthResponse authResponse = AuthResponse.builder()
                .token("mock-jwt-token")
                .email("alice@test.com")
                .firstName("Alice")
                .lastName("Smith")
                .roles(Set.of("ROLE_USER"))
                .build();

        when(authService.register(any(RegisterRequest.class))).thenReturn(authResponse);

        // Act + Assert
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())                              // 201
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").value("mock-jwt-token"))
                .andExpect(jsonPath("$.data.email").value("alice@test.com"))
                .andExpect(jsonPath("$.data.firstName").value("Alice"));
    }

    @Test
    @DisplayName("POST /auth/register with blank email → 400 Bad Request (validation)")
    void register_blankEmail_returns400() throws Exception {
        // Arrange — @Email @NotBlank validation should reject this
        RegisterRequest req = buildRegisterRequest("", "Alice", "Smith", "pass123");

        // Act + Assert — Spring's @Valid kicks in BEFORE the controller method runs
        // GlobalExceptionHandler catches MethodArgumentNotValidException → 400
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /auth/register with short password → 400 Bad Request (validation)")
    void register_shortPassword_returns400() throws Exception {
        RegisterRequest req = buildRegisterRequest("alice@test.com", "Alice", "Smith", "abc"); // < 6 chars

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /auth/register with duplicate email → 409 Conflict")
    void register_duplicateEmail_returns409() throws Exception {
        RegisterRequest req = buildRegisterRequest("alice@test.com", "Alice", "Smith", "pass123");

        // AuthService throws ConflictException → GlobalExceptionHandler → 409
        when(authService.register(any())).thenThrow(new ConflictException("Email already registered: alice@test.com"));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Email already registered: alice@test.com"));
    }

    // ── POST /auth/login ──────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /auth/login with valid credentials → 200 OK with token")
    void login_validCredentials_returns200() throws Exception {
        // Arrange
        LoginRequest req = new LoginRequest();
        req.setEmail("alice@test.com");
        req.setPassword("pass123");

        AuthResponse authResponse = AuthResponse.builder()
                .token("login-token")
                .email("alice@test.com")
                .firstName("Alice")
                .lastName("Smith")
                .roles(Set.of("ROLE_USER"))
                .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

        // Act + Assert
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").value("login-token"));
    }

    @Test
    @DisplayName("POST /auth/login with missing body → 400 Bad Request")
    void login_missingBody_returns400() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")) // empty JSON — violates @NotBlank constraints
                .andExpect(status().isBadRequest());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private RegisterRequest buildRegisterRequest(
            String email, String firstName, String lastName, String password) {
        RegisterRequest req = new RegisterRequest();
        req.setEmail(email);
        req.setFirstName(firstName);
        req.setLastName(lastName);
        req.setPassword(password);
        return req;
    }
}
