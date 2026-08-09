package com.ecommerce.order;

import com.ecommerce.address.AddressClient;
import com.ecommerce.address.AddressSnapshot;
import com.ecommerce.cart.CartClient;
import com.ecommerce.cart.CartLineItem;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.config.CurrentRequestToken;
import com.ecommerce.config.JwtValidator;
import com.ecommerce.notification.NotificationClient;
import com.ecommerce.order.dto.OrderItemResponse;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.dto.PlaceOrderRequest;
import com.ecommerce.product.ProductClient;
import com.ecommerce.product.ProductSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Same @Transactional shape as the monolith (ADR B-08) -- but that
 * annotation now ONLY covers this service's own DB writes (the `orders`
 * table). It does NOT cover cart-service's clear, or product-service's
 * stock adjustment -- those are separate HTTP calls with their own separate
 * commits. If this method throws AFTER product-service already decremented
 * stock, that decrement does NOT roll back. This is the real, harder
 * problem the extraction order deliberately saved for last: a distributed
 * "transaction" across services needs a saga/compensating-action pattern
 * (Phase 17+), not just @Transactional. Stated here plainly rather than
 * pretended away.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartClient cartClient;
    private final ProductClient productClient;
    private final AddressClient addressClient;
    private final NotificationClient notificationClient;
    private final JwtValidator jwtValidator;

    @Transactional
    public OrderResponse placeOrder(String email, PlaceOrderRequest request) {
        List<CartLineItem> cartItems = cartClient.getItems();
        if (cartItems.isEmpty()) {
            throw new IllegalArgumentException("Cannot place order: cart is empty");
        }

        AddressSnapshot address = resolveShippingAddress(request.getShippingAddressId());

        // Fetch FRESH price/stock for every item -- deliberately ignoring
        // whatever cart-service had cached, since it may be stale.
        List<OrderItem> orderItems = cartItems.stream().map(line -> {
            ProductSnapshot product = productClient.getFresh(line.productId());
            if (line.quantity() > product.stockQuantity()) {
                throw new IllegalArgumentException(
                        "Insufficient stock for: " + product.name() +
                        ". Available: " + product.stockQuantity() + ", Requested: " + line.quantity());
            }
            return OrderItem.builder()
                    .productId(product.id())
                    .productName(product.name())
                    .quantity(line.quantity())
                    .priceAtPurchase(product.price())
                    .build();
        }).toList();

        BigDecimal total = orderItems.stream()
                .map(i -> i.getPriceAtPurchase().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder()
                .userEmail(email)
                .status(Order.OrderStatus.PENDING)
                .totalPrice(total)
                .shippingStreet(address != null ? address.street() : null)
                .shippingCity(address != null ? address.city() : null)
                .shippingState(address != null ? address.state() : null)
                .shippingPincode(address != null ? address.pincode() : null)
                .shippingCountry(address != null ? address.country() : null)
                .build();

        orderItems.forEach(item -> item.setOrder(order));
        order.getItems().addAll(orderItems);
        Order saved = orderRepository.save(order);

        // Deduct stock -- one HTTP call per line item, each its own commit on product-service's side
        for (OrderItem item : orderItems) {
            productClient.adjustStock(item.getProductId(), -item.getQuantity());
        }

        cartClient.clear();

        log.info("Order placed: orderId={}, user={}, total={}", saved.getId(), email, total);
        String firstName = extractFirstName(email);
        notificationClient.sendOrderConfirmation(email, firstName, saved);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getMyOrders(String email, int page, int size) {
        return orderRepository.findByUserEmail(email,
                        PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(String email, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.getUserEmail().equals(email))
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        return toResponse(order);
    }

    @Transactional
    public OrderResponse cancelOrder(String email, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.getUserEmail().equals(email))
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        Set<Order.OrderStatus> cancellable = Set.of(Order.OrderStatus.PENDING, Order.OrderStatus.CONFIRMED);
        if (!cancellable.contains(order.getStatus())) {
            throw new IllegalArgumentException(
                    "Cannot cancel order in status: " + order.getStatus() +
                    ". Only PENDING or CONFIRMED orders can be cancelled.");
        }

        for (OrderItem item : order.getItems()) {
            productClient.adjustStock(item.getProductId(), item.getQuantity()); // restore stock
        }

        order.setStatus(Order.OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);

        log.info("Order cancelled: orderId={}, user={}", orderId, email);
        String firstName = extractFirstName(email);
        notificationClient.sendOrderCancellation(email, firstName, orderId, order.getTotalPrice());
        return toResponse(saved);
    }

    /** Called by payment-service (16.6) after it confirms payment for this order. Not admin-gated -- it's a side effect of the customer's own payment succeeding, checked by ownership like everything else here. */
    @Transactional
    public OrderResponse markConfirmed(String email, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.getUserEmail().equals(email))
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        order.setStatus(Order.OrderStatus.CONFIRMED);
        return toResponse(orderRepository.save(order));
    }

    private AddressSnapshot resolveShippingAddress(Long addressId) {
        if (addressId != null) return addressClient.getById(addressId);
        return addressClient.getDefault();
    }

    private String extractFirstName(String email) {
        String token = CurrentRequestToken.get();
        if (token == null) return email;
        String firstName = jwtValidator.firstName(jwtValidator.parse(token));
        return firstName != null ? firstName : email;
    }

    /** public: AdminService (16.8) reuses this instead of duplicating the mapping. */
    public OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .orderItemId(item.getId())
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .quantity(item.getQuantity())
                        .priceAtPurchase(item.getPriceAtPurchase())
                        .subtotal(item.getPriceAtPurchase().multiply(BigDecimal.valueOf(item.getQuantity())))
                        .build())
                .toList();

        return OrderResponse.builder()
                .orderId(order.getId())
                .status(order.getStatus())
                .totalPrice(order.getTotalPrice())
                .items(items)
                .placedAt(order.getCreatedAt())
                .shippingCity(order.getShippingCity())
                .build();
    }
}
