package com.ecommerce.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Phase 15 — Security Response Headers.
 *
 * PROBLEM: Browser-based clients (React, Angular, etc.) are vulnerable to
 * certain attacks by default. We can instruct browsers to protect users
 * by sending specific HTTP response headers.
 *
 * These headers are part of defense-in-depth — even if there's an XSS bug
 * in the frontend, some headers limit the damage.
 *
 * ── X-Content-Type-Options: nosniff ──────────────────────────────────────
 *   ATTACK: MIME Sniffing
 *   Without this: browser ignores Content-Type and guesses from content.
 *   Attack: upload "evil.gif" that's actually JavaScript. Browser sniffs it,
 *   executes it as JS → XSS attack via file upload.
 *
 *   With nosniff: browser strictly respects Content-Type.
 *   "This is image/gif? I'll display it as an image. Full stop."
 *
 * ── X-Frame-Options: DENY ────────────────────────────────────────────────
 *   ATTACK: Clickjacking
 *   Without this: attacker loads your site in an invisible <iframe>,
 *   overlays it with "Click here to win!" → user clicks on your action button.
 *   Can steal clicks on "Delete account", "Transfer money", etc.
 *
 *   DENY: browser refuses to render page inside any frame or iframe.
 *   SAMEORIGIN: only same domain can frame it.
 *
 * ── X-XSS-Protection: 1; mode=block ─────────────────────────────────────
 *   Legacy XSS filter built into older browsers (IE, old Chrome).
 *   Modern browsers use CSP instead, but setting this provides safety
 *   for clients on older browsers.
 *   mode=block: stops page rendering if attack detected (vs sanitizing).
 *
 * ── Strict-Transport-Security (HSTS) ─────────────────────────────────────
 *   ATTACK: SSL Stripping
 *   Without this: user types "myapp.com" → browser tries HTTP first →
 *   attacker intercepts, downgrades to HTTP before redirect to HTTPS.
 *   User never knows they're on an insecure connection.
 *
 *   With HSTS:
 *   First visit (HTTPS): server says "For the next year, ALWAYS use HTTPS.
 *   Never try HTTP first."
 *   Browser caches this — all future requests go HTTPS directly.
 *   Attacker can't intercept the initial upgrade.
 *
 *   max-age=31536000 = 1 year (seconds)
 *   includeSubDomains: applies to all subdomains too
 *
 *   NOTE: Only send HSTS over HTTPS! Over HTTP it's ignored/dangerous.
 *   In dev (HTTP): this header is harmless but technically unnecessary.
 *
 * ── Referrer-Policy: strict-origin-when-cross-origin ─────────────────────
 *   Controls what URL is sent in the Referer header when following links.
 *
 *   Without this: user on https://yourapp.com/cart?promo=SECRET clicks
 *   a link to google.com. Google's server sees:
 *   Referer: https://yourapp.com/cart?promo=SECRET
 *   → query param leaked to third party!
 *
 *   strict-origin-when-cross-origin:
 *   - Same origin (yourapp.com → yourapp.com): full URL sent
 *   - Cross-origin (yourapp.com → google.com): only origin sent (https://yourapp.com)
 *   - Downgrade (HTTPS → HTTP): nothing sent
 *
 * ── Content-Security-Policy ───────────────────────────────────────────────
 *   ATTACK: XSS (Cross-Site Scripting)
 *   Without this: attacker injects <script>evil()</script> into your page.
 *   Browser executes it — steals cookies, tokens, keystrokes.
 *
 *   CSP tells the browser WHERE scripts can come from.
 *   "Only execute scripts from 'self' (same domain). Block everything else."
 *
 *   Our API is JSON-only — no HTML/scripts.
 *   For a REST API: "default-src 'none'" is safe.
 *   For a full-stack app with templates: you'd configure script-src specifically.
 *
 *   NOTE: CSP is complex. The value here is API-safe but would block a browser
 *   from rendering HTML from this server (fine since we only serve JSON).
 */
@Component
@Order(3)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        boolean isSwagger = path.contains("/swagger-ui") || path.contains("/v3/api-docs");

        // Prevent MIME sniffing
        response.setHeader("X-Content-Type-Options", "nosniff");

        // Prevent clickjacking
        response.setHeader("X-Frame-Options", "DENY");

        // Legacy XSS protection (modern browsers use CSP)
        response.setHeader("X-XSS-Protection", "1; mode=block");

        // HTTPS enforcement (HSTS)
        response.setHeader("Strict-Transport-Security",
                "max-age=31536000; includeSubDomains");

        // Referrer policy — don't leak URL params to third parties
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // CSP — Swagger UI needs inline scripts/styles and self-hosted assets.
        // All other API paths keep the strict "block everything" policy.
        if (isSwagger) {
            response.setHeader("Content-Security-Policy",
                    "default-src 'self'; " +
                    "script-src 'self' 'unsafe-inline'; " +
                    "style-src 'self' 'unsafe-inline'; " +
                    "img-src 'self' data:; " +
                    "font-src 'self' data:; " +
                    "connect-src 'self'");
        } else {
            response.setHeader("Content-Security-Policy",
                    "default-src 'none'; frame-ancestors 'none'");
        }

        // Explicitly disable client-side caching for API responses
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");

        chain.doFilter(request, response);
    }
}
