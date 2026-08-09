package com.ecommerce.order;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.config.CurrentRequestToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

/**
 * order-service's GET /orders/{id} already does the ownership check itself
 * (filters by the JWT's email) -- payment-service doesn't repeat that
 * logic, it just forwards the same token and trusts a 404 to mean "not
 * yours, or doesn't exist." No separate authorization code needed here.
 */
@Component
@SuppressWarnings("unchecked")
public class OrderClient {

    private final RestClient restClient;

    public OrderClient(@Value("${services.order.base-url:http://localhost:8085}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public OrderSnapshot getOrder(Long orderId) {
        Map<String, Object> body;
        try {
            body = restClient.get()
                    .uri("/api/orders/{id}", orderId)
                    .header("Authorization", "Bearer " + CurrentRequestToken.get())
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new ResourceNotFoundException("Order " + orderId + " not found");
                    })
                    .body(Map.class);
        } catch (ResourceNotFoundException e) {
            throw e;
        }
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        return new OrderSnapshot(
                Long.valueOf(data.get("orderId").toString()),
                (String) data.get("status"),
                new BigDecimal(data.get("totalPrice").toString()));
    }

    /** The endpoint order-service reserved for this back in 16.5. */
    public void markConfirmed(Long orderId) {
        restClient.patch()
                .uri("/api/orders/{id}/confirm-payment", orderId)
                .header("Authorization", "Bearer " + CurrentRequestToken.get())
                .retrieve()
                .toBodilessEntity();
    }
}
