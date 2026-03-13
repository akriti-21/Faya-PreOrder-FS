package com.yourorg.foodorder.util;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Per-request MDC population, trace ID lifecycle, and HTTP access logging.
 *
 * <h2>Position in the filter chain</h2>
 * {@code @Order(Ordered.HIGHEST_PRECEDENCE)} places this filter first in the
 * entire Servlet filter chain — before Spring Security, before any other
 * {@code @Component} filter. This ordering is non-negotiable:
 * <ul>
 *   <li>Spring Security's filters log authentication events. Those log lines
 *       carry a {@code traceId} only if this filter has already populated MDC.</li>
 *   <li>{@link com.foodorder.exception.GlobalExceptionHandler} and
 *       {@link com.foodorder.security.JwtAuthenticationEntryPoint} read
 *       {@code MDC.get("traceId")} to populate the {@code ApiResponse} envelope.
 *       They get {@code null} if MDC is not yet set.</li>
 * </ul>
 *
 * <h2>Execution sequence per request</h2>
 * <ol>
 *   <li><b>Resolve traceId</b> — accept a validated client-supplied
 *       {@code X-Trace-Id} header, or generate a fresh UUID.</li>
 *   <li><b>Populate MDC</b> — {@code traceId} key set for this thread.
 *       All subsequent log statements on this thread automatically include it.</li>
 *   <li><b>Echo response header</b> — {@code X-Trace-Id} on the response lets
 *       clients correlate a specific request in server logs.</li>
 *   <li><b>Start timing</b> — single {@code System.currentTimeMillis()} snapshot.</li>
 *   <li><b>Log request entry</b> at DEBUG — method and URI path.</li>
 *   <li><b>Delegate to chain</b> — the rest of the application runs here.</li>
 *   <li><b>Log access line</b> in {@code finally} — method, URI, status,
 *       duration. Always executes, even if the chain throws an exception.</li>
 *   <li><b>Clear MDC</b> in {@code finally} — Tomcat reuses threads from its
 *       thread pool. A stale traceId on a reused thread would appear on the
 *       next request's log lines, corrupting distributed trace correlation.</li>
 * </ol>
 *
 * <h2>Log levels</h2>
 * <ul>
 *   <li><b>DEBUG</b> — request entry line. In dev this confirms routing is
 *       reached; in prod it is suppressed at the default INFO threshold.</li>
 *   <li><b>INFO</b> — single access log line in {@code finally}. Always written
 *       in every profile. Carries method, URI, status, and duration.</li>
 *   <li><b>WARN</b> — slow request threshold exceeded (configurable).</li>
 * </ul>
 *
 * <h2>Slow-request detection</h2>
 * Requests exceeding {@link #SLOW_REQUEST_THRESHOLD_MS} are logged at WARN
 * in addition to the normal INFO access line. This makes performance regressions
 * visible in production without requiring a separate APM tool.
 *
 * <h2>Exception path coverage</h2>
 * The access log line is written in {@code finally}, not in the {@code try} block.
 * This ensures it is written even when {@code FilterChain.doFilter()} throws a
 * {@code ServletException} or {@code IOException} — for example, when the client
 * disconnects mid-stream or when a filter earlier in the chain throws.
 *
 * <h2>Security: log injection prevention</h2>
 * The {@code X-Trace-Id} header is user-controlled input. Accepting it without
 * validation is a log injection vulnerability: a client can send a value
 * containing newlines, ANSI escape codes, or other characters that corrupt
 * structured log output or exploit log-parsing tools.
 *
 * <p>This filter validates the header against {@link #SAFE_TRACE_ID_PATTERN}
 * before accepting it. Non-matching values are silently discarded and a fresh
 * UUID is generated. The rejected value is <b>never logged</b> — doing so
 * would itself be a log injection vector.
 *
 * <h2>What is never logged</h2>
 * <ul>
 *   <li>Request or response body — may contain passwords, PII, payment data</li>
 *   <li>{@code Authorization} header — contains the Bearer JWT</li>
 *   <li>Query string — may contain tokens, sensitive search terms, or PII</li>
 *   <li>Response body — may contain PII</li>
 *   <li>The raw {@code X-Trace-Id} value when it fails validation</li>
 * </ul>
 *
 * <h2>Actuator health-probe suppression</h2>
 * {@link #shouldNotFilter} excludes {@code /actuator} paths. Load balancers
 * and container orchestrators probe {@code /actuator/health} multiple times
 * per minute. Logging each probe at INFO would drown out meaningful entries.
 * MDC is still populated for actuator paths via the pre-condition in
 * {@link #doFilterInternal} — skip logic is handled by Spring's
 * {@link OncePerRequestFilter} invoking {@link #shouldNotFilter} before
 * calling {@link #doFilterInternal}.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    /** MDC key used by every log appender pattern (logback-spring.xml: %X{traceId}) */
    public static final String MDC_TRACE_ID_KEY = "traceId";

    /** Response header name echoed to clients for support correlation */
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * Safe trace ID format: alphanumeric + hyphens, 8–64 characters.
     *
     * <p>Matches:
     * <ul>
     *   <li>Standard UUID ({@code a3b2c1d0-e5f6-7890-abcd-ef1234567890})</li>
     *   <li>AWS X-Ray trace IDs ({@code 1-5e1b3d2a-abc123def456789012345678})</li>
     *   <li>Short hex IDs from API gateways ({@code abc123def456})</li>
     * </ul>
     *
     * <p>Rejects: newlines, tabs, quotes, braces, ANSI escape codes, values
     * longer than 64 characters. All of these are log injection vectors.
     */
    private static final Pattern SAFE_TRACE_ID_PATTERN =
            Pattern.compile("^[a-zA-Z0-9\\-]{8,64}$");

    /**
     * Duration threshold above which a request is also logged at WARN.
     *
     * <p>500 ms is a conservative threshold for a REST API. Adjust based on
     * your SLA targets. Consider moving to {@code @ConfigurationProperties}
     * if different environments need different thresholds.
     */
    private static final long SLOW_REQUEST_THRESHOLD_MS = 500L;

    // ── Filter logic ──────────────────────────────────────────────────────────

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         filterChain) throws ServletException, IOException {

        // ── 1. Resolve and bind trace ID ──────────────────────────────────────
        String traceId = resolveTraceId(request);
        MDC.put(MDC_TRACE_ID_KEY, traceId);

        // Echo back so clients can include it in support requests.
        // Set early — even if the chain throws, the response header is present.
        response.setHeader(TRACE_ID_HEADER, traceId);

        // ── 2. Capture start time ─────────────────────────────────────────────
        // Single clock snapshot — used for both duration and slow-request check.
        long startMs = System.currentTimeMillis();

        // ── 3. Log request entry (DEBUG — suppressed in prod by default) ──────
        // Logged at DEBUG rather than INFO to avoid doubling log volume in prod.
        // The exit line (step 5) carries all the same information plus status+duration.
        // Includes a query-string indicator ("?" suffix) so we know a query was
        // present without logging its potentially sensitive content.
        String queryIndicator = (request.getQueryString() != null) ? "?..." : "";
        log.debug("--> {} {}{}", request.getMethod(), request.getRequestURI(), queryIndicator);

        // ── 4. Delegate to the rest of the filter chain ───────────────────────
        try {
            filterChain.doFilter(request, response);
        } finally {
            // ── 5. Log access line ────────────────────────────────────────────
            // Written in finally — executes whether doFilter() returns normally
            // OR throws (e.g. client disconnect, upstream filter exception).
            // On the exception path, response.getStatus() is 0 or undefined,
            // so we treat it as-is; the exception itself will appear in the
            // ERROR log from GlobalExceptionHandler with the same traceId.
            long durationMs = System.currentTimeMillis() - startMs;
            int  status     = response.getStatus();

            log.info("<-- {} {}{} | {} | {}ms",
                    request.getMethod(),
                    request.getRequestURI(),
                    queryIndicator,
                    status,
                    durationMs);

            // ── 6. Slow-request warning ───────────────────────────────────────
            // Separate WARN line so alerting rules can target "WARN.*SLOW_REQUEST"
            // without parsing the INFO access log. Threshold is conservative at
            // 500 ms — tune to your SLA (e.g. p99 target from your load tests).
            if (durationMs > SLOW_REQUEST_THRESHOLD_MS) {
                log.warn("SLOW_REQUEST: {} {}{} took {}ms (threshold: {}ms)",
                        request.getMethod(),
                        request.getRequestURI(),
                        queryIndicator,
                        durationMs,
                        SLOW_REQUEST_THRESHOLD_MS);
            }

            // ── 7. Clear MDC ──────────────────────────────────────────────────
            // CRITICAL: must be last, unconditional, and in finally.
            // Tomcat thread pools reuse threads. A traceId left in MDC appears
            // on the next request's log lines, corrupting distributed tracing
            // and potentially leaking one user's traceId into another's response.
            MDC.clear();
        }
    }

    // ── Exclusions ────────────────────────────────────────────────────────────

    /**
     * Suppresses filter execution for actuator probe endpoints.
     *
     * <p>Container orchestrators and load balancers probe {@code /actuator/health}
     * frequently (typically every 5–30 seconds). Logging each probe at INFO
     * would produce hundreds of useless entries per hour per instance.
     *
     * <p>Applies to any path prefixed with {@code /actuator} — this covers
     * {@code /actuator/health}, {@code /actuator/health/liveness},
     * {@code /actuator/health/readiness}, and any future actuator endpoints.
     *
     * <p><b>Note:</b> when this returns {@code true}, Spring's
     * {@link OncePerRequestFilter} calls {@code filterChain.doFilter()} directly,
     * bypassing {@link #doFilterInternal}. MDC is NOT populated for skipped paths.
     * This is intentional — actuator probe logs do not need distributed tracing.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Determines the traceId for this request.
     *
     * <p>Accepts a client-supplied {@code X-Trace-Id} header to enable end-to-end
     * distributed tracing: an API gateway or upstream service can set this header
     * and all log lines for the downstream request will carry the same ID.
     *
     * <p>Validation rules:
     * <ul>
     *   <li>Must match {@link #SAFE_TRACE_ID_PATTERN} (alphanumeric + hyphens,
     *       8–64 chars)</li>
     *   <li>Any header value that fails validation is silently discarded — a fresh
     *       UUID is generated. The invalid value is NEVER logged (log injection
     *       risk).</li>
     * </ul>
     *
     * @param request the incoming HTTP request
     * @return a validated, non-blank, log-safe trace ID
     */
    private String resolveTraceId(HttpServletRequest request) {
        String incoming = request.getHeader(TRACE_ID_HEADER);
        if (incoming != null && SAFE_TRACE_ID_PATTERN.matcher(incoming).matches()) {
            return incoming;
        }
        // Client did not supply a valid trace ID — generate a fresh one.
        return UUID.randomUUID().toString();
    }
}