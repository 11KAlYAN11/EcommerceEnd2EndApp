package com.ecommerce.payment;

import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.payment.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * POST /api/payments/initiate/{orderId}?method=UPI
     * Step 1: start payment process, get a reference ID back
     */
    @PostMapping("/initiate/{orderId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> initiate(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long orderId,
            @RequestParam(defaultValue = "UPI") Payment.PaymentMethod method) {
        return ResponseEntity.ok(ApiResponse.success("Payment initiated",
                paymentService.initiatePayment(userDetails.getUsername(), orderId, method)));
    }

    /**
     * POST /api/payments/confirm/{orderId}
     * Step 2: simulate gateway confirming payment success
     * In production this is a webhook from Razorpay/Stripe, not a user action
     */
    @PostMapping("/confirm/{orderId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> confirm(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.success("Payment confirmed",
                paymentService.confirmPayment(userDetails.getUsername(), orderId)));
    }

    /**
     * POST /api/payments/fail/{orderId}
     * Simulate payment failure (user cancelled payment, card declined, etc.)
     */
    @PostMapping("/fail/{orderId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> fail(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.success("Payment marked as failed",
                paymentService.failPayment(userDetails.getUsername(), orderId)));
    }

    /**
     * GET /api/payments/order/{orderId}
     * View payment details for an order
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.success("Payment fetched",
                paymentService.getPaymentForOrder(userDetails.getUsername(), orderId)));
    }
}
