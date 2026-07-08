package com.ecommerce.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phase 14 — Custom Business Metrics with Micrometer.
 *
 * WHAT IS MICROMETER?
 *   Micrometer is the "SLF4J for metrics" — a vendor-neutral facade.
 *   You write: counter.increment() or timer.record(() -> doWork())
 *   Micrometer sends data to: Prometheus, Datadog, CloudWatch, Influx, etc.
 *   Swapping monitoring backends = change one dependency, not your code.
 *
 * WHAT IS PROMETHEUS?
 *   An open-source time-series database for metrics.
 *   Spring Actuator exposes /actuator/prometheus — Prometheus scrapes it.
 *   Grafana reads from Prometheus and renders dashboards.
 *
 *   Full observability stack (Phase 16+):
 *     App → /actuator/prometheus → Prometheus → Grafana dashboard
 *
 * THE 4 GOLDEN SIGNALS (Google SRE):
 *   1. Latency   — how long requests take
 *   2. Traffic   — how many requests per second
 *   3. Errors    — how many requests fail
 *   4. Saturation — how full your system is (queue depth, memory %)
 *
 * WHY CUSTOM METRICS vs just using actuator defaults?
 *   Actuator gives HTTP metrics, JVM memory, thread counts automatically.
 *   But you also need BUSINESS metrics:
 *   - "How many orders were placed in the last hour?"
 *   - "How many users registered today?"
 *   - "How many items are currently in active carts?"
 *   These don't come from generic HTTP metrics — you instrument them yourself.
 *
 * METRIC TYPES:
 *
 *   Counter: monotonically increasing number (never decreases)
 *     Use for: events that happen (orders placed, logins, emails sent)
 *     Prometheus function: rate(orders_placed_total[5m]) → orders per second
 *
 *   Gauge: value that goes up and down
 *     Use for: current state (active carts, queue depth, JVM memory)
 *     Prometheus function: ecommerce_active_carts → current snapshot
 *
 *   Timer: measures duration of operations
 *     Use for: how long order processing takes, DB query time
 *     Prometheus: histogram_quantile(0.95, ...) → 95th percentile latency
 *
 * MeterRegistry:
 *   Spring Actuator auto-creates a MeterRegistry bean.
 *   We inject it and register our custom metrics.
 *   If Prometheus dependency is present: it's a PrometheusMeterRegistry.
 *   Otherwise: it's a SimpleMeterRegistry (in-memory, for testing).
 */
@Service
@Slf4j
public class MetricsService {

    // ── Counters ──────────────────────────────────────────────────────────────

    private final Counter ordersPlacedCounter;
    private final Counter userRegistrationCounter;
    private final Counter orderCancellationCounter;
    private final Counter paymentSuccessCounter;
    private final Counter paymentFailureCounter;

    // ── Gauge backing value ───────────────────────────────────────────────────

    private final AtomicInteger activeCartCount = new AtomicInteger(0);

    // ── Timer ─────────────────────────────────────────────────────────────────

    private final Timer orderProcessingTimer;

    public MetricsService(MeterRegistry registry) {
        // Counter.builder(name) — metric name uses dots, Prometheus converts to underscores
        // ecommerce.orders.placed → ecommerce_orders_placed_total in Prometheus
        ordersPlacedCounter = Counter.builder("ecommerce.orders.placed")
                .description("Total number of orders successfully placed")
                .tag("type", "order") // tags allow filtering in Grafana
                .register(registry);

        userRegistrationCounter = Counter.builder("ecommerce.users.registered")
                .description("Total number of user registrations")
                .register(registry);

        orderCancellationCounter = Counter.builder("ecommerce.orders.cancelled")
                .description("Total number of orders cancelled")
                .register(registry);

        paymentSuccessCounter = Counter.builder("ecommerce.payments.success")
                .description("Total number of successful payments")
                .register(registry);

        paymentFailureCounter = Counter.builder("ecommerce.payments.failed")
                .description("Total number of failed payment attempts")
                .register(registry);

        // Gauge: wraps an AtomicInteger — Micrometer reads its value when scraped
        // This is a live snapshot: "how many carts exist right now?"
        Gauge.builder("ecommerce.carts.active", activeCartCount, AtomicInteger::get)
                .description("Number of currently active shopping carts")
                .register(registry);

        // Timer: measures how long order processing takes
        // Produces: sum, count, max, and histogram buckets in Prometheus
        orderProcessingTimer = Timer.builder("ecommerce.orders.processing.time")
                .description("Time taken to process and save an order")
                .register(registry);

        log.info("Custom business metrics registered: orders, users, payments, carts");
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void incrementOrdersPlaced() {
        ordersPlacedCounter.increment();
    }

    public void incrementRegistrations() {
        userRegistrationCounter.increment();
    }

    public void incrementOrderCancellations() {
        orderCancellationCounter.increment();
    }

    public void incrementPaymentSuccess() {
        paymentSuccessCounter.increment();
    }

    public void incrementPaymentFailure() {
        paymentFailureCounter.increment();
    }

    public void incrementActiveCarts() {
        activeCartCount.incrementAndGet();
    }

    public void decrementActiveCarts() {
        activeCartCount.decrementAndGet();
    }

    /**
     * Records order processing time.
     * Usage: metricsService.recordOrderProcessing(() -> { ... order logic ... });
     * The timer records duration automatically.
     */
    public void recordOrderProcessing(Runnable task) {
        orderProcessingTimer.record(task);
    }
}
