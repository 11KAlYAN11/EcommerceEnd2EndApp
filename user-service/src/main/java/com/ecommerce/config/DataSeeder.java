package com.ecommerce.config;

import com.ecommerce.user.Role;
import com.ecommerce.user.Role.RoleName;
import com.ecommerce.user.RoleRepository;
import com.ecommerce.user.User;
import com.ecommerce.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Trimmed version of the monolith's DataSeeder (problems-overcomed.md #16 —
 * credentials from env vars, local-only defaults). The monolith's seeder
 * also seeded products/categories in the same class; that part belongs to
 * product-service once THAT extraction happens, not here. This service
 * only seeds what it owns: roles and the two demo accounts.
 *
 * Demo accounts:
 *   admin@test.com / Admin@123  -> ROLE_ADMIN
 *   user@test.com  / User@123   -> ROLE_USER
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${seed.admin.email:admin@test.com}")
    private String adminEmail;

    @Value("${seed.admin.password:Admin@123}")
    private String adminPassword;

    @Value("${seed.admin.firstName:Admin}")
    private String adminFirstName;

    @Value("${seed.admin.lastName:ShopEase}")
    private String adminLastName;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        Role adminRole = ensureRole(RoleName.ROLE_ADMIN);
        Role userRole = ensureRole(RoleName.ROLE_USER);

        if (!userRepository.existsByEmail(adminEmail)) {
            userRepository.save(User.builder()
                    .firstName(adminFirstName)
                    .lastName(adminLastName)
                    .email(adminEmail)
                    .password(passwordEncoder.encode(adminPassword))
                    .phone("9999999999")
                    .roles(Set.of(adminRole))
                    .build());
            log.info("Seeded admin user: {}", adminEmail);
        }

        if (!userRepository.existsByEmail("user@test.com")) {
            userRepository.save(User.builder()
                    .firstName("Demo")
                    .lastName("User")
                    .email("user@test.com")
                    .password(passwordEncoder.encode("User@123"))
                    .phone("8888888888")
                    .roles(Set.of(userRole))
                    .build());
            log.info("Seeded demo user: user@test.com");
        }
    }

    private Role ensureRole(RoleName name) {
        return roleRepository.findByName(name)
                .orElseGet(() -> roleRepository.save(new Role(name)));
    }
}
