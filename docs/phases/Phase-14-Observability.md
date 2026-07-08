# Phase 14 — Observability

## Objective
Make the app observable in production: know what it's doing, catch problems before users do, and trace a specific request through log noise using correlation IDs.

---

## What We Built

| File | Purpose |
|---|---|
| `observability/CorrelationIdFilter.java` | Attaches unique request ID to every request and every log line |
| `observability/MetricsService.java` | Custom business counters, gauges, timers via Micrometer |
| `application.properties` | Exposes `/actuator/prometheus`, structured log pattern |
| `pom.xml` | Added `micrometer-registry-prometheus` |

---

## The Three Pillars of Observability

```
1. LOGS     → What happened, in words
               "Order #42 placed for alice@test.com, total=₹1,200"
               "User bob@test.com failed login 3 times"

2. METRICS  → What is happening, in numbers
               Orders placed per minute: 42
               Failed logins per hour: 3
               95th percentile latency: 240ms

3. TRACES   → How did this specific request flow through the system?
               Request abc123 → AuthFilter (2ms) → ProductService (18ms) → DB (5ms)
               (Full distributed tracing → Phase 16 with Jaeger/Zipkin)

Phase 14 covers logs (correlation IDs) + metrics (Micrometer/Prometheus).
Distributed tracing comes with microservices in Phase 16.
```

---

## Concepts Introduced

### Correlation ID — Tying Log Lines to Requests

```
PROBLEM: Production app receives 100 req/second.
Log files contain thousands of lines from different requests, interleaved:

14:22:01.001 INFO  CartService - Cart loaded for alice@test.com
14:22:01.002 INFO  CartService - Cart loaded for bob@test.com
14:22:01.003 ERROR OrderService - Out of stock: product_id=5
14:22:01.004 INFO  OrderService - Order saved: id=99

Who got the out-of-stock error? Alice or Bob? Impossible to tell.

WITH CORRELATION ID:
14:22:01.001 [a1b2c3] INFO  CartService - Cart loaded for alice@test.com
14:22:01.002 [e5f6g7] INFO  CartService - Cart loaded for bob@test.com
14:22:01.003 [a1b2c3] ERROR OrderService - Out of stock: product_id=5
14:22:01.004 [e5f6g7] INFO  OrderService - Order saved: id=99

grep "a1b2c3" → see Alice's full request lifecycle instantly.
User support: "Can you give me the X-Correlation-Id from the error?"
→ DevOps finds the logs in seconds.
```

### MDC (Mapped Diagnostic Context) — Per-Thread Key-Value Storage

```java
// MDC is a ThreadLocal map provided by SLF4J
// Values put into MDC appear in every log statement for the current thread

MDC.put("correlationId", "a1b2c3");
log.info("Cart loaded");    // → "[a1b2c3] Cart loaded"
log.error("Out of stock");  // → "[a1b2c3] Out of stock"
MDC.remove("correlationId"); // MUST remove — threads are reused

// Log pattern in application.properties:
logging.pattern.console=%d{HH:mm:ss} [%X{correlationId:---}] %-5level %logger{36} - %msg%n
//                                      ↑ %X{key} prints MDC value
//                                            ↑ :--- = default if not set
```

### Micrometer — The SLF4J of Metrics

```
SLF4J is a facade for logging:
  log.info("message") → works with Logback, Log4j2, JUL
  Same code, swap backend.

Micrometer is a facade for metrics:
  counter.increment() → works with Prometheus, Datadog, CloudWatch
  Same code, swap monitoring backend.

Spring Boot auto-creates MeterRegistry.
With micrometer-registry-prometheus: it's a PrometheusMeterRegistry.
Exposes /actuator/prometheus → Prometheus scrapes it → Grafana displays it.
```

### The 4 Metric Types

```
1. COUNTER — monotonically increasing, never decreases
   Use for: events that happen (orders placed, logins, errors)
   Query: rate(ecommerce_orders_placed_total[5m]) → orders per second
   Resets when app restarts.

2. GAUGE — current snapshot, can go up or down
   Use for: current state (active carts, queue size, thread count)
   Query: ecommerce_carts_active → how many carts right now?
   
3. TIMER — measures duration
   Use for: how long operations take (DB queries, order processing)
   Produces: count, sum, max, histogram buckets
   Query: histogram_quantile(0.95, ...) → 95th percentile latency

4. DISTRIBUTION SUMMARY — like Timer but for sizes, not time
   Use for: request payload sizes, email queue depth
```

### Counter Usage Pattern

```java
// Define once (in constructor or @PostConstruct)
Counter ordersPlaced = Counter.builder("ecommerce.orders.placed")
        .description("Total orders successfully placed")
        .tag("type", "order")  // tags allow filtering in Grafana
        .register(registry);

// Call where the event happens
public OrderResponse placeOrder(...) {
    // ... business logic ...
    Order saved = orderRepository.save(order);
    ordersPlaced.increment(); // ← fire once per successful order
    return toResponse(saved);
}

// In Prometheus:
// ecommerce_orders_placed_total 142
// → This app has handled 142 orders since last restart

// In Grafana: rate(ecommerce_orders_placed_total[5m])
// → Orders per second over last 5 minutes → trends, spikes
```

### Actuator Endpoints

```
/actuator/health      → Is the app healthy? (Used by load balancer, Docker)
/actuator/info        → App version, build info (from application.properties info.*)
/actuator/metrics     → List all metric names
/actuator/metrics/ecommerce.orders.placed → value of specific metric
/actuator/prometheus  → All metrics in Prometheus format (text, scraped by Prometheus)
/actuator/loggers     → View/change log levels at runtime without restart

# Change log level at runtime (no restart needed):
POST /actuator/loggers/com.ecommerce
Body: { "configuredLevel": "DEBUG" }
→ Instantly get DEBUG logs for that package
→ Revert: send { "configuredLevel": null }
```

### Prometheus Scrape + Grafana Stack (Production)

```
┌─────────────┐    scrape every 15s    ┌────────────┐   query   ┌─────────┐
│  Spring App │ ──/actuator/prometheus──▶ Prometheus │ ─────────▶ Grafana │
│             │                         │ (time DB)  │           │ (UI)   │
└─────────────┘                         └────────────┘           └─────────┘

Prometheus query language (PromQL):
  rate(ecommerce_orders_placed_total[5m])          → orders/second
  rate(ecommerce_payments_failed_total[1h])         → failures/hour
  histogram_quantile(0.99, ecommerce_orders_processing_time_seconds_bucket)
                                                    → 99th percentile order time
  ecommerce_carts_active                            → current snapshot

Grafana panels show these as graphs, numbers, alerts.
Alerting: "If order failure rate > 5%, send PagerDuty alert."
```

---

## API — Actuator Endpoints

```bash
# Health check (used by load balancers, Docker HEALTHCHECK)
GET /api/actuator/health

# Prometheus metrics (scraped by Prometheus server)
GET /api/actuator/prometheus

# All metric names
GET /api/actuator/metrics

# Specific metric value
GET /api/actuator/metrics/ecommerce.orders.placed
GET /api/actuator/metrics/http.server.requests

# App info
GET /api/actuator/info

# View logger levels
GET /api/actuator/loggers/com.ecommerce

# Change logger level at runtime
POST /api/actuator/loggers/com.ecommerce
Content-Type: application/json
{"configuredLevel": "DEBUG"}
```

---

## Common Bugs

| Bug | Cause | Fix |
|---|---|---|
| Correlation ID missing from logs | MDC pattern not in `logging.pattern.console` | Add `%X{correlationId:---}` to pattern |
| MDC bleeds into next request | Forgot `MDC.remove()` in finally block | Always clean MDC in finally |
| `@Async` loses MDC | New thread starts with empty MDC | Pass MDC values explicitly or configure thread pool to inherit MDC |
| `/actuator/prometheus` returns 404 | `micrometer-registry-prometheus` not in pom.xml | Add dependency |
| `/actuator/prometheus` returns 401 | Actuator secured | Add to `management.endpoints.web.exposure.include` |
| Gauge always returns 0 | Gauge reads stale `AtomicInteger` reference | Register with `AtomicInteger::get` supplier, not a copied value |

---

## Interview Questions

**Q: What is observability and how is it different from monitoring?**
> Monitoring answers "is it up?" — a binary check. Observability answers "why is it slow/failing?" — derived from logs, metrics, and traces. A monitored system tells you something is wrong. An observable system tells you what's wrong, which request caused it, and why. Observability requires instrumentation built into the code (MDC, Micrometer), not just pings and uptime checks.

**Q: What is Micrometer and why use it instead of logging metrics?**
> Micrometer is a vendor-neutral metrics facade. You instrument once (`counter.increment()`) and the data flows to Prometheus, Datadog, CloudWatch, or any backend by changing configuration. Logging metrics (log.info("orders=42")) requires parsing logs to extract numbers — fragile, slow, and hard to aggregate across instances. Time-series databases (Prometheus) aggregate, rate, and percentile data natively.

**Q: What is MDC and why must you always remove values from it?**
> MDC (Mapped Diagnostic Context) is a ThreadLocal map in SLF4J. Values added to MDC appear in all subsequent log statements for the current thread. Java web servers use thread pools — threads are reused across requests. If you don't remove MDC values, the next request on the same thread inherits the previous request's correlation ID, causing incorrect tracing. Always use a `try/finally` block: set MDC before `chain.doFilter()`, remove it in `finally`.

**Q: What is the Prometheus data model?**
> Every metric is a time series identified by name + labels (tags). `ecommerce_orders_placed_total{type="order"}` = one time series. At each scrape, Prometheus records (timestamp, value). You query with PromQL: `rate(ecommerce_orders_placed_total[5m])` = average orders per second over last 5 minutes. Labels let you filter: `{status="500"}` = only 500 errors. High-cardinality labels (per-user labels) create millions of time series — avoid.

---

## MFAQ

**Does @Async break MDC (correlation ID lost in email threads)?**
Yes. `@Async` runs in a different thread. MDC is ThreadLocal — it doesn't cross thread boundaries. The email thread starts with empty MDC → correlation ID is lost in email logs. Fix: before submitting the async task, capture the MDC map and restore it in the background thread:
```java
Map<String, String> mdcCopy = MDC.getCopyOfContextMap();
executor.submit(() -> {
    if (mdcCopy != null) MDC.setContextMap(mdcCopy);
    try { sendEmail(); } finally { MDC.clear(); }
});
```
For Phase 14's learning purposes, this is fine to know but not implement yet.

**When should I alert vs just dashboard?**
Dashboard (visual): for trends you check proactively ("orders are down today").
Alert (pager): for critical thresholds that need immediate action.
Alert rules: error rate > 5% for 5min → page someone. P99 latency > 2s → page. Alert on SYMPTOMS (error rate, latency), not causes (CPU %). High CPU by itself doesn't mean users are impacted — alert when impact is confirmed.

**What's the difference between /actuator/health and /actuator/metrics?**
`/health` is a binary check: UP or DOWN, with sub-checks (DB connectivity, disk space). Used by load balancers and Docker HEALTHCHECK — if DOWN, take the instance out of rotation. `/metrics` is continuous instrumentation — counters, gauges, timers. Used by Prometheus for dashboards and alerting. Health = "is it working?" Metrics = "how well is it working?"
