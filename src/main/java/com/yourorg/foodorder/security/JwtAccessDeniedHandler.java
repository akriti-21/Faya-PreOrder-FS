package com.yourorg.foodorder.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourorg.foodorder.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Handles HTTP 403 Forbidden for authenticated users accessing resources
 * they are not authorized to use.
 *
 * <h3>403 vs 401 — why they need separate handlers</h3>
 * <ul>
 *   <li><b>401 Unauthorized</b> — the request is unauthenticated. No valid
 *       JWT was provided. Handled by {@link JwtAuthenticationEntryPoint}.</li>
 *   <li><b>403 Forbidden</b> — the request is authenticated (valid JWT,
 *       known user) but the user lacks the required role or permission.
 *       Handled here.</li>
 * </ul>
 * Without this handler, Spring Security returns a default 403 response in
 * its own format (typically a plain-text "Forbidden"), breaking the
 * {@link ApiResponse} envelope contract.
 *
 * <h3>Security: no internal detail disclosed</h3>
 * The response message does not reveal which role or permission was required.
 * Specifically, {@code ex.getMessage()} is <b>never</b> logged to the response
 * output — Spring's {@code AccessDeniedException} messages can contain
 * {@code @PreAuthorize} expression text, which would expose the internal
 * role/permission model to clients.
 *
 * <h3>When this is invoked</h3>
 * <ul>
 *   <li>An authenticated user requests an endpoint with insufficient role.</li>
 *   <li>A {@code @PreAuthorize("hasRole('ADMIN')")} expression on a
 *       controller or service method fails evaluation.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(
            HttpServletRequest   request,
            HttpServletResponse  response,
            AccessDeniedException ex) throws IOException {

        // Log method + URI only — do NOT log ex.getMessage() which may contain
        // @PreAuthorize expression text revealing the internal permission model.
        log.warn("403 Forbidden: method={}, uri={}", request.getMethod(), request.getRequestURI());

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        ApiResponse<Void> body = ApiResponse.<Void>builder()
                .success(false)
                .statusCode(HttpStatus.FORBIDDEN.value())
                .message("You do not have permission to access this resource.")
                .timestamp(Instant.now().toString())
                .traceId(MDC.get("traceId"))
                .build();

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}