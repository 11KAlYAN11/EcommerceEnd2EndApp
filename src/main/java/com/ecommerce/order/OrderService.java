package com.ecommerce.order;

import com.ecommerce.address.Address;
import com.ecommerce.address.AddressRepository;
import com.ecommerce.cart.Cart;
import com.ecommerce.cart.CartItem;
import com.ecommerce.cart.CartService;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.notification.EmailService;
import com.ecommerce.observability.MetricsService;
import com.ecommerce.order.dto.OrderItemResponse;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.dto.PlaceOrderRequest;
import com.ecommerce.product.Product;
import com.ecommerce.product.ProductRepository;
import com.ecommerce.user.User;
import com.ecommerce.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.access.prepost.PreAuthorize;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final CartService cartService;
    private final EmailService emailService;
    private final MetricsService metricsService;

    /**
     * Place an order from the user's current cart.
     *
     * @Transactional guarantees ACID:
     *   Atomic   → all steps succeed or none do
     *   Consistent → business rules enforced throughout
     *   Isolated → concurrent requests don't see each other's partial state
     *   Durable  → once committed, it's in the DB permanently
     *
     * If any step throws an exception → Spring rolls back the entire transaction:
     *   - stock is NOT deducted
     *   - order is NOT created
     *   - cart is NOT cleared
     *   The DB is left exactly as it was before this method was called.
     */
    @Transactional
    public OrderResponse placeOrder(String email, PlaceOrderRequest request) {
        User user = getUser(email);
        Cart cart = cartService.getOrCreateCart(email);

        // Rule: can't place order with empty cart
        if (cart.getItems().isEmpty()) {
            throw new IllegalArgumentException("Cannot place order: cart is empty");
        }

        // Resolve shipping address
        Address shippingAddress = resolveShippingAddress(user, request.getShippingAddressId());

        // Step 1: Validate stock and build order items
        // We do this BEFORE creating the order so we don't partially create anything
        List<OrderItem> orderItems = buildOrderItems(cart.getItems());

        // Step 2: Calculate total (using snapshot prices — not current product.price)
        BigDecimal total = orderItems.stream()
                .map(i -> i.getPriceAtPurchase().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Step 3: Create the Order
        Order order = Order.builder()
                .user(user)
                .status(Order.OrderStatus.PENDING)
                .totalPrice(total)
                .shippingAddress(shippingAddress)
                .build();

        // Step 4: Attach items to the order
        orderItems.forEach(item -> item.setOrder(order));
        order.getItems().addAll(orderItems);

        // Step 5: Deduct stock for each product
        // This happens AFTER building the order so we know all items are valid
        for (OrderItem item : orderItems) {
            Product product = item.getProduct();
            product.setStockQuantity(product.getStockQuantity() - item.getQuantity());
            productRepository.save(product);
        }

        // Step 6: Save order (cascades to order_items)
        Order saved = orderRepository.save(order);

        // Step 7: Clear the cart
        cartService.clearCart(email);

        log.info("Order placed: orderId={}, user={}, total={}", saved.getId(), email, total);
        metricsService.incrementOrdersPlaced(); // Phase 14 — track order count
        emailService.sendOrderConfirmation(user, saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getMyOrders(String email, int page, int size) {
        User user = getUser(email);
        return orderRepository.findByUserId(user.getId(),
                PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(String email, Long orderId) {
        User user = getUser(email);
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        return toResponse(order);
    }

    /**
     * Cancel an order.
     * Only cancellable from PENDING or CONFIRMED status.
     * Restores stock for each item — also in the same transaction.
     */
    @Transactional
    public OrderResponse cancelOrder(String email, Long orderId) {
        User user = getUser(email);
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        Set<Order.OrderStatus> cancellableStatuses =
                Set.of(Order.OrderStatus.PENDING, Order.OrderStatus.CONFIRMED);

        if (!cancellableStatuses.contains(order.getStatus())) {
            throw new IllegalArgumentException(
                    "Cannot cancel order in status: " + order.getStatus() +
                    ". Only PENDING or CONFIRMED orders can be cancelled."
            );
        }

        // Restore stock for every item in this order
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            productRepository.save(product);
        }

        order.setStatus(Order.OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);

        log.info("Order cancelled: orderId={}, user={}", orderId, email);
        emailService.sendOrderCancellation(user, saved);
        return toResponse(saved);
    }

    /**
     * Admin: paginated, filterable view of ALL orders.
     *
     * WHY @Transactional(readOnly=true) here?
     *   order.getItems() is a LAZY collection. Accessing it outside a transaction
     *   throws LazyInitializationException. By keeping this method @Transactional,
     *   the JPA session stays open while we call toResponse() → items are loaded.
     *   readOnly=true: tells Hibernate to skip dirty-checking → slight perf gain.
     *
     * WHY return Page<OrderResponse> not Page<Order>?
     *   If we returned Page<Order>, Jackson would serialize it AFTER this method
     *   returns and the transaction closes → LazyInitializationException on items/user.
     *   Mapping to DTO inside this @Transactional method solves that completely.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrdersFiltered(
            Order.OrderStatus status, LocalDateTime from, LocalDateTime to,
            int page, int size) {
        return orderRepository.findByFilters(status, from, to,
                PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(this::toResponse);
    }

    // Admin: update order status (CONFIRMED → PROCESSING → SHIPPED → DELIVERED)
    @Transactional
    public OrderResponse updateStatus(Long orderId, Order.OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        order.setStatus(newStatus);
        return toResponse(orderRepository.save(order));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private List<OrderItem> buildOrderItems(List<CartItem> cartItems) {
        return cartItems.stream().map(cartItem -> {
            Product product = cartItem.getProduct();

            // Stock check — concurrent orders could have depleted stock since cart add
            if (cartItem.getQuantity() > product.getStockQuantity()) {
                throw new IllegalArgumentException(
                        "Insufficient stock for: " + product.getName() +
                        ". Available: " + product.getStockQuantity() +
                        ", Requested: " + cartItem.getQuantity()
                );
            }

            return OrderItem.builder()
                    .product(product)
                    .quantity(cartItem.getQuantity())
                    .priceAtPurchase(product.getPrice()) // snapshot — frozen at order time
                    .build();
        }).toList();
    }

    private Address resolveShippingAddress(User user, Long addressId) {
        if (addressId != null) {
            return addressRepository.findById(addressId)
                    .filter(a -> a.getUser().getId().equals(user.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Address", addressId));
        }
        // Fall back to user's default address (optional — order can proceed without)
        return addressRepository.findByUserIdAndIsDefaultTrue(user.getId()).orElse(null);
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .orderItemId(item.getId())
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .quantity(item.getQuantity())
                        .priceAtPurchase(item.getPriceAtPurchase())
                        .subtotal(item.getPriceAtPurchase()
                                .multiply(BigDecimal.valueOf(item.getQuantity())))
                        .build())
                .toList();

        return OrderResponse.builder()
                .orderId(order.getId())
                .status(order.getStatus())
                .totalPrice(order.getTotalPrice())
                .items(items)
                .placedAt(order.getCreatedAt())
                .shippingAddressId(order.getShippingAddress() != null
                        ? order.getShippingAddress().getId() : null)
                .build();
    }
}
