package com.ecommerce.category;

import com.ecommerce.category.dto.CategoryResponse;
import com.ecommerce.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @Cacheable("categories"):
 *   First call  → runs method, stores result in Redis under key "categories::all"
 *   Subsequent  → returns Redis result, method body NEVER runs
 *   TTL 30 min  → auto-evicted after 30 minutes
 *
 * @CacheEvict:
 *   When a category is created/updated → the cached list is stale → evict it
 *   Next GET will re-query DB and re-populate the cache
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Cacheable(value = "categories", key = "'all'")
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllActive() {
        return categoryRepository.findByActiveTrue()
                .stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Cacheable(value = "category", key = "#id")
    @Transactional(readOnly = true)
    public CategoryResponse getById(Long id) {
        Category cat = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
        return CategoryResponse.from(cat);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Caching(evict = {
        @CacheEvict(value = "categories", key = "'all'")
    })
    @Transactional
    public CategoryResponse create(String name, String description) {
        Category category = Category.builder()
                .name(name)
                .description(description)
                .build();
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Caching(evict = {
        @CacheEvict(value = "categories", key = "'all'"),
        @CacheEvict(value = "category", key = "#id")
    })
    @Transactional
    public CategoryResponse update(Long id, String name, String description) {
        Category cat = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
        cat.setName(name);
        if (description != null) cat.setDescription(description);
        return CategoryResponse.from(categoryRepository.save(cat));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Caching(evict = {
        @CacheEvict(value = "categories", key = "'all'"),
        @CacheEvict(value = "category", key = "#id")
    })
    @Transactional
    public void delete(Long id) {
        Category cat = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
        cat.setActive(false);
        categoryRepository.save(cat);
    }
}
