# Phase 4 — Products & Categories

## Objective
Expose product and category data through a proper REST API. Learn DTOs, pagination, soft delete, and role-based write access.

---

## What We Built
| File | Purpose |
|---|---|
| `category/Category.java` | Self-referential entity (parent/child categories) |
| `category/dto/CategoryResponse.java` | DTO — safe serialization, no lazy field exposure |
| `category/CategoryService.java` | CRUD + returns DTOs (not entities) |
| `category/CategoryController.java` | HTTP layer with `@PreAuthorize` for writes |
| `product/Product.java` | Product entity with BigDecimal, soft delete |
| `product/dto/ProductRequest.java` | Input DTO with validation |
| `product/dto/ProductResponse.java` | Output DTO |
| `product/ProductService.java` | CRUD + pagination |
| `product/ProductController.java` | HTTP layer |

## API Endpoints Built
```
GET    /api/categories              → list all (public)
POST   /api/categories              → create (ADMIN only)
GET    /api/categories/{id}         → get by id (public)
PUT    /api/categories/{id}         → update (ADMIN only)
DELETE /api/categories/{id}         → delete (ADMIN only)

GET    /api/products                → paginated list (public)
POST   /api/products                → create (ADMIN only)
GET    /api/products/{id}           → get by id (public)
PUT    /api/products/{id}           → update (ADMIN only)
DELETE /api/products/{id}           → soft delete (ADMIN only)
GET    /api/products/category/{id}  → products in category
GET    /api/products/search?q=      → search by name
```

---

## Concepts Introduced

### Why DTOs? — The Root Cause of the 500 Bug

The biggest bug in Phase 4 was returning `Category` entities directly from the controller:

```
WHAT HAPPENED:
  Controller returns Category entity
  Jackson tries to serialize it to JSON
  Category.parent is @ManyToOne(fetch = LAZY) — not loaded yet
  spring.jpa.open-in-view=false → Hibernate session is CLOSED
  Jackson calls getParent() → Hibernate tries to load → session closed
  → LazyInitializationException → 500 Internal Server Error

THE FIX:
  Inside @Transactional service method (session is OPEN):
    CategoryResponse.from(category) — accesses parent.getId(), parent.getName()
    All fields loaded into the DTO object (plain Java fields, no proxies)
  @Transactional ends → session closes
  Controller returns CategoryResponse (plain Java object)
  Jackson serializes it — no lazy fields, no Hibernate proxies → works
```

**Rule: Never return JPA entities from controllers. Always use DTOs.**

### `spring.jpa.open-in-view=false` — Why We Set It

```
Default (open-in-view=true):
  Hibernate session stays open for the entire HTTP request lifecycle
  Jackson can serialize lazy fields after the @Transactional method returns
  Works fine in development
  Problem: session held open during Jackson serialization → occupies DB connection
             → connection pool exhausted under load → production outage

open-in-view=false:
  Session opens with @Transactional, closes when @Transactional ends
  Forces you to write correct code — load what you need, map to DTO, return DTO
  Correct production behavior — no accidental lazy loading
  LazyInitializationException is your early warning that you're doing it wrong
```

### DTO Pattern — The Three-Layer Boundary

```
Layer 1: API Contract  (what the client sends/receives)
         ProductRequest, ProductResponse, CategoryResponse

Layer 2: Business Logic (what the service works with internally)
         Product entity

Layer 3: Database     (what Hibernate persists)
         products table

Why separate?
  - DB schema can change without breaking the API contract
  - API contract can change (add/remove fields) without changing the entity
  - Entities can have fields you never want to expose (password hash!)
  - DTOs can combine data from multiple entities (parent category name in CategoryResponse)
```

### `CategoryResponse.from(Category)` — The Static Factory Pattern

```java
public static CategoryResponse from(Category c) {
    return CategoryResponse.builder()
        .id(c.getId())
        .name(c.getName())
        .description(c.getDescription())
        .parentId(c.getParent() != null ? c.getParent().getId() : null)
        .parentName(c.getParent() != null ? c.getParent().getName() : null)
        .build();
}
```

Why static factory on the DTO?
- Keeps the mapping logic close to the DTO (cohesion)
- Service just calls `CategoryResponse.from(category)` — clean single line
- DTO knows how to build itself from an entity

### Pagination — Why It Matters

```
Problem: 10,000 products in DB
  GET /products → load all 10,000 → serialize → 10MB JSON response
  → Slow, memory-intensive, bad for mobile clients

Solution: Pagination
  GET /products?page=0&size=20 → load 20 products → fast response
  Response includes: content, totalElements, totalPages, currentPage, hasNext

Spring Pageable:
  Controller parameter: Pageable pageable
  Request: ?page=0&size=20&sort=price,asc
  Repository: productRepository.findAll(pageable) → Page<Product>
  Response: Page<ProductResponse>
```

```java
// Spring auto-creates Pageable from query params:
@GetMapping
public ResponseEntity<ApiResponse<Page<ProductResponse>>> getProducts(Pageable pageable) {
    // pageable = PageRequest.of(0, 20, Sort.by("name").ascending())
    // from ?page=0&size=20&sort=name,asc
}

// Default if no params: page=0, size=20
@PageableDefault(size = 20, sort = "name")
```

### Soft Delete — Never Delete Products

```java
// Hard delete — WRONG for products
productRepository.delete(product);
// Breaks: OrderItem.product_id → FK points to deleted row → DB error

// Soft delete — CORRECT
product.setActive(false);
productRepository.save(product);
// Product hidden from listings, but all FK references still valid
// Order history still shows the product name and price

// Repository: filter out inactive by default
List<Product> findByActiveTrue();
Page<Product> findByCategoryAndActiveTrue(Category category, Pageable pageable);
```

### `@PreAuthorize` on Write Operations

```java
// Public: anyone can browse
@GetMapping
public ResponseEntity<?> getProducts(Pageable pageable) { ... }

// ADMIN only: create, update, delete
@PostMapping
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> createProduct(@RequestBody ProductRequest req) { ... }

@DeleteMapping("/{id}")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> deleteProduct(@PathVariable Long id) { ... }
```

If a ROLE_USER tries POST /products → AccessDeniedException → 403 before the method body executes.

### Self-Referential Relationship — Category Tree

```java
@Entity
public class Category {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")    // nullable — root categories have no parent
    private Category parent;

    @OneToMany(mappedBy = "parent")
    private List<Category> children;
}

// Example tree:
// Electronics (parent=null)
//   ├── Phones (parent=Electronics)
//   │     └── Android (parent=Phones)
//   └── Laptops (parent=Electronics)
```

---

## Common Bugs

| Bug | Cause | Fix |
|---|---|---|
| 500 on GET /categories | Returning entity → Jackson serializes lazy `parent` → session closed | Use `CategoryResponse` DTO, map inside `@Transactional` |
| 403 on GET /products | SecurityConfig had `anyRequest().authenticated()` | Added `GET /products/**` and `GET /categories/**` to public URLs |
| 403 on POST /products | User has ROLE_USER | Use `/dev/make-admin` then re-login |
| Products not showing in search | Search includes inactive products | Add `AND active = true` to search query |
| `StackOverflowError` serializing Category | Entity has `children → parent → children` circular reference | DTOs break the cycle — `CategoryResponse` has no children list |
| Page 0 is empty | Pagination is zero-indexed | `?page=0` is first page, not `?page=1` |

---

## Interview Questions

**Q: What is a DTO and why should you never return JPA entities from REST controllers?**
> DTO (Data Transfer Object) is a plain Java object shaped specifically for the API response. Returning entities directly exposes internal DB structure (other tables, sensitive fields), causes `LazyInitializationException` when Jackson tries to serialize lazy-loaded relationships, and risks infinite recursion (A serializes B, B serializes A). DTOs decouple your API contract from your DB schema.

**Q: What is `LazyInitializationException` and how do you fix it?**
> It's thrown when you try to access a `FetchType.LAZY` relationship after the Hibernate session has closed. With `spring.jpa.open-in-view=false`, the session closes when `@Transactional` ends. Fix: map all needed data to a DTO inside the `@Transactional` service method while the session is still open. Never pass entities across the transaction boundary.

**Q: What is pagination and how does Spring implement it?**
> Pagination loads a subset of records (e.g., 20 at a time) instead of all rows. Spring's `Pageable` interface captures page number, size, and sort. Pass it to repository methods returning `Page<T>`. Spring auto-creates `Pageable` from `?page=0&size=20&sort=name,asc` query params. `Page<T>` response includes content, total elements, total pages, and navigation info.

**Q: What is soft delete and when should you use it?**
> Soft delete sets a boolean flag (`active=false`) instead of removing the row. Use it when other records reference the entity via foreign keys. Hard-deleting a product that appears in `order_items` would break order history. Soft delete hides the product from listings while preserving referential integrity.

**Q: Why is `@PreAuthorize("hasRole('ADMIN')")` on the controller method and not in SecurityConfig?**
> SecurityConfig URL-based rules are coarse-grained (all POSTs to `/products` require admin). Method-level `@PreAuthorize` is fine-grained and lives next to the code it protects — easier to review security boundaries. Both approaches work; method-level is more maintainable for complex apps with many rules.

---

## MFAQ

**Why is the `from()` method on the DTO and not the service?**
Keeps mapping logic cohesive with the DTO that defines the shape. If you add a field to `CategoryResponse`, you add it in one place. If mapping were in the service, you'd have to update the service AND the DTO separately.

**Can I use `@JsonIgnore` on the entity instead of a DTO?**
Technically yes, but it's a bad practice. `@JsonIgnore` leaks infrastructure concerns into your domain model. It also ties your API shape to your entity — you can't have two different APIs returning the same entity with different fields. DTOs are the correct solution.

**Why `Page<ProductResponse>` instead of `List<ProductResponse>`?**
`List` just gives you the items. `Page` gives you items + pagination metadata: `totalElements`, `totalPages`, `number`, `hasNext`, `hasPrevious`. The frontend needs this metadata to render "Page 1 of 47" and navigation buttons.
