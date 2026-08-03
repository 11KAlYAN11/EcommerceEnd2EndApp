package com.ecommerce.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Same pattern as product-service: no UserDetailsService, no DB lookup.
 * The Authentication's principal is the plain email string (the JWT's
 * `sub` claim) -- CartController reads it straight off via
 * @AuthenticationPrincipal String email.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StatelessJwtAuthFilter extends OncePerRequestFilter {

    private final JwtValidator jwtValidator;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtValidator.parse(authHeader.substring(7));
            if (!jwtValidator.isExpired(claims) && SecurityContextHolder.getContext().getAuthentication() == null) {
                String username = jwtValidator.username(claims);
                var authorities = jwtValidator.roles(claims).stream().map(SimpleGrantedAuthority::new).toList();
                var authToken = new UsernamePasswordAuthenticationToken(username, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.debug("Authenticated (stateless) user: {}", username);
            }
        } catch (Exception e) {
            log.warn("JWT validation failed: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
