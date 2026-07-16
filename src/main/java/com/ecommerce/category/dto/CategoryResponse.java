package com.ecommerce.category.dto;

import com.ecommerce.category.Category;
import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * DTO for Category — never return the entity directly.
 *
 * WHY this was causing 500:
 *   Category entity has `parent: Category` with FetchType.LAZY.
 *   When CategoryController returned the entity directly, Jackson tried to
 *   serialize `parent` AFTER the @Transactional service method had returned
 *   (and the Hibernate session was closed). Accessing a lazy proxy outside
 *   a session throws LazyInitializationException → 500.
 *
 *   This DTO breaks that chain — we extract only what we need INSIDE the
 *   transaction, then return a plain object with no Hibernate proxies.
 */
@Getter
@Builder
public class CategoryResponse implements Serializable {
    private Long id;
    private String name;
    private String description;
    private String imageUrl;
    private boolean active;
    private Long parentId;       // just the ID, not the full parent object
    private String parentName;   // denormalized for convenience
    private LocalDateTime createdAt;

    public static CategoryResponse from(Category c) {
        return CategoryResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .description(c.getDescription())
                .imageUrl(c.getImageUrl())
                .active(c.isActive())
                .parentId(c.getParent() != null ? c.getParent().getId() : null)
                .parentName(c.getParent() != null ? c.getParent().getName() : null)
                .createdAt(c.getCreatedAt())
                .build();
    }
}
