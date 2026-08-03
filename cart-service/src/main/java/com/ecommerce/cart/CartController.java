package com.ecommerce.cart;

import com.ecommerce.cart.dto.CartItemRequest;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * @AuthenticationPrincipal String email -- with the stateless filter, the
 * Authentication's principal IS the plain email string (the JWT's `sub`
 * claim), not a UserDetails object. No DB round-trip needed to resolve it.
 */
@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(ApiResponse.success("Cart fetched", cartService.getCart(email)));
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            @AuthenticationPrincipal String email, @Valid @RequestBody CartItemRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Item added to cart", cartService.addItem(email, request)));
    }

    @PatchMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<CartResponse>> updateQuantity(
            @AuthenticationPrincipal String email, @PathVariable Long cartItemId, @RequestParam int quantity) {
        return ResponseEntity.ok(ApiResponse.success("Cart updated",
                cartService.updateItemQuantity(email, cartItemId, quantity)));
    }

    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(
            @AuthenticationPrincipal String email, @PathVariable Long cartItemId) {
        return ResponseEntity.ok(ApiResponse.success("Item removed", cartService.removeItem(email, cartItemId)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clearCart(@AuthenticationPrincipal String email) {
        cartService.clearCart(email);
        return ResponseEntity.ok(ApiResponse.success("Cart cleared"));
    }
}
