# Phase 12 — Advanced Querying

## Objective
Go beyond auto-generated repository methods. Learn JPQL, JOIN FETCH (the N+1 fix), aggregate queries, dynamic filters, and admin analytics.

---

## What We Built
| File | Purpose |
|---|---|
| `order/OrderRepository.java` | JOIN FETCH, aggregates, date-range filters |
| `admin/AdminDashboardService.java` | Business analytics using aggregate JPQL |
| `admin/AdminDashboardController.java` | Admin endpoints for dashboard, revenue, top customers |

## New API Endpoints
```
GET  /api/admin/dashboard               → total revenue, orders by status, product/user counts
GET  /api/admin/revenue?from=...&to=... → revenue in date range
GET  /api/admin/top-customers?limit=10  → highest-spending customers
GET  /api/admin/orders?status=PENDING&from=...&to=... → filtered paginated orders
PATCH /api/admin/orders/{id}/status     → update order status
All require ROLE_ADMIN
```

---

## Concepts Introduced

### The N+1 Query Problem — The Most Common JPA Performance Bug

```
Scenario: Load 10 orders and show each order's items.

WITHOUT JOIN FETCH (N+1 — bad):
  Query 1:  SELECT * FROM orders LIMIT 10            → 10 rows
  Query 2:  SELECT * FROM order_items WHERE order_id = 1
  Query 3:  SELECT * FROM order_items WHERE order_id = 2
  ...
  Query 11: SELECT * FROM order_items WHERE order_id = 10
  TOTAL: 11 queries for 10 orders

If 100 orders: 101 queries.
If 1000 orders: 1001 queries. Performance collapses.

The "1" = the first query to load the list.
The "N" = one query per entity to load the lazy relationship.

WITH JOIN FETCH (1 query — correct):
  SELECT DISTINCT o FROM Order o
  LEFT JOIN FETCH o.items i
  LEFT JOIN FETCH i.product
  WHERE o.user.id = :userId

  Generates:
  SELECT o.*, oi.*, p.*
  FROM orders o
  LEFT JOIN order_items oi ON oi.order_id = o.id
  LEFT JOIN products p ON p.id = oi.product_id
  WHERE o.user_id = ?

  → ONE query. All data loaded together. 100x faster for large datasets.
```

### When N+1 Happens

```
@Transactional(readOnly = true)
public List<OrderResponse> getOrders(Long userId) {
    List<Order> orders = orderRepository.findByUserId(userId); // 1 query
    return orders.stream()
        .map(order -> {
            order.getItems(); // LAZY → triggers 1 query per order → N+1!
            ...
        }).toList();
}

Fix options:
  1. JOIN FETCH in the repository query (best for specific use cases)
  2. @EntityGraph (declarative, cleaner for simple cases)
  3. Batch fetching: @BatchSize(size=20) on the collection (Hibernate hint)
```

### JPQL vs SQL — Key Differences

```
SQL (table-centric):
  SELECT o.*, u.email
  FROM orders o
  JOIN users u ON u.id = o.user_id
  WHERE o.status = 'PENDING'

JPQL (object-centric):
  SELECT o FROM Order o
  JOIN FETCH o.user
  WHERE o.status = :status

Differences:
  - JPQL uses entity class names (Order, not orders)
  - JPQL uses field names (o.user.email, not u.email)
  - JPQL traverses object graph (o.user.email auto-generates JOIN)
  - No SELECT * — you select the entity, Hibernate maps all columns
  - Parameters use :name syntax (not ?)
  - Hibernate translates JPQL → SQL at startup (validated early)
```

### Aggregate Queries — SUM, COUNT, AVG, GROUP BY

```java
// Total revenue
@Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.status = 'DELIVERED'")
BigDecimal getTotalRevenue();

// COALESCE: if no delivered orders, SUM returns NULL. COALESCE(NULL, 0) → 0.
// Without COALESCE: new app with no orders → NullPointerException in service.

// Count by status — returns List<Object[]>
@Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
List<Object[]> countByStatus();

// Each Object[]:
//   row[0] = OrderStatus (the enum value)
//   row[1] = Long (the count)
// Service maps it:
for (Object[] row : statusCounts) {
    String status = row[0].toString(); // e.g. "PENDING"
    Long count = (Long) row[1];        // e.g. 42
}
```

### Dynamic Filters — Optional WHERE Clauses

```java
@Query("""
    SELECT o FROM Order o
    WHERE (:status IS NULL OR o.status = :status)
    AND (:from IS NULL OR o.createdAt >= :from)
    AND (:to IS NULL OR o.createdAt <= :to)
    ORDER BY o.createdAt DESC
    """)
Page<Order> findByFilters(
    @Param("status") Order.OrderStatus status,
    @Param("from") LocalDateTime from,
    @Param("to") LocalDateTime to,
    Pageable pageable);

// Calling with only status filter:
orderRepository.findByFilters(Order.OrderStatus.PENDING, null, null, pageable);
// SQL: WHERE status = 'PENDING' (date conditions skipped via IS NULL)

// No filters (all orders):
orderRepository.findByFilters(null, null, null, pageable);
// SQL: WHERE (true) — no conditions → all rows
```

### `@DateTimeFormat` — Parsing DateTime from Query Params

```java
@GetMapping("/revenue")
public ResponseEntity<?> getRevenue(
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

// Client sends: ?from=2026-01-01T00:00:00&to=2026-12-31T23:59:59
// @DateTimeFormat parses the ISO-8601 string → LocalDateTime
// Without it: Spring can't convert the string → 400 Bad Request
```

### DISTINCT in JOIN FETCH — Avoid Duplicate Rows

```java
SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items

// Without DISTINCT:
// If an order has 3 items, the JOIN produces 3 rows for that order
// Hibernate returns List with the same Order object 3 times
// DISTINCT (at JPQL level) deduplicates before returning

// Note: this DISTINCT is JPA-level, not always SQL-level
// Hibernate processes the result set and deduplicates in-memory
```

### Spring Data Specifications — Dynamic Queries Without JPQL Strings

```java
// Alternative to @Query for very complex dynamic filters:
// Specification<Order> spec = (root, query, cb) ->
//     cb.and(
//         status != null ? cb.equal(root.get("status"), status) : cb.conjunction(),
//         from != null ? cb.greaterThanOrEqualTo(root.get("createdAt"), from) : cb.conjunction()
//     );
// orderRepository.findAll(spec, pageable);

// JpaSpecificationExecutor interface must be added to the repository.
// More verbose than @Query but fully type-safe (no strings at all).
// Prefer @Query for known, fixed queries. Specifications for truly dynamic queries.
```

---

## Dashboard API Examples

```
# Full dashboard summary
GET /api/admin/dashboard
Authorization: Bearer <admin-token>

# Revenue for a specific month
GET /api/admin/revenue?from=2026-06-01T00:00:00&to=2026-06-30T23:59:59

# Top 5 customers
GET /api/admin/top-customers?limit=5

# Pending orders today
GET /api/admin/orders?status=PENDING&from=2026-06-29T00:00:00&to=2026-06-29T23:59:59

# Update order to SHIPPED
PATCH /api/admin/orders/42/status
Body: { "status": "SHIPPED" }
```

---

## Common Bugs

| Bug | Cause | Fix |
|---|---|---|
| N+1 queries in logs | LAZY relationship accessed in loop | Use `JOIN FETCH` in repository |
| `HibernateException: cannot simultaneously fetch multiple bags` | Multiple `JOIN FETCH` on List fields | Change `List` to `Set` or use separate queries |
| `NullPointerException` on `SUM` result | No matching rows → SQL returns NULL | Wrap with `COALESCE(SUM(...), 0)` |
| `ConverterNotFoundException` for LocalDateTime | Missing `@DateTimeFormat` | Add annotation to `@RequestParam` |
| `InvalidDataAccessApiUsageException` | JPQL references non-existent field | Field names must match Java entity fields, not DB columns |
| Duplicate orders in JOIN FETCH result | Missing `DISTINCT` | Add `SELECT DISTINCT o` |

---

## Interview Questions

**Q: What is the N+1 query problem? How do you solve it in Spring Data JPA?**
> N+1 occurs when loading N entities triggers N additional queries to load a lazy relationship (one per entity). Loading 100 orders with lazy items = 101 queries. Solve with `JOIN FETCH` in JPQL: `SELECT o FROM Order o LEFT JOIN FETCH o.items` — one query loads everything. Alternatives: `@EntityGraph` (declarative), `@BatchSize` (Hibernate batch loading).

**Q: What is the difference between JPQL and SQL?**
> JPQL is object-oriented — queries reference entity class names and field names. SQL is table-oriented — queries reference table and column names. Hibernate translates JPQL to SQL. JPQL can traverse object graphs (`o.user.email` auto-generates a JOIN), whereas SQL requires explicit JOIN conditions.

**Q: What does `COALESCE` do in a JPQL aggregate query?**
> `COALESCE(expr, default)` returns the first non-null value. `SUM()` returns NULL when there are no matching rows. `COALESCE(SUM(o.totalPrice), 0)` ensures the result is `0` instead of `null` when no orders exist — preventing NPE in the service layer.

**Q: When would you use Spring Data Specifications instead of `@Query`?**
> Specifications (JPA Criteria API) are useful when the query structure itself changes at runtime — e.g., an admin filter UI where any combination of 10 fields can be active. `@Query` JPQL strings are good for a known, fixed query structure with optional parameters (using `param IS NULL OR ...` pattern). Specifications are more verbose but fully type-safe.

**Q: What is `DISTINCT` in `SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items`?**
> Without DISTINCT, a JOIN FETCH multiplies rows: an order with 3 items produces 3 result rows, all pointing to the same Order object. JPQL `DISTINCT` instructs Hibernate to deduplicate at the application level — the returned list contains each Order once with its items properly populated.

---

## MFAQ

**Why does `GROUP BY` in JPQL return `Object[]` instead of a DTO?**
Aggregate queries like `SELECT o.status, COUNT(o)` return multiple columns of different types — no single entity or DTO maps to this naturally. Spring Data returns `List<Object[]>` where each `Object[]` is one row. You manually cast: `(String) row[0]`, `(Long) row[1]`. For cleaner code, use a projection interface or a record DTO with constructor expression: `SELECT NEW com.ecommerce.admin.StatusCount(o.status, COUNT(o))`.

**Can I use `@Query` with native SQL instead of JPQL?**
Yes: `@Query(value = "SELECT * FROM orders WHERE ...", nativeQuery = true)`. Native queries bypass Hibernate's entity mapping — useful for DB-specific functions (PostgreSQL FTS, window functions) that JPQL can't express. Drawback: not portable across databases.

**Why is `AdminDashboardService` annotated with `@PreAuthorize` at class level?**
Class-level `@PreAuthorize` applies to ALL methods in the class — you don't repeat the annotation on every method. Any method not meant for admins should be in a different service. This is the "secure by default" principle: the class is locked down, and you explicitly open individual methods if needed.
