package com.ecommerce.health;

import com.ecommerce.common.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit test for HealthController.
 *
 * ─────────────────────────────────────────────────────────────
 * @WebMvcTest(HealthController.class)
 * ─────────────────────────────────────────────────────────────
 *   Loads ONLY the web layer — Controllers, Filters, Converters.
 *   Does NOT load: Services, Repositories, Database connections.
 *
 *   Why? Unit tests should test ONE thing in isolation.
 *   We want to test: "Does HealthController handle HTTP correctly?"
 *   Not: "Does the whole app work end-to-end?"
 *
 *   This makes tests fast (no DB startup) and focused.
 *
 * ─────────────────────────────────────────────────────────────
 * MockMvc
 * ─────────────────────────────────────────────────────────────
 *   Simulates HTTP requests without starting a real server.
 *   We can call endpoints and assert on responses — without Postman.
 *   This is how you test controllers in Spring Boot.
 *
 * ─────────────────────────────────────────────────────────────
 * @MockitoBean
 * ─────────────────────────────────────────────────────────────
 *   Creates a Mockito mock of HealthService and puts it in the
 *   Spring context. The controller gets this mock injected instead
 *   of the real HealthService. We then control what the mock returns
 *   using when(...).thenReturn(...).
 *
 *   Why mock the service?
 *   We're testing the CONTROLLER, not the service.
 *   The service has its own test. Here we just say:
 *   "Assume the service returns THIS — does the controller respond correctly?"
 */
@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HealthService healthService;

    @Test
    @DisplayName("GET /health should return 200 with UP status")
    void checkHealth_shouldReturn200WithUpStatus() throws Exception {
        // ARRANGE — set up what the mock service should return
        HealthStatus mockStatus = HealthStatus.builder()
                .status("UP")
                .service("Ecommerce Backend API")
                .version("1.0.0")
                .profile("test")
                .uptimeSeconds(42L)
                .build();

        when(healthService.getHealth()).thenReturn(mockStatus);

        // ACT + ASSERT — perform the HTTP call and verify the response
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Application is healthy"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.service").value("Ecommerce Backend API"));
    }

    @Test
    @DisplayName("GET /health/db should return 200 when DB is UP")
    void checkDatabaseHealth_shouldReturn200WhenDbUp() throws Exception {
        HealthStatus mockDbStatus = HealthStatus.builder()
                .status("UP")
                .service("PostgreSQL")
                .build();

        when(healthService.getDatabaseHealth()).thenReturn(mockDbStatus);

        mockMvc.perform(get("/health/db"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    @DisplayName("GET /health/db should return 503 when DB is DOWN")
    void checkDatabaseHealth_shouldReturn503WhenDbDown() throws Exception {
        HealthStatus mockDbStatus = HealthStatus.builder()
                .status("DOWN")
                .service("PostgreSQL")
                .build();

        when(healthService.getDatabaseHealth()).thenReturn(mockDbStatus);

        mockMvc.perform(get("/health/db"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false));
    }
}
