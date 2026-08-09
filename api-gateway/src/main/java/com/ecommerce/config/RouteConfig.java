package com.ecommerce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Pure routing by path prefix -- no path rewriting needed at all, because
 * every service already exposes its business endpoints under /api/...
 * (via server.servlet.context-path=/api on 5 of them, and notification-
 * service's controller declaring @RequestMapping("/api/notifications")
 * directly despite having no context-path). Whatever path the client sends
 * the gateway is exactly the path each service already expects.
 *
 * NO JWT VALIDATION HERE. This is deliberate, not an oversight -- the
 * pattern established since product-service (16.3) is that every service
 * validates the token itself, independently, rather than trusting a
 * single upstream checkpoint. The gateway staying "dumb" about auth means
 * no service's security depends on network topology (which box sits in
 * front of it) -- it depends only on the token, exactly like today.
 */
@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator routes(
            RouteLocatorBuilder builder,
            @Value("${services.user.base-url}") String userUrl,
            @Value("${services.product.base-url}") String productUrl,
            @Value("${services.cart.base-url}") String cartUrl,
            @Value("${services.order.base-url}") String orderUrl,
            @Value("${services.payment.base-url}") String paymentUrl,
            @Value("${services.notification.base-url}") String notificationUrl) {

        return builder.routes()
                .route("user-service", r -> r
                        .path("/api/auth/**", "/api/dev/**", "/api/addresses/**", "/api/users/**")
                        .uri(userUrl))
                .route("product-service", r -> r
                        .path("/api/products/**", "/api/categories/**", "/api/search/**")
                        .uri(productUrl))
                .route("cart-service", r -> r
                        .path("/api/cart/**")
                        .uri(cartUrl))
                .route("order-service", r -> r
                        .path("/api/orders/**", "/api/admin/**")
                        .uri(orderUrl))
                .route("payment-service", r -> r
                        .path("/api/payments/**")
                        .uri(paymentUrl))
                .route("notification-service", r -> r
                        .path("/api/notifications/**")
                        .uri(notificationUrl))
                .build();
    }
}
