package com.ecommerce.user;

import com.ecommerce.config.CurrentRequestToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 16.8: admin dashboard summary needs a total user count. user-service's
 * /users/count is admin-gated, so this forwards the caller's token like
 * every other write/privileged call in this service does.
 */
@Component
public class UserClient {

    private final RestClient restClient;

    public UserClient(@Value("${services.user.base-url:http://localhost:8082}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public long count() {
        Map<String, Object> body = restClient.get()
                .uri("/api/users/count")
                .header("Authorization", "Bearer " + CurrentRequestToken.get())
                .retrieve()
                .body(Map.class);
        return Long.parseLong(body.get("data").toString());
    }
}
