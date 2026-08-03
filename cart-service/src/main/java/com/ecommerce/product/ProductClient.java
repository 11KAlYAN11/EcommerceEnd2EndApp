package com.ecommerce.product;

import com.ecommerce.common.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

/**
 * The call that makes this extraction different from every prior one: this
 * is a REQUIRED read, not a fire-and-forget notify. If product-service is
 * down, addItem/updateQuantity cannot proceed -- there's no "log and
 * continue" option when you literally cannot know the price or stock.
 *
 * getCart() (listing what's already in the cart) does NOT call this --
 * see CartService for why.
 */
@Component
public class ProductClient {

    private final RestClient restClient;

    public ProductClient(@Value("${services.product.base-url:http://localhost:8083}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    @SuppressWarnings("unchecked")
    public ProductSnapshot getActiveProduct(Long productId) {
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
        boolean active = (boolean) data.get("active");
        if (!active) {
            throw new ResourceNotFoundException("Product", productId);
        }

        return new ProductSnapshot(
                Long.valueOf(data.get("id").toString()),
                (String) data.get("name"),
                new BigDecimal(data.get("price").toString()),
                (String) data.get("imageUrl"),
                true,
                Integer.valueOf(data.get("stockQuantity").toString())
        );
    }
}
