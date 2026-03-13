package com.yourorg.foodorder.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Central service for recording business metrics via Micrometer.
 * No business logic lives here — only instrumentation calls.
 */
@Service
public class MetricsService {

    // ── Counters ─────────────────────────────────────────────────────────────

    private final Counter ordersTotal;
    private final Counter paymentsSuccessTotal;
    private final Counter paymentsFailedTotal;
    private final Counter cartCheckoutTotal;
    private final Counter ordersDeliveredTotal;

    // ── Timers ───────────────────────────────────────────────────────────────

    private final Timer orderProcessingTimer;
    private final Timer paymentProcessingTimer;

    private final MeterRegistry registry;

    public MetricsService(MeterRegistry registry) {
        this.registry = registry;

        this.ordersTotal = Counter.builder("orders_total")
                .description("Total number of orders placed")
                .register(registry);

        this.paymentsSuccessTotal = Counter.builder("payments_success_total")
                .description("Total successful payment transactions")
                .register(registry);

        this.paymentsFailedTotal = Counter.builder("payments_failed_total")
                .description("Total failed payment transactions")
                .register(registry);

        this.cartCheckoutTotal = Counter.builder("cart_checkout_total")
                .description("Total cart checkout attempts")
                .register(registry);

        this.ordersDeliveredTotal = Counter.builder("orders_delivered_total")
                .description("Total orders successfully delivered")
                .register(registry);

        this.orderProcessingTimer = Timer.builder("order_processing_duration_seconds")
                .description("Time taken to process an order end-to-end")
                .register(registry);

        this.paymentProcessingTimer = Timer.builder("payment_processing_duration_seconds")
                .description("Time taken to process a payment")
                .register(registry);
    }

    // ── Business event recording ──────────────────────────────────────────────

    public void recordOrderPlaced() {
        ordersTotal.increment();
    }

    public void recordOrderDelivered() {
        ordersDeliveredTotal.increment();
    }

    public void recordPaymentSuccess() {
        paymentsSuccessTotal.increment();
    }

    public void recordPaymentFailure() {
        paymentsFailedTotal.increment();
    }

    public void recordCartCheckout() {
        cartCheckoutTotal.increment();
    }

    /**
     * Records order processing duration in milliseconds.
     */
    public void recordOrderProcessingTime(long durationMs) {
        orderProcessingTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Records payment processing duration in milliseconds.
     */
    public void recordPaymentProcessingTime(long durationMs) {
        paymentProcessingTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Increment a generic tagged counter — useful for ad-hoc events.
     * e.g. recordTaggedEvent("order_status_change", "status", "DELIVERED")
     */
    public void recordTaggedEvent(String metricName, String tagKey, String tagValue) {
        registry.counter(metricName, tagKey, tagValue).increment();
    }
}