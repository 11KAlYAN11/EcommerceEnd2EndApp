package com.ecommerce.notification;

import com.ecommerce.order.Order;
import com.ecommerce.order.OrderItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Fire-and-forget, same rule as user-service's NotificationClient (16.2):
 * if notification-service is down, order placement must still succeed.
 * Payload shape here matches notification-service's existing
 * OrderConfirmationRequest/OrderItemLine records exactly (built in 16.1) --
 * no changes needed on that side to receive this.
 */
@Component
@Slf4j
public class NotificationClient {

    private final RestClient restClient;

    public NotificationClient(@Value("${services.notification.base-url:http://localhost:8081}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public void sendOrderConfirmation(String email, String firstName, Order order) {
        try {
            List<Map<String, Object>> items = order.getItems().stream()
                    .map(i -> Map.<String, Object>of(
                            "productName", i.getProductName(),
                            "quantity", i.getQuantity(),
                            "priceAtPurchase", i.getPriceAtPurchase()))
                    .toList();

            restClient.post()
                    .uri("/api/notifications/order-confirmation")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "recipientEmail", email,
                            "firstName", firstName,
                            "orderId", order.getId(),
                            "status", order.getStatus().name(),
                            "totalPrice", order.getTotalPrice(),
                            "items", items,
                            "placedAt", order.getCreatedAt()
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("notification-service unreachable, order confirmation not sent for order {}: {}",
                    order.getId(), e.getMessage());
        }
    }

    public void sendOrderCancellation(String email, String firstName, Long orderId, BigDecimal totalPrice) {
        try {
            restClient.post()
                    .uri("/api/notifications/order-cancellation")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "recipientEmail", email,
                            "firstName", firstName,
                            "orderId", orderId,
                            "totalPrice", totalPrice
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("notification-service unreachable, cancellation email not sent for order {}: {}",
                    orderId, e.getMessage());
        }
    }
}
