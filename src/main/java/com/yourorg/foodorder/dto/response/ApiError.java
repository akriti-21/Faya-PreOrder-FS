package com.yourorg.foodorder.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

/**
 * Single field-level validation failure within an {@link ApiResponse#errors} list.
 *
 * <h2>Wire shape</h2>
 * <pre>{@code
 * {
 *   "field":         "email",
 *   "rejectedValue": "not-an-email",   // omitted for sensitive fields
 *   "message":       "must be a valid email address"
 * }
 * }</pre>
 *
 * <h2>Design decisions</h2>
 *
 * <b>Dot-notation field paths.</b> Nested DTO fields use dot notation:
 * {@code "address.zipCode"}, {@code "items[0].quantity"}. This lets frontend
 * form libraries map errors directly to input fields by path.
 *
 * <b>rejectedValue is security-sensitive.</b> Some field values must never
 * be echoed back to the client:
 * <ul>
 *   <li>Passwords — even invalid ones reveal attempted credentials</li>
 *   <li>Tokens / secrets — if invalid, revealing them aids replay attacks</li>
 *   <li>SSNs, card numbers — PII that must not appear in API responses</li>
 * </ul>
 * {@link GlobalExceptionHandler} filters these before constructing
 * {@code ApiError} instances. The {@link #of(String, String)} factory
 * (no rejectedValue) is provided for callers that have already stripped them.
 *
 * <b>No machine-readable error code.</b> A {@code code} field (e.g.
 * {@code "INVALID_EMAIL_FORMAT"}) would be useful for frontend i18n but is
 * deferred — Bean Validation messages are not stable enough to use as codes
 * directly and a code registry adds maintenance overhead.
 *
 * <b>{@code @JsonInclude(NON_NULL)}</b> ensures {@code rejectedValue} is
 * omitted from JSON when null, keeping responses clean for sensitive fields.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    /**
     * Field name that failed validation.
     *
     * <p>Uses dot notation for nested objects: {@code "address.zipCode"}.
     * Uses bracket notation for collections: {@code "items[0].quantity"}.
     * Matches the JSON property path in the request body.
     */
    private final String field;

    /**
     * The value that was rejected by validation.
     *
     * <p><b>SECURITY:</b> This field MUST be null for sensitive fields.
     * See {@link GlobalExceptionHandler#SENSITIVE_FIELDS} for the strip list.
     * The field is omitted from JSON output when null via {@code @JsonInclude}.
     *
     * <p>Type is {@code Object} to accommodate String, Number, Boolean values
     * without losing type information in the JSON output.
     */
    private final Object rejectedValue;

    /**
     * Human-readable description of why the value was rejected.
     *
     * <p>Sources: Bean Validation constraint message, or a custom message
     * from the constraint annotation's {@code message} attribute.
     * Can be customized via {@code ValidationMessages.properties}.
     */
    private final String message;

    // ── Factory methods ───────────────────────────────────────────────────────

    /**
     * Creates an {@code ApiError} with the rejected value included.
     *
     * <p>Use only for non-sensitive fields (not passwords, tokens, secrets).
     * The caller is responsible for checking sensitivity before calling this method.
     *
     * @param field         field path (dot notation)
     * @param rejectedValue the value that failed validation
     * @param message       human-readable failure description
     */
    public static ApiError of(String field, Object rejectedValue, String message) {
        return ApiError.builder()
                .field(field)
                .rejectedValue(rejectedValue)
                .message(message)
                .build();
    }

    /**
     * Creates an {@code ApiError} without a rejected value.
     *
     * <p>Use for security-sensitive fields where the rejected value must
     * not be echoed (passwords, tokens, API keys, SSNs, card numbers, etc.).
     *
     * @param field   field path (dot notation)
     * @param message human-readable failure description
     */
    public static ApiError of(String field, String message) {
        return ApiError.builder()
                .field(field)
                .message(message)
                .build();
    }
}