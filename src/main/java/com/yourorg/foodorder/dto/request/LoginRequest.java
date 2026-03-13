package com.yourorg.foodorder.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inbound DTO for POST /api/v1/auth/login.
 *
 * Minimal validation — we only need to verify the fields are present and
 * structurally valid before attempting authentication. We deliberately do NOT
 * apply the same 8-char minimum on password here: a user with an old account
 * created under looser rules must still be able to log in.
 *
 * The raw password is passed to AuthenticationManager.authenticate() and
 * compared against the stored BCrypt hash. It is never persisted, logged,
 * or included in any response.
 */
public record LoginRequest(

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    String email,

    @NotBlank(message = "Password is required")
    @Size(max = 72, message = "Password must not exceed 72 characters")
    String password
) {}