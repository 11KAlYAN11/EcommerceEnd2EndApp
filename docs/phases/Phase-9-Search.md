# Phase 9 — Product Search

## Objective
Add powerful, flexible product search with keyword matching across multiple fields, price range filtering, and category filtering — all in one API call.

---

## What We Built
| File | Purpose |
|---|---|
| `search/SearchController.java` | Dedicated search endpoint |
| `product/ProductRepository.java` | JPQL queries for multi-field search + filters |
| `product/ProductService.java` | `searchWithFilters()` method |

## API Endpoint
```
GET /api/search/products
  ?q=iphone          → search name and description (optional)
  &minPrice=50000    → minimum price (optional)
  &maxPrice=100000   → maximum price (optional)
  &categoryId=1      → filter by category (optional)
  &page=0            → page number (default 0)
  &size=10           → page size (default 10)
  &sort=name         → sort field (default name)

All params are optional — no params = all active products
Public endpoint — no auth needed
```

---

## Concepts Introduced

### Why a Separate SearchController?

```
ProductController: CRUD on the /products resource
  GET    /products          → list all (paginated)
  GET    /products/{id}     → get one
  POST   /products          → create
  PUT    /products/{id}     → update
  DELETE /products/{id}     → delete

SearchController: query operation, not a resource operation
  GET /search/products?q=iphone&minPrice=...

Why separate?
  REST principle: controllers map to resources (nouns).
  Search is a query (a verb acting on a resource).
  Different concern, different URL.
  In Microservices (Phase 16), search would be its own service.
```

### JPQL — Java Persistence Query Language

```
JPQL is like SQL but written against ENTITY CLASSES not TABLE NAMES.
Hibernate translates it to real SQL.

SQL:   SELECT * FROM products WHERE name ILIKE '%iphone%' AND active = true
JPQL:  SELECT p FROM Product p WHERE LOWER(p.name) LIKE LOWER('%iphone%') AND p.active = true

Key difference:
  SQL: column names (name, category_id)
  JPQL: field names (p.name, p.category.id — traverses the object graph)

@Query annotation: write JPQL inline on the repository method
  Spring validates it at startup (not at runtime) → fail-fast
```

### Dynamic Query with Optional Filters

```java
@Query("""
    SELECT p FROM Product p
    WHERE p.active = true
    AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                          OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
    AND (:minPrice IS NULL OR p.price >= :minPrice)
    AND (:maxPrice IS NULL OR p.price <= :maxPrice)
    AND (:categoryId IS NULL OR p.category.id = :categoryId)
    """)
Page<Product> searchWithFilters(...);
```

The trick: `:param IS NULL OR condition`
  If the param is null (client didn't send it) → condition is skipped
  If param is provided → condition is applied

This gives us ONE query that handles ALL filter combinations:
  No params       → returns all active products
  keyword only    → filters by name/description
  priceRange only → filters by price
  all params      → full filtered search

### `LIKE` vs Full-Text Search

```
LIKE '%keyword%' (our approach):
  Simple. Works everywhere. Good for small/medium datasets.
  Drawback: can't rank by relevance. "iphone" in description ranks same as "iphone" in name.
  No stemming: "phones" won't match "phone".
  Case handled by LOWER().

PostgreSQL Full-Text Search (production upgrade path):
  Uses tsvector (pre-processed text tokens) + tsquery
  Supports:
    - Relevance ranking (ts_rank)
    - Stemming (phone/phones/phoning all match)
    - Stop words (ignore "the", "a", "and")
    - Phrase search ("iphone 15")

  -- Example PostgreSQL FTS:
  SELECT *, ts_rank(to_tsvector('english', name || ' ' || description),
                    to_tsquery('english', 'iphone & 15')) AS rank
  FROM products
  WHERE to_tsvector('english', name || ' ' || description)
        @@ to_tsquery('english', 'iphone & 15')
  ORDER BY rank DESC;

  Add GIN index for performance:
  CREATE INDEX idx_products_fts ON products
  USING GIN(to_tsvector('english', name || ' ' || description));

Elasticsearch (enterprise):
  Separate search engine with inverted index, ML ranking, synonyms, autocomplete.
  When: dataset is huge, relevance matters a lot, real-time indexing needed.
  Overkill for most projects until you have millions of products.
```

### Spring Data Derived Query Methods

```java
// Spring reads the method name and generates the SQL:
Page<Product> findByPriceBetweenAndActiveTrue(
    BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

// Spring derives:
// SELECT * FROM products
// WHERE price BETWEEN :minPrice AND :maxPrice
// AND active = true
// [pagination applied]

Derivation rules:
  findBy     → WHERE clause
  Between    → BETWEEN x AND y
  And        → AND
  GreaterThan, LessThan, Like, In, IsNull, OrderBy...
  These method names are parsed by Spring at startup.
```

### `@Param` — Binding Named Parameters

```java
@Query("SELECT p FROM Product p WHERE p.name LIKE :keyword")
Page<Product> search(@Param("keyword") String keyword, Pageable pageable);

// :keyword in the query = the parameter named "keyword"
// @Param("keyword") binds the Java param to the query placeholder
// Without @Param: Spring can't map the parameter → runtime error
```

---

## Search API Examples

```
# Basic search
GET /api/search/products?q=iphone

# With price range
GET /api/search/products?q=phone&minPrice=20000&maxPrice=80000

# Category filter only
GET /api/search/products?categoryId=1

# Full filter
GET /api/search/products?q=apple&minPrice=50000&maxPrice=100000&categoryId=1&sort=price&page=0&size=5

# No filters (all products)
GET /api/search/products
```

---

## Common Bugs

| Bug | Cause | Fix |
|---|---|---|
| Search returns no results | LIKE is case-sensitive on some DBs | Use `LOWER()` on both sides |
| `@Param` not found | Forgot `@Param` annotation | Every named param in `@Query` needs `@Param` |
| `InvalidJpaQueryMethodException` | JPQL syntax error | Check entity field names (not column names) |
| `NullPointerException` in search | null keyword passed to LIKE | Use `:keyword IS NULL OR ...` pattern |
| Pagination not working with `@Query` | Missing `countQuery` for total elements | Spring usually derives it; add `countQuery` if not |

---

## Interview Questions

**Q: What is JPQL and how is it different from SQL?**
> JPQL (Java Persistence Query Language) queries operate on entity class names and field names, not table/column names. Hibernate translates JPQL to SQL. `SELECT p FROM Product p` → `SELECT * FROM products p`. JPQL can traverse object relationships: `p.category.name` becomes a JOIN in SQL automatically.

**Q: What is the difference between `LIKE '%keyword%'` search and Elasticsearch?**
> `LIKE` is simple pattern matching — no ranking, no stemming, no synonyms. Works for small datasets. Elasticsearch uses an inverted index (word → documents containing word), TF-IDF/BM25 relevance scoring, and supports advanced features like autocomplete, fuzzy matching, synonyms. Elasticsearch becomes necessary when you need ranked results, typo tolerance, or millions of documents.

**Q: How do you implement optional query filters without writing N separate SQL queries?**
> Use the `param IS NULL OR condition` pattern in JPQL/SQL. When a param is null (user didn't filter by it), the OR short-circuits and the condition is skipped. One query handles all combinations of optional filters.

**Q: What is a Spring Data derived query method?**
> Spring Data reads the repository method name and auto-generates the JPQL/SQL. `findByPriceBetweenAndActiveTrue(min, max, pageable)` → `WHERE price BETWEEN :min AND :max AND active = true`. Reduces boilerplate dramatically. Falls apart for complex joins — use `@Query` for those.

---

## MFAQ

**When should I switch from LIKE search to Elasticsearch?**
When: (1) dataset has millions of products, (2) relevance ranking matters (better matches should appear first), (3) you need typo tolerance (user types "iphne" → shows iPhone), (4) autocomplete as-you-type. For most e-commerce with <100K products, PostgreSQL full-text search or even LIKE is sufficient.

**Why is the search endpoint in `/search/` and not `/products/search`?**
Either works. `/products/search` is a valid RESTful sub-resource pattern. We used `/search/products` to make it clear this is a query domain, and it scales: `GET /search/categories`, `GET /search/orders` — all searches live under one prefix. This is purely a design convention choice.

**Can I search across products AND categories in one call?**
Not currently — would require a UNION query or Elasticsearch. For now, two separate calls: `GET /search/products?q=...` and `GET /categories?q=...` (not implemented yet). Unified search is an Elasticsearch use case.
