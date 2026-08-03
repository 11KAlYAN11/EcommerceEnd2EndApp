package com.ecommerce.address;

import com.ecommerce.address.dto.AddressResponse;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.user.User;
import com.ecommerce.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * NEW in Phase 16.5 -- the monolith never needed this. OrderService used to
 * reach `addressRepository.findById(...)` directly, in-process, because it
 * lived in the same JVM. Once order-service is a separate process, it has
 * to ask for this over HTTP -- which means user-service needs to expose it
 * for the first time. A real, recurring cost of splitting a monolith:
 * upstream services often need new endpoints they never required before,
 * because "just call the repository" stops being an option.
 */
@RestController
@RequestMapping("/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AddressResponse>> getById(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        User user = getUser(userDetails);
        Address address = addressRepository.findById(id)
                .filter(a -> a.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Address", id));
        return ResponseEntity.ok(ApiResponse.success("Address fetched", AddressResponse.from(address)));
    }

    @GetMapping("/default")
    public ResponseEntity<ApiResponse<AddressResponse>> getDefault(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        Address address = addressRepository.findByUserIdAndIsDefaultTrue(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No default address set for this user"));
        return ResponseEntity.ok(ApiResponse.success("Default address fetched", AddressResponse.from(address)));
    }

    private User getUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userDetails.getUsername()));
    }
}
