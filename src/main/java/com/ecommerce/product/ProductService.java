package com.ecommerce.product;

import com.ecommerce.category.Category;
import com.ecommerce.category.CategoryRepository;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.product.dto.ProductRequest;
import com.ecommerce.product.dto.ProductResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    // Public: anyone can browse products (no auth needed)
    @Transactional(readOnly = true)
    public Page<ProductResponse> getProducts(int page, int size, String sortBy, String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy).ascending());
        Page<Product> products;

        if (search != null && !search.isBlank()) {
            products = productRepository.findByNameContainingIgnoreCaseAndActiveTrue(search, pageable);
        } else {
            products = productRepository.findByActiveTrue(pageable);
        }

        return products.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> getProductsByCategory(Long categoryId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        return productRepository.findByCategoryIdAndActiveTrue(categoryId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long id) {
        Product product = findActiveProductById(id);
        return toResponse(product);
    }

    // Admin only
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.getCategoryId()));

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .stockQuantity(request.getStockQuantity())
                .imageUrl(request.getImageUrl())
                .category(category)
                .build();

        Product saved = productRepository.save(product);
        log.info("Product created: {} (id={})", saved.getName(), saved.getId());
        return toResponse(saved);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = findActiveProductById(id);
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.getCategoryId()));

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setImageUrl(request.getImageUrl());
        product.setCategory(category);

        return toResponse(productRepository.save(product));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void deleteProduct(Long id) {
        Product product = findActiveProductById(id);
        product.setActive(false); // soft delete — preserves order history
        productRepository.save(product);
        log.info("Product soft-deleted: id={}", id);
    }

    private Product findActiveProductById(Long id) {
        return productRepository.findById(id)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private ProductResponse toResponse(Product p) {
        return ProductResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .price(p.getPrice())
                .stockQuantity(p.getStockQuantity())
                .imageUrl(p.getImageUrl())
                .active(p.isActive())
                .categoryId(p.getCategory().getId())
                .categoryName(p.getCategory().getName())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
