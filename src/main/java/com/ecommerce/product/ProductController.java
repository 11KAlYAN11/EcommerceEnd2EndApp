package com.ecommerce.product;

import com.ecommerce.common.response.ApiResponse;
import com.ecommerce.product.dto.ProductRequest;
import com.ecommerce.product.dto.ProductResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /** GET /api/products?page=0&size=10&sortBy=name&search=phone */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> getProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(ApiResponse.success("Products fetched",
                productService.getProducts(page, size, sortBy, search)));
    }

    /** GET /api/products/category/1?page=0&size=10 */
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> getByCategory(
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Products by category",
                productService.getProductsByCategory(categoryId, page, size)));
    }

    /** GET /api/products/1 */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Product fetched",
                productService.getProduct(id)));
    }

    /** POST /api/products  [ADMIN only] */
    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created",
                        productService.createProduct(request)));
    }

    /** PUT /api/products/1  [ADMIN only] */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Product updated",
                productService.updateProduct(id, request)));
    }

    /** DELETE /api/products/1  [ADMIN only] */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Product deleted"));
    }
}
