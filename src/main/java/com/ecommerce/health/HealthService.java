package com.ecommerce.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;

/**
 * Business logic for health checks.
 *
 * Why a separate Service for something this simple?
 *   Discipline. In real applications, a health check might:
 *     - Check DB connectivity
 *     - Check Redis connectivity
 *     - Check Kafka connectivity
 *     - Check disk space
 *   If all of that logic lived in the Controller, it would become
 *   hundreds of lines and impossible to test. The Controller's job
 *   is ONLY to handle HTTP. Business logic belongs in the Service.
 *
 * @Service:
 *   Registers this class as a Spring Bean in the IoC container.
 *   Spring creates ONE instance (singleton by default) and manages it.
 *
 * @Slf4j (Lombok):
 *   Injects a Logger field: private static final Logger log = ...
 *   Equivalent to: Logger log = LoggerFactory.getLogger(HealthService.class)
 *   We use this to log what the service is doing.
 *
 * @Value("${...}"):
 *   Reads a value from application.properties and injects it here.
 *   This is how Spring reads config — no hardcoded strings.
 *
 * Environment:
 *   Spring's Environment object lets us inspect active profiles,
 *   properties, and system properties at runtime.
 *   Spring injects it via constructor injection.
 */
@Service
@Slf4j
public class HealthService {

    @Value("${info.app.version:1.0.0}")
    private String appVersion;

    @Value("${health.db-check-enabled:true}")
    private boolean isDbCheckEnabled;

    private final Environment environment;

    // Constructor injection — Spring sees this single constructor and
    // automatically injects Environment without needing @Autowired
    public HealthService(Environment environment) {
        this.environment = environment;
    }

    /**
     * Returns the current health status of the application.
     *
     * ManagementFactory.getRuntimeMXBean().getUptime():
     *   This is a JVM built-in. The MXBean (Management Bean) exposes
     *   JVM internals like uptime, heap usage, thread count, etc.
     *   We use it to show how long the app has been running.
     */
    public HealthStatus getHealth() {
        log.debug("Health check requested");

        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        String[] activeProfiles = environment.getActiveProfiles();
        String activeProfile = activeProfiles.length > 0 ? activeProfiles[0] : "default";

        log.info("Health check OK — uptime: {}s, profile: {}", uptimeMs / 1000, activeProfile);

        return HealthStatus.builder()
                .status("UP")
                .service("Ecommerce Backend API")
                .version(appVersion)
                .profile(activeProfile)
                .uptimeSeconds(uptimeMs / 1000)
                .build();
    }

    /**
     * Simulates a database health check.
     * In Phase 2+, this will do a real SELECT 1 query against PostgreSQL.
     * For now it demonstrates the pattern.
     */
    public HealthStatus getDatabaseHealth() {
        log.debug("Database health check requested");

        // Phase 2: replace this with real DB ping
        // DataSource ds = ... ; ds.getConnection().isValid(1)
        boolean dbReachable = simulateDatabasePing();
        if(!isDbCheckEnabled) {
            // If DB check is disabled via config, we log and return UP
            log.warn("Database health check is disabled via configuration. Returning UP without checking.");
            return HealthStatus.builder()
                    .status("UP")
                    .service("PostgreSQL")
                    .build();
        }

        return HealthStatus.builder()
                .status(dbReachable ? "UP" : "DOWN")
                .service("PostgreSQL")
                .build();
    }

    private boolean simulateDatabasePing() {
        // Will be replaced with real DB check in Phase 2
        return true;
    }

    public HealthStatus getDatabaseMemoryHealth() {
        log.debug("Database memory health check is requested: ");

        // Phase 2: replace this with real DB memory check

        boolean dbMemoryOK = simulateDatabasePing();

        return HealthStatus.builder()
                .status(dbMemoryOK ? "OK" : "NOT OK")
                .service("PostgreSQL")
                .build();

    }

}
