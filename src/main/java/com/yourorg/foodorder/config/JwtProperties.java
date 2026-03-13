package com.yourorg.foodorder.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.Base64;

/**
 * Strongly-typed binding for {@code app.jwt.*} configuration properties.
 *
 * <h2>Why @ConfigurationProperties over @Value?</h2>
 * <ul>
 *   <li>Groups all JWT config under one auditable object — easy to find in code review.</li>
 *   <li>IDE auto-complete via {@code spring-boot-configuration-processor}.</li>
 *   <li>Trivially unit-testable: instantiate, set fields, call {@link #validate()}.</li>
 *   <li>Type conversion is handled by Spring Binder — no parsing code in this class.</li>
 * </ul>
 *
 * <h2>Fail-fast startup validation</h2>
 * {@link #validate()} runs via {@code @PostConstruct}. Any misconfiguration
 * aborts startup with a clear, actionable error message before the application
 * serves a single request. This is the correct "fail fast" posture for security
 * configuration — a server with a missing JWT secret must not start.
 *
 * <h2>Key encoding requirement</h2>
 * The secret MUST be Base64-encoded. Provide raw strings will fail validation.
 * Minimum decoded length is 32 bytes (256 bits) — the floor for HS256.
 *
 * <p>Generate a compliant secret:
 * <pre>openssl rand -base64 32</pre>
 *
 * <h2>Bean registration</h2>
 * Registered as a Spring bean via {@code @Component}. The {@code @Validated}
 * annotation activates JSR-380 (Bean Validation) processing on this class,
 * enabling {@code @NotNull}, {@code @Min} etc. on fields if needed in future.
 * Current validation is performed imperatively in {@link #validate()} for
 * richer error messages with remediation steps.
 */
@Component
@ConfigurationProperties(prefix = "app.jwt")
@Validated
public class JwtProperties {

    /**
     * Base64-encoded HMAC secret.
     *
     * <p>Source: {@code JWT_SECRET} environment variable. Never hardcode.
     * <p>Minimum: decodes to ≥ 32 bytes (256 bits) for HS256.
     * <p>Generate: {@code openssl rand -base64 32}
     */
    private String secret;

    /**
     * Access token TTL in milliseconds.
     *
     * <p>Default: 3 600 000 ms (1 hour). Must be positive and ≤ 24 hours.
     * Long-lived access tokens increase the blast radius of a leaked token.
     */
    private long expirationMs;

    /**
     * Refresh token TTL in milliseconds.
     *
     * <p>Default: 86 400 000 ms (24 hours). Must be positive and ≥ expirationMs.
     * Refresh tokens should be stored server-side for rotation and revocation.
     */
    private long refreshExpirationMs;

    // ── Startup validation ────────────────────────────────────────────────────

    /**
     * Validates all JWT configuration at application startup.
     *
     * <p>Checks (in order):
     * <ol>
     *   <li>Secret is present and non-blank.</li>
     *   <li>Secret is valid Base64. Validated here — not inside JwtTokenProvider —
     *       so the error message names the configuration property, not a deep
     *       internal class.</li>
     *   <li>Decoded secret is ≥ 32 bytes. A 32-CHARACTER Base64 string decodes to
     *       only ~24 bytes — insufficient for HS256. This check operates on decoded
     *       bytes, not the raw string length, which is a common implementation
     *       mistake.</li>
     *   <li>Access token TTL is positive and ≤ 24 hours.</li>
     *   <li>Refresh token TTL is positive and ≥ access token TTL.</li>
     * </ol>
     *
     * @throws IllegalStateException if any rule is violated, with a message
     *         that identifies the property and the corrective action
     */
    @PostConstruct
    public void validate() {

        // ── 1. Presence ───────────────────────────────────────────────────────
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "[Security] JWT secret is not configured. " +
                "Set JWT_SECRET environment variable. " +
                "Generate one with: openssl rand -base64 32");
        }

        // ── 2. Base64 validity ────────────────────────────────────────────────
        // Validated here so the error names 'app.jwt.secret' not an internal class.
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                "[Security] app.jwt.secret (JWT_SECRET) is not valid Base64. " +
                "Generate a valid secret with: openssl rand -base64 32", ex);
        }

        // ── 3. Key length in BYTES (not characters) ───────────────────────────
        // 32 Base64 characters = ~24 raw bytes — below HS256 minimum.
        // This check is on decoded bytes, not the Base64 string length.
        if (keyBytes.length < 32) {
            throw new IllegalStateException(String.format(
                "[Security] app.jwt.secret decodes to %d bytes; minimum is 32 bytes (256 bits) " +
                "for HS256. The Base64 string must encode at least 32 random bytes. " +
                "Generate a valid secret: openssl rand -base64 32",
                keyBytes.length));
        }

        // ── 4. Access token TTL ───────────────────────────────────────────────
        if (expirationMs <= 0) {
            throw new IllegalStateException(
                "[Security] app.jwt.expiration-ms must be a positive number. " +
                "Default: 3600000 (1 hour). Set via JWT_EXPIRATION_MS.");
        }
        if (expirationMs > 86_400_000L) {
            throw new IllegalStateException(
                "[Security] app.jwt.expiration-ms exceeds 24 hours (86400000 ms). " +
                "Long-lived access tokens increase breach exposure. " +
                "Use shorter access tokens with refresh token rotation.");
        }

        // ── 5. Refresh token TTL ──────────────────────────────────────────────
        if (refreshExpirationMs <= 0) {
            throw new IllegalStateException(
                "[Security] app.jwt.refresh-expiration-ms must be positive. " +
                "Default: 86400000 (24 hours). Set via JWT_REFRESH_EXPIRATION_MS.");
        }
        if (refreshExpirationMs < expirationMs) {
            throw new IllegalStateException(
                "[Security] app.jwt.refresh-expiration-ms must be >= expiration-ms. " +
                "A refresh token that expires before the access token it renews is invalid.");
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────
    // Explicit getters/setters — Lombok @Data can interfere with @ConfigurationProperties
    // binding in edge cases (null defaults, setter naming conventions).

    public String getSecret()                { return secret; }
    public void   setSecret(String v)        { this.secret = v; }

    public long   getExpirationMs()          { return expirationMs; }
    public void   setExpirationMs(long v)    { this.expirationMs = v; }

    public long   getRefreshExpirationMs()   { return refreshExpirationMs; }
    public void   setRefreshExpirationMs(long v) { this.refreshExpirationMs = v; }
}