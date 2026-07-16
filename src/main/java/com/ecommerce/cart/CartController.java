package com.ecommerce.cart;

import com.ecommerce.cart.dto.CartItemRequest;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * @AuthenticationPrincipal UserDetails:
 *   Spring injects the currently logged-in user from SecurityContext.
 *   We use userDetails.getUsername() which returns the email (set in UserDetailsServiceImpl).
 *   This is how controllers know WHO is making the request — no session, no userId param.
 *   The JWT filter already validated the token and populated SecurityContext before this runs.
 */
@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    private ResponseEntity<ApiResponse<CartResponse>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Authentication required"));
    }

    /** GET /api/cart */
    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return unauthorized();
        return ResponseEntity.ok(ApiResponse.success("Cart fetched",
                cartService.getCart(userDetails.getUsername())));
    }

    /** POST /api/cart/items  body: { productId, quantity } */
    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CartItemRequest request) {
        if (userDetails == null) return unauthorized();
        return ResponseEntity.ok(ApiResponse.success("Item added to cart",
                cartService.addItem(userDetails.getUsername(), request)));
    }

    /** PATCH /api/cart/items/{cartItemId}?quantity=3 */
    @PatchMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<CartResponse>> updateQuantity(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long cartItemId,
            @RequestParam int quantity) {
        if (userDetails == null) return unauthorized();
        return ResponseEntity.ok(ApiResponse.success("Cart updated",
                cartService.updateItemQuantity(userDetails.getUsername(), cartItemId, quantity)));
    }

    /** DELETE /api/cart/items/{cartItemId} */
    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long cartItemId) {
        if (userDetails == null) return unauthorized();
        return ResponseEntity.ok(ApiResponse.success("Item removed",
                cartService.removeItem(userDetails.getUsername(), cartItemId)));
    }

    /** DELETE /api/cart */
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clearCart(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Authentication required"));
        cartService.clearCart(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Cart cleared"));
    }
}
