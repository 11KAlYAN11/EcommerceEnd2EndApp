package com.ecommerce.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

/**
 * Bridges our User entity with Spring Security's UserDetails model.
 *
 * Spring Security doesn't know about our User class.
 * It works with the UserDetails interface.
 * This service loads our User from DB and wraps it in Spring's UserDetails.
 *
 * @Transactional on loadUserByUsername:
 *   User.getRoles() is EAGER so it loads with the user.
 *   Still good practice to mark DB reads transactional.
 *
 * SimpleGrantedAuthority("ROLE_USER"):
 *   Spring Security checks roles as GrantedAuthority objects.
 *   We convert our Role enum names to SimpleGrantedAuthority.
 *   hasRole("USER") → Spring internally checks for "ROLE_USER".
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .authorities(user.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority(role.getName().name()))
                        .collect(Collectors.toSet()))
                .accountExpired(false)
                .accountLocked(!user.isEnabled())
                .credentialsExpired(false)
                .disabled(!user.isEnabled())
                .build();
    }
}
