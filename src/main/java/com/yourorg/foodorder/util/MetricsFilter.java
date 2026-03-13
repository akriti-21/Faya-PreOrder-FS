package com.yourorg.foodorder.util;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;

/**
 * Servlet filter that instruments every inbound HTTP request with:
 *   - request_count  (counter, tagged by method + uri template + status)
 *   - request_latency (timer,   tagged by method + uri template + status)
 *
 * Runs AFTER RequestLoggingFilter (order 2).
 */
@Component
@Order(2)
public class MetricsFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MetricsFilter.class);

    private final MeterRegistry registry;

    public MetricsFilter(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        long startNs = System.nanoTime();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationNs = System.nanoTime() - startNs;

            // Prefer the matched route template (/api/orders/{id}) over raw URI
            // to avoid high-cardinality tag explosion.
            String uriTemplate = getUriTemplate(request);
            String method      = request.getMethod();
            String status      = String.valueOf(response.getStatus());

            // ── Counter ──────────────────────────────────────────────────────
            registry.counter("request_count",
                    "method", method,
                    "uri",    uriTemplate,
                    "status", status
            ).increment();

            // ── Timer ────────────────────────────────────────────────────────
            Timer.builder("request_latency")
                    .description("HTTP request latency")
                    .tags("method", method, "uri", uriTemplate, "status", status)
                    .register(registry)
                    .record(durationNs, java.util.concurrent.TimeUnit.NANOSECONDS);

            log.debug("Metrics recorded [method={} uri={} status={} durationMs={}]",
                    method, uriTemplate, status, durationNs / 1_000_000);
        }
    }

    /**
     * Returns the Spring MVC URI template (e.g. /api/orders/{orderId}) when
     * available, falling back to the raw request URI.
     */
    private String getUriTemplate(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern != null) {
            return pattern.toString();
        }
        // Strip query string to avoid cardinality blowup
        String uri = request.getRequestURI();
        return uri.length() > 100 ? uri.substring(0, 100) : uri;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Skip actuator endpoints — Spring Boot already instruments those
        return request.getRequestURI().startsWith("/actuator");
    }
}