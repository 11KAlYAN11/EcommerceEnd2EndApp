# Phase 8 — Redis Caching

## Objective
Add a caching layer between the app and the DB so repeated reads don't hit PostgreSQL. Learn Redis, Spring Cache abstraction, TTL, and cache invalidation.

---

## What We Built
| File | Purpose |
|---|---|
| `config/CacheConfig.java` | Redis config with per-cache TTL, graceful fallback |
| `category/CategoryService.java` | `@Cacheable` on reads, `@CacheEvict` on writes |
| `product/ProductService.java` | `@Cacheable` on `getProduct(id)`, `@CacheEvict` on mutations |
| `application.properties` | Redis host/port, TTL values |

## API Changes
No new endpoints — existing endpoints got faster. Observable with logs:
```
First  GET /api/products/1  → "Hibernate: select ..." in logs (DB hit)
Second GET /api/products/1  → NO Hibernate log (cache hit, DB not touched)
```

---

## Concepts Introduced

### Why Cache? — The Request Journey

```
Without cache:
  Client → App → PostgreSQL (disk I/O) → App → Client
  Every request: 5–50ms DB time

With Redis cache:
  1st request:   Client → App → Redis (miss) → PostgreSQL → Redis (store) → Client
  2nd–1000th:   Client → App → Redis (hit, sub-millisecond) → Client
  DB not touched for requests 2–1000
```

### Redis — What It Is

```
Redis = Remote Dictionary Server
  - In-memory key-value store (like a HashMap, but standalone)
  - Extremely fast: ~0.1ms reads (100x faster than DB)
  - TTL support: keys auto-expire after N seconds
  - Shared: all app instances read the same cache
  - Optional persistence: can snapshot to disk

Use cases (besides caching):
  - Sessions store
  - Rate limiting counters
  - Pub/Sub messaging
  - Distributed locks
  - Leaderboards (sorted sets)
```

### Spring Cache Abstraction — The Key Idea

```
Spring Cache is a DECORATION layer.
You annotate a method. Spring wraps it in a proxy.
On every call, the proxy asks: "Is the result already in cache?"
  YES → return cached result, skip method body
  NO  → run method, store result in cache, return result

The beautiful thing: you pick the backend in one config class.
  - Development: ConcurrentMapCacheManager (in-memory, simple)
  - Production:  RedisCacheManager (shared, TTL, persistent)
  The @Cacheable annotations on methods DON'T CHANGE.
  Only CacheConfig.java changes.
```

### `@Cacheable` — The Core Annotation

```java
@Cacheable(value = "product", key = "#id")
public ProductResponse getProduct(Long id) {
    // This body runs ONLY on cache miss
    return toResponse(productRepository.findById(id)...);
}

// What happens:
// 1. Spring generates cache key: "product::5"  (value::key)
// 2. Checks Redis: does "product::5" exist?
//    YES → return cached ProductResponse, method body skipped
//    NO  → run method, store result under "product::5" in Redis
```

### `@CacheEvict` — Invalidation

```java
@CacheEvict(value = "product", key = "#id")
public ProductResponse updateProduct(Long id, ProductRequest req) {
    // After this method runs, "product::5" is deleted from Redis
    // Next GET /products/5 → cache miss → fresh DB read
}
```

Why evict instead of update?
  Cache update: read old value, compute new value, write. Race conditions possible.
  Cache evict:  delete the key. Next read auto-repopulates. Simple and safe.

### `@Caching` — Multiple Evictions At Once

```java
@Caching(evict = {
    @CacheEvict(value = "categories", key = "'all'"),  // evict the list
    @CacheEvict(value = "category",   key = "#id")     // evict the single item
})
public CategoryResponse update(Long id, ...) { ... }
```

When you update a category, both the list cache AND the individual cache become stale.
`@Caching` lets you apply multiple cache operations in one annotation.

### TTL — Time To Live

```
TTL = how long an entry survives in Redis before auto-deletion.

products:   TTL 10 minutes
  Products change somewhat often (price updates, stock changes)
  Acceptable to show slightly stale data for 10 minutes
  After 10 min, next request repopulates from DB automatically

categories: TTL 30 minutes
  Categories rarely change (Electronics, Clothing, Books)
  30 minutes is safe — admins rarely add/rename categories

Without TTL: cache grows forever → stale data never refreshed → memory exhausted
```

### Cache Key Design

```
"product::5"      → ProductResponse for product with id=5
"product::42"     → ProductResponse for product with id=42
"categories::all" → List<CategoryResponse> of all active categories
"category::3"     → CategoryResponse for category with id=3

Why NOT cache paginated product lists?
  Key would be: "products::page=0&size=10&sort=name&search=phone"
  Thousands of unique keys for different param combinations
  Low reuse (most unique key combinations used only once)
  Cache hit rate would be near 0% — not worth the memory
```

### Graceful Fallback — App Works Without Redis

```java
try {
    connectionFactory.getConnection().ping(); // test Redis
    return RedisCacheManager.builder(...).build(); // Redis cache
} catch (Exception e) {
    log.warn("Redis not available. Falling back to in-memory cache.");
    return new ConcurrentMapCacheManager(...); // simple in-memory
}
```

This means:
  Redis running   → distributed cache with TTL (correct production behavior)
  Redis not running → in-process cache (no TTL, not shared, app still works)

Caching is an OPTIMISATION — the app must function without it.

---

## Installing Redis (for full caching)

```bash
# Windows: download from https://github.com/microsoftarchive/redis/releases
# Or use Docker:
docker run -d -p 6379:6379 --name redis redis:7-alpine

# Test it's running:
redis-cli ping  → PONG
```

---

## Common Bugs

| Bug | Cause | Fix |
|---|---|---|
| `@Cacheable` doing nothing | `@EnableCaching` missing | On `CacheConfig` class |
| Stale data after update | `@CacheEvict` not on update method | Add `@CacheEvict` to every write method |
| Wrong cache key | Using `"all"` without quotes in SpEL | Must be `key = "'all'"` — with inner single quotes (it's a SpEL string literal) |
| `SerializationException` | DTO not serializable | DTOs must have a no-arg constructor for JSON deserialization. Add `@NoArgsConstructor` if needed |
| Cache returns null | `disableCachingNullValues()` set | Good — null means record not found, don't cache that |

---

## Interview Questions

**Q: What is Redis and why use it as a cache instead of a HashMap?**
> Redis is a standalone in-memory key-value store. Unlike a HashMap in your app's heap: Redis is shared across all app instances (3 servers share one cache), survives app restarts, supports TTL (auto-expiry), and can be backed by disk for durability. A HashMap is local, per-process, and vanishes on restart.

**Q: What is cache invalidation? Why is it hard?**
> Cache invalidation = removing stale entries when the underlying data changes. It's hard because you must decide: when does the cache become stale? Too aggressive eviction → high DB load. Too lazy → stale data shown to users. Phil Karlton famously said: "There are only two hard things in Computer Science: cache invalidation and naming things."

**Q: What is TTL and why is it important?**
> TTL (Time To Live) is the expiry time for a cache entry. Without TTL, entries stay forever — the cache grows unboundedly and data becomes permanently stale. With TTL, entries auto-expire and are repopulated on next access. TTL is the safety net that ensures eventual consistency even without explicit eviction.

**Q: What is the Spring Cache abstraction? What are its benefits?**
> Spring Cache is an annotation-based caching facade. `@Cacheable`, `@CacheEvict`, `@CachePut` are on your methods; the actual backing store (Redis, EhCache, Caffeine, in-memory) is configured once in a `CacheManager` bean. You can swap from in-memory to Redis by changing one config class — no method annotations change.

**Q: When should you NOT cache?**
> Don't cache: data that changes per-request (user-specific data like "my cart"), write-heavy operations, data that MUST be real-time accurate (stock levels during checkout), or very large objects that would blow up Redis memory. Cache: read-heavy, relatively stable, shared-across-users data like product catalogs.

---

## MFAQ

**Why is the paginated product list NOT cached?**
Too many unique cache keys (every combination of page+size+sort+search = one key). Hit rate would be nearly 0%. Cache provides value when the same key is requested many times. For paginated lists, browse patterns are too varied.

**What's the difference between `@CacheEvict` and `@CachePut`?**
`@CacheEvict` deletes the entry — next read misses and repopulates. `@CachePut` updates the entry — stores the method's return value without checking the cache first. `@CacheEvict` is simpler and avoids race conditions; use it by default.

**Why `key = "'all'"` with inner single quotes?**
The `key` attribute is a Spring Expression Language (SpEL) expression. `"all"` would be evaluated as a variable reference (no variable named `all` exists → null). `"'all'"` — the inner single quotes make it a string literal in SpEL. Always use single quotes for string literals in `key` expressions.
