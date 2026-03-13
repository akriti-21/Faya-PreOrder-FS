package com.yourorg.foodorder.exception;

/**
 * Thrown when a requested resource cannot be found in the data store.
 *
 * <p>Maps to HTTP 404 Not Found via {@link GlobalExceptionHandler}.
 *
 * <h2>Note: no @ResponseStatus on this class</h2>
 * {@code @ResponseStatus} is deliberately absent. {@link GlobalExceptionHandler}
 * is the single authority on HTTP status codes for all exception types.
 *
 * <h2>Two construction styles</h2>
 *
 * <b>Structured</b> — preferred for domain entity lookups. Produces a
 * consistent, parseable message:
 * <pre>{@code
 *   throw new ResourceNotFoundException("Order", "id", orderId);
 *   // → "Order not found with id: 'a1b2c3d4-...'"
 *
 *   throw new ResourceNotFoundException("User", "email", email);
 *   // → "User not found with email: 'alice@example.com'"
 *
 *   throw new ResourceNotFoundException("MenuItem", "id", itemId);
 *   // → "MenuItem not found with id: '99'"
 * }</pre>
 *
 * <b>Free-form</b> — for cases that don't fit the structured pattern:
 * <pre>{@code
 *   throw new ResourceNotFoundException("No active session found for this device");
 * }</pre>
 *
 * <h2>Security note</h2>
 * The {@code fieldValue} is included in the exception message. Avoid using
 * user-controlled values (e.g. raw email addresses, usernames) as {@code fieldValue}
 * if those values might appear in client-facing error responses. The
 * {@link GlobalExceptionHandler} logs only the resource name and status, not
 * the full message, to limit PII exposure in logs.
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceName;
    private final String fieldName;
    private final Object fieldValue;

    /**
     * Structured constructor for domain entity lookups.
     *
     * <p>Produces the message: {@code "{resourceName} not found with {fieldName}: '{fieldValue}'"}
     *
     * @param resourceName the entity type (e.g. "Order", "User", "MenuItem")
     * @param fieldName    the lookup field (e.g. "id", "email", "slug")
     * @param fieldValue   the value that was not found
     */
    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s: '%s'", resourceName, fieldName, fieldValue));
        this.resourceName = resourceName;
        this.fieldName    = fieldName;
        this.fieldValue   = fieldValue;
    }

    /**
     * Free-form constructor for cases that don't map to a single entity/field.
     *
     * @param message description of what was not found
     */
    public ResourceNotFoundException(String message) {
        super(message);
        this.resourceName = null;
        this.fieldName    = null;
        this.fieldValue   = null;
    }

    /**
     * Returns the entity type name, or {@code null} if the free-form constructor was used.
     * Useful for logging: {@code "Resource type: " + ex.getResourceName()}.
     */
    public String getResourceName() { return resourceName; }

    /**
     * Returns the lookup field name, or {@code null} if free-form constructor was used.
     */
    public String getFieldName()    { return fieldName; }

    /**
     * Returns the field value that was not found, or {@code null} if free-form.
     * Treat as potentially sensitive — may be a user email or UUID from a URL path.
     */
    public Object getFieldValue()   { return fieldValue; }
}