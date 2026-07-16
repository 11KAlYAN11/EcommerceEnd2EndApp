package com.ecommerce.product;

import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

public class ProductSpec {

    private ProductSpec() {}

    public static Specification<Product> isActive() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    public static Specification<Product> keywordContains(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        String pattern = "%" + keyword.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("description")), pattern)
        );
    }

    public static Specification<Product> priceGte(BigDecimal min) {
        return min == null ? null :
                (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), min);
    }

    public static Specification<Product> priceLte(BigDecimal max) {
        return max == null ? null :
                (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), max);
    }

    public static Specification<Product> inCategory(Long categoryId) {
        return categoryId == null ? null :
                (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }
}
