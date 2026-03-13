package com.yourorg.foodorder.controller;

import com.yourorg.foodorder.dto.request.LoginRequest;
import com.yourorg.foodorder.dto.request.RegisterRequest;
import com.yourorg.foodorder.dto.response.ApiResponse;
import com.yourorg.foodorder.dto.response.AuthResponse;
import com.yourorg.foodorder.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication controller — registration and login endpoints.
 *
 * URL prefix: /api/v1/auth
 * Both endpoints are public (no JWT required) — declared in SecurityConfig.
 *
 * Design principles:
 *
 * Thin controller:
 *   Controllers validate input (@Valid) and delegate to the service.
 *   No business logic lives here. If it is more than 3 lines in a method,
 *   it belongs in a service.
 *
 * ApiResponse envelope:
 *   Every response is wrapped in ApiResponse<T> for consistent client contracts.
 *   Status codes are set via ResponseEntity — never via @ResponseStatus here,
 *   because @ResponseStatus cannot be changed at runtime based on outcomes.
 *
 * HTTP status selection:
 *   POST /register → 201 Created (a new resource was created)
 *   POST /login    → 200 OK (authentication is not resource creation)
 *
 * @Valid:
 *   Triggers Jakarta Bean Validation on the request body before the method body
 *   runs. Violations throw MethodArgumentNotValidException, caught by
 *   GlobalExceptionHandler and returned as 400 with per-field error details.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Register a new user account.
     *
     * POST /api/v1/auth/register
     *
     * Validates the request body, delegates to AuthService.registerUser(),
     * and returns a JWT so the user is immediately authenticated after signup
     * without a separate login step.
     *
     * Request body:  RegisterRequest (email, password, firstName, lastName)
     * Response body: ApiResponse<AuthResponse>
     * HTTP status:   201 Created
     *
     * Error cases:
     *   400 — validation failure (missing/invalid fields) → GlobalExceptionHandler
     *   409 — email already registered                    → GlobalExceptionHandler
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {

        AuthResponse authResponse = authService.registerUser(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Registration successful.", authResponse));
    }

    /**
     * Authenticate an existing user.
     *
     * POST /api/v1/auth/login
     *
     * Validates the request body, delegates credential verification to
     * AuthService.loginUser() (which uses Spring Security's AuthenticationManager),
     * and returns a JWT on success.
     *
     * Request body:  LoginRequest (email, password)
     * Response body: ApiResponse<AuthResponse>
     * HTTP status:   200 OK
     *
     * Error cases:
     *   400 — validation failure (missing/invalid fields) → GlobalExceptionHandler
     *   409 — invalid credentials (AuthService throws BusinessException) → GlobalExceptionHandler
     *          Note: 409 is used here because BusinessException defaults to CONFLICT.
     *          For a cleaner 401, override the status in AuthService:
     *            throw new BusinessException("Invalid email or password.", HttpStatus.UNAUTHORIZED);
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        AuthResponse authResponse = authService.loginUser(request);

        return ResponseEntity.ok(ApiResponse.success("Login successful.", authResponse));
    }
}