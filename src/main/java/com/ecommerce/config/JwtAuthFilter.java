package com.ecommerce.config;

import com.ecommerce.common.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT authentication filter — runs once per HTTP request.
 *
 * OncePerRequestFilter: Spring guarantees this filter runs exactly once
 * per request, even in complex dispatch scenarios (forwards, includes).
 *
 * Flow for every incoming request:
 * 1. Extract "Authorization: Bearer <token>" header
 * 2. If missing → skip (public endpoint or will fail at Security layer)
 * 3. Extract username from token
 * 4. Load UserDetails from DB (ensures user still exists and is enabled)
 * 5. Validate token (signature + expiry)
 * 6. If valid → set Authentication in SecurityContext
 *    (this tells Spring Security: "this request is authenticated as X")
 * 7. Continue down the filter chain
 *
 * SecurityContextHolder:
 *   Thread-local storage for the current authenticated user.
 *   Once we set authentication here, @PreAuthorize and hasRole() work
 *   automatically for the rest of this request's lifecycle.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // No auth header or not a Bearer token → pass through (let Security decide)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7); // strip "Bearer "
        final String username;

        try {
            username = jwtUtil.extractUsername(jwt);
        } catch (Exception e) {
            // Malformed or tampered token → log and pass through
            // SecurityContext remains empty → request will get 401
            log.warn("JWT parsing failed: {}", e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        // Only authenticate if not already authenticated (e.g. nested filters)
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            if (jwtUtil.isTokenValid(jwt, userDetails)) {
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null, // credentials not needed after auth
                                userDetails.getAuthorities()
                        );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.debug("Authenticated user: {}", username);
            }
        }

        filterChain.doFilter(request, response);
    }
}
