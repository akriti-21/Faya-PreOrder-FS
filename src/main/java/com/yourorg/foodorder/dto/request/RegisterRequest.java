package com.yourorg.foodorder.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inbound DTO for POST /api/v1/auth/register.
 *
 * Record (Java 16+): immutable, compact, no Lombok needed.
 * All fields are validated via Jakarta Validation before AuthService is called.
 *
 * Password constraints:
 *   8–72 chars — BCrypt silently truncates at 72 bytes, so enforcing an upper
 *   bound here (rather than silently accepting longer passwords that get
 *   truncated) is more honest. The constraint is documented to users.
 *
 * This DTO never reaches a response body — it is consumed by AuthService
 * and discarded. The password field never appears in logs or responses.
 */
public record RegisterRequest(

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72,
          message = "Password must be between 8 and 72 characters")
    String password,

    @NotBlank(message = "First name is required")
    @Size(min = 1, max = 100, message = "First name must be between 1 and 100 characters")
    String firstName,

    @NotBlank(message = "Last name is required")
    @Size(min = 1, max = 100, message = "Last name must be between 1 and 100 characters")
    String lastName
) {}