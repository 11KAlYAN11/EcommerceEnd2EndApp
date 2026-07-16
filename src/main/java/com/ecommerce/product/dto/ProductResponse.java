package com.ecommerce.product.dto;

import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO (Data Transfer Object) — what the client receives.
 *
 * Why not return the Product entity directly?
 * 1. Entities may have circular references (Product→Category→Products) → JSON infinite loop
 * 2. Entities expose internal fields (password, internal flags) we don't want to share
 * 3. API contract should be independent of DB schema — changing DB shouldn't break clients
 * 4. DTOs let us shape the response exactly as the frontend needs it
 */
@Getter
@Builder
public class ProductResponse implements Serializable {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stockQuantity;
    private String imageUrl;
    private boolean active;
    private Long categoryId;
    private String categoryName;
    private LocalDateTime createdAt;
}
