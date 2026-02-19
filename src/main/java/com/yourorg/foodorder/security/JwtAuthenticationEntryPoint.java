package com.foodorder.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodorder.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Handles 401 Unauthorized responses for unauthenticated requests.
 *
 * Architecture decision:
 * Spring Security, when authentication is required but missing, calls
 * AuthenticationEntryPoint rather than letting the exception propagate
 * to @ControllerAdvice. Without this component, Spring would return its
 * own error format, breaking the ApiResponse envelope contract.
 *
 * This ensures ALL 401 responses — whether from missing JWT, expired JWT,
 * or missing auth on a protected endpoint — use the same JSON structure
 * as every other API error.
 *
 * Security principle: The response message is deliberately generic.
 * "Authentication required" tells the client what happened without
 * revealing why (invalid token, expired, wrong format, etc.).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        // Log the unauthorized access attempt — path only, not credentials
        log.warn("Unauthorized access attempt: method={}, uri={}, ip={}",
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr());

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        // Return a consistent ApiResponse envelope — same format as all other errors
        ApiResponse<Void> errorResponse = ApiResponse.<Void>builder()
                .success(false)
                .statusCode(HttpStatus.UNAUTHORIZED.value())
                .message("Authentication required. Please provide a valid Bearer token.")
                .timestamp(Instant.now().toString())
                .build();

        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}