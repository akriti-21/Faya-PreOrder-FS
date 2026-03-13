package com.yourorg.foodorder.controller;

import com.yourorg.foodorder.dto.response.ApiResponse;
import com.yourorg.foodorder.dto.response.UserResponse;
import com.yourorg.foodorder.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * User profile controller — authenticated user profile operations.
 *
 * URL prefix: /api/v1/users
 * All endpoints require a valid JWT (enforced by SecurityConfig: anyRequest().authenticated()).
 *
 * @PreAuthorize:
 *   isAuthenticated() is redundant given the security config but makes the
 *   intent explicit at the method level. Useful when reading the controller
 *   in isolation. Methods that require elevated privileges use hasRole('ADMIN').
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * GET /api/v1/users/me
     *
     * Returns the profile of the currently authenticated user.
     * Identity is derived from the SecurityContext — no path variable needed.
     * This is the canonical "who am I" endpoint for frontends after login.
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser() {
        UserResponse user = userService.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success("User profile retrieved.", user));
    }

    /**
     * GET /api/v1/users/{id}
     *
     * Returns a user profile by UUID. Restricted to ADMIN role.
     * Regular users access their own profile via /me.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable UUID id) {
        UserResponse user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success("User retrieved.", user));
    }
}