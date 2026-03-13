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
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Handles HTTP 401 Unauthorized for unauthenticated requests to protected endpoints.
 *
 * <h2>Why this class is necessary</h2>
 * Spring Security intercepts authentication failures before they reach
 * {@code @ControllerAdvice}. Without this entry point, Spring returns its own
 * error representation (plain text or non-standard JSON), breaking the
 * {@link ApiResponse} envelope contract every client depends on.
 *
 * <h2>When this is invoked</h2>
 * Called by Spring Security's {@code ExceptionTranslationFilter} when:
 * <ul>
 *   <li>No {@code Authorization} header is present on a protected endpoint.</li>
 *   <li>The JWT token is invalid, expired, or malformed.</li>
 *   <li>The token's subject resolves to a non-existent user.</li>
 * </ul>
 * <b>Not invoked for 403.</b> Authenticated-but-unauthorized requests are
 * handled by {@link JwtAccessDeniedHandler}.
 *
 * <h2>Security: generic response message</h2>
 * The client receives a fixed generic message regardless of failure reason.
 * We do not reveal whether the token was missing, expired, invalid, or malformed.
 * Specific failure reasons are logged server-side only. Revealing exact failure
 * reasons assists token-probing and enumeration attacks.
 *
 * <h2>traceId in response body</h2>
 * The {@code traceId} from MDC (set by {@code RequestLoggingFilter}) is included
 * in the response body, consistent with all other error responses from
 * {@link com.foodorder.exception.GlobalExceptionHandler}. Clients include it in
 * support requests; ops teams grep server logs for it.
 *
 * <h2>Proxy-aware client IP — log injection protection</h2>
 * Behind a reverse proxy (nginx, ALB, Cloudflare), {@code getRemoteAddr()} returns
 * the proxy IP. We read {@code X-Forwarded-For} first.
 *
 * <p><b>Log injection risk:</b> {@code X-Forwarded-For} is a user-controlled header.
 * Without sanitization, an attacker can send a header value containing newlines or
 * ANSI escape codes that corrupt structured log output or exploit log-parsing tools.
 * We validate the extracted IP against {@link #IP_SAFE_PATTERN} before logging.
 * Values failing validation are replaced with {@code "[sanitized]"}.
 *
 * <p><b>XFF spoofing:</b> This header is only trustworthy if your outermost proxy
 * strips and sets it. Configure Tomcat's {@code RemoteIpValve} or Spring's
 * {@code ForwardedHeaderFilter} to have the container normalize it before it
 * reaches application code.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    /**
     * Accepts IPv4, IPv6 addresses, and "unknown". Rejects everything else —
     * including newlines, ANSI escapes, or injected log content.
     *
     * <p>Pattern breakdown:
     * <ul>
     *   <li>IPv4: dotted-decimal notation, 1–3 digits per octet</li>
     *   <li>IPv6: hex groups and colons (simplified — validation is loose but safe)</li>
     *   <li>"unknown": sent by some proxies when the real IP is unavailable</li>
     * </ul>
     */
    private static final Pattern IP_SAFE_PATTERN =
        Pattern.compile("^([0-9]{1,3}\\.){3}[0-9]{1,3}$"    // IPv4
            + "|^[0-9a-fA-F:]{3,39}$"                        // IPv6 (simplified)
            + "|^unknown$");

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest      request,
            HttpServletResponse     response,
            AuthenticationException authException) throws IOException {

        String clientIp = resolveClientIp(request);

        // Log method + URI + sanitized IP for security audit.
        // NEVER log authException.getMessage() — Spring Security exception messages
        // can contain token fragments, expression text, or internal detail.
        log.warn("401 Unauthorized: method={}, uri={}, ip={}",
            request.getMethod(),
            request.getRequestURI(),
            clientIp);

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        // Consistent ApiResponse envelope — same JSON shape as all other errors.
        // traceId links this 401 to the full request log entry (set by RequestLoggingFilter).
        ApiResponse<Void> body = ApiResponse.<Void>builder()
            .success(false)
            .statusCode(HttpStatus.UNAUTHORIZED.value())
            .message("Authentication required. Please provide a valid Bearer token.")
            .timestamp(Instant.now().toString())
            .traceId(MDC.get("traceId"))
            .build();

        objectMapper.writeValue(response.getOutputStream(), body);
    }

    /**
     * Resolves the real client IP with log injection protection.
     *
     * <ol>
     *   <li>Read {@code X-Forwarded-For} — may be a comma-separated list
     *       ({@code client, proxy1, proxy2}). Take the leftmost value.</li>
     *   <li>Validate against {@link #IP_SAFE_PATTERN}. Reject anything that
     *       is not a recognizable IP address format.</li>
     *   <li>Fall back to {@code remoteAddr} (the connecting socket IP).</li>
     * </ol>
     *
     * @return a log-safe IP string, never blank, never containing injection characters
     */
    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader(X_FORWARDED_FOR);
        if (xff != null && !xff.isBlank()) {
            // Take the leftmost entry — original client IP in a proxy chain
            String candidate = xff.split(",")[0].strip();
            if (IP_SAFE_PATTERN.matcher(candidate).matches()) {
                return candidate;
            }
            // XFF value did not match a safe IP pattern — do not log it
            return "[sanitized]";
        }
        return request.getRemoteAddr();
    }
}