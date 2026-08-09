package com.ecommerce.user;

import com.ecommerce.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * NEW in Phase 16.8 -- same story as AddressController back in 16.5: order-
 * service's admin dashboard needs a total-user count, and that's a question
 * only user-service can answer now that the tables are split. Admin-gated
 * because it's business data (headcount), unlike /dev/users which is a
 * dev-profile-only debugging aid with no such data-sensitivity concern.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/count")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Long>> count() {
        return ResponseEntity.ok(ApiResponse.success("Total user count", userRepository.count()));
    }
}
