package com.yourorg.foodorder.exception;

import com.yourorg.foodorder.dto.response.ApiError;
import com.yourorg.foodorder.dto.response.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Set;

/**
 * Centralised exception-to-HTTP translation for the entire application.
 *
 * <h2>Architecture principles</h2>
 *
 * <b>Single authority.</b> This class is the only place in the codebase that
 * translates exceptions into HTTP responses. Controllers throw typed exceptions;
 * they never write to {@code HttpServletResponse} directly.
 *
 * <b>Envelope consistency.</b> Every error response — including Spring MVC
 * framework exceptions — uses the {@link ApiResponse} envelope. Without
 * extending {@link ResponseEntityExceptionHandler} (see below), Spring's own
 * handler fires for framework exceptions and breaks the envelope contract.
 *
 * <b>Information hiding.</b> Internal details (stack traces, SQL, column names,
 * {@code @PreAuthorize} expressions, Hibernate internals) are logged server-side
 * only. Clients receive a traceId to reference the error; never the root cause.
 *
 * <h2>Why this class extends ResponseEntityExceptionHandler</h2>
 *
 * Spring MVC auto-registers {@code ResponseEntityExceptionHandlerAdvice} if no
 * {@code @ControllerAdvice} extends {@link ResponseEntityExceptionHandler}. That
 * default handler processes ~15 standard MVC exceptions (missing params, wrong
 * method, unreadable body, etc.) and returns Spring's own JSON — not our
 * {@link ApiResponse} envelope.
 *
 * <p>By extending {@link ResponseEntityExceptionHandler} here, we take ownership
 * of all those exceptions and redirect them through our envelope. We override
 * only the methods we need to customise; the base class handles the rest safely.
 *
 * <h2>Security exception routing — filter chain vs method security</h2>
 *
 * {@code @RestControllerAdvice} only intercepts exceptions that propagate through
 * the Spring MVC dispatcher. Spring Security's filter chain runs before MVC and
 * has its own exception handlers for JWT/URL-level auth failures:
 *
 * <table>
 *   <tr><th>Scenario</th><th>Handler</th></tr>
 *   <tr><td>Missing/invalid JWT on protected URL</td>
 *       <td>{@link com.foodorder.security.JwtAuthenticationEntryPoint} → 401</td></tr>
 *   <tr><td>Authenticated but wrong role for URL pattern</td>
 *       <td>{@link com.foodorder.security.JwtAccessDeniedHandler} → 403</td></tr>
 *   <tr><td>{@code @PreAuthorize} fail on {@code @Controller} method</td>
 *       <td>{@link #handleAccessDenied} here → 403</td></tr>
 *   <tr><td>Programmatic {@code authManager.authenticate()} fail in service</td>
 *       <td>{@link #handleAuthentication} here → 401</td></tr>
 * </table>
 *
 * <h2>Preventing Spring's /error from leaking</h2>
 *
 * Even with this handler, some errors bypass MVC entirely and are forwarded
 * to {@code /error} by the Servlet container (e.g. Servlet-level 404s, filter
 * errors that call {@code response.sendError()}). {@link ApiErrorController}
 * overrides Spring Boot's {@code BasicErrorController} at {@code /error} to
 * ensure those responses also return the {@link ApiResponse} envelope.
 *
 * Additionally, {@code application.yml} disables whitelabel error page and
 * removes all internal detail from Spring's error attributes.
 *
 * <h2>Exception routing table</h2>
 * <pre>
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │ Exception                              │ HTTP  │ Handler                 │
 * ├──────────────────────────────────────────────────────────────────────────┤
 * │ MethodArgumentNotValidException        │  400  │ handleMethodArgumentNotValid   │
 * │ ConstraintViolationException           │  400  │ handleConstraintViolation      │
 * │ HttpMessageNotReadableException        │  400  │ handleHttpMessageNotReadable   │
 * │ MissingServletRequestParameterException│  400  │ handleMissingServletRequestParam│
 * │ MethodArgumentTypeMismatchException    │  400  │ handleTypeMismatch             │
 * │ AuthenticationException (method sec.) │  401  │ handleAuthentication    │
 * │ AccessDeniedException (method sec.)   │  403  │ handleAccessDenied      │
 * │ ResourceNotFoundException             │  404  │ handleResourceNotFound  │
 * │ NoResourceFoundException              │  404  │ handleNoResource        │
 * │ HttpRequestMethodNotSupportedException │  405  │ handleMethodNotAllowed  │
 * │ HttpMediaTypeNotSupportedException     │  415  │ handleUnsupportedMedia  │
 * │ BusinessException                     │409/422│ handleBusiness          │
 * │ DataIntegrityViolationException       │  409  │ handleDataIntegrity     │
 * │ Exception (catch-all)                 │  500  │ handleUnexpected        │
 * └──────────────────────────────────────────────────────────────────────────┘
 * </pre>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Field names whose rejected values MUST be stripped from error responses.
     *
     * <p>Uses case-insensitive substring matching — so the key {@code "password"}
     * covers {@code password}, {@code confirmPassword}, {@code currentPassword},
     * {@code newPassword}, etc. No need to enumerate every variant.
     *
     * <p>Extend this set when new sensitive field categories are added to request DTOs.
     * Prefer minimal root keys over exhaustive enumeration.
     */
    static final Set<String> SENSITIVE_FIELDS = Set.of(
        "password",    // covers: password, confirmPassword, currentPassword, newPassword
        "token",       // covers: token, refreshToken, accessToken, bearerToken
        "secret",      // covers: secret, apiSecret, clientSecret
        "apikey",      // covers: apiKey, apiToken (matched post-toLowerCase)
        "creditcard",  // covers: creditCard, creditCardNumber
        "cvv",         // covers: cvv, cvc
        "ssn",         // covers: ssn (Social Security / SIN)
        "taxid"        // covers: taxId, taxID
    );

    // ── 400 Bad Request ───────────────────────────────────────────────────────

    /**
     * Handles {@code @Valid} / {@code @Validated} failures on {@code @RequestBody} DTOs.
     *
     * <p>Triggered by: {@code @Valid} on a {@code @RequestBody} parameter when one or
     * more fields fail Bean Validation constraints ({@code @NotBlank}, {@code @Email},
     * {@code @Size}, custom constraints, etc.).
     *
     * <p>All field errors are collected into the {@code errors} array. Rejected values
     * for fields matching {@link #SENSITIVE_FIELDS} are stripped before the response
     * is sent — a failed password validation must not echo the attempted password.
     *
     * <p>Override of {@link ResponseEntityExceptionHandler#handleMethodArgumentNotValid}
     * to route through our {@link ApiResponse} envelope rather than Spring's default.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<ApiError> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toApiError)
                .toList();

        log.debug("Request body validation failed: {} error(s)", errors.size());

        return asObject(badRequest(
            "Request validation failed. See the 'errors' array for details.",
            errors));
    }

    /**
     * Handles Bean Validation failures on {@code @RequestParam}, {@code @PathVariable},
     * and method parameters annotated with constraints when {@code @Validated} is on the
     * controller class.
     *
     * <p>Triggered by: {@code @Validated} on the controller + a constraint annotation
     * on a method parameter, e.g. {@code @PathVariable @Positive Long id}.
     *
     * <p>This is a distinct exception type from {@link MethodArgumentNotValidException}.
     * Without an explicit handler, path/param constraint violations fall to the
     * catch-all and incorrectly return HTTP 500.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex) {

        List<ApiError> errors = ex.getConstraintViolations()
                .stream()
                .map(this::toApiError)
                .toList();

        log.debug("Parameter/path constraint violation: {} violation(s)", errors.size());

        return badRequest(
            "Request parameter validation failed. See the 'errors' array for details.",
            errors);
    }

    /**
     * Handles malformed or unparseable JSON in the request body.
     *
     * <p>Triggered by:
     * <ul>
     *   <li>JSON syntax errors (unclosed brackets, unquoted strings)</li>
     *   <li>Type mismatches (string where number expected)</li>
     *   <li>Unrecognised enum values</li>
     *   <li>Completely missing request body on a required body endpoint</li>
     * </ul>
     *
     * <p>Security: Jackson exception messages sometimes include the offending input
     * fragment. We log the exception class name only at DEBUG and return a generic
     * message. The raw Jackson message is never sent to the client.
     *
     * <p>Override of {@link ResponseEntityExceptionHandler#handleHttpMessageNotReadable}.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        log.debug("Unreadable request body: {}", ex.getClass().getSimpleName());

        return asObject(badRequest(
            "Request body could not be parsed. " +
            "Verify the body is valid JSON and all field types match the API contract.",
            null));
    }

    /**
     * Handles missing required {@code @RequestParam} values.
     *
     * <p>Triggered by: a controller method declares
     * {@code @RequestParam String sort} (required by default) and the caller
     * omits it. Spring returns 400 but via its own format unless overridden here.
     *
     * <p>Override of
     * {@link ResponseEntityExceptionHandler#handleMissingServletRequestParameter}.
     */
    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        log.debug("Missing required request parameter: name={}, type={}",
            ex.getParameterName(), ex.getParameterType());

        String message = String.format(
            "Required request parameter '%s' is missing.", ex.getParameterName());

        return asObject(badRequest(message, null));
    }

    /**
     * Handles path variable and request parameter type conversion failures.
     *
     * <p>Triggered by: {@code GET /api/v1/orders/not-a-uuid} when the controller
     * declares {@code @PathVariable UUID id}. Spring cannot convert the string
     * {@code "not-a-uuid"} to {@code UUID} and throws this exception.
     *
     * <p>Without this handler, the exception falls to the catch-all and incorrectly
     * returns HTTP 500. This is unambiguously a client error — HTTP 400.
     *
     * <p>Security: we include the parameter name in the message but NOT the
     * invalid value ({@code ex.getValue()}), which is user-controlled input that
     * could contain injection characters or sensitive data.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {

        // ex.getValue() is user-controlled — never include in response or log line
        String requiredType = ex.getRequiredType() != null
                ? ex.getRequiredType().getSimpleName()
                : "unknown";

        log.debug("Type mismatch: parameter='{}', requiredType='{}'",
            ex.getName(), requiredType);

        String message = String.format(
            "Parameter '%s' must be of type %s.", ex.getName(), requiredType);

        return badRequest(message, null);
    }

    // ── 401 Unauthorized ──────────────────────────────────────────────────────

    /**
     * Handles {@link AuthenticationException} from Spring Security method security.
     *
     * <p><b>Scope:</b> Fires only when an {@code AuthenticationException} propagates
     * through the MVC dispatcher — specifically from programmatic
     * {@code authManager.authenticate()} calls inside {@code @Service} methods that
     * propagate the exception rather than catching it.
     *
     * <p><b>NOT triggered by:</b> Missing or invalid JWT tokens on incoming requests.
     * Those are intercepted earlier in the filter chain by
     * {@link com.foodorder.security.JwtAuthenticationEntryPoint}.
     *
     * <p>Security: {@code ex.getMessage()} is not logged — Spring Security authentication
     * exception messages can contain internal credential detail.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(
            AuthenticationException ex) {
        log.warn("AuthenticationException via method security: {}", ex.getClass().getSimpleName());
        return buildResponse(HttpStatus.UNAUTHORIZED, "Authentication required.");
    }

    // ── 403 Forbidden ─────────────────────────────────────────────────────────

    /**
     * Handles {@link AccessDeniedException} from Spring Security method security.
     *
     * <p><b>Scope:</b>
     * <ul>
     *   <li>{@code @PreAuthorize}/{@code @PostAuthorize} on a {@code @Controller}
     *       or {@code @Service} method — the exception propagates past the method
     *       boundary through MVC.</li>
     * </ul>
     *
     * <p><b>NOT triggered by:</b> URL-level access denials (a role required for a
     * URL pattern in {@code SecurityConfig.authorizeHttpRequests()}). Those are
     * intercepted in the filter chain by
     * {@link com.foodorder.security.JwtAccessDeniedHandler}.
     *
     * <p>Security: {@code ex.getMessage()} is logged at DEBUG only. Spring's
     * {@code AccessDeniedException} message contains the {@code @PreAuthorize}
     * expression text (e.g. {@code "hasRole('ADMIN')"}), which would expose the
     * internal role/permission model in production logs if logged at INFO or WARN.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            AccessDeniedException ex) {
        // DEBUG only — message reveals @PreAuthorize expression text
        log.debug("Access denied via method security: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN,
            "You do not have permission to perform this action.");
    }

    // ── 404 Not Found ─────────────────────────────────────────────────────────

    /**
     * Handles domain resource lookups that find no matching entity.
     *
     * <p>Triggered by service-layer code:
     * <pre>{@code
     *   orderRepository.findById(id)
     *       .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
     * }</pre>
     *
     * <p>Security: logs only the resource type — not the full exception message,
     * which may contain a user-supplied identifier (email address, UUID from URL path).
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(
            ResourceNotFoundException ex) {
        String resourceType = ex.getResourceName() != null ? ex.getResourceName() : "Resource";
        log.debug("404 Not Found: resourceType={}", resourceType);
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Handles requests to URL paths with no mapped handler.
     *
     * <p>Triggered by Spring Boot 3.2+ / Spring 6.1+ when no {@code @RequestMapping}
     * matches the request URL. This superseded {@code NoHandlerFoundException} in
     * Spring Framework 6.1.
     *
     * <p>Without this handler, the exception propagates to {@code /error} via the
     * Servlet container, bypassing the envelope — even with {@link ApiErrorController}
     * registered — because this exception may arrive via MVC dispatch, not Servlet
     * error dispatch, depending on Spring version and configuration.
     *
     * <p>Override of {@link ResponseEntityExceptionHandler#handleNoResourceFoundException}.
     */
    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        log.debug("404 No handler: method={}, path={}", ex.getHttpMethod(), ex.getResourcePath());
        return asObject(buildResponse(HttpStatus.NOT_FOUND,
            String.format("No endpoint found for %s /%s",
                ex.getHttpMethod(), ex.getResourcePath())));
    }

    // ── 405 Method Not Allowed ────────────────────────────────────────────────

    /**
     * Handles requests using an HTTP method not supported by the matched endpoint.
     *
     * <p>Triggered by: {@code POST /api/v1/orders/{id}} when only {@code GET} and
     * {@code DELETE} are mapped to that path.
     *
     * <p>The allowed methods are included in the response message. Spring MVC also
     * sets the {@code Allow} response header automatically.
     *
     * <p>Override of
     * {@link ResponseEntityExceptionHandler#handleHttpRequestMethodNotSupported}.
     */
    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        String allowed = ex.getSupportedHttpMethods() != null
                ? ex.getSupportedHttpMethods().toString() : "unknown";
        log.debug("405 Method not allowed: attempted={}, allowed={}", ex.getMethod(), allowed);

        return asObject(buildResponse(HttpStatus.METHOD_NOT_ALLOWED,
            String.format("HTTP method '%s' is not supported. Allowed: %s",
                ex.getMethod(), allowed)));
    }

    // ── 415 Unsupported Media Type ────────────────────────────────────────────

    /**
     * Handles requests with an unsupported {@code Content-Type} header.
     *
     * <p>Triggered by: sending {@code Content-Type: text/xml} to an endpoint that
     * only accepts {@code application/json}.
     *
     * <p>Override of
     * {@link ResponseEntityExceptionHandler#handleHttpMediaTypeNotSupported}.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        String contentType = ex.getContentType() != null
                ? ex.getContentType().toString() : "unknown";
        log.debug("415 Unsupported media type: received='{}'", contentType);

        return asObject(buildResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            String.format(
                "Content-Type '%s' is not supported. Use 'application/json'.",
                contentType)));
    }

    // ── 409 / 422 — Business exceptions ──────────────────────────────────────

    /**
     * Handles business rule and domain invariant violations.
     *
     * <p>The HTTP status code is carried on the exception itself
     * ({@link BusinessException#getStatus()}) and defaults to 409 Conflict.
     * Throw with {@code HttpStatus.UNPROCESSABLE_ENTITY} (422) for semantic
     * validation failures where the request is syntactically valid but
     * semantically impossible given current state:
     *
     * <pre>{@code
     *   // 409 — resource state conflict
     *   throw new BusinessException("Order has already been delivered");
     *
     *   // 422 — semantic rule violation
     *   throw new BusinessException(
     *       "Discount cannot be applied: order total is below the minimum",
     *       HttpStatus.UNPROCESSABLE_ENTITY);
     * }</pre>
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        log.debug("{} BusinessException: {}", ex.getStatus().value(), ex.getMessage());
        return buildResponse(ex.getStatus(), ex.getMessage());
    }

    /**
     * Handles database constraint violations (unique index, FK constraint, NOT NULL).
     *
     * <p>Maps to 409 Conflict — the request conflicts with existing data state.
     *
     * <p>Security: Postgres/MySQL error messages include table names, column names,
     * index names, and sometimes the conflicting value — all of which can constitute
     * schema disclosure or PII leakage. This handler:
     * <ul>
     *   <li>Logs the root cause class name at WARN (not ERROR — this is a client
     *       data conflict, not an application failure)</li>
     *   <li>Never sends DB error detail to the client</li>
     * </ul>
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(
            DataIntegrityViolationException ex) {
        // Root cause class only — the message contains column names / index names
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getClass().getSimpleName());
        return buildResponse(HttpStatus.CONFLICT,
            "The request conflicts with existing data. " +
            "This resource may already exist, or a referenced resource is still in use.");
    }

    // ── 500 Internal Server Error — mandatory catch-all ──────────────────────

    /**
     * Mandatory catch-all for any exception not handled by a more specific handler.
     *
     * <h3>Why this handler is critical</h3>
     * Without it, unhandled exceptions reach Spring Boot's {@code BasicErrorController}
     * or the {@code /error} fallback, which returns a different JSON shape. Even with
     * {@link ApiErrorController} overriding {@code /error}, some exceptions propagate
     * differently depending on where they are thrown in the request lifecycle.
     *
     * <h3>What this handler guarantees</h3>
     * <ol>
     *   <li>Full stack trace logged at ERROR with traceId — ops can find the exact
     *       failure by grepping the traceId in logs.</li>
     *   <li>Generic 500 message returned to the client — no internal detail
     *       (class names, stack frames, SQL, file paths).</li>
     *   <li>traceId included in the response body so clients can reference this
     *       specific failure in a support request. Guarded for null (MDC may be
     *       cleared before this handler runs on some async or forwarded paths).</li>
     * </ol>
     *
     * <h3>What it never does</h3>
     * Does not return {@code ex.getMessage()}, the exception class name, any stack
     * frame, or any internal state in the response body.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        String traceId = MDC.get("traceId");

        // Full stack trace logged server-side. Never sent to the client.
        log.error("Unhandled exception [traceId={}]: {}", traceId, ex.getMessage(), ex);

        // Guard for null traceId: MDC may be cleared before this handler runs
        // (e.g. async dispatch, error-forwarded requests through /error path)
        String supportRef = traceId != null
                ? "Reference ID: " + traceId
                : "Contact support and describe the time and action that caused this error.";

        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred. Please try again. " + supportRef);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Builds a 400 Bad Request response with an optional list of field errors.
     *
     * @param message top-level validation summary (shown in {@code message} field)
     * @param errors  per-field errors, or {@code null} for non-field 400s
     */
    private ResponseEntity<ApiResponse<Void>> badRequest(
            String message, List<ApiError> errors) {
        ApiResponse<Void> body = (errors != null && !errors.isEmpty())
                ? ApiResponse.error(HttpStatus.BAD_REQUEST.value(), message, errors)
                : ApiResponse.error(HttpStatus.BAD_REQUEST.value(), message);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Builds an error response for any HTTP status code.
     *
     * <p>{@link ApiResponse#error(int, String)} auto-populates {@code timestamp}
     * and reads {@code traceId} from MDC.
     */
    private ResponseEntity<ApiResponse<Void>> buildResponse(HttpStatus status, String message) {
        return ResponseEntity
                .status(status)
                .body(ApiResponse.error(status.value(), message));
    }

    /**
     * Casts {@code ResponseEntity<ApiResponse<Void>>} to the {@code ResponseEntity<Object>}
     * return type required by all {@link ResponseEntityExceptionHandler} override methods.
     *
     * <p>This is safe because {@code ApiResponse<Void>} is a subtype of {@code Object}
     * and the Jackson serialiser handles the generic type via runtime type tokens.
     * The cast avoids duplicating the response-building logic for the two different
     * return type signatures.
     */
    @SuppressWarnings("unchecked")
    private ResponseEntity<Object> asObject(ResponseEntity<ApiResponse<Void>> response) {
        return (ResponseEntity<Object>) (ResponseEntity<?>) response;
    }

    /**
     * Maps a Spring MVC {@link FieldError} to an {@link ApiError}.
     *
     * <p>Security: rejected values for fields matching {@link #SENSITIVE_FIELDS}
     * are stripped (null) before inclusion in the response. Sensitivity is determined
     * by case-insensitive substring match — {@code "confirmPassword"} matches the
     * root key {@code "password"}.
     */
    private ApiError toApiError(FieldError error) {
        String field = error.getField();
        if (isSensitive(field)) {
            return ApiError.of(field, error.getDefaultMessage());
        }
        return ApiError.of(field, error.getRejectedValue(), error.getDefaultMessage());
    }

    /**
     * Maps a Bean Validation {@link ConstraintViolation} to an {@link ApiError}.
     *
     * <p>Extracts the leaf property name from the full violation path.
     * For a violation on method parameter {@code createUser(email)}, the path is
     * {@code createUser.email} — we take the last segment: {@code email}.
     *
     * <p>Security: same sensitive-field stripping as {@link #toApiError(FieldError)}.
     */
    private ApiError toApiError(ConstraintViolation<?> violation) {
        String path  = violation.getPropertyPath().toString();
        String field = path.contains(".")
                ? path.substring(path.lastIndexOf('.') + 1)
                : path;

        if (isSensitive(field)) {
            return ApiError.of(field, violation.getMessage());
        }
        return ApiError.of(field, violation.getInvalidValue(), violation.getMessage());
    }

    /**
     * Returns {@code true} if {@code fieldName} contains any sensitive keyword
     * (case-insensitive substring match).
     *
     * <p>The field name is lowercased once and then tested against each key in
     * {@link #SENSITIVE_FIELDS}. A single root key covers all variants:
     * {@code "password"} matches {@code password}, {@code confirmPassword},
     * {@code currentPassword}, {@code newPassword}, etc.
     *
     * @param fieldName the DTO field name from the binding result or constraint violation
     */
    private boolean isSensitive(String fieldName) {
        String lower = fieldName.toLowerCase();
        return SENSITIVE_FIELDS.stream().anyMatch(lower::contains);
    }
}