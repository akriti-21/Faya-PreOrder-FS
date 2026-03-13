package com.yourorg.foodorder.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.List;

/**
 * Universal API response envelope — the single JSON contract for every
 * response this API returns, success and error alike.
 *
 * <h2>Wire shape</h2>
 * <pre>{@code
 * {
 *   "success":    true,
 *   "statusCode": 200,
 *   "message":    "Order placed successfully",
 *   "data":       { ... },           // null on error responses — omitted by @JsonInclude
 *   "errors":     [ ... ],           // null except on 400 validation failures — omitted
 *   "timestamp":  "2025-01-15T10:30:00.123456Z",
 *   "traceId":    "a3b2c1d0-..."     // MDC correlation ID — grep in server logs
 * }
 * }</pre>
 *
 * <h2>Design invariants</h2>
 * <ol>
 *   <li><b>Every response uses this shape.</b> Controllers, exception handlers,
 *       security filters, and the /error fallback all return this envelope.
 *       Client code can unconditionally parse {@code success} and branch.</li>
 *   <li><b>{@code @JsonInclude(NON_NULL)}</b> keeps the wire format clean:
 *       {@code data} is absent on errors; {@code errors} is absent on success.</li>
 *   <li><b>traceId is populated from MDC in all factory methods.</b>
 *       Success responses and error responses both carry the correlation ID,
 *       so clients can reference any response in a support request.</li>
 *   <li><b>timestamp is always set to {@code Instant.now()}.</b> ISO-8601 UTC,
 *       millisecond precision. Both {@code success()} and {@code error()}
 *       factory methods set it automatically.</li>
 *   <li><b>Generic {@code <T> data}</b> allows type-safe controller return types:
 *       {@code ResponseEntity<ApiResponse<OrderResponse>>}, not wildcards.</li>
 * </ol>
 *
 * <h2>Factory methods vs builder</h2>
 * Prefer factory methods for common cases:
 * <pre>{@code
 *   // Controller — 200 OK
 *   return ResponseEntity.ok(ApiResponse.success("Order placed", orderDto));
 *
 *   // Controller — 201 Created
 *   return ResponseEntity.status(201)
 *       .body(ApiResponse.success(201, "Order created", orderDto));
 *
 *   // Controller — 204 No Content (no data)
 *   return ResponseEntity.status(204)
 *       .body(ApiResponse.<Void>success(204, "Order cancelled", null));
 * }</pre>
 *
 * Use the builder directly only for non-standard responses:
 * <pre>{@code
 *   ApiResponse.<Void>builder()
 *       .success(false)
 *       .statusCode(429)
 *       .message("Rate limit exceeded. Retry after 60 seconds.")
 *       .traceId(MDC.get("traceId"))
 *       .timestamp(Instant.now().toString())
 *       .build();
 * }</pre>
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /** {@code true} if the request was processed without error. */
    private final boolean success;

    /**
     * HTTP status code mirrored in the body.
     * Redundant with the HTTP status line but useful when the response is
     * deserialized from a queue or log where the HTTP context is lost.
     */
    private final int statusCode;

    /** Human-readable message. Suitable for display to developers; may be shown to users. */
    private final String message;

    /** Response payload. {@code null} (and omitted from JSON) on error responses. */
    private final T data;

    /**
     * Field-level validation errors. Non-null only on 400 Bad Request responses.
     * Each entry describes one validation failure with field name and reason.
     */
    private final List<ApiError> errors;

    /**
     * ISO-8601 UTC timestamp of response generation.
     * Example: {@code "2025-01-15T10:30:00.123456Z"}
     */
    private final String timestamp;

    /**
     * Distributed trace correlation ID from MDC (Mapped Diagnostic Context).
     *
     * <p>Set by {@code RequestLoggingFilter} at the start of each request.
     * Also returned as the {@code X-Trace-Id} response header.
     *
     * <p>Clients should include this value in support requests.
     * Server operators grep logs for it to find the full request/error context.
     */
    private final String traceId;

    // ── Factory methods ───────────────────────────────────────────────────────
    // All factory methods populate timestamp and traceId automatically.
    // This ensures every response — success or error — carries both fields.

    /**
     * 200 OK with a response payload.
     *
     * @param message human-readable success description
     * @param data    the response payload; may be null for empty success responses
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .statusCode(200)
                .message(message)
                .data(data)
                .timestamp(now())
                .traceId(traceId())
                .build();
    }

    /**
     * Success response with an explicit status code (e.g. 201 Created, 202 Accepted).
     *
     * @param statusCode HTTP status code to echo in the body
     * @param message    human-readable success description
     * @param data       the response payload; may be null
     */
    public static <T> ApiResponse<T> success(int statusCode, String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .statusCode(statusCode)
                .message(message)
                .data(data)
                .timestamp(now())
                .traceId(traceId())
                .build();
    }

    /**
     * Error response without field-level detail.
     * Used for 401, 403, 404, 409, 500, etc.
     *
     * @param statusCode HTTP status code
     * @param message    human-readable error description (do not include internal detail)
     */
    public static <T> ApiResponse<T> error(int statusCode, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .statusCode(statusCode)
                .message(message)
                .timestamp(now())
                .traceId(traceId())
                .build();
    }

    /**
     * Error response with field-level validation errors.
     * Used for 400 Bad Request responses from validation handlers.
     *
     * @param statusCode HTTP status code (typically 400)
     * @param message    summary message ("Validation failed")
     * @param errors     per-field error details
     */
    public static <T> ApiResponse<T> error(int statusCode, String message, List<ApiError> errors) {
        return ApiResponse.<T>builder()
                .success(false)
                .statusCode(statusCode)
                .message(message)
                .errors(errors)
                .timestamp(now())
                .traceId(traceId())
                .build();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Current UTC instant as ISO-8601 string. */
    private static String now() {
        return Instant.now().toString();
    }

    /**
     * Current request's trace ID from MDC.
     *
     * <p>Returns {@code null} (omitted from JSON) if called outside a request
     * context (e.g. in unit tests that don't populate MDC).
     */
    private static String traceId() {
        return MDC.get("traceId");
    }
}