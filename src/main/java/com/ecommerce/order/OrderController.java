package com.ecommerce.order;

import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.dto.PlaceOrderRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** POST /api/orders — place order from cart */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> placeOrder(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody(required = false) PlaceOrderRequest request) {
        if (request == null) request = new PlaceOrderRequest();
        OrderResponse response = orderService.placeOrder(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order placed successfully", response));
    }

    /** GET /api/orders — my order history */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> getMyOrders(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Orders fetched",
                orderService.getMyOrders(userDetails.getUsername(), page, size)));
    }

    /** GET /api/orders/{id} — single order detail */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Order fetched",
                orderService.getOrder(userDetails.getUsername(), id)));
    }

    /** DELETE /api/orders/{id}/cancel — cancel order */
    @DeleteMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Order cancelled",
                orderService.cancelOrder(userDetails.getUsername(), id)));
    }

    /** PATCH /api/orders/{id}/status — admin updates status */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
            @PathVariable Long id,
            @RequestParam Order.OrderStatus status) {
        return ResponseEntity.ok(ApiResponse.success("Order status updated",
                orderService.updateStatus(id, status)));
    }
}
