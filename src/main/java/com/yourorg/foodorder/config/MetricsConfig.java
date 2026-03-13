package com.yourorg.foodorder.config;

import com.yourorg.foodorder.repository.OrderRepository;
import com.yourorg.foodorder.repository.UserRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 * Wires up JVM/system binders and registers DB-backed Gauges so that
 * Prometheus can scrape live counts without waiting for a REST call.
 */
@Configuration
public class MetricsConfig {

    private final MeterRegistry   registry;
    private final OrderRepository orderRepository;
    private final UserRepository  userRepository;

    public MetricsConfig(MeterRegistry registry,
                          OrderRepository orderRepository,
                          UserRepository userRepository) {
        this.registry        = registry;
        this.orderRepository = orderRepository;
        this.userRepository  = userRepository;
    }

    @PostConstruct
    public void registerMetrics() {
        // ── JVM & system binders ─────────────────────────────────────────────
        new JvmMemoryMetrics().bindTo(registry);
        new JvmGcMetrics().bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
        new ProcessorMetrics().bindTo(registry);

        // ── DB-backed Gauges (scraped lazily on Prometheus pull) ─────────────
        Gauge.builder("db_orders_total", orderRepository, OrderRepository::countAllOrders)
                .description("Live total order count from database")
                .register(registry);

        Gauge.builder("db_active_users_total", userRepository, UserRepository::countActiveUsers)
                .description("Users who have placed at least one order")
                .register(registry);
    }
}