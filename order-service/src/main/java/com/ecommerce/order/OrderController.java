package com.ecommerce.order;

import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.dto.PlaceOrderRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> placeOrder(
            @AuthenticationPrincipal String email,
            @RequestBody(required = false) PlaceOrderRequest request) {
        if (request == null) request = new PlaceOrderRequest();
        OrderResponse response = orderService.placeOrder(email, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order placed successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> getMyOrders(
            @AuthenticationPrincipal String email,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Orders fetched",
                orderService.getMyOrders(email, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(
            @AuthenticationPrincipal String email, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Order fetched", orderService.getOrder(email, id)));
    }

    @DeleteMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @AuthenticationPrincipal String email, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Order cancelled", orderService.cancelOrder(email, id)));
    }

    /** Called by payment-service (16.6), forwarding the same customer's JWT. */
    @PatchMapping("/{id}/confirm-payment")
    public ResponseEntity<ApiResponse<OrderResponse>> markConfirmed(
            @AuthenticationPrincipal String email, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Order confirmed", orderService.markConfirmed(email, id)));
    }
}
