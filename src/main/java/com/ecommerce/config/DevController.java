package com.ecommerce.config;

import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.user.Role;
import com.ecommerce.user.RoleRepository;
import com.ecommerce.user.User;
import com.ecommerce.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * DEV-ONLY controller — only active when spring.profiles.active=dev.
 * @Profile("dev") ensures this bean is NEVER created in production.
 *
 * Purpose: bootstrap admin users without needing raw SQL or pgAdmin.
 * In production, admin promotion would require a separate secure workflow.
 */
@RestController
@RequestMapping("/dev")
@RequiredArgsConstructor
@Profile("dev")
public class DevController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    /**
     * POST /api/dev/make-admin?email=alice@test.com
     * Makes any registered user an ADMIN. No auth required (dev only).
     */
    @PostMapping("/make-admin")
    public ResponseEntity<ApiResponse<String>> makeAdmin(@RequestParam String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        Role adminRole = roleRepository.findByName(Role.RoleName.ROLE_ADMIN)
                .orElseGet(() -> roleRepository.save(new Role(Role.RoleName.ROLE_ADMIN)));

        user.getRoles().add(adminRole);
        userRepository.save(user);

        Set<String> roles = user.getRoles().stream()
                .map(r -> r.getName().name())
                .collect(Collectors.toSet());

        return ResponseEntity.ok(ApiResponse.success(
                email + " is now ADMIN. Login again to get a fresh token with ROLE_ADMIN.",
                "Roles: " + roles
        ));
    }

    /**
     * GET /api/dev/users — list all registered users (dev debugging)
     */
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<?>> listUsers() {
        var users = userRepository.findAll().stream()
                .map(u -> new Object() {
                    public final Long id = u.getId();
                    public final String email = u.getEmail();
                    public final String name = u.getFirstName() + " " + u.getLastName();
                    public final Set<String> roles = u.getRoles().stream()
                            .map(r -> r.getName().name()).collect(Collectors.toSet());
                })
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Users", users));
    }
}
