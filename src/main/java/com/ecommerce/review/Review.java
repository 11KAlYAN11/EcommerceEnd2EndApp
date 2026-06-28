package com.ecommerce.review;

import com.ecommerce.common.audit.Auditable;
import com.ecommerce.product.Product;
import com.ecommerce.user.User;
import jakarta.persistence.*;
import lombok.*;

/**
 * Represents a product review left by a user.
 *
 * Unique constraint on (user_id, product_id):
 *   One user can review one product exactly once.
 *   If the same user tries to review the same product again, the DB
 *   rejects it with a unique constraint violation.
 *   Business rule enforced at the database level — most reliable layer.
 *
 * rating (1–5):
 *   We store the rating as an Integer.
 *   Validation that it's between 1 and 5 belongs in the Service layer
 *   (or @Min/@Max annotations in Phase 4 when we add validation).
 *
 * verified_purchase:
 *   True if the user actually bought this product (confirmed via order history).
 *   Review from a verified buyer carries more weight — like Amazon's badge.
 *   In Phase 6, when an order is DELIVERED, we automatically set
 *   verified_purchase=true for the user-product combination.
 */
@Entity
@Table(
    name = "reviews",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_reviews_user_product", columnNames = {"user_id", "product_id"})
    },
    indexes = {
        @Index(name = "idx_reviews_product", columnList = "product_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "verified_purchase", nullable = false)
    @Builder.Default
    private boolean verifiedPurchase = false;
}
