package com.yourorg.foodorder.controller;

import com.yourorg.foodorder.dto.MonitoringMetricsResponse;
import com.yourorg.foodorder.repository.OrderRepository;
import com.yourorg.foodorder.repository.UserRepository;
import com.yourorg.foodorder.util.ApiResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Collection;

@RestController
@RequestMapping("/api/admin/monitoring")
@PreAuthorize("hasRole('ADMIN')")
public class MonitoringController {

    private final MeterRegistry  registry;
    private final OrderRepository orderRepository;
    private final UserRepository  userRepository;

    public MonitoringController(MeterRegistry registry,
                                 OrderRepository orderRepository,
                                 UserRepository userRepository) {
        this.registry        = registry;
        this.orderRepository = orderRepository;
        this.userRepository  = userRepository;
    }

    /**
     * GET /api/admin/monitoring/metrics
     *
     * Aggregates live Micrometer counters with DB-level counts into a
     * single admin-facing snapshot. Intentionally lightweight — heavy
     * analytics live in AdminAnalyticsController.
     */
    @GetMapping("/metrics")
    public ResponseEntity<ApiResponse<MonitoringMetricsResponse>> getMetrics() {

        MonitoringMetricsResponse response = MonitoringMetricsResponse.builder()
                .totalOrders(orderRepository.countAllOrders())
                .ordersDelivered(sumCounter("orders_delivered_total"))
                .totalRevenue(safeRevenue())
                .activeUsers(userRepository.countActiveUsers())
                .paymentsSuccess(sumCounter("payments_success_total"))
                .paymentsFailed(sumCounter("payments_failed_total"))
                .cartCheckouts(sumCounter("cart_checkout_total"))
                .requestCount(sumCounter("request_count"))
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Sums all counters matching a metric name (across all tag combinations).
     */
    private double sumCounter(String name) {
        Collection<Counter> counters = Search.in(registry).name(name).counters();
        return counters.stream().mapToDouble(Counter::count).sum();
    }

    private BigDecimal safeRevenue() {
        BigDecimal rev = orderRepository.sumTotalRevenue();
        return rev != null ? rev : BigDecimal.ZERO;
    }
}