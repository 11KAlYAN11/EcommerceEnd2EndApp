package com.ecommerce.product;

import com.ecommerce.category.Category;
import com.ecommerce.category.CategoryRepository;
import java.math.BigDecimal;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.product.dto.ProductRequest;
import com.ecommerce.product.dto.ProductResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public Page<ProductResponse> getProducts(int page, int size, String sortBy, String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy).ascending());
        Page<Product> products = (search != null && !search.isBlank())
                ? productRepository.findByNameContainingIgnoreCaseAndActiveTrue(search, pageable)
                : productRepository.findByActiveTrue(pageable);
        return products.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> getProductsByCategory(Long categoryId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        return productRepository.findByCategoryIdAndActiveTrue(categoryId, pageable).map(this::toResponse);
    }

    @Cacheable(value = "product", key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long id) {
        return toResponse(findActiveProductById(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @CacheEvict(value = "product", allEntries = true)
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
    @CacheEvict(value = "product", key = "#id")
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
    @CacheEvict(value = "product", key = "#id")
    @Transactional
    public void deleteProduct(Long id) {
        Product product = findActiveProductById(id);
        product.setActive(false);
        productRepository.save(product);
        log.info("Product soft-deleted: id={}", id);
    }

    @CacheEvict(value = "product", key = "#id")
    @Transactional
    public void updateImageUrl(Long id, String imageUrl) {
        Product product = findActiveProductById(id);
        product.setImageUrl(imageUrl);
        productRepository.save(product);
    }

    /**
     * NEW in Phase 16.5 -- the monolith never needed this either. OrderService
     * used to do `product.setStockQuantity(product.getStockQuantity() - qty)`
     * directly on the same JPA entity, inside the same transaction as the
     * order write. Now order-service has no access to this entity at all, so
     * it asks for an adjustment over HTTP instead.
     *
     * Known gap, stated plainly: this is a read-then-write with no
     * distributed transaction wrapping it and the order-service write. Two
     * concurrent orders for the last unit of stock can both pass this check
     * before either commits -- the exact race condition ADR B-08 already
     * flagged as an accepted gap in the MONOLITH (single-JVM, no @Version
     * lock). Splitting into services doesn't introduce this race, but it
     * does remove the option of fixing it later with a simple @Transactional
     * boundary -- a real distributed fix needs a saga or reservation
     * pattern (Phase 17+), not just annotations.
     */
    @PreAuthorize("isAuthenticated()")
    @CacheEvict(value = "product", key = "#id")
    @Transactional
    public ProductResponse adjustStock(Long id, int delta) {
        Product product = findActiveProductById(id);
        int newQty = product.getStockQuantity() + delta;
        if (newQty < 0) {
            throw new IllegalArgumentException(
                    "Cannot reduce stock below zero for: " + product.getName());
        }
        product.setStockQuantity(newQty);
        return toResponse(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> searchWithFilters(
            String keyword, BigDecimal minPrice, BigDecimal maxPrice,
            Long categoryId, int page, int size, String sortBy) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy).ascending());
        Specification<Product> spec = Specification.where(ProductSpec.isActive())
                .and(ProductSpec.keywordContains(keyword))
                .and(ProductSpec.priceGte(minPrice))
                .and(ProductSpec.priceLte(maxPrice))
                .and(ProductSpec.inCategory(categoryId));
        return productRepository.findAll(spec, pageable).map(this::toResponse);
    }

    /** For order-service's admin dashboard summary (16.8) -- avoids order-service needing to know anything about how products are stored. */
    @Transactional(readOnly = true)
    public long countActive() {
        return productRepository.countByActiveTrue();
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
