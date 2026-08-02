package com.ecommerce.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * This is the whole point of Phase 16.2: where the monolith did
 * `emailService.sendWelcome(user)` as an in-process method call, user-service
 * makes an HTTP call to notification-service instead. Same intent
 * ("tell someone to send a welcome email"), different mechanism.
 *
 * RestClient (Spring Framework 6.1+, available since Boot 3.2) is the
 * modern synchronous HTTP client — it replaces the older RestTemplate
 * (still extremely common in existing/legacy codebases, so know both names)
 * and predates fully committing to WebClient's reactive model, which isn't
 * needed here since this call is fire-and-forget from a blocking controller
 * thread anyway.
 *
 * WHY THIS MUST NOT BLOCK REGISTRATION ON FAILURE:
 * Same rule as Phase 11 (ADR B-11) applies one hop further out now: if
 * notification-service is down/slow, a new user must still be able to
 * register. We catch and log rather than propagate — the alternative
 * (a network blip in another service breaking your own core feature) is
 * exactly the fragility a synchronous inter-service call risks, and exactly
 * why Phase 17 eventually replaces this call with a published event instead.
 */
@Component
@Slf4j
public class NotificationClient {

    private final RestClient restClient;

    public NotificationClient(@Value("${services.notification.base-url:http://localhost:8081}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public void sendWelcome(String email, String firstName) {
        try {
            restClient.post()
                    .uri("/api/notifications/welcome")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(new WelcomeRequest(email, firstName))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("notification-service unreachable, welcome email not sent for {}: {}",
                    email, e.getMessage());
        }
    }

    private record WelcomeRequest(String recipientEmail, String firstName) {
    }
}
