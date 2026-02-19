package com.foodorder.util;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Request logging filter — runs before all other filters.
 *
 * Responsibilities:
 *   1. Generate a unique traceId per request and store in MDC.
 *      MDC (Mapped Diagnostic Context) makes traceId available in all
 *      log statements for that request's thread, without passing it explicitly.
 *
 *   2. Log incoming request: method, URI, remote IP.
 *
 *   3. Log outgoing response: status code, duration in ms.
 *
 *   4. Set X-Trace-Id response header so clients can reference
 *      the trace in support tickets.
 *
 *   5. Clear MDC after the request to prevent thread pool contamination
 *      (Tomcat reuses threads — MDC must be explicitly cleared).
 *
 * What we log:
 *   - HTTP method, URI, response status, duration, remote IP
 *
 * What we NEVER log:
 *   - Request body (may contain passwords, PII, tokens)
 *   - Authorization header (contains Bearer token)
 *   - Query parameters (may contain sensitive data)
 *   - Response body (may contain PII)
 *
 * @Order(1): Runs first in the filter chain so traceId is in MDC
 *            before any other filter or security processing logs.
 */
@Slf4j
@Component
@Order(1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String MDC_TRACE_ID_KEY = "traceId";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Generate trace ID — check if client sent one (e.g., from API gateway)
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        // Store in MDC: all log statements in this thread will include traceId
        MDC.put(MDC_TRACE_ID_KEY, traceId);

        // Set response header: client can reference this in support requests
        response.setHeader(TRACE_ID_HEADER, traceId);

        long startTime = System.currentTimeMillis();

        try {
            // NOTE: We log URI but NOT query string (may contain sensitive params)
            log.info(">> {} {}", request.getMethod(), request.getRequestURI());

            filterChain.doFilter(request, response);

            long duration = System.currentTimeMillis() - startTime;
            log.info("<< {} {} | status={} | {}ms",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    duration);

        } finally {
            // CRITICAL: Clear MDC after every request.
            // Tomcat thread pools reuse threads. Failure to clear MDC here
            // would cause the next request on this thread to inherit the
            // previous request's traceId — a subtle, hard-to-debug bug.
            MDC.clear();
        }
    }

    /**
     * Skip logging for actuator endpoints to reduce noise in logs.
     * Actuator is on a separate management port but some probes may
     * go through the main port too.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator");
    }
}
