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

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final ProductService productService;

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
