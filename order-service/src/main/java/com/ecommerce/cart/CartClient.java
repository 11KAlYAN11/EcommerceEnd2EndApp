package com.ecommerce.cart;

import com.ecommerce.config.CurrentRequestToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Forwards the caller's own JWT to cart-service -- order-service doesn't
 * have its own identity to act as, it acts AS the customer placing the
 * order. cart-service's SecurityConfig requires auth on everything, so
 * without this forwarded header every call here would 401.
 */
@Component
@SuppressWarnings("unchecked")
public class CartClient {

    private final RestClient restClient;

    public CartClient(@Value("${services.cart.base-url:http://localhost:8084}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public List<CartLineItem> getItems() {
        Map<String, Object> body = restClient.get()
                .uri("/api/cart")
                .header("Authorization", "Bearer " + CurrentRequestToken.get())
                .retrieve()
                .body(Map.class);

        Map<String, Object> data = (Map<String, Object>) body.get("data");
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");

        return items.stream()
                .map(i -> new CartLineItem(
                        Long.valueOf(i.get("productId").toString()),
                        Integer.parseInt(i.get("quantity").toString())))
                .toList();
    }

    public void clear() {
        restClient.delete()
                .uri("/api/cart")
                .header("Authorization", "Bearer " + CurrentRequestToken.get())
                .retrieve()
                .toBodilessEntity();
    }
}
