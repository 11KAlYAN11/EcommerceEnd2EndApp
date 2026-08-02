package com.ecommerce.notification;

import com.ecommerce.notification.dto.LowStockAlertRequest;
import com.ecommerce.notification.dto.OrderCancellationRequest;
import com.ecommerce.notification.dto.OrderConfirmationRequest;
import com.ecommerce.notification.dto.WelcomeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Called synchronously by other services (order-service, auth/user-service,
 * once they exist) instead of an in-process method call. This is the
 * "REST for now, event for later" seam mentioned in the extraction plan:
 * in Phase 17 these endpoints get replaced by a Kafka consumer listening
 * for OrderPlaced / OrderCancelled / UserRegistered events, and the callers
 * publish an event instead of making an HTTP call. The DTOs and EmailService
 * underneath don't need to change when that happens — only how they're
 * invoked.
 *
 * No JWT/auth on this controller: these are internal, service-to-service
 * calls, not browser-facing endpoints. Locking this down (mTLS, a shared
 * service token, or simply "not internet-routable") is a hardening step
 * for later, not something the first extraction needs to solve.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final EmailService emailService;

    @PostMapping("/order-confirmation")
    public ResponseEntity<Void> orderConfirmation(@Valid @RequestBody OrderConfirmationRequest req) {
        emailService.sendOrderConfirmation(req);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/order-cancellation")
    public ResponseEntity<Void> orderCancellation(@Valid @RequestBody OrderCancellationRequest req) {
        emailService.sendOrderCancellation(req);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/welcome")
    public ResponseEntity<Void> welcome(@Valid @RequestBody WelcomeRequest req) {
        emailService.sendWelcome(req);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/low-stock-alert")
    public ResponseEntity<Void> lowStockAlert(@Valid @RequestBody LowStockAlertRequest req) {
        emailService.sendLowStockAlert(req);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
