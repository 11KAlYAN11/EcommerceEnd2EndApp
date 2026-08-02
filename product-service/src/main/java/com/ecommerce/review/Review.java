package com.ecommerce.review;

import com.ecommerce.common.audit.Auditable;
import com.ecommerce.product.Product;
import jakarta.persistence.*;
import lombok.*;

/**
 * Only real change from the monolith: `User user` (a JPA @ManyToOne into a
 * table this service no longer has) becomes a plain `Long userId` — same
 * entity-coupling-to-ID-reference pattern as everywhere else in this
 * extraction, just going the other direction (product-service holding a
 * reference to a user-service ID, instead of the reverse).
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

    @Column(name = "user_id", nullable = false)
    private Long userId;

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
