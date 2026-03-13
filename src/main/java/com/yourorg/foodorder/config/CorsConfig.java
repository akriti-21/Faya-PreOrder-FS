package com.yourorg.foodorder.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS (Cross-Origin Resource Sharing) configuration.
 *
 * <h2>Why a dedicated class, not inline in SecurityConfig?</h2>
 * CORS policy is a web-transport concern; authentication is a security concern.
 * They evolve for different reasons:
 * <ul>
 *   <li>New frontend deployment → CORS origin list changes</li>
 *   <li>New auth mechanism → SecurityConfig changes</li>
 * </ul>
 * Separating them makes each independently reviewable, testable, and auditable.
 *
 * <h2>Spring Security 6 integration</h2>
 * Expose a {@link CorsConfigurationSource} {@code @Bean}.
 * {@link SecurityConfig} wires it in via:
 * <pre>{@code
 *   .cors(cors -> cors.configurationSource(corsConfigurationSource))
 * }</pre>
 *
 * <b>Do not</b> use {@code WebMvcConfigurer.addCorsMappings()} alongside Spring
 * Security — they apply at different layers and their interaction is undefined.
 * One will silently win; the loser becomes a debugging puzzle.
 *
 * <h2>Why CORS is processed before JWT authentication</h2>
 * Spring Security's {@code CorsFilter} runs at the start of the security filter
 * chain, before {@link JwtAuthenticationFilter}. This is required: OPTIONS
 * preflight requests carry no JWT (the browser hasn't sent the actual request
 * yet). If authentication ran first, all preflight would receive 401 and no
 * cross-origin call could ever succeed.
 *
 * <h2>Security constraints enforced here</h2>
 * <ul>
 *   <li>No wildcard {@code *} origin — incompatible with {@code allowCredentials=true}
 *       per the Fetch specification. Exact origins only.</li>
 *   <li>Origins are runtime-configurable — never hardcoded in source.</li>
 *   <li>Explicit method and header allowlists — no wildcards.</li>
 *   <li>Deny-all catch-all for every non-API path prevents accidental CORS
 *       exposure on actuator, error pages, or future path additions.</li>
 * </ul>
 *
 * <h2>Fail-fast startup validation</h2>
 * {@link #validateConfig()} runs via {@code @PostConstruct} to catch a missing
 * or empty {@code CORS_ALLOWED_ORIGINS} before any requests are processed.
 * An empty allowed-origins list would silently reject all cross-origin requests
 * in production with no error message — a subtle, hard-to-debug misconfiguration.
 */
@Configuration
public class CorsConfig {

    /**
     * Comma-separated list of allowed origins.
     *
     * <p>Dev default: Vite (5173) and CRA (3000) dev servers.
     * <p>Production: set {@code CORS_ALLOWED_ORIGINS} to exact frontend domain(s).
     * Example: {@code https://app.foodorder.com,https://admin.foodorder.com}
     */
    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String allowedOriginsRaw;

    /**
     * Preflight cache TTL. Browsers re-send OPTIONS at most this often.
     * Reduces round-trips for high-frequency APIs. Default: 1 hour.
     */
    @Value("${app.cors.max-age-seconds:3600}")
    private long maxAgeSeconds;

    /**
     * Validates CORS configuration at startup.
     *
     * <p>Fails fast if {@code CORS_ALLOWED_ORIGINS} resolves to an empty string.
     * An empty origins list would silently fail all cross-origin requests in
     * production — a misconfiguration whose symptoms look like network errors,
     * not a configuration problem.
     */
    @PostConstruct
    public void validateConfig() {
        List<String> origins = parseOrigins();
        if (origins.isEmpty()) {
            throw new IllegalStateException(
                "[CORS] No allowed origins configured. " +
                "Set CORS_ALLOWED_ORIGINS to at least one origin. " +
                "Example: https://app.foodorder.com");
        }
    }

    /**
     * Produces the {@link CorsConfigurationSource} bean consumed by
     * {@link SecurityConfig}'s filter chain.
     *
     * <p>Two URL patterns are registered:
     * <ol>
     *   <li>{@code /api/**} — full CORS policy with credentials support.</li>
     *   <li>{@code /**} — deny-all catch-all for every other path. Without
     *       this, unregistered paths fall through to a permissive default
     *       that allows cross-origin access to actuator, /error, and any
     *       future path not explicitly covered by /api/**.</li>
     * </ol>
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = parseOrigins();

        CorsConfiguration api = new CorsConfiguration();
        api.setAllowedOrigins(origins);

        // Explicit allowlist — no wildcards. HEAD is included for content
        // negotiation pings from some API clients.
        api.setAllowedMethods(List.of(
            "GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        // Authorization: sends the Bearer token.
        // Content-Type: required for JSON request bodies.
        // X-Trace-Id: allows distributed trace propagation from frontend.
        api.setAllowedHeaders(List.of(
            "Authorization",
            "Content-Type",
            "Accept",
            "X-Requested-With",
            "X-Trace-Id"
        ));

        // Response headers the browser's JavaScript is allowed to read.
        // X-Total-Count: pagination metadata.
        // X-Trace-Id: echoed back for support correlation.
        api.setExposedHeaders(List.of("X-Trace-Id", "X-Total-Count"));

        // Required for Authorization header to be sent by browsers.
        // Per the Fetch spec, allowCredentials=true prohibits wildcard (*) origins —
        // enforced above by using exact origin list.
        api.setAllowCredentials(true);

        // Cache preflight for maxAgeSeconds. Browsers re-send OPTIONS at most once
        // per this interval, reducing latency on high-frequency API calls.
        api.setMaxAge(maxAgeSeconds);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        // Full CORS policy for all API endpoints
        source.registerCorsConfiguration("/api/**", api);

        // Deny-all catch-all: every non-API path (actuator, /error, root, etc.)
        // returns no CORS headers — cross-origin access is forbidden by omission.
        CorsConfiguration denyAll = new CorsConfiguration();
        denyAll.setAllowedOrigins(List.of());
        source.registerCorsConfiguration("/**", denyAll);

        return source;
    }

    // ── private ───────────────────────────────────────────────────────────────

    private List<String> parseOrigins() {
        return Arrays.stream(allowedOriginsRaw.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .toList();
    }
}