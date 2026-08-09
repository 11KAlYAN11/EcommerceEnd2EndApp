package com.ecommerce.order;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

/** Same pattern as product-service's ProductSpec -- each method returns null for an unset filter, and Specification.where(...).and(...) silently skips null predicates. */
public class OrderSpec {

    private OrderSpec() {}

    public static Specification<Order> statusEquals(Order.OrderStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Order> createdAfter(LocalDateTime from) {
        return from == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Order> createdBefore(LocalDateTime to) {
        return to == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }
}
