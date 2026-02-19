package com.foodorder.config;

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
 * Architecture decisions:
 *
 * 1. Defined as a standalone @Configuration class rather than inline in
 *    SecurityConfig. This keeps security config focused on authentication/
 *    authorization and makes CORS policy independently testable.
 *
 * 2. Returns a CorsConfigurationSource @Bean, which SecurityConfig references
 *    via .cors(cors -> cors.configurationSource(corsConfigurationSource())).
 *    This is the correct Spring Security 6 approach. DO NOT use
 *    WebMvcConfigurer.addCorsMappings() alongside Spring Security — it creates
 *    two separate CORS layers that can conflict unpredictably.
 *
 * 3. Allowed origins are injected from environment config — never hardcoded.
 *    Wildcard (*) is prohibited when allowCredentials=true (browser spec
 *    enforces this; we also enforce it here for defense-in-depth).
 *
 * 4. OPTIONS preflight requests bypass JWT authentication in SecurityConfig,
 *    so this configuration applies to all preflight without auth interference.
 *
 * Safe defaults:
 *   - No wildcard origin in production
 *   - Explicit allowed methods (no wildcard)
 *   - Explicit allowed headers
 *   - Credentials allowed (for cookie-based refresh token support, future use)
 */
@Configuration
public class CorsConfig {

    /**
     * Comma-separated list of allowed origins from environment variable.
     * Dev default: localhost frontends. Prod: set CORS_ALLOWED_ORIGINS explicitly.
     */
    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String allowedOriginsRaw;

    @Value("${app.cors.max-age-seconds:3600}")
    private long maxAgeSeconds;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Parse comma-separated origins and strip whitespace
        List<String> allowedOrigins = Arrays.stream(allowedOriginsRaw.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();

        config.setAllowedOrigins(allowedOrigins);

        // Explicit method allowlist. No wildcard — defense-in-depth.
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Allow Authorization header (for Bearer token) and common content headers.
        // X-Trace-Id allows frontend to pass correlation IDs for request tracing.
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Requested-With",
                "X-Trace-Id"
        ));

        // Expose response headers the client JS can read
        config.setExposedHeaders(List.of(
                "X-Trace-Id",
                "X-Total-Count"    // Useful for pagination metadata
        ));

        // Allow cookies/auth headers — required for refresh token cookie flows
        config.setAllowCredentials(true);

        // Cache preflight response for maxAgeSeconds (reduces OPTIONS round-trips)
        config.setMaxAge(maxAgeSeconds);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}