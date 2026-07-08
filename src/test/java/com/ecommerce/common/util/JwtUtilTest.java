package com.ecommerce.common.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WHAT WE ARE TESTING: JwtUtil — the class responsible for generating,
 * parsing, and validating JWT tokens.
 *
 * WHY THIS IS A "PURE UNIT TEST":
 *   - No Spring ApplicationContext loaded (@SpringBootTest NOT used)
 *   - No database, no HTTP server, no Redis
 *   - We directly instantiate JwtUtil and set its @Value fields
 *     using ReflectionTestUtils (Spring test utility)
 *   - Speed: pure unit tests run in < 100ms vs 5-10s for Spring context tests
 *
 * ReflectionTestUtils.setField(object, "fieldName", value):
 *   - Bypasses Java's access modifiers (private fields become settable)
 *   - Used specifically to inject @Value fields in Spring beans without
 *     starting a full Spring context
 *   - Only for testing — never use reflection in production code
 *
 * WHAT WE ARE LEARNING:
 *   How JWTs actually work: generate → sign → parse → validate
 *   The token is only valid if: correct username + not expired + same secret
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;

    // Same secret used in application-test.properties
    private static final String SECRET =
            "test-secret-key-for-unit-testing-min-256-bits-pad0000000000";
    private static final long EXPIRATION_MS = 3_600_000L; // 1 hour

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        // Inject the @Value fields that would normally come from application.properties
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiration", EXPIRATION_MS);
    }

    // ── Token generation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("generateToken returns a non-blank string with 3 dot-separated parts")
    void generateToken_returnsValidJwtStructure() {
        UserDetails user = buildUser("alice@test.com", "ROLE_USER");

        String token = jwtUtil.generateToken(user);

        // JWT format: header.payload.signature — always exactly 3 parts
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    // ── Username extraction ───────────────────────────────────────────────────

    @Test
    @DisplayName("extractUsername returns the email embedded in the token")
    void extractUsername_returnsCorrectEmail() {
        String email = "bob@test.com";
        String token = jwtUtil.generateToken(buildUser(email, "ROLE_USER"));

        String extracted = jwtUtil.extractUsername(token);

        // The "sub" (subject) claim stores the email
        assertThat(extracted).isEqualTo(email);
    }

    @Test
    @DisplayName("extractUsername works for ADMIN users too")
    void extractUsername_worksForAdmin() {
        String token = jwtUtil.generateToken(buildUser("admin@test.com", "ROLE_ADMIN"));

        assertThat(jwtUtil.extractUsername(token)).isEqualTo("admin@test.com");
    }

    // ── Token validation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("isTokenValid returns true for a fresh token belonging to the same user")
    void isTokenValid_freshTokenSameUser_returnsTrue() {
        UserDetails user = buildUser("carol@test.com", "ROLE_USER");
        String token = jwtUtil.generateToken(user);

        assertThat(jwtUtil.isTokenValid(token, user)).isTrue();
    }

    @Test
    @DisplayName("isTokenValid returns false when token belongs to a different user")
    void isTokenValid_differentUser_returnsFalse() {
        UserDetails alice = buildUser("alice@test.com", "ROLE_USER");
        UserDetails bob   = buildUser("bob@test.com",   "ROLE_USER");

        // Token generated for Alice
        String token = jwtUtil.generateToken(alice);

        // Bob tries to use Alice's token — must be rejected
        assertThat(jwtUtil.isTokenValid(token, bob)).isFalse();
    }

    @Test
    @DisplayName("Expired token causes an exception — not silently accepted")
    void expiredToken_throwsJwtException() {
        // Set expiration to -1000ms → token is born already expired
        ReflectionTestUtils.setField(jwtUtil, "expiration", -1000L);
        UserDetails user = buildUser("dave@test.com", "ROLE_USER");
        String expiredToken = jwtUtil.generateToken(user);

        // Restore normal expiration so the validator runs (it re-parses the token)
        ReflectionTestUtils.setField(jwtUtil, "expiration", EXPIRATION_MS);

        // isTokenValid calls extractAllClaims which throws ExpiredJwtException
        // We assert ANY exception is thrown — verifies expired tokens are rejected
        assertThatThrownBy(() -> jwtUtil.isTokenValid(expiredToken, user))
                .isInstanceOf(Exception.class); // io.jsonwebtoken.ExpiredJwtException
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private UserDetails buildUser(String email, String role) {
        return User.withUsername(email)
                .password("hashed-password-not-used-in-jwt")
                .authorities(new SimpleGrantedAuthority(role))
                .build();
    }
}
