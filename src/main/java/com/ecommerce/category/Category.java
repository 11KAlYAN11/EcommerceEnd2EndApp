package com.ecommerce.category;

import com.ecommerce.common.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;

/**
 * Represents a product category (Electronics, Clothing, Books...).
 *
 * Why a separate Category table (not just a string column on Product)?
 *   If we stored category as a string on products:
 *     product.category = "Electronics"
 *   Problems:
 *     - Typos: "Electroncs" vs "Electronics" = different categories
 *     - No metadata: can't add category description, image, or parent
 *     - Can't rename "Electronics" to "Tech" without updating every product
 *   A dedicated table fixes all of this: one row per category, referenced by FK.
 *
 * Self-referential (parent category):
 *   Categories can have sub-categories:
 *     Electronics → Phones → Smartphones
 *   The parent_id FK references the same table (self-join).
 *   A root category has parent = null.
 *   This is the standard tree pattern for category hierarchies.
 */
@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    // Self-referential: a category can have a parent category
    // Electronics → parent is null (root)
    // Smartphones → parent is Electronics
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;
}
