package com.foodorder.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a business rule or domain constraint is violated.
 * Maps to HTTP 409 Conflict or HTTP 422 Unprocessable Entity depending on context.
 *
 * Examples:
 *   - Attempting to cancel an already-delivered order
 *   - Adding an item that's out of stock
 *   - Registering with an email that already exists
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}