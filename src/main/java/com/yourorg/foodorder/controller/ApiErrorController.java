package com.yourorg.foodorder.controller;

import com.yourorg.foodorder.dto.response.ApiResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Spring Boot's {@code BasicErrorController} at the {@code /error} path.
 *
 * <h2>Why this class exists</h2>
 * {@link com.foodorder.exception.GlobalExceptionHandler} ({@code @RestControllerAdvice})
 * intercepts exceptions that propagate through the Spring MVC dispatcher. But some
 * error paths bypass MVC entirely and are forwarded to {@code /error} directly:
 *
 * <ul>
 *   <li>Servlet-container 404s — a path that matches no servlet at all</li>
 *   <li>{@code Filter.doFilter()} methods that call {@code response.sendError()} rather
 *       than throwing (some Spring Security and Tomcat internals do this)</li>
 *   <li>Errors thrown from a {@code Filter} before MVC routing begins</li>
 *   <li>Any path the {@code DispatcherServlet} forwards to {@code /error} after
 *       failing to find a handler</li>
 * </ul>
 *
 * Without this class, Spring Boot's {@code BasicErrorController} handles these and
 * returns its own JSON shape:
 * <pre>{@code
 * { "timestamp": "...", "status": 404, "error": "Not Found", "path": "/no-such-thing" }
 * }</pre>
 * That breaks the {@link ApiResponse} envelope contract every client relies on.
 *
 * <h2>How the override works</h2>
 * Implementing {@link ErrorController} and mapping {@code /error} causes Spring Boot
 * to route all error-forwarded requests here instead of to {@code BasicErrorController}.
 * The original HTTP status code is recovered from the Servlet error dispatch attributes
 * ({@code jakarta.servlet.error.status_code}).
 *
 * <h2>traceId availability</h2>
 * {@code RequestLoggingFilter} runs at {@code Ordered.HIGHEST_PRECEDENCE} and populates
 * MDC before any Spring Security or MVC processing. By the time this controller runs,
 * MDC should still be populated if the request went through the normal filter chain.
 * For edge cases (errors during container startup, filter-chain bypasses), we fall back
 * to the {@code X-Trace-Id} response header set earlier in the request.
 *
 * <h2>Security</h2>
 * The Servlet error message attribute ({@code jakarta.servlet.error.message}) can contain
 * internal detail from Spring Security filters or the Servlet container. We never
 * include it in client responses — only the status code is extracted and used.
 */
@Slf4j
@RestController
public class ApiErrorController implements ErrorController {

    private static final String ERROR_PATH = "/error";

    /**
     * Handles all Servlet-forwarded error requests.
     *
     * <p>Reads the original HTTP status code from the standard Servlet error
     * dispatch attribute. Falls back to 500 if the attribute is absent or if the
     * value is not a recognised HTTP status code.
     *
     * <p>The response uses the {@link ApiResponse} envelope — the same shape as
     * all other error responses — including a {@code traceId} for client support
     * correlation.
     *
     * @param request  the error-dispatch request (current URI is {@code /error})
     * @param response the current response (used to read X-Trace-Id as MDC fallback)
     */
    @RequestMapping(ERROR_PATH)
    public ResponseEntity<ApiResponse<Void>> handleError(
            HttpServletRequest  request,
            HttpServletResponse response) {

        // Recover the original status code from Servlet error dispatch attributes
        Integer rawStatus = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = resolveStatus(rawStatus);

        // The current URI is /error — recover the original URI for logging
        Object originalUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);

        // Log at DEBUG — most /error dispatches are normal 404s from bots or wrong paths.
        // Unexpected 500s were already logged at ERROR by GlobalExceptionHandler.
        log.debug("/error dispatch: status={}, originalUri={}", status.value(), originalUri);

        return ResponseEntity
                .status(status)
                .body(ApiResponse.error(status.value(), messageFor(status)));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Resolves an {@link HttpStatus} from a nullable integer.
     *
     * Falls back to 500 INTERNAL_SERVER_ERROR for null, zero, or unrecognised codes.
     */
    private HttpStatus resolveStatus(Integer code) {
        if (code == null) return HttpStatus.INTERNAL_SERVER_ERROR;
        try {
            return HttpStatus.valueOf(code);
        } catch (IllegalArgumentException ex) {
            log.warn("Unrecognised status code from Servlet error dispatch: {}", code);
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    /**
     * Returns a safe, generic, status-appropriate message.
     *
     * Messages are intentionally terse and do not include any implementation detail.
     * The {@code traceId} is in the {@link ApiResponse} envelope body — it is NOT
     * embedded in the message string.
     */
    private String messageFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST            -> "The request could not be understood. Check the request body and parameters.";
            case UNAUTHORIZED           -> "Authentication required.";
            case FORBIDDEN              -> "Access denied.";
            case NOT_FOUND              -> "The requested resource was not found.";
            case METHOD_NOT_ALLOWED     -> "HTTP method not supported for this endpoint.";
            case UNSUPPORTED_MEDIA_TYPE -> "Unsupported Content-Type. Use application/json.";
            case TOO_MANY_REQUESTS      -> "Too many requests. Please slow down and try again.";
            case SERVICE_UNAVAILABLE    -> "Service temporarily unavailable. Please try again shortly.";
            default -> status.is4xxClientError()
                    ? "The request could not be processed. Check the request and try again."
                    : "An unexpected error occurred. Please try again or contact support.";
        };
    }
}
