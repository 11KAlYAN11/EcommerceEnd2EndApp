package com.ecommerce.product;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.config.CurrentRequestToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

/**
 * getFresh(): public GET, no token needed (product-service's browsing
 * routes are public) -- called at order placement to get the CURRENT price
 * and stock, deliberately ignoring whatever cart-service had cached, since
 * stock/price may have moved since the item was added to the cart.
 *
 * adjustStock(): the write side. Requires auth (product-service's new
 * endpoint from 16.5) -- forwards the customer's token, since there's no
 * separate service-identity yet (a known, stated gap -- see product-service's
 * ProductService.adjustStock javadoc for the distributed-transaction caveat).
 */
@Component
@SuppressWarnings("unchecked")
public class ProductClient {

    private final RestClient restClient;

    public ProductClient(@Value("${services.product.base-url:http://localhost:8083}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public ProductSnapshot getFresh(Long productId) {
        Map<String, Object> body;
        try {
            body = restClient.get()
                    .uri("/api/products/{id}", productId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new ResourceNotFoundException("Product", productId);
                    })
                    .body(Map.class);
        } catch (ResourceNotFoundException e) {
            throw e;
        }
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        return new ProductSnapshot(
                Long.valueOf(data.get("id").toString()),
                (String) data.get("name"),
                new BigDecimal(data.get("price").toString()),
                Integer.valueOf(data.get("stockQuantity").toString()),
                (boolean) data.get("active"));
    }

    public void adjustStock(Long productId, int delta) {
        restClient.patch()
                .uri("/api/products/{id}/stock", productId)
                .header("Authorization", "Bearer " + CurrentRequestToken.get())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(Map.of("delta", delta))
                .retrieve()
                .toBodilessEntity();
    }
}
