package com.foodorder.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * Strongly-typed binding for app.jwt.* properties.
 *
 * Architecture decision: Using @ConfigurationProperties instead of @Value
 * provides compile-safe property access, easy testability (just instantiate
 * and set fields), and a clear contract of what JWT config is required.
 *
 * The @PostConstruct validation enforces fail-fast startup: if JWT_SECRET
 * is missing or too short, the app refuses to start rather than running
 * with a weak or missing secret.
 */
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /**
     * Base64-encoded secret key. Must be at least 32 bytes (256 bits)
     * for HS256 signature algorithm. Loaded from JWT_SECRET env var.
     * Generate: openssl rand -base64 32
     */
    private String secret;

    /** Access token TTL in milliseconds. Default: 3600000 (1 hour) */
    private long expirationMs;

    /** Refresh token TTL in milliseconds. Default: 86400000 (24 hours) */
    private long refreshExpirationMs;

    /**
     * Startup validation: fail loudly if JWT configuration is missing or inadequate.
     * A missing secret would mean the app starts with no ability to sign tokens.
     * A too-short secret weakens HMAC security below acceptable thresholds.
     */
    @PostConstruct
    public void validate() {
        Assert.hasText(secret,
            "JWT secret is not configured. Set the JWT_SECRET environment variable.");
        // Base64: 32 bytes = ~44 characters. Enforce minimum meaningful length.
        Assert.isTrue(secret.length() >= 32,
            "JWT secret must be at least 32 characters (use openssl rand -base64 32).");
        Assert.isTrue(expirationMs > 0,
            "JWT expiration must be a positive value in milliseconds.");
    }

    // --- Getters and setters (no Lombok here: config properties need explicit setters) ---

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public long getExpirationMs() { return expirationMs; }
    public void setExpirationMs(long expirationMs) { this.expirationMs = expirationMs; }

    public long getRefreshExpirationMs() { return refreshExpirationMs; }
    public void setRefreshExpirationMs(long refreshExpirationMs) { this.refreshExpirationMs = refreshExpirationMs; }
}