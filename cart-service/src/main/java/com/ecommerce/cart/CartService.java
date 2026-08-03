package com.ecommerce.cart;

import com.ecommerce.cart.dto.CartItemRequest;
import com.ecommerce.cart.dto.CartItemResponse;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.product.ProductClient;
import com.ecommerce.product.ProductSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductClient productClient;

    /**
     * No call to product-service here. This is the most frequently hit
     * endpoint in the whole cart flow (every cart page view), and it only
     * needs what's already stored locally -- the snapshot taken at the last
     * add/update. Trade-off: price shown here can be stale until the next
     * add/update touches that line item. Accepted deliberately -- Cart is
     * not a financial record like Order (ADR B-07's price snapshot is
     * permanent by design; this one is short-lived and self-refreshing).
     */
    @Transactional
    public CartResponse getCart(String email) {
        return toCartResponse(getOrCreateCart(email));
    }

    @Transactional
    public CartResponse addItem(String email, CartItemRequest request) {
        Cart cart = getOrCreateCart(email);
        ProductSnapshot product = productClient.getActiveProduct(request.getProductId());

        if (request.getQuantity() > product.stockQuantity()) {
            throw new IllegalArgumentException(
                "Only " + product.stockQuantity() + " units available for: " + product.name());
        }

        cartItemRepository.findByCartIdAndProductId(cart.getId(), product.id())
                .ifPresentOrElse(
                    existingItem -> {
                        int newQty = existingItem.getQuantity() + request.getQuantity();
                        if (newQty > product.stockQuantity()) {
                            throw new IllegalArgumentException(
                                "Cannot exceed stock. Available: " + product.stockQuantity());
                        }
                        existingItem.setQuantity(newQty);
                        // refresh snapshot -- price/name may have changed since last add
                        existingItem.setProductName(product.name());
                        existingItem.setImageUrl(product.imageUrl());
                        existingItem.setUnitPrice(product.price());
                        cartItemRepository.save(existingItem);
                    },
                    () -> {
                        CartItem item = CartItem.builder()
                                .cart(cart)
                                .productId(product.id())
                                .productName(product.name())
                                .imageUrl(product.imageUrl())
                                .unitPrice(product.price())
                                .quantity(request.getQuantity())
                                .build();
                        cart.getItems().add(item);
                    }
                );

        return toCartResponse(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse updateItemQuantity(String email, Long cartItemId, int quantity) {
        Cart cart = getOrCreateCart(email);
        CartItem item = cartItemRepository.findById(cartItemId)
                .filter(i -> i.getCart().getId().equals(cart.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Cart item", cartItemId));

        if (quantity <= 0) {
            cart.getItems().remove(item);
            cartItemRepository.delete(item);
        } else {
            // Re-check stock fresh -- it may have dropped since this was added
            ProductSnapshot product = productClient.getActiveProduct(item.getProductId());
            if (quantity > product.stockQuantity()) {
                throw new IllegalArgumentException("Only " + product.stockQuantity() + " units available");
            }
            item.setQuantity(quantity);
            item.setUnitPrice(product.price());
            cartItemRepository.save(item);
        }

        return toCartResponse(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse removeItem(String email, Long cartItemId) {
        Cart cart = getOrCreateCart(email);
        CartItem item = cartItemRepository.findById(cartItemId)
                .filter(i -> i.getCart().getId().equals(cart.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Cart item", cartItemId));

        cart.getItems().remove(item);
        cartItemRepository.delete(item);
        return toCartResponse(cartRepository.save(cart));
    }

    @Transactional
    public void clearCart(String email) {
        Cart cart = getOrCreateCart(email);
        cart.getItems().clear();
        cartRepository.save(cart);
    }

    public Cart getOrCreateCart(String email) {
        return cartRepository.findByUserEmail(email)
                .orElseGet(() -> cartRepository.save(Cart.builder().userEmail(email).build()));
    }

    private CartResponse toCartResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream().map(this::toItemResponse).toList();
        BigDecimal total = items.stream()
                .map(CartItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartResponse.builder()
                .cartId(cart.getId())
                .items(items)
                .totalItems(items.size())
                .totalPrice(total)
                .build();
    }

    private CartItemResponse toItemResponse(CartItem item) {
        BigDecimal subtotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        return CartItemResponse.builder()
                .cartItemId(item.getId())
                .productId(item.getProductId())
                .productName(item.getProductName())
                .imageUrl(item.getImageUrl())
                .unitPrice(item.getUnitPrice())
                .quantity(item.getQuantity())
                .subtotal(subtotal)
                .build();
    }
}
