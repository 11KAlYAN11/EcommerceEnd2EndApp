package com.ecommerce.health;

import com.ecommerce.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP handler for health-check endpoints.
 *
 * ─────────────────────────────────────────────────────────────
 * @RestController
 * ─────────────────────────────────────────────────────────────
 *   = @Controller + @ResponseBody
 *
 *   @Controller: registers this as a Spring Bean AND marks it as
 *   an MVC controller — Spring's DispatcherServlet will route
 *   HTTP requests to methods in this class.
 *
 *   @ResponseBody: tells Spring to serialize the return value of
 *   every method to JSON (using Jackson) and write it to the
 *   HTTP response body directly. Without this, Spring would try
 *   to find an HTML view template (like Thymeleaf).
 *
 *   Since REST APIs always return JSON, @RestController is used
 *   instead of @Controller for every REST endpoint.
 *
 * ─────────────────────────────────────────────────────────────
 * @RequestMapping("/health")
 * ─────────────────────────────────────────────────────────────
 *   Sets the base URL path for ALL methods in this controller.
 *   Combined with server.servlet.context-path=/api:
 *     Full URL = http://localhost:8080/api/health
 *
 * ─────────────────────────────────────────────────────────────
 * ResponseEntity<T>
 * ─────────────────────────────────────────────────────────────
 *   Represents the FULL HTTP response: status code + headers + body.
 *   Without it, Spring always returns 200 OK.
 *   With it, we control exactly what HTTP status code is returned.
 *
 *   ResponseEntity.ok(body) → 200 OK with body
 *   ResponseEntity.status(HttpStatus.CREATED).body(b) → 201 with body
 *   ResponseEntity.notFound().build() → 404 with no body
 *
 *   In REST APIs, correct HTTP status codes are part of the contract.
 *   200 means "success", 201 means "created", 404 means "not found",
 *   500 means "server error". Clients use these to handle responses.
 */
@RestController
@RequestMapping("/health")
@Slf4j
public class HealthController {

    // Constructor injection — HealthService is injected by Spring IoC container
    // Spring finds the HealthService bean and passes it here automatically
    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    /**
     * GET /api/health
     *
     * @GetMapping: handles HTTP GET requests to this controller's base path (/health)
     *
     * Request flow:
     *   Postman: GET http://localhost:8080/api/health
     *   → Tomcat receives request
     *   → DispatcherServlet finds this method via @GetMapping
     *   → Spring calls checkHealth()
     *   → healthService.getHealth() runs business logic
     *   → HealthStatus object returned
     *   → Wrapped in ApiResponse
     *   → Jackson converts ApiResponse to JSON
     *   → JSON written to HTTP response body
     *   → 200 OK sent back to Postman
     */
    @GetMapping
    public ResponseEntity<ApiResponse<HealthStatus>> checkHealth() {
        log.info("GET /api/health called");
        HealthStatus status = healthService.getHealth();
        return ResponseEntity.ok(
                ApiResponse.success("Application is healthy", status)
        );
    }

    /**
     * GET /api/health/db
     *
     * @GetMapping("/db"): handles GET requests to /health/db
     * The path is relative to the class-level @RequestMapping
     * Full path: /api/health/db
     *
     * Why a separate database health endpoint?
     *   In production, load balancers call /health to decide if traffic
     *   should be routed to this instance. If DB is down, /health/db
     *   returns DOWN, and ops team is alerted — but the main /health
     *   might still return UP (app is running, just can't reach DB).
     *   Different endpoints give different granularity.
     */
    @GetMapping("/db")
    public ResponseEntity<ApiResponse<HealthStatus>> checkDatabaseHealth() {
        log.info("GET /api/health/db called");
        HealthStatus dbStatus = healthService.getDatabaseHealth();

        if ("UP".equals(dbStatus.getStatus())) {
            return ResponseEntity.ok(
                    ApiResponse.success("Database is reachable", dbStatus)
            );
        } else {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Database is unreachable"));
        }
    }

    @GetMapping("/memory")
    public ResponseEntity<ApiResponse<HealthStatus>> getDatabaseMemoryHealth() {
        log.info("GET /api/health/memory called");
        HealthStatus dbMemory = healthService.getDatabaseMemoryHealth();

        if("OK".equals(dbMemory.getStatus())){
            return ResponseEntity.ok(
                    ApiResponse.success("Database memory is OK", dbMemory)
            );
        } else {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Database memory is not OK"));
        }
    }
}
