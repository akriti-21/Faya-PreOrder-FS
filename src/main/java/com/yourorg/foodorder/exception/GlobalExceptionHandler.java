package com.foodorder.exception;

import com.foodorder.dto.response.ApiError;
import com.foodorder.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Set;

/**
 * Global exception handler — single point of exception-to-HTTP translation.
 *
 * Architecture decisions:
 *
 * 1. @RestControllerAdvice = @ControllerAdvice + @ResponseBody.
 *    Every handler method here returns JSON automatically.
 *
 * 2. This is the ONLY place exceptions become HTTP responses.
 *    Controllers and services throw domain exceptions — they never
 *    manipulate HttpServletResponse or set status codes directly.
 *
 * 3. Catch-all handler (Exception.class) is mandatory.
 *    Without it, unhandled exceptions reach Spring's /error endpoint
 *    which returns its own format, breaking the ApiResponse contract.
 *
 * 4. Security principle: internal details never leak to the client.
 *    - DB table/column names hidden (DataIntegrityViolationException)
 *    - Stack traces logged server-side, trace ID returned to client
 *    - Auth failures return generic messages regardless of cause
 *
 * 5. Sensitive field values are stripped before including in error responses.
 *    See SENSITIVE_FIELDS set below.
 *
 * 6. Trace ID from MDC is included in all error responses, allowing
 *    clients to reference specific error occurrences in support requests.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Fields whose rejected values must never be echoed in error responses.
     * Add any field name containing sensitive data.
     */
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "confirmPassword", "currentPassword", "newPassword",
            "token", "secret", "apiKey", "creditCard", "cvv", "ssn"
    );

    // ----------------------------------------------------------------
    // 400 Bad Request — Validation failures
    // ----------------------------------------------------------------

    /**
     * Handles @Valid annotation failures on @RequestBody DTOs.
     * Extracts all field-level errors into ApiError list.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex) {

        List<ApiError> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::mapFieldError)
                .toList();

        log.debug("Validation failed: {} field error(s)", errors.size());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        HttpStatus.BAD_REQUEST.value(),
                        "Request validation failed. Check the 'errors' field for details.",
                        errors
                ));
    }

    /**
     * Handles path variable or request parameter type mismatches.
     * e.g., passing "abc" for a Long orderId path variable.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {

        String message = String.format(
                "Parameter '%s' should be of type '%s'",
                ex.getName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown"
        );

        log.debug("Type mismatch: {}", message);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, message);
    }

    // ----------------------------------------------------------------
    // 401 / 403 — Security exceptions
    // ----------------------------------------------------------------

    /**
     * Handles 403 Forbidden — authenticated but not authorized.
     * Returns generic message: never expose why authorization failed.
     *
     * Note: 401 from missing/invalid token is handled by
     * JwtAuthenticationEntryPoint, not here.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
            AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN,
                "You do not have permission to perform this action.");
    }

    /**
     * Fallback for Spring Security authentication exceptions that escape
     * the security filter chain (rare, but possible with method security).
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(
            AuthenticationException ex) {
        log.warn("Authentication exception: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED,
                "Authentication required.");
    }

    // ----------------------------------------------------------------
    // 404 Not Found — Resource not found
    // ----------------------------------------------------------------

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(
            ResourceNotFoundException ex) {
        log.debug("Resource not found: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // ----------------------------------------------------------------
    // 409 Conflict — Business rule violations
    // ----------------------------------------------------------------

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException ex) {
        log.debug("Business rule violation: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * DB constraint violations (unique index, FK, not-null column).
     * SECURITY: Never expose DB table/column names to the client.
     * Log the detail internally; return generic message externally.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {
        // Log full detail internally for debugging
        log.error("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());

        // Return generic message — no DB internals
        return buildErrorResponse(HttpStatus.CONFLICT,
                "The request could not be completed due to a data conflict. " +
                "This resource may already exist.");
    }

    // ----------------------------------------------------------------
    // 500 Internal Server Error — Catch-all
    // ----------------------------------------------------------------

    /**
     * MANDATORY catch-all handler.
     *
     * This intercepts ANY unhandled exception and:
     *   1. Logs the full stack trace with trace ID for debugging
     *   2. Returns a generic message to the client — no internal details
     *
     * Without this handler, unhandled exceptions reach Spring Boot's
     * /error endpoint which returns its own response format, breaking
     * the ApiResponse envelope contract.
     *
     * The traceId is returned to the client so they can reference
     * the specific failure in a support request, while you grep logs
     * for the same traceId to see the full stack trace.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception ex) {
        String traceId = MDC.get("traceId");

        // Full stack trace logged server-side — NEVER sent to client
        log.error("Unhandled exception [traceId={}]: {}", traceId, ex.getMessage(), ex);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("An unexpected error occurred. Please try again or contact support.")
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    private ResponseEntity<ApiResponse<Void>> buildErrorResponse(HttpStatus status, String message) {
        ApiResponse<Void> body = ApiResponse.<Void>builder()
                .success(false)
                .statusCode(status.value())
                .message(message)
                .traceId(MDC.get("traceId"))
                .build();
        return ResponseEntity.status(status).body(body);
    }

    /**
     * Maps a FieldError to ApiError.
     * SECURITY: Strips rejected values for sensitive field names.
     */
    private ApiError mapFieldError(FieldError fieldError) {
        String fieldName = fieldError.getField();
        boolean isSensitive = SENSITIVE_FIELDS.stream()
                .anyMatch(s -> fieldName.toLowerCase().contains(s.toLowerCase()));

        if (isSensitive) {
            // Don't echo back sensitive values (e.g., a too-short password)
            return ApiError.of(fieldName, fieldError.getDefaultMessage());
        }

        return ApiError.of(
                fieldName,
                fieldError.getRejectedValue(),
                fieldError.getDefaultMessage()
        );
    }
}