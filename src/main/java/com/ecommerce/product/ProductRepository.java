package com.ecommerce.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Product entity.
 *
 * Page<Product> with Pageable:
 *   Pageable carries: page number, page size, sort direction.
 *   Page<Product> returns: the data + total count + total pages.
 *   This is how we implement pagination in Phase 4 without writing SQL.
 *
 *   Usage in service:
 *   Pageable pageable = PageRequest.of(0, 10, Sort.by("name").ascending());
 *   Page<Product> page = productRepo.findByActiveTrue(pageable);
 *   page.getContent()  → List<Product> for this page
 *   page.getTotalPages() → how many pages total
 *
 * findByCategoryId vs findByCategory_Id:
 *   Both work. findByCategoryId uses the foreign key column directly.
 *   Spring Data understands: Category is the entity, id is its field.
 *   The underscore notation (Category_Id) is explicit about traversal.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByActiveTrue(Pageable pageable);

    Page<Product> findByCategoryIdAndActiveTrue(Long categoryId, Pageable pageable);

    Page<Product> findByNameContainingIgnoreCaseAndActiveTrue(String name, Pageable pageable);

    List<Product> findByCategoryId(Long categoryId);
}
