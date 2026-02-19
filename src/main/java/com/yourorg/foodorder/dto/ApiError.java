package com.foodorder.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

/**
 * Represents a single field-level validation error within an ApiResponse.
 *
 * Used in the 'errors' array of ApiResponse for 400 Bad Request responses.
 *
 * Shape:
 * {
 *   "field": "email",
 *   "rejectedValue": "not-an-email",
 *   "message": "must be a valid email address"
 * }
 *
 * Architecture decisions:
 *
 * 1. 'field' uses dot-notation for nested objects: "address.zipCode"
 *    This allows frontend forms to map errors directly to input fields.
 *
 * 2. 'rejectedValue' is @JsonInclude(NON_NULL) — omitted when null.
 *    For security: some rejected values should NOT be echoed back
 *    (e.g., if a password field fails validation, do not echo the
 *    attempted password). Filter these in GlobalExceptionHandler before
 *    building ApiError.
 *
 * 3. 'message' is the Bean Validation message, which can be customized
 *    via validation annotation attributes or message bundles.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    /** The field name that failed validation (dot-notation for nested) */
    private final String field;

    /**
     * The value that was rejected.
     * SECURITY NOTE: Never include sensitive values here (passwords, tokens).
     * Null this out in GlobalExceptionHandler for sensitive fields.
     */
    private final Object rejectedValue;

    /** Human-readable description of the validation failure */
    private final String message;

    /**
     * Factory method for clean construction.
     */
    public static ApiError of(String field, Object rejectedValue, String message) {
        return ApiError.builder()
                .field(field)
                .rejectedValue(rejectedValue)
                .message(message)
                .build();
    }

    /**
     * Factory method without rejected value (for security-sensitive fields).
     */
    public static ApiError of(String field, String message) {
        return ApiError.builder()
                .field(field)
                .message(message)
                .build();
    }
}