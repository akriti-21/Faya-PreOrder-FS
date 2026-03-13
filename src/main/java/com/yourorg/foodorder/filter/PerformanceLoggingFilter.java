package com.yourorg.foodorder.util;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Logs a WARNING for any request that takes longer than SLOW_THRESHOLD_MS.
 *
 * Order(5) — runs after all protection filters, wrapping the full chain.
 *
 * Log format (structured for log aggregators):
 *   SLOW_REQUEST method=GET uri=/api/orders/{id} status=200 durationMs=1234
 */
@Component
@Order(5)
public class PerformanceLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PerformanceLoggingFilter.class);

    /** Threshold in milliseconds above which a request is considered slow. */
    private static final long SLOW_THRESHOLD_MS = 500L;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        long startMs = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startMs;
            if (durationMs >= SLOW_THRESHOLD_MS) {
                log.warn("SLOW_REQUEST method={} uri={} status={} durationMs={}",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        durationMs);
            } else {
                log.debug("REQUEST method={} uri={} status={} durationMs={}",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        durationMs);
            }
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }
}