package com.ecommerce.category.dto;

import com.ecommerce.category.Category;
import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Builder
public class CategoryResponse implements Serializable {
    private Long id;
    private String name;
    private String description;
    private String imageUrl;
    private boolean active;
    private Long parentId;
    private String parentName;
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
