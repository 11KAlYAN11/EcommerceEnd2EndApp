package com.ecommerce.payment;

import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.payment.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate/{orderId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> initiate(
            @PathVariable Long orderId,
            @RequestParam(defaultValue = "UPI") Payment.PaymentMethod method) {
        return ResponseEntity.ok(ApiResponse.success("Payment initiated",
                paymentService.initiatePayment(orderId, method)));
    }

    @PostMapping("/confirm/{orderId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> confirm(@PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.success("Payment confirmed",
                paymentService.confirmPayment(orderId)));
    }

    @PostMapping("/fail/{orderId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> fail(@PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.success("Payment marked as failed",
                paymentService.failPayment(orderId)));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(@PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.success("Payment fetched",
                paymentService.getPaymentForOrder(orderId)));
    }
}
