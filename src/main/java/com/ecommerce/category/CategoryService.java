package com.ecommerce.category;

import com.ecommerce.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<Category> getAllActive() {
        return categoryRepository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public Category getById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Category create(String name, String description) {
        Category category = Category.builder()
                .name(name)
                .description(description)
                .build();
        return categoryRepository.save(category);
    }
}
