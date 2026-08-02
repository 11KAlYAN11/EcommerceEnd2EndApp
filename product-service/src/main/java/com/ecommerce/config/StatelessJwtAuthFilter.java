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
import java.util.List;

/**
 * The rewrite flagged back in user-service's doc (16.2): no UserDetailsService,
 * no DB call. Everything needed (username, roles) is already IN the token,
 * signed by user-service. Trust it, don't re-derive it.
 *
 *   user-service's filter:  token -> extract username -> DB lookup -> Authentication
 *   this filter:            token -> extract username + roles -> Authentication
 *
 * Trade-off: if a user is disabled/banned in user-service, this service
 * won't know until the token expires (max 24h) — there's no DB to check
 * "is this account still enabled" against. That staleness window is the
 * accepted cost of not calling another service on every single request.
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
                List<String> roles = jwtValidator.roles(claims);

                var authorities = roles.stream().map(SimpleGrantedAuthority::new).toList();
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
