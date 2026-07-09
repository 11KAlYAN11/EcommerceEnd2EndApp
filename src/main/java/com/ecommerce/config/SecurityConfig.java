package com.ecommerce.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Central Spring Security configuration.
 *
 * @EnableWebSecurity: activates Spring Security's web security support.
 * @EnableMethodSecurity: enables @PreAuthorize on individual methods.
 *   e.g. @PreAuthorize("hasRole('ADMIN')") on a controller method.
 *
 * Key decisions explained:
 *
 * CSRF disabled:
 *   CSRF (Cross-Site Request Forgery) protection uses session cookies.
 *   JWT APIs are stateless — no session cookies → CSRF is irrelevant.
 *   Disabling it removes unnecessary overhead.
 *
 * SessionCreationPolicy.STATELESS:
 *   Spring Security will NOT create or use HTTP sessions.
 *   Every request must carry its JWT. There is no "logged in session."
 *   This is what makes REST APIs horizontally scalable — any server
 *   can handle any request because state is in the token, not the server.
 *
 * BCryptPasswordEncoder:
 *   BCrypt is a password hashing function designed to be slow.
 *   "Work factor" 10 means 2^10 = 1024 iterations.
 *   Even if the DB is breached, cracking hashed passwords takes years.
 *   NEVER store plain text passwords. NEVER use MD5/SHA1 for passwords.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    @Value("${cors.allowed-origins:*}")
    private String allowedOrigins;

    private static final String[] PUBLIC_URLS = {
            "/auth/**",
            "/health/**",
            "/actuator/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/dev/**"          // dev-only bootstrap endpoints (disable in prod)
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                    // OPTIONS preflight: browser sends this BEFORE every cross-origin POST/PUT/DELETE
                    // with an Authorization header. Must be permitted without JWT or preflight fails
                    // and the real request never goes through.
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers(PUBLIC_URLS).permitAll()
                    // Anyone can browse products, categories, search, and uploaded images
                    .requestMatchers(HttpMethod.GET, "/products/**", "/categories/**", "/search/**", "/files/**").permitAll()
                    .anyRequest().authenticated()
            )
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authenticationProvider(authenticationProvider())
            // Our JWT filter runs BEFORE Spring's built-in username/password filter
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // work factor 10 by default
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Use allowedOriginPatterns (supports wildcards) + allowCredentials=true.
        // allowedOrigins("*") + allowCredentials=true is INVALID — browser rejects it.
        // allowedOriginPatterns("*") + allowCredentials=true IS valid.
        // In prod, ALLOWED_ORIGINS env var contains actual domains, not "*".
        List<String> origins = List.of(allowedOrigins.split(","));
        config.setAllowedOriginPatterns(origins);

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));
        config.setExposedHeaders(List.of("X-Correlation-Id"));  // let frontend read this header
        config.setAllowCredentials(true);
        config.setMaxAge(3600L); // cache preflight for 1 hour — avoids preflight on every request

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
