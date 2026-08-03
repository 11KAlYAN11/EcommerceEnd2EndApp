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
 * Unchanged from the monolith's JwtUtil — same secret, same claim shape
 * (sub=email, roles=[...], iat, exp). This matters more here than it did
 * in notification-service: whatever token this service issues on
 * login/register must be verifiable by every OTHER service later (cart,
 * order, ...), which means they all need the SAME jwt.secret value —
 * set via the same JWT_SECRET env var everywhere, not a per-service default.
 */
@Component
@Slf4j
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(userDetails, null);
    }

    /**
     * firstName as a claim (Phase 16.5): order-service needs it for
     * notification emails but has no `users` table to look it up in.
     * Cheaper to denormalize a small, rarely-changing field into the token
     * at issue-time than to add a whole new service-to-service call just to
     * fetch a name. Trade-off: if a user renames themselves mid-token-life,
     * downstream services see the old name until they log in again (same
     * staleness-until-expiry trade-off as the roles claim already has).
     */
    public String generateToken(UserDetails userDetails, String firstName) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", userDetails.getAuthorities().stream()
                .map(Object::toString)
                .toList());
        if (firstName != null) {
            claims.put("firstName", firstName);
        }
        return buildToken(claims, userDetails.getUsername());
    }

    private String buildToken(Map<String, Object> extraClaims, String subject) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
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
