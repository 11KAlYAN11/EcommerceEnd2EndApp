package com.ecommerce.search;

import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.product.ProductService;
import com.ecommerce.product.dto.ProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Phase 9 — Search API
 *
 * WHY a separate SearchController instead of adding params to ProductController?
 *   ProductController handles CRUD (create, read by id, update, delete).
 *   Search is a different concern — different query logic, different filters.
 *   Keeps ProductController focused on resource operations.
 *   In Microservices (Phase 16), search would be its own service anyway.
 *
 * All search endpoints are public (no auth needed — browsing is public).
 */
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final ProductService productService;

    /**
     * GET /api/search/products?q=iphone&minPrice=50000&maxPrice=100000&categoryId=1&page=0&size=10
     *
     * All params are optional:
     *   q          → searches name and description (case-insensitive)
     *   minPrice   → lower price bound
     *   maxPrice   → upper price bound
     *   categoryId → filter by category
     *   page/size  → pagination
     *   sort       → sort field (default: name)
     */
    @GetMapping("/products")
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> searchProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sort) {

        Page<ProductResponse> results = productService.searchWithFilters(
                q, minPrice, maxPrice, categoryId, page, size, sort);

        return ResponseEntity.ok(ApiResponse.success(
                "Found " + results.getTotalElements() + " products", results));
    }
}
