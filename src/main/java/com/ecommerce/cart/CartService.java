package com.ecommerce.cart;

import com.ecommerce.cart.dto.CartItemRequest;
import com.ecommerce.cart.dto.CartItemResponse;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.product.Product;
import com.ecommerce.product.ProductRepository;
import com.ecommerce.user.User;
import com.ecommerce.user.UserRepository;
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
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public CartResponse getCart(String email) {
        Cart cart = getOrCreateCart(email);
        return toCartResponse(cart);
    }

    @Transactional
    public CartResponse addItem(String email, CartItemRequest request) {
        Cart cart = getOrCreateCart(email);
        Product product = getActiveProduct(request.getProductId());

        // Business rule: can't add more than available stock
        if (request.getQuantity() > product.getStockQuantity()) {
            throw new IllegalArgumentException(
                "Only " + product.getStockQuantity() + " units available for: " + product.getName()
            );
        }

        // If product already in cart → increment quantity
        // If not → insert new CartItem
        cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())
                .ifPresentOrElse(
                    existingItem -> {
                        int newQty = existingItem.getQuantity() + request.getQuantity();
                        if (newQty > product.getStockQuantity()) {
                            throw new IllegalArgumentException(
                                "Cannot exceed stock. Available: " + product.getStockQuantity()
                            );
                        }
                        existingItem.setQuantity(newQty);
                        cartItemRepository.save(existingItem);
                        log.debug("Cart item quantity updated: product={}, qty={}", product.getId(), newQty);
                    },
                    () -> {
                        CartItem item = CartItem.builder()
                                .cart(cart)
                                .product(product)
                                .quantity(request.getQuantity())
                                .build();
                        cart.getItems().add(item);
                        log.debug("New cart item added: product={}", product.getId());
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
            // quantity 0 or less = remove the item
            cart.getItems().remove(item);
            cartItemRepository.delete(item);
        } else {
            if (quantity > item.getProduct().getStockQuantity()) {
                throw new IllegalArgumentException(
                    "Only " + item.getProduct().getStockQuantity() + " units available"
                );
            }
            item.setQuantity(quantity);
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
        log.info("Cart item removed: cartItemId={}, user={}", cartItemId, email);
        return toCartResponse(cartRepository.save(cart));
    }

    @Transactional
    public void clearCart(String email) {
        Cart cart = getOrCreateCart(email);
        cart.getItems().clear();
        cartRepository.save(cart);
        log.info("Cart cleared for user: {}", email);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    // Gets existing cart or creates a new one (lazy cart creation)
    public Cart getOrCreateCart(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
        return cartRepository.findByUserId(user.getId())
                .orElseGet(() -> cartRepository.save(
                        Cart.builder().user(user).build()
                ));
    }

    private Product getActiveProduct(Long productId) {
        return productRepository.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
    }

    CartResponse toCartResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream()
                .map(this::toItemResponse)
                .toList();

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
        BigDecimal subtotal = item.getProduct().getPrice()
                .multiply(BigDecimal.valueOf(item.getQuantity()));
        return CartItemResponse.builder()
                .cartItemId(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .imageUrl(item.getProduct().getImageUrl())
                .unitPrice(item.getProduct().getPrice())
                .quantity(item.getQuantity())
                .subtotal(subtotal)
                .build();
    }
}
