package com.ecommerce.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 15 — IP-Based Rate Limiter.
 *
 * PROBLEM IT SOLVES:
 *   Without rate limiting, a single client can:
 *   - Scrape your entire product catalog in seconds
 *   - Attempt thousands of password combinations (brute force)
 *   - Send thousands of orders per second (abuse / DoS)
 *   - Exhaust your DB connection pool → all users experience 503
 *
 * ALGORITHM: Sliding Window (Token Bucket alternative)
 *
 *   For each IP, we maintain a deque (double-ended queue) of timestamps.
 *   On each request:
 *     1. Remove timestamps older than 1 minute from the front (expired)
 *     2. Add current timestamp to the back
 *     3. If deque size > MAX_REQUESTS: reject with 429
 *     4. Otherwise: let request through
 *
 *   This is "sliding window log" — more accurate than fixed windows
 *   (fixed window: 100 requests at 00:59 + 100 at 01:00 = 200 in 2 seconds — bad)
 *   (sliding window: always at most 100 requests in any 60-second window)
 *
 * EXAMPLE:
 *   IP 192.168.1.1 sends 100 requests between 14:00:00 and 14:00:59 → all pass
 *   Request 101 at 14:00:59 → deque has 100 entries in last 60s → REJECTED (429)
 *   At 14:01:01, the first 2 requests expire → deque shrinks to 98 → next request PASSES
 *
 * THREAD SAFETY:
 *   ConcurrentHashMap handles concurrent IP-key lookups safely.
 *   compute() is atomic for the key — prevents race conditions when two
 *   requests for the same IP arrive simultaneously.
 *   The Deque itself is NOT thread-safe, but since compute() is atomic,
 *   only one thread modifies a given IP's deque at a time.
 *
 * LIMITATIONS (production would address these):
 *   - In-memory: rate limit resets on app restart
 *   - Single-node: if you have 3 app instances, each has its own counter
 *     → effective limit becomes MAX_REQUESTS * 3 per IP
 *   - Production fix: Redis-based rate limiting (e.g., Bucket4j + Redis)
 *     → shared counter across all instances
 *   - Memory growth: one entry per unique IP — clean up inactive IPs periodically
 *
 * @Order(2): runs after CorrelationIdFilter (Order 1) so the correlation ID
 *   is already in MDC when we log the rate limit violation.
 */
@Component
@Order(2)
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    /** Kill switch — set RATELIMIT_ENABLED=false to bypass entirely (e.g. load testing). */
    @Value("${ratelimit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${ratelimit.max-requests:100}")
    private int maxRequests;          // per IP per window

    @Value("${ratelimit.window-ms:60000}")
    private long windowMs;            // sliding window size

    // IP → timestamps of recent requests (sliding window log)
    // ConcurrentHashMap: thread-safe for concurrent request handling
    private final ConcurrentHashMap<String, Deque<Long>> requestLog = new ConcurrentHashMap<>();

    // Actuator endpoints should not be rate-limited (needed for health checks)
    private static final String ACTUATOR_PREFIX = "/actuator";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        // Kill switch — bypass everything when disabled (load testing etc.)
        if (!rateLimitEnabled) {
            chain.doFilter(request, response);
            return;
        }

        // Skip rate limiting for actuator health checks
        if (request.getRequestURI().startsWith(ACTUATOR_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = extractClientIp(request);
        long now = System.currentTimeMillis();

        // compute() is atomic — thread-safe modification per IP key
        requestLog.compute(clientIp, (ip, timestamps) -> {
            if (timestamps == null) {
                timestamps = new ArrayDeque<>();
            }
            // Slide the window: remove timestamps older than the window
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMs) {
                timestamps.pollFirst();
            }
            timestamps.addLast(now);
            return timestamps;
        });

        int requestCount = requestLog.get(clientIp).size();

        if (requestCount > maxRequests) {
            log.warn("Rate limit exceeded for IP: {} ({} requests in {}s)",
                    clientIp, requestCount, windowMs / 1000);

            response.setStatus(429); // 429 Too Many Requests
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"success\":false,\"message\":\"Too many requests. Limit: "
                    + maxRequests + " per minute per IP.\"}");
            return; // DO NOT call chain.doFilter — request is rejected
        }

        chain.doFilter(request, response);
    }

    /**
     * Extracts the real client IP address.
     *
     * WHY NOT just use request.getRemoteAddr()?
     *   Behind a load balancer or reverse proxy (nginx, AWS ALB):
     *   request.getRemoteAddr() returns the PROXY's IP (e.g., 10.0.0.1)
     *   ALL requests look like they come from the same source → rate limit fires immediately.
     *
     *   Load balancers forward the real client IP in X-Forwarded-For:
     *   X-Forwarded-For: 203.0.113.5, 10.0.0.1 (left to right: client → proxies)
     *   We take the leftmost (first) value = the real client IP.
     *
     * SECURITY NOTE:
     *   X-Forwarded-For can be spoofed by clients in some configurations.
     *   In production: configure your proxy to strip and re-set this header.
     *   Or use X-Real-IP header (nginx sets this reliably).
     */
    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // Multiple proxies: "203.0.113.5, 10.0.0.1, 172.16.0.1"
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }
}
