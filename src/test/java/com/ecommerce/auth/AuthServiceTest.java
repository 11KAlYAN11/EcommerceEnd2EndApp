package com.ecommerce.auth;

import com.ecommerce.auth.dto.AuthResponse;
import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.RegisterRequest;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.util.JwtUtil;
import com.ecommerce.notification.EmailService;
import com.ecommerce.user.Role;
import com.ecommerce.user.RoleRepository;
import com.ecommerce.user.User;
import com.ecommerce.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * UNIT TEST for AuthService.
 *
 * KEY TESTING CONCEPTS:
 *
 * @ExtendWith(MockitoExtension.class):
 *   Activates Mockito in JUnit 5. Without this, @Mock fields are not initialized.
 *   Alternative to the old JUnit 4: @RunWith(MockitoJUnitRunner.class)
 *
 * @Mock:
 *   Creates a mock object (a fake) that does nothing by default.
 *   All methods return null/0/false unless you configure them with when().
 *   We mock UserRepository because we don't want to hit a real database.
 *
 * @InjectMocks:
 *   Creates the real AuthService and injects all @Mock fields into it.
 *   This is constructor injection — Mockito finds the matching constructor.
 *   Result: AuthService runs with mocked dependencies, no Spring context.
 *
 * when().thenReturn():
 *   "When this method is called with these args, return this value."
 *   Controls the behavior of mocks so we can test specific scenarios.
 *
 * verify():
 *   "Assert that this method was called (or not called) on this mock."
 *   verify(emailService).sendWelcome(any()) → email WAS sent
 *   verify(userRepository, never()).save(any()) → save was NOT called
 *
 * THE ARRANGE-ACT-ASSERT PATTERN:
 *   Arrange: set up mocks and test data
 *   Act:     call the method under test
 *   Assert:  verify the result and interactions
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    // ── Mocked dependencies ───────────────────────────────────────────────────
    @Mock UserRepository        userRepository;
    @Mock RoleRepository        roleRepository;
    @Mock PasswordEncoder       passwordEncoder;
    @Mock AuthenticationManager authenticationManager;
    @Mock UserDetailsService    userDetailsService;
    @Mock JwtUtil               jwtUtil;
    @Mock EmailService          emailService;

    // ── Real service with mocks injected ─────────────────────────────────────
    @InjectMocks
    AuthService authService;

    // ── register() ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("register: new email → creates user, returns token, sends welcome email")
    void register_newEmail_success() {
        // Arrange
        RegisterRequest req = buildRegisterRequest("alice@test.com", "Alice", "Smith", "pass123");

        Role userRole = new Role(Role.RoleName.ROLE_USER);
        User savedUser = User.builder()
                .firstName("Alice").lastName("Smith")
                .email("alice@test.com").password("hashed")
                .roles(Set.of(userRole)).build();

        when(userRepository.existsByEmail("alice@test.com")).thenReturn(false);
        when(roleRepository.findByName(Role.RoleName.ROLE_USER)).thenReturn(Optional.of(userRole));
        when(passwordEncoder.encode("pass123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userDetailsService.loadUserByUsername("alice@test.com"))
                .thenReturn(buildSpringUser("alice@test.com"));
        when(jwtUtil.generateToken(any())).thenReturn("mock-jwt-token");

        // Act
        AuthResponse response = authService.register(req);

        // Assert
        assertThat(response.getToken()).isEqualTo("mock-jwt-token");
        assertThat(response.getEmail()).isEqualTo("alice@test.com");
        assertThat(response.getFirstName()).isEqualTo("Alice");

        // Email MUST be triggered on successful registration
        verify(emailService).sendWelcome(any(User.class));
        // User MUST be saved
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("register: duplicate email → throws ConflictException, no save, no email")
    void register_emailAlreadyExists_throwsConflict() {
        // Arrange
        RegisterRequest req = buildRegisterRequest("alice@test.com", "Alice", "Smith", "pass123");
        when(userRepository.existsByEmail("alice@test.com")).thenReturn(true);

        // Act + Assert
        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("alice@test.com");

        // Side effects must NOT happen — no save, no email
        verify(userRepository, never()).save(any());
        verify(emailService, never()).sendWelcome(any());
    }

    @Test
    @DisplayName("register: creates ROLE_USER when role doesn't exist yet")
    void register_createsRoleIfNotFound() {
        // Arrange
        RegisterRequest req = buildRegisterRequest("new@test.com", "Bob", "Jones", "pass123");

        Role newRole = new Role(Role.RoleName.ROLE_USER);
        User savedUser = User.builder()
                .firstName("Bob").lastName("Jones")
                .email("new@test.com").password("hashed")
                .roles(Set.of(newRole)).build();

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        // Role not in DB yet → roleRepository.save() is called
        when(roleRepository.findByName(Role.RoleName.ROLE_USER)).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenReturn(newRole);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userDetailsService.loadUserByUsername(anyString()))
                .thenReturn(buildSpringUser("new@test.com"));
        when(jwtUtil.generateToken(any())).thenReturn("token");

        // Act
        authService.register(req);

        // Role must have been saved to DB
        verify(roleRepository).save(any(Role.class));
    }

    // ── login() ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login: valid credentials → authenticates and returns token")
    void login_validCredentials_returnsToken() {
        // Arrange
        LoginRequest req = buildLoginRequest("alice@test.com", "pass123");

        User user = User.builder()
                .firstName("Alice").lastName("Smith")
                .email("alice@test.com").password("hashed")
                .roles(Set.of(new Role(Role.RoleName.ROLE_USER))).build();

        when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(user));
        when(userDetailsService.loadUserByUsername("alice@test.com"))
                .thenReturn(buildSpringUser("alice@test.com"));
        when(jwtUtil.generateToken(any())).thenReturn("login-token");

        // Act
        AuthResponse response = authService.login(req);

        // Assert
        assertThat(response.getToken()).isEqualTo("login-token");
        // AuthenticationManager.authenticate() MUST have been called
        // (this is what validates the password against BCrypt hash)
        verify(authenticationManager).authenticate(
                any(UsernamePasswordAuthenticationToken.class));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RegisterRequest buildRegisterRequest(
            String email, String firstName, String lastName, String password) {
        RegisterRequest req = new RegisterRequest();
        req.setEmail(email);
        req.setFirstName(firstName);
        req.setLastName(lastName);
        req.setPassword(password);
        return req;
    }

    private LoginRequest buildLoginRequest(String email, String password) {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        return req;
    }

    private UserDetails buildSpringUser(String email) {
        return org.springframework.security.core.userdetails.User
                .withUsername(email)
                .password("hashed")
                .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                .build();
    }
}
