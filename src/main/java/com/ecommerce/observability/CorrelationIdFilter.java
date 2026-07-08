package com.ecommerce.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Phase 14 — Correlation ID Filter.
 *
 * PROBLEM IT SOLVES:
 *   In production, many requests come in at the same time. Their log lines
 *   are interleaved. If a user reports a bug, how do you find ALL the log
 *   lines for their specific request among thousands of others?
 *
 *   Answer: every request gets a unique ID. Every log line for that request
 *   includes that ID. You can grep the ID → see the full story of one request.
 *
 * HOW IT WORKS:
 *   1. Client sends request, optionally with header: X-Correlation-Id: abc-123
 *   2. If header present: use it (client tracing — tracks across services)
 *   3. If not: generate a UUID (server-side correlation)
 *   4. Put the ID into MDC (Mapped Diagnostic Context) — SLF4J's per-thread storage
 *   5. Every log.info/warn/error call in the same thread includes the MDC value
 *   6. Return the ID in the response header (client can see which ID to search)
 *   7. ALWAYS remove from MDC in finally block — thread pools reuse threads,
 *      leftover MDC values would bleed into next request
 *
 * MDC (Mapped Diagnostic Context):
 *   - ThreadLocal map provided by SLF4J/Logback
 *   - Key-value pairs added here appear in every log line for this thread
 *   - In logback pattern: %X{correlationId} prints the MDC value
 *   - Pattern set in application.properties:
 *     logging.pattern.console=%d [%thread] [%X{correlationId}] %-5level %msg%n
 *
 * EXAMPLE LOG OUTPUT WITHOUT correlation ID:
 *   14:22:01 [http-nio-1] INFO  ProductService - Product fetched: id=5
 *   14:22:01 [http-nio-2] INFO  ProductService - Product fetched: id=8
 *   14:22:01 [http-nio-1] ERROR OrderService - Out of stock: id=5
 *   → Which error belongs to which request?
 *
 * EXAMPLE LOG OUTPUT WITH correlation ID:
 *   14:22:01 [http-nio-1] [a1b2c3d4] INFO  ProductService - Product fetched: id=5
 *   14:22:01 [http-nio-2] [e5f6g7h8] INFO  ProductService - Product fetched: id=8
 *   14:22:01 [http-nio-1] [a1b2c3d4] ERROR OrderService - Out of stock: id=5
 *   → Search "a1b2c3d4" → see the full lifecycle of request 1
 *
 * @Order(1): this filter runs FIRST, before JWT filter, so all subsequent
 *   filters and controllers see the correlationId in MDC.
 *
 * OncePerRequestFilter: guaranteed to execute exactly once per request
 *   (Spring may forward requests internally; this prevents double-execution).
 */
@Component
@Order(1)
@Slf4j
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        // Accept incoming correlation ID (from API gateway, mobile app, etc.)
        // or generate a new one for requests that don't carry one
        String correlationId = request.getHeader(CORRELATION_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }

        // Put into MDC — all log statements in this thread will include it
        MDC.put(MDC_KEY, correlationId);

        // Echo back in response — useful for clients and API consumers
        response.setHeader(CORRELATION_HEADER, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            // CRITICAL: remove from MDC after request completes.
            // Threads in a pool are reused. If we don't clear MDC,
            // the next request on this thread inherits the previous ID.
            MDC.remove(MDC_KEY);
        }
    }
}
