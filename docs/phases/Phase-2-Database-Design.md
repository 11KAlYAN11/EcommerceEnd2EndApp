# Phase 2 — Database Design

## Objective
Design the complete database schema as JPA entities. No business logic — only data modeling.

---

## What We Built
| Entity | Table | Key Relationships |
|---|---|---|
| `User` | `users` | Has many Orders, one Cart, many Addresses |
| `Role` | `roles` | Many-to-many with User |
| `Category` | `categories` | Self-referential (parent category) |
| `Product` | `products` | Many-to-one with Category |
| `Cart` | `carts` | One-to-one with User |
| `CartItem` | `cart_items` | Many-to-one with Cart and Product |
| `Order` | `orders` | Many-to-one with User |
| `OrderItem` | `order_items` | Many-to-one with Order and Product |
| `Payment` | `payments` | One-to-one with Order |
| `Address` | `addresses` | Many-to-one with User |
| `Review` | `reviews` | Many-to-one with User and Product |

Plus `Auditable` base class (not a table — `@MappedSuperclass`) that adds `created_at` and `updated_at` to every entity.

---

## Concepts Introduced

### JPA vs Hibernate vs Spring Data JPA
```
JPA             = Specification (interface, rules, annotations)
                  Defines: @Entity, @Table, @Column, @OneToMany...
                  Has NO implementation — just a contract

Hibernate       = Implementation of JPA
                  Generates actual SQL, manages connections
                  Translates Java object operations to DB queries

Spring Data JPA = Layer on top of JPA/Hibernate
                  Gives you JpaRepository with free CRUD
                  Eliminates boilerplate — no implementation needed

Analogy:
  JPA       = USB spec (shape, voltage, protocol)
  Hibernate = A USB cable from Anker
  Spring Data JPA = The device you plug it into
```

### The Object-Relational Mismatch
```
Java world:                    Database world:
─────────────────              ─────────────────────
User object                    users table
  String name    ←──────→      name VARCHAR(100)
  List<Order>    ←──────→      orders table + user_id FK
  (object ref)                 (integer foreign key)
```
JPA bridges this gap. You think in objects. Hibernate thinks in SQL.

### Key JPA Annotations
```java
@Entity                          // This class = a DB table
@Table(name = "users")           // Table name (be explicit)
@Id                              // Primary key
@GeneratedValue(strategy = IDENTITY) // DB auto-increment
@Column(nullable = false, unique = true, length = 255) // Column constraints
@Enumerated(EnumType.STRING)     // Store enum name (not ordinal index)
@MappedSuperclass                // Not a table — fields inherited by child entities
@EntityListeners(AuditingEntityListener.class) // Auto-set created_at/updated_at
```

### FetchType.LAZY vs EAGER — Critical Concept
```
EAGER: "Load this relationship immediately with every query"
  @ManyToOne(fetch = EAGER)
  → SELECT * FROM users WHERE id=1;
  → SELECT * FROM roles WHERE user_id=1;  ← automatic even if you don't need it
  Problem: Load 1 user → loads all their 500 orders → slow, wasteful

LAZY: "Load only when accessed in code"
  @ManyToOne(fetch = LAZY)
  → SELECT * FROM users WHERE id=1;       ← only this
  → (roles loaded only if user.getRoles() is called)

RULE: Always use LAZY. Only exception: small always-needed collections
      like user roles (needed on every security check).
```

### Cascade Types
```
Without cascade:
  order.getItems().add(item); // add to collection
  orderRepository.save(order); // only saves order
  itemRepository.save(item);   // must save separately!

With cascade = CascadeType.ALL:
  order.getItems().add(item);
  orderRepository.save(order); // saves order AND all items automatically

TYPES:
  PERSIST → save parent = save children
  MERGE   → update parent = update children
  REMOVE  → delete parent = delete children
  ALL     → all of the above

WARNING: Never use CASCADE.ALL on @ManyToMany — deletes shared entities.
```

### `orphanRemoval = true`
```java
@OneToMany(mappedBy = "cart", cascade = ALL, orphanRemoval = true)
private List<CartItem> items;

// If you remove an item from the list and save:
cart.getItems().remove(item);
cartRepository.save(cart);
// → Hibernate automatically DELETEs the CartItem row from DB
// Without orphanRemoval: item disappears from list but stays in DB (orphan)
```

### `mappedBy` — Who Owns the Foreign Key?
```java
// ORDER side — OWNS the FK (has @JoinColumn)
@ManyToOne(fetch = LAZY)
@JoinColumn(name = "user_id")   // creates user_id column in orders table
private User user;

// USER side — INVERSE (just a mirror for navigation)
@OneToMany(mappedBy = "user")   // "user" = field name in Order class
private List<Order> orders;
```
Without `mappedBy`, Hibernate creates a separate join table — wrong.

### Normalization — Why Split Data Into Tables?
```
BAD (denormalized):
orders: id | user_email | product_name | product_price | qty
         1 | a@x.com   | iPhone 15    | 999.00        | 1
         2 | a@x.com   | MacBook      | 2499.00       | 1

Problems:
  - Email stored twice → update once = inconsistency
  - Product price stored twice → can't track price changes

GOOD (normalized):
  users table → one row for Alice
  products table → one row for iPhone 15
  orders table → references user by user_id (FK)
  order_items table → references order and product by FK
```

### BigDecimal for Money — Non-Negotiable
```java
// ❌ NEVER DO THIS
double price = 0.1 + 0.2; // = 0.30000000000000004 (floating point error)

// ✅ ALWAYS DO THIS
BigDecimal price = new BigDecimal("0.1").add(new BigDecimal("0.2")); // = 0.3 EXACT

@Column(precision = 10, scale = 2) // DECIMAL(10,2) in DB — exact
private BigDecimal price;
```
Using `float`/`double` for money is a critical production bug. Financial audits fail.

### price_at_purchase — The Snapshot Pattern
```
Scenario: iPhone 15 costs ₹79,999 on Jan 1
          Apple reduces price to ₹69,999 on Jan 15
          User views order history on Jan 20

If OrderItem.price = product.price (live price):
  → Order history shows ₹69,999 ← WRONG

If OrderItem.priceAtPurchase = 79999.00 (snapshot at purchase time):
  → Order history shows ₹79,999 ← CORRECT
```
Capture state at transaction time. Historical data must never change retroactively.

### Soft Delete vs Hard Delete
```java
// Hard delete (BAD for e-commerce):
productRepository.delete(product); // Breaks all OrderItems referencing this product

// Soft delete (CORRECT):
product.setActive(false);          // Hides from listings
productRepository.save(product);   // Order history still works
```
Never hard-delete entities that are referenced by historical transactions.

---

## ER Diagram (Text)
```
USERS ──────── ROLES (many-to-many via user_roles)
  │
  ├─ has many ──► ORDERS
  │                  ├─ has many ──► ORDER_ITEMS ──► PRODUCTS
  │                  ├─ has one  ──► PAYMENT
  │                  └─ ref      ──► ADDRESSES
  │
  ├─ has one  ──► CART
  │                  └─ has many ──► CART_ITEMS ──► PRODUCTS
  │
  ├─ has many ──► ADDRESSES
  └─ has many (via products) ──► REVIEWS

PRODUCTS ──► CATEGORIES (self-referential tree)
```

---

## Common Bugs

| Bug | Cause | Fix |
|---|---|---|
| `LazyInitializationException` | Accessing LAZY field after session closed | Use DTOs — map inside `@Transactional` |
| `could not serialize: infinite recursion` | Entity A → Entity B → Entity A | Use DTOs, break the cycle |
| `Table "order" already exists` error | `order` is a reserved SQL keyword | Always name the table `orders` |
| `EnumType.ORDINAL` issues | Adding enum value in middle shifts all ordinals | Always use `EnumType.STRING` |
| `Duplicate entry` on save | Unique constraint violated | Check `existsBy...` before saving |

---

## Interview Questions

**Q: What is the difference between JPA and Hibernate?**
> JPA is a specification — a set of interfaces and annotations (like `@Entity`, `@Table`) defined by Jakarta EE. Hibernate is the most popular implementation of JPA. Spring Data JPA adds a repository abstraction layer on top that eliminates boilerplate CRUD code.

**Q: What is the N+1 query problem?**
> Load 100 orders (`SELECT * FROM orders`) then for each order access `order.getUser()` lazily — 100 more `SELECT * FROM users WHERE id = ?` queries = 101 total. Fix: use `JOIN FETCH` in JPQL to load everything in one query.

**Q: What is `FetchType.LAZY` and why should you prefer it?**
> LAZY means the related entity is loaded from DB only when first accessed in code. EAGER loads it immediately with every query, even when not needed. LAZY prevents over-fetching and the N+1 problem. Use LAZY everywhere; fetch eagerly only when needed with `JOIN FETCH`.

**Q: Why do we store `priceAtPurchase` on `OrderItem` instead of using `product.price`?**
> Product prices change. `priceAtPurchase` captures the exact price at the moment of purchase — a historical record that must never change. This is the "snapshot pattern." Without it, order history shows wrong amounts after price changes.

**Q: What is soft delete and when should you use it?**
> Soft delete sets a flag (`active=false`) instead of removing the row. Use it whenever other tables reference the entity (products referenced by order_items, users referenced by orders). Hard-deleting a product would break order history — the FK would point to a non-existent row.

---

## MFAQ

**Why does `@MappedSuperclass` not create a table?**
`@MappedSuperclass` tells JPA: "don't create a table for this class, but inherit its fields into every subclass's table." `Auditable` has no table — `users` gets `created_at` and `updated_at` columns.

**Why use `Set<Role>` instead of `List<Role>` for user roles?**
1. A user should never have the same role twice — Set enforces uniqueness.
2. Hibernate has a known bug with `@ManyToMany + List`: on any collection change, it deletes ALL rows and re-inserts. With Set, it only deletes/inserts the changed element.

**What is `@EnableJpaAuditing`?**
Activates Spring Data JPA's auditing infrastructure. Without it, `@CreatedDate` and `@LastModifiedDate` are ignored — those fields stay null. Must be on the main application class.
