package com.yourorg.foodorder.config;

import com.foodorder.security.JwtAccessDeniedHandler;
import com.foodorder.security.JwtAuthenticationEntryPoint;
import com.foodorder.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.DependsOn;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Spring Security configuration — stateless, JWT-authenticated REST API.
 *
 * <h2>Security filter chain execution order</h2>
 *
 * Every HTTP request passes through these layers in the order shown.
 * Understanding the order is critical: misplacing a filter causes silent
 * security failures that are hard to detect in testing.
 *
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  SERVLET CONTAINER (Tomcat)                                             │
 * │                                                                         │
 * │  ① RequestLoggingFilter  [Ordered.HIGHEST_PRECEDENCE, Servlet filter]   │
 * │     Populates MDC with traceId BEFORE Spring Security processes the     │
 * │     request. This ensures every log line — including Spring Security    │
 * │     debug output — carries the traceId for correlation.                 │
 * │     Lives in: com.foodorder.util                                        │
 * │                                                                         │
 * │  ② Spring Security FilterChainProxy  [wraps the chain below]            │
 * │  │                                                                      │
 * │  │  ③ CorsFilter  [added by .cors()]                                    │
 * │  │     Processes CORS and answers OPTIONS preflight requests.           │
 * │  │     MUST run before authentication so preflight succeeds without a   │
 * │  │     JWT — the browser has not yet sent the actual request.           │
 * │  │     Source: CorsConfig.corsConfigurationSource() bean.              │
 * │  │                                                                      │
 * │  │  ④ SecurityContextHolderFilter                                       │
 * │  │     In STATELESS mode: creates an empty SecurityContext and          │
 * │  │     discards it at the end. Never persists to session storage.       │
 * │  │                                                                      │
 * │  │  ⑤ JwtAuthenticationFilter  [addFilterBefore ⑥]                     │
 * │  │     Reads Authorization: Bearer <token>.                             │
 * │  │     Valid token  → populate SecurityContext, continue.               │
 * │  │     Missing token → do nothing, continue (public endpoints work).   │
 * │  │     Invalid token → log WARN, do nothing, continue.                 │
 * │  │     Lives in: com.foodorder.security                                 │
 * │  │                                                                      │
 * │  │  ⑥ UsernamePasswordAuthenticationFilter  [disabled, never matches]   │
 * │  │     Canonical insertion point for custom token filters. Present in   │
 * │  │     the chain but form-login and basic-auth are explicitly disabled. │
 * │  │                                                                      │
 * │  │  ⑦ AuthorizationFilter  [evaluates authorizeHttpRequests rules]      │
 * │  │     If unauthenticated and rule requires auth → triggers ⑧           │
 * │  │     If authenticated but unauthorized → triggers ⑨                  │
 * │  │                                                                      │
 * │  │  ⑧ JwtAuthenticationEntryPoint  → HTTP 401                          │
 * │  │     Returns ApiResponse JSON envelope. Generic message — no detail  │
 * │  │     about WHY auth failed (prevents token-probing attacks).          │
 * │  │                                                                      │
 * │  │  ⑨ JwtAccessDeniedHandler  → HTTP 403                               │
 * │  │     Returns ApiResponse JSON envelope. Does NOT log                 │
 * │  │     AccessDeniedException.getMessage() (exposes @PreAuthorize text). │
 * │  │                                                                      │
 * └─────────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Key security decisions</h2>
 *
 * <b>STATELESS:</b> No HttpSession created or consulted. Every request is
 * independently authenticated. Required for horizontal scaling.
 *
 * <b>CSRF disabled:</b> CSRF attacks exploit session cookies. No session cookies
 * means no CSRF attack surface. Safe to disable; enables clean API clients.
 *
 * <b>Form login + HTTP Basic explicitly disabled:</b> Prevents Spring Boot
 * autoconfiguration from re-enabling these mechanisms on future dependency
 * changes. Belt-and-suspenders — in STATELESS mode they are inert anyway.
 *
 * <b>Deny-all default:</b> {@code .anyRequest().authenticated()} is the final
 * rule. New endpoints are locked by default; public access requires deliberate
 * allowlisting. This is the principle of least privilege applied to routing.
 *
 * <b>HTTP security headers:</b> Hardened against browser-side attack vectors.
 * Even for a pure JSON API these cost nothing and document security intent.
 *
 * <b>HttpFirewall:</b> Explicit configuration with documented rationale for
 * each setting. Prevents request smuggling and path traversal at the container
 * boundary before any controller code runs.
 *
 * <h2>Bean ordering</h2>
 * {@code securityFilterChain} depends on:
 * <ul>
 *   <li>{@link JwtProperties} — must be validated first (via @PostConstruct)</li>
 *   <li>{@link JwtAuthenticationFilter} — depends on JwtTokenProvider + UserDetailsService</li>
 *   <li>{@link JwtAuthenticationEntryPoint} — depends on ObjectMapper</li>
 *   <li>{@link JwtAccessDeniedHandler} — depends on ObjectMapper</li>
 *   <li>{@link CorsConfig#corsConfigurationSource()} — depends on @Value injection</li>
 * </ul>
 * {@code @DependsOn("jwtProperties")} ensures JwtProperties.validate() runs before
 * any bean that uses JWT configuration. Spring resolves the rest transitively.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    // ── Bean injection ────────────────────────────────────────────────────────
    // CorsConfigurationSource produced by CorsConfig — injected by type.
    private final CorsConfigurationSource       corsConfigurationSource;
    private final JwtAuthenticationFilter       jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint   jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler        jwtAccessDeniedHandler;

    // ── Primary Security Filter Chain ─────────────────────────────────────────

    /**
     * Configures the Spring Security filter chain for the API.
     *
     * <p>{@code @DependsOn("jwtProperties")} forces {@link JwtProperties#validate()}
     * to complete before this bean is constructed. If the JWT secret is missing or
     * invalid, the application context fails here with a clear error rather than
     * failing obscurely when the first authenticated request is made.
     */
    @Bean
    @DependsOn("jwtProperties")
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            // ── ③ CORS ───────────────────────────────────────────────────────
            // Runs first inside the Security chain. OPTIONS preflight is handled
            // here — before JwtAuthenticationFilter runs — so preflight succeeds
            // without a token. See CorsConfig for the full policy.
            .cors(cors -> cors.configurationSource(corsConfigurationSource))

            // ── CSRF ─────────────────────────────────────────────────────────
            // Stateless JWT has no auth cookies. No cookies = no CSRF surface.
            // Explicitly disable to prevent any autoconfiguration re-enabling it.
            .csrf(AbstractHttpConfigurer::disable)

            // ── FORM LOGIN ────────────────────────────────────────────────────
            // Explicitly disabled. We never want browser redirect-based auth on
            // an API. Without this, a Spring Boot upgrade could re-enable it.
            .formLogin(AbstractHttpConfigurer::disable)

            // ── HTTP BASIC ────────────────────────────────────────────────────
            // Explicitly disabled. Base64 credentials over HTTP are insecure.
            // Belt-and-suspenders: STATELESS mode already makes this inert.
            .httpBasic(AbstractHttpConfigurer::disable)

            // ── SESSION MANAGEMENT ────────────────────────────────────────────
            // STATELESS: Spring Security creates no HttpSession and never reads
            // one. sessionFixation().none() is explicit documentation that we
            // accept no session fixation risk because no sessions exist.
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .sessionFixation().none())

            // ── HTTP SECURITY HEADERS ─────────────────────────────────────────
            // Applied to every response. For a JSON API these are belt-and-
            // suspenders — browsers shouldn't render our responses as pages,
            // but these headers make that contract explicit and machine-enforced.
            //
            // DEFAULT HEADERS Spring Security adds automatically (we leave on):
            //   X-Content-Type-Options: nosniff      — prevents MIME sniffing
            //   Cache-Control: no-cache, no-store     — prevents response caching
            //   X-Frame-Options: DENY                 — added by .frameOptions()
            //
            // ADDITIONAL HEADERS we configure below:
            .headers(headers -> headers

                // ── X-Content-Type-Options: nosniff ───────────────────────────
                // Spring Security enables this by default. We leave it on.
                // It prevents browsers from MIME-sniffing a response away from
                // its declared Content-Type, blocking content-sniffing XSS.
                // (No explicit call needed — present in Spring Security defaults.)

                // ── X-Frame-Options: DENY ────────────────────────────────────
                // Prevents any framing of our API responses. Defends against
                // clickjacking. Zero cost for a JSON API.
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)

                // ── HTTP Strict-Transport-Security (HSTS) ─────────────────────
                // Instructs browsers to only ever connect via HTTPS for 1 year.
                // includeSubDomains: child domains (e.g. api.foodorder.com) also
                //   inherit the HTTPS-only policy.
                // preload: eligible for HSTS preload list (browser vendor lists).
                // NOTE: harmless in dev (HTTP), enforced only after first HTTPS
                //   response reaches the browser.
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31_536_000)   // 1 year in seconds
                    .preload(true))

                // ── Referrer-Policy ───────────────────────────────────────────
                // STRICT_ORIGIN_WHEN_CROSS_ORIGIN: send only the origin (no path)
                // in the Referer header for cross-origin requests.
                // Prevents API endpoint paths from leaking to third-party domains
                // via embedded assets, analytics, or external links.
                .referrerPolicy(referrer -> referrer
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy
                        .STRICT_ORIGIN_WHEN_CROSS_ORIGIN))

                // ── Permissions-Policy ────────────────────────────────────────
                // Instructs the browser to disable APIs this server never needs.
                // Belt-and-suspenders: a JSON API has no reason to access device
                // hardware. Even if an XSS payload runs in a browser parsing our
                // JSON, it cannot access camera, mic, location, or payment flows.
                .permissionsPolicy(p -> p.policy(
                    "camera=(), microphone=(), geolocation=(), payment=()"
                ))
            )

            // ── EXCEPTION HANDLERS ────────────────────────────────────────────
            // Both handlers write the ApiResponse JSON envelope so ALL error
            // responses — auth and business — have the same JSON shape.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)  // → 401
                .accessDeniedHandler(jwtAccessDeniedHandler))           // → 403

            // ── AUTHORIZATION RULES ───────────────────────────────────────────
            // Evaluated top-to-bottom; first match wins.
            // PRINCIPLE OF LEAST PRIVILEGE: default DENY, explicit ALLOW.
            .authorizeHttpRequests(auth -> auth

                // -- Truly public: no token, no auth, no constraints -----------

                // OPTIONS preflight: browser sends this before any credentialed
                // cross-origin request. Must succeed without a token.
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // Token issuance: you can't present a token to get a token.
                .requestMatchers(
                    "/api/v1/auth/login",
                    "/api/v1/auth/register",
                    "/api/v1/auth/refresh"
                ).permitAll()

                // Menu browsing is public: guests can discover items before login.
                .requestMatchers(HttpMethod.GET, "/api/v1/menu/**").permitAll()

                // API-port liveness probe: load balancers need this without auth.
                .requestMatchers("/api/v1/health").permitAll()

                // Actuator /health only on the API port. /actuator/info is NOT
                // permitted here — it can expose Git SHA, build info, env vars.
                // Full actuator access is on management port 8081 (application.yml).
                .requestMatchers("/actuator/health").permitAll()

                // -- Everything else: valid JWT required -----------------------
                // This is the explicit deny-all catch-all.
                // A new endpoint added anywhere in the codebase is LOCKED by default.
                // Access requires intentional addition to the allowlist above.
                .anyRequest().authenticated()
            )

            // ── ⑤ JWT FILTER ─────────────────────────────────────────────────
            // Inserted before UsernamePasswordAuthenticationFilter — the canonical
            // position for token-based authentication filters in the Spring chain.
            // The filter is idempotent (skips if SecurityContext already populated).
            .addFilterBefore(jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ── AuthenticationManager ─────────────────────────────────────────────────

    /**
     * Exposes {@link AuthenticationManager} as a Spring bean.
     *
     * <p>Required by {@code AuthService.login()} to call
     * {@code authManager.authenticate(new UsernamePasswordAuthenticationToken(...))}
     * during credential verification.
     *
     * <p>Spring Boot 3 / Security 6 change: {@code AuthenticationManager} is
     * no longer auto-exposed as a bean. It must be obtained explicitly from
     * {@link AuthenticationConfiguration}.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // ── HTTP Firewall ─────────────────────────────────────────────────────────

    /**
     * Explicit {@link StrictHttpFirewall} bean with each setting documented.
     *
     * <p>Spring Security's {@code WebSecurityConfiguration} picks up a bean named
     * {@code "httpFirewall"} automatically via convention. Declaring it explicitly:
     * <ul>
     *   <li>Makes every security decision visible at code review time.</li>
     *   <li>Prevents silent policy changes on Spring Security version upgrades.</li>
     *   <li>Provides rationale for each blocked URL pattern.</li>
     * </ul>
     *
     * <p>The firewall runs at the Servlet container boundary, before any
     * Spring MVC routing. Blocked requests never reach controller code.
     */
    @Bean
    public HttpFirewall httpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();

        // Semicolons in URLs enable session hijacking via ;jsessionid= attacks
        // and matrix variable abuse. No legitimate API path uses semicolons.
        firewall.setAllowSemicolon(false);

        // Backslashes are path separators on Windows. In URLs they can be used
        // to bypass path-based access rules on servers with Windows path normalization.
        firewall.setAllowBackSlash(false);

        // URL-encoded slashes (%2F) can bypass path traversal detection in
        // security filters that operate on decoded paths while routing uses raw paths.
        firewall.setAllowUrlEncodedSlash(false);

        // Double URL-encoded slashes (%252F, decoded to %2F) are a second-order
        // path traversal vector. Block at the firewall level.
        firewall.setAllowUrlEncodedDoubleSlash(false);

        // URL-encoded periods (%2E) are the canonical component of path traversal
        // sequences (../). Block to prevent filter-bypass via encoding.
        firewall.setAllowUrlEncodedPeriod(false);

        // Null bytes (%00) in URLs terminate C strings and can bypass filename
        // checks in underlying OS or library code.
        firewall.setAllowNull(false);

        return firewall;
    }
}