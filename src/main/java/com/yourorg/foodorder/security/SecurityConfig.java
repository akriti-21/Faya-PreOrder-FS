package com.foodorder.config;

import com.foodorder.security.JwtAuthenticationEntryPoint;
import com.foodorder.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Spring Security configuration — stateless, JWT-ready baseline.
 *
 * Architecture decisions:
 *
 * STATELESS:
 *   SessionCreationPolicy.STATELESS means no HttpSession is created or used.
 *   Every request must carry a valid JWT. This is mandatory for horizontal
 *   scaling — any instance can serve any request with no shared session state.
 *
 * CSRF DISABLED:
 *   CSRF attacks exploit browser session cookies. Since we use stateless JWT
 *   (no cookies for auth), there is no session to forge. Disabling CSRF is
 *   correct and safe for a stateless token-based API. Enabling it would add
 *   complexity and break standard API clients for zero security benefit.
 *
 * JWT FILTER PLACEMENT:
 *   JwtAuthenticationFilter is added BEFORE UsernamePasswordAuthenticationFilter.
 *   It reads Bearer token, validates it, and sets SecurityContext.
 *   On missing/invalid token, it does NOT throw — it simply doesn't set auth,
 *   allowing downstream endpoint security to produce the 401.
 *
 * METHOD SECURITY:
 *   @EnableMethodSecurity enables @PreAuthorize/@PostAuthorize on service/controller
 *   methods. This gives fine-grained authorization beyond URL patterns.
 *
 * PUBLIC ENDPOINTS:
 *   Principle of least privilege — lock everything down by default, open
 *   specific endpoints explicitly. New endpoints require deliberate allowlisting.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    /**
     * Primary security filter chain.
     *
     * Endpoint access rules:
     *   - Auth endpoints: publicly accessible (login, register)
     *   - Menu browsing: publicly readable (guest users can browse)
     *   - Actuator health: public (load balancer health checks require no auth)
     *   - Actuator management port (8081): secured separately in application.yml
     *   - Everything else: requires valid JWT
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ----------------------------------------------------------------
            // CORS: Delegate to our CorsConfigurationSource bean (CorsConfig).
            // This must be called before other configuration — CORS preflight
            // OPTIONS requests must be processed before authentication filters.
            // ----------------------------------------------------------------
            .cors(cors -> cors.configurationSource(corsConfigurationSource))

            // ----------------------------------------------------------------
            // CSRF: Disabled for stateless JWT API.
            // Reason: no session cookies = no CSRF attack surface.
            // ----------------------------------------------------------------
            .csrf(AbstractHttpConfigurer::disable)

            // ----------------------------------------------------------------
            // SESSION: Stateless — no HttpSession created or consulted.
            // Spring Security will not create or use a session for auth state.
            // ----------------------------------------------------------------
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ----------------------------------------------------------------
            // EXCEPTION HANDLING
            // authenticationEntryPoint: handles 401 when authentication is
            // missing or invalid (e.g., bad/expired JWT token).
            // accessDeniedHandler: handles 403 when authenticated but unauthorized
            // (default Spring handler returns JSON-inconsistent response — override later).
            // ----------------------------------------------------------------
            .exceptionHandling(exceptions ->
                exceptions.authenticationEntryPoint(jwtAuthenticationEntryPoint))

            // ----------------------------------------------------------------
            // ENDPOINT ACCESS RULES
            // Order matters: more specific rules first, catch-all last.
            // ----------------------------------------------------------------
            .authorizeHttpRequests(auth -> auth

                // Preflight requests must be allowed without authentication.
                // Browsers send OPTIONS before cross-origin requests.
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // Authentication endpoints — no JWT required (obtaining one here)
                .requestMatchers(
                    "/api/v1/auth/login",
                    "/api/v1/auth/register",
                    "/api/v1/auth/refresh"
                ).permitAll()

                // Public menu browsing — guests can browse without an account
                .requestMatchers(HttpMethod.GET, "/api/v1/menu/**").permitAll()

                // Actuator health — load balancers and monitoring need this unauthenticated
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                // All other requests require a valid, authenticated JWT
                .anyRequest().authenticated()
            )

            // ----------------------------------------------------------------
            // JWT FILTER
            // Inserted before UsernamePasswordAuthenticationFilter.
            // On each request: extracts Bearer token → validates → sets SecurityContext.
            // Does not throw on missing token — silently passes through.
            // ----------------------------------------------------------------
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * AuthenticationManager bean — required for programmatic authentication
     * in AuthService (e.g., during login to verify credentials).
     *
     * Spring Boot 3 / Security 6: must be explicitly exposed as a @Bean.
     * AuthenticationConfiguration provides the pre-configured manager.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}