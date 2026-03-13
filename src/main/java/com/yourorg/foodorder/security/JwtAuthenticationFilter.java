package com.yourorg.foodorder.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Per-request JWT authentication filter.
 *
 * <h3>Position in the filter chain</h3>
 * Inserted <b>before</b> {@code UsernamePasswordAuthenticationFilter}
 * (see {@link com.foodorder.config.SecurityConfig}). This is the standard
 * insertion point for custom token-based authentication filters.
 *
 * <h3>Contract</h3>
 * <table border="1">
 *   <tr><th>Condition</th><th>Action</th></tr>
 *   <tr><td>Valid Bearer token</td><td>Populate SecurityContext, continue chain</td></tr>
 *   <tr><td>Missing Authorization header</td><td>Do nothing, continue chain</td></tr>
 *   <tr><td>Invalid/expired token</td><td>Log warning, do nothing, continue chain</td></tr>
 *   <tr><td>User not found in DB</td><td>Log warning, clear context, continue chain</td></tr>
 * </table>
 *
 * <p>This filter <b>never short-circuits</b> the chain. The endpoint's
 * authorization rule determines whether a 401 is returned. This keeps
 * public endpoints working without tokens.
 *
 * <h3>Idempotency</h3>
 * If the SecurityContext is already populated when this filter runs
 * (unusual in STATELESS mode, but possible in tests or with other filters),
 * the existing authentication is preserved and this filter is a no-op.
 *
 * <h3>Error classification</h3>
 * Exceptions are split by type to avoid masking real bugs:
 * <ul>
 *   <li>{@code JwtException} and {@code UsernameNotFoundException} → WARN
 *       (expected failure path — bad token or unknown user).</li>
 *   <li>Any other {@code RuntimeException} → ERROR + log, but chain continues.
 *       A bug in {@code UserDetailsService} should not silently grant access.</li>
 * </ul>
 *
 * <h3>What is never logged</h3>
 * <ul>
 *   <li>The raw token string (bearer credential).</li>
 *   <li>Passwords or any credential material.</li>
 *   <li>Full stack traces for expected token failures.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX        = "Bearer ";

    /**
     * Paths excluded from JWT processing.
     *
     * <p>These paths never carry a JWT so token validation is pure overhead.
     * <b>Note:</b> skipping JWT processing does NOT skip MDC tracing —
     * {@code RequestLoggingFilter} runs at {@code Ordered.HIGHEST_PRECEDENCE}
     * (before Spring Security) and populates {@code traceId} for all paths.
     *
     * <p>Management endpoints are served on port 8081 but the paths are listed
     * here for defence-in-depth in case the management port is inadvertently
     * mapped to the same Tomcat connector in test environments.
     */
    private static final Set<String> EXCLUDED_PATH_PREFIXES = Set.of(
        "/actuator",
        "/api/v1/health"
    );

    private final JwtTokenProvider   jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         filterChain) throws ServletException, IOException {

        String token = extractBearerToken(request);

        // Fast path: no token present — continue chain without touching SecurityContext.
        // Public endpoints will succeed; protected endpoints will trigger 401 via
        // JwtAuthenticationEntryPoint.
        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Fast path: SecurityContext already has authentication (idempotency guard).
        // In STATELESS mode this should never be true, but is a defensive check.
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            log.trace("SecurityContext already populated — skipping JWT processing");
            filterChain.doFilter(request, response);
            return;
        }

        // Validate token — signature and expiry. validateToken() never throws.
        if (!jwtTokenProvider.validateToken(token)) {
            // Invalid token — do not populate SecurityContext.
            // The endpoint's access rule will return 401 if auth is required.
            filterChain.doFilter(request, response);
            return;
        }

        // Token is valid. Load user and populate SecurityContext.
        try {
            String username = jwtTokenProvider.extractUsername(token);
            if (username == null) {
                log.warn("JWT validated but subject claim is null for URI: {}",
                         request.getRequestURI());
                filterChain.doFilter(request, response);
                return;
            }

            // Load UserDetails fresh on every request.
            // This reflects role changes, account deactivation, or deletion
            // immediately — without waiting for the token to expire.
            //
            // Performance note: for very high-throughput services, consider
            // a short-lived in-process cache (Caffeine, ~5s TTL) or embedding
            // a stable role set in the token (with understanding of staleness risk).
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,                          // credentials null post-authentication
                    userDetails.getAuthorities()
                );

            // Attach request metadata (IP, session ref) to the Authentication object.
            // Available via SecurityContextHolder in downstream code if needed.
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(auth);

            // Log identity only — NEVER log the token value
            log.debug("Authenticated: user={}, uri={}", username, request.getRequestURI());

        } catch (UsernameNotFoundException ex) {
            // Token was valid but the user no longer exists in the database.
            // This can happen when an account is deleted but old tokens persist.
            log.warn("JWT valid but user not found: {}, uri={}",
                     ex.getMessage(), request.getRequestURI());
            // SecurityContext remains unpopulated — 401 if endpoint requires auth.

        } catch (RuntimeException ex) {
            // Unexpected failure (e.g., DB down, NPE in UserDetailsService).
            // Log at ERROR — this is a real bug, not an expected auth failure.
            // Still continue the chain so the request gets a 401/500, not a hang.
            log.error("Unexpected error during JWT authentication for uri={}: {}",
                      request.getRequestURI(), ex.getMessage(), ex);
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Skips the filter for paths that never carry a JWT.
     * Reduces unnecessary processing and log noise on health/actuator endpoints.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return EXCLUDED_PATH_PREFIXES.stream().anyMatch(uri::startsWith);
    }

    /**
     * Extracts the raw JWT from {@code Authorization: Bearer <token>}.
     *
     * @return the token string, or {@code null} if the header is absent or malformed
     */
    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).strip();
        }
        return null;
    }
}