package com.ecommerce.product;

import com.ecommerce.category.Category;
import com.ecommerce.category.CategoryRepository;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.product.dto.ProductRequest;
import com.ecommerce.product.dto.ProductResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * UNIT TESTS for ProductService.
 *
 * WHAT WE ARE NOT TESTING (deliberately excluded):
 *   - @Cacheable behavior: cache annotations are Spring AOP.
 *     With @InjectMocks (no Spring context), AOP proxies don't wrap the bean.
 *     @Cacheable is silently ignored → the method runs normally every time.
 *     To test caching, you'd need @SpringBootTest with the full context.
 *   - @PreAuthorize: same reason — Spring Security AOP doesn't run here.
 *     @PreAuthorize("hasRole('ADMIN')") is ignored in pure unit tests.
 *     Security is tested in controller tests with @WithMockUser.
 *
 * WHAT WE ARE TESTING:
 *   - Business logic: soft delete sets active=false
 *   - Error paths: not found, soft-deleted product
 *   - DB interactions: which repository methods are called with what args
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock ProductRepository  productRepository;
    @Mock CategoryRepository categoryRepository;

    @InjectMocks
    ProductService productService;

    // ── getProduct() ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getProduct: found active product → returns response with correct fields")
    void getProduct_found_returnsResponse() {
        // Arrange
        Category cat = buildCategory(1L, "Electronics");
        Product product = buildProduct(1L, "Phone", new BigDecimal("999.99"), cat, true);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        // Act
        ProductResponse response = productService.getProduct(1L);

        // Assert
        assertThat(response.getName()).isEqualTo("Phone");
        assertThat(response.getPrice()).isEqualByComparingTo(new BigDecimal("999.99"));
        assertThat(response.getCategoryName()).isEqualTo("Electronics");
        assertThat(response.isActive()).isTrue();
    }

    @Test
    @DisplayName("getProduct: product not in DB → throws ResourceNotFoundException")
    void getProduct_notFound_throwsException() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProduct(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getProduct: product is soft-deleted (active=false) → treated as not found")
    void getProduct_softDeleted_throwsException() {
        // Soft-deleted products must NOT be visible to users — same as not found
        Category cat = buildCategory(1L, "Electronics");
        Product inactive = buildProduct(2L, "Old Phone", BigDecimal.TEN, cat, false);

        when(productRepository.findById(2L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> productService.getProduct(2L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── deleteProduct() ───────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteProduct: sets active=false (soft delete), does NOT call repository.delete()")
    void deleteProduct_setsActiveFalse() {
        // WHY SOFT DELETE?
        // Physical DELETE would break order history: "SELECT * FROM orders JOIN products"
        // would return null for deleted products. Orders would lose line items.
        // Soft delete (active=false) hides the product from listings while
        // preserving referential integrity for all historical data.
        Category cat = buildCategory(1L, "Electronics");
        Product product = buildProduct(3L, "Tablet", new BigDecimal("499.99"), cat, true);

        when(productRepository.findById(3L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);

        productService.deleteProduct(3L);

        assertThat(product.isActive()).isFalse();
        verify(productRepository).save(product); // save is called (update, not delete)
        verify(productRepository, never()).delete(any()); // physical delete never called
        verify(productRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("deleteProduct: product not found → throws ResourceNotFoundException")
    void deleteProduct_notFound_throwsException() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.deleteProduct(999L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(productRepository, never()).save(any());
    }

    // ── createProduct() ───────────────────────────────────────────────────────

    @Test
    @DisplayName("createProduct: valid request → saves product and returns response")
    void createProduct_valid_returnsResponse() {
        Category cat = buildCategory(1L, "Electronics");
        ProductRequest req = buildProductRequest("Laptop", new BigDecimal("1200.00"), 1L, 10);

        Product savedProduct = buildProduct(10L, "Laptop", new BigDecimal("1200.00"), cat, true);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(cat));
        when(productRepository.save(any(Product.class))).thenReturn(savedProduct);

        ProductResponse response = productService.createProduct(req);

        assertThat(response.getName()).isEqualTo("Laptop");
        assertThat(response.getCategoryId()).isEqualTo(1L);
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("createProduct: category not found → throws ResourceNotFoundException")
    void createProduct_categoryNotFound_throwsException() {
        ProductRequest req = buildProductRequest("Laptop", new BigDecimal("1200.00"), 99L, 10);

        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.createProduct(req))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(productRepository, never()).save(any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Category buildCategory(Long id, String name) {
        return Category.builder().id(id).name(name).active(true).build();
    }

    private Product buildProduct(Long id, String name, BigDecimal price,
                                 Category category, boolean active) {
        return Product.builder()
                .id(id)
                .name(name)
                .price(price)
                .stockQuantity(10)
                .category(category)
                .active(active)
                .build();
    }

    private ProductRequest buildProductRequest(String name, BigDecimal price,
                                               Long categoryId, int stock) {
        ProductRequest req = new ProductRequest();
        req.setName(name);
        req.setPrice(price);
        req.setCategoryId(categoryId);
        req.setStockQuantity(stock);
        req.setDescription("Test description");
        return req;
    }
}
