package com.ecommerce.address;

import com.ecommerce.config.CurrentRequestToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Unlike ProductClient's required fetch, a missing address is NOT fatal --
 * the monolith always allowed placing an order with no shipping address
 * (`orElse(null)`). A 404 here just means "no address," not "something's
 * broken" -- so it's caught and turned into null rather than propagating.
 */
@Component
@SuppressWarnings("unchecked")
public class AddressClient {

    private final RestClient restClient;

    public AddressClient(@Value("${services.user.base-url:http://localhost:8082}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public AddressSnapshot getById(Long addressId) {
        return fetch("/api/addresses/{id}", addressId);
    }

    public AddressSnapshot getDefault() {
        return fetch("/api/addresses/default", null);
    }

    private AddressSnapshot fetch(String uri, Long id) {
        try {
            Map<String, Object> body = (id == null
                    ? restClient.get().uri(uri)
                    : restClient.get().uri(uri, id))
                    .header("Authorization", "Bearer " + CurrentRequestToken.get())
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> { /* treated as "no address" below */ })
                    .body(Map.class);

            if (body == null || !Boolean.TRUE.equals(body.get("success"))) return null;
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            if (data == null) return null;

            return new AddressSnapshot(
                    (String) data.get("street"),
                    (String) data.get("city"),
                    (String) data.get("state"),
                    (String) data.get("pincode"),
                    (String) data.get("country"));
        } catch (Exception e) {
            return null;
        }
    }
}
