package com.ecommerce.cart;

/** Just what order-service needs from a cart line -- price/name are re-fetched fresh from product-service, not trusted from cart's own (possibly stale) snapshot. */
public record CartLineItem(Long productId, int quantity) {
}
