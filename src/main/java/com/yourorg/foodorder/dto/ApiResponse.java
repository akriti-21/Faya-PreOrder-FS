package com.foodorder.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * Universal API response envelope.
 *
 * EVERY response from this API — success or error — uses this structure.
 * This contract must never be broken: inconsistent response shapes make
 * frontend and mobile client development significantly harder.
 *
 * Shape:
 * {
 *   "success": true,
 *   "statusCode": 200,
 *   "message": "Order placed successfully",
 *   "data": { ... },          // null on error responses
 *   "errors": null,           // populated on validation failures (400)
 *   "timestamp": "2025-01-01T12:00:00Z",
 *   "traceId": "abc-123"      // correlation ID — grep this in logs
 * }
 *
 * Architecture decisions:
 *
 * 1. Generic <T> data field: type-safe at the controller level.
 *    Controllers return ResponseEntity<ApiResponse<OrderResponse>>,
 *    not ResponseEntity<ApiResponse<?>>.
 *
 * 2. @JsonInclude(NON_NULL): null fields are omitted from JSON output.
 *    This keeps responses clean: 'errors' only appears on validation failures,
 *    'data' only appears on success. Frontend checks 'success' flag first.
 *
 * 3. traceId: Generated per-request via MDC (RequestLoggingFilter).
 *    Also returned as X-Trace-Id response header.
 *    Clients include this in support requests; ops teams grep logs for it.
 *
 * 4. @Builder: Clean construction in controllers and GlobalExceptionHandler.
 *    The factory methods (success/error) are convenience shortcuts.
 *
 * Usage in controllers:
 *   return ResponseEntity.ok(ApiResponse.success("Order created", orderResponse));
 *
 * Usage in GlobalExceptionHandler:
 *   return ResponseEntity.status(404).body(ApiResponse.error(404, "Order not found", errors));
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /** Whether the request was processed successfully */
    private final boolean success;

    /** HTTP status code mirrored in body for client convenience */
    private final int statusCode;

    /** Human-readable message suitable for logging and debugging */
    private final String message;

    /** Response payload — null on error responses */
    private final T data;

    /**
     * Field-level validation errors — populated only on 400 Bad Request.
     * Each ApiError describes one validation failure.
     */
    private final List<ApiError> errors;

    /** ISO-8601 UTC timestamp of when this response was generated */
    private final String timestamp;

    /**
     * Correlation ID for distributed tracing.
     * Set from MDC (Mapped Diagnostic Context) by request logging filter.
     * Present in logs and response headers as X-Trace-Id.
     */
    private final String traceId;

    // ----------------------------------------------------------------
    // Factory methods — preferred over builder for common cases
    // ----------------------------------------------------------------

    /**
     * Creates a successful response with data payload.
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .statusCode(200)
                .message(message)
                .data(data)
                .timestamp(Instant.now().toString())
                .build();
    }

    /**
     * Creates a successful response with custom status code (e.g., 201 Created).
     */
    public static <T> ApiResponse<T> success(int statusCode, String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .statusCode(statusCode)
                .message(message)
                .data(data)
                .timestamp(Instant.now().toString())
                .build();
    }

    /**
     * Creates an error response without field-level errors.
     */
    public static <T> ApiResponse<T> error(int statusCode, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .statusCode(statusCode)
                .message(message)
                .timestamp(Instant.now().toString())
                .build();
    }

    /**
     * Creates an error response with field-level validation errors.
     */
    public static <T> ApiResponse<T> error(int statusCode, String message, List<ApiError> errors) {
        return ApiResponse.<T>builder()
                .success(false)
                .statusCode(statusCode)
                .message(message)
                .errors(errors)
                .timestamp(Instant.now().toString())
                .build();
    }
}
