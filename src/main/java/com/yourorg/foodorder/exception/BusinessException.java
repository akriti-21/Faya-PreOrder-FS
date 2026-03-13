package com.yourorg.foodorder.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a business rule or domain invariant is violated.
 *
 * <h2>HTTP status mapping</h2>
 * The status code is carried on the exception and read by
 * {@link GlobalExceptionHandler}. Two codes are appropriate:
 *
 * <ul>
 *   <li><b>409 Conflict</b> — a valid request cannot be fulfilled due to
 *       the current state of a resource. Examples:
 *       <ul>
 *         <li>Cancelling an already-delivered order</li>
 *         <li>Registering with an email that already exists</li>
 *         <li>Attempting to publish a menu item with no price</li>
 *       </ul>
 *   </li>
 *   <li><b>422 Unprocessable Entity</b> — the request is well-formed and
 *       the resource exists, but semantic validation failed. Examples:
 *       <ul>
 *         <li>Ordering a quantity that exceeds available stock</li>
 *         <li>Applying an expired coupon code</li>
 *         <li>Scheduling a delivery for a past date</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <p>Default is 409. Pass an explicit {@link HttpStatus} to override:
 * <pre>{@code
 *   throw new BusinessException(
 *       "Coupon has expired",
 *       HttpStatus.UNPROCESSABLE_ENTITY);
 * }</pre>
 *
 * <h2>Note: no @ResponseStatus on this class</h2>
 * {@code @ResponseStatus} is deliberately absent. {@link GlobalExceptionHandler}
 * is the single authority on HTTP status mapping. Placing {@code @ResponseStatus}
 * on exceptions is misleading when a {@code @ExceptionHandler} method is present
 * (the handler takes precedence anyway), and creates confusion about which
 * mechanism actually controls the response code.
 *
 * <h2>Usage examples</h2>
 * <pre>{@code
 *   // Default 409 Conflict
 *   throw new BusinessException("Order has already been delivered and cannot be cancelled");
 *
 *   // Explicit 422 Unprocessable Entity
 *   throw new BusinessException(
 *       "Cannot apply discount: order total must be at least $10.00",
 *       HttpStatus.UNPROCESSABLE_ENTITY);
 *
 *   // With root cause (for logging context)
 *   throw new BusinessException("Payment processing failed", cause);
 * }</pre>
 */
public class BusinessException extends RuntimeException {

    /**
     * HTTP status this exception should be mapped to.
     * Defaults to 409 Conflict; override to 422 for semantic validation failures.
     */
    private final HttpStatus status;

    /**
     * Creates a 409 Conflict business exception.
     *
     * @param message user-visible description of the business rule violation
     */
    public BusinessException(String message) {
        super(message);
        this.status = HttpStatus.CONFLICT;
    }

    /**
     * Creates a business exception with an explicit HTTP status.
     *
     * @param message user-visible description of the business rule violation
     * @param status  the HTTP status to return (409 CONFLICT or 422 UNPROCESSABLE_ENTITY)
     */
    public BusinessException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    /**
     * Creates a 409 Conflict business exception wrapping a root cause.
     * The root cause is logged by {@link GlobalExceptionHandler} but
     * never included in the client response.
     *
     * @param message user-visible description of the business rule violation
     * @param cause   the underlying exception (for server-side logging)
     */
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.status = HttpStatus.CONFLICT;
    }

    /**
     * Creates a business exception with an explicit HTTP status and root cause.
     *
     * @param message user-visible description of the business rule violation
     * @param status  the HTTP status to return
     * @param cause   the underlying exception (for server-side logging)
     */
    public BusinessException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    /**
     * Returns the HTTP status this exception maps to.
     * Read by {@link GlobalExceptionHandler} to set the response status.
     */
    public HttpStatus getStatus() {
        return status;
    }
}