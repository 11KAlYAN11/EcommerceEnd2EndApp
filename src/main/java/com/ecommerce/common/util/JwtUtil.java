package com.ecommerce.common.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Handles all JWT operations: generate, validate, extract claims.
 *
 * @Component (not @Service): This is a utility, not a business service.
 * Semantically correct — it doesn't orchestrate business logic.
 *
 * SecretKey: JWT HS256 requires minimum 256-bit key.
 * We decode the Base64 secret from properties into raw bytes.
 * Keys.hmacShaKeyFor() wraps it into a safe SecretKey object.
 *
 * Claims: the payload fields inside a JWT.
 *   "sub"  → subject (email/username — who the token belongs to)
 *   "iat"  → issued at (timestamp)
 *   "exp"  → expiration (timestamp — token invalid after this)
 *   Custom claims we add: roles
 */
@Component
@Slf4j
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration; // milliseconds (86400000 = 24 hours)

    private SecretKey getSigningKey() {
        // The secret in properties is plain text. We use its UTF-8 bytes as key.
        // In production: secret should be a long random Base64-encoded string.
        byte[] keyBytes = secret.getBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        // Add roles as a custom claim so the frontend knows user permissions
        claims.put("roles", userDetails.getAuthorities().stream()
                .map(Object::toString)
                .toList());
        return buildToken(claims, userDetails.getUsername());
    }

    private String buildToken(Map<String, Object> extraClaims, String subject) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)               // email goes here
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
