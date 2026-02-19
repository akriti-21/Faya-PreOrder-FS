package com.foodorder.security;

import com.foodorder.config.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

/**
 * JWT token lifecycle management.
 *
 * Responsibilities:
 *   - Token generation (access + refresh)
 *   - Token validation (signature + expiry)
 *   - Claims extraction
 *
 * Architecture decisions:
 *
 * 1. Key derivation: The secret string from JwtProperties is Base64-decoded
 *    and wrapped in an HMAC-SHA key. Keys.hmacShaKeyFor() selects the
 *    appropriate algorithm (HS256/384/512) based on key length automatically.
 *
 * 2. NEVER log the key, secret, or full token string. Only log the subject
 *    claim (username) and expiry for debugging.
 *
 * 3. Token revocation is NOT implemented here (Day 2 concern: Redis blocklist).
 *    Architecture hook: validateToken() calls an injectable TokenBlocklistService.
 *    Add that service when refresh token rotation or logout-invalidation is needed.
 *
 * 4. Claims are minimal: sub (subject/username) + iat + exp.
 *    Add roles/authorities as claims cautiously — they become stale if changed.
 *    Re-fetch from DB on each request (done in JwtAuthenticationFilter) is safer.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final long expirationMs;
    private final long refreshExpirationMs;

    // Constructor injection — no field injection.
    // Benefits: immutable after construction, explicit dependencies, testable.
    public JwtTokenProvider(JwtProperties jwtProperties) {
        // Decode the Base64 secret and build an HMAC key
        byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.getSecret());
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = jwtProperties.getExpirationMs();
        this.refreshExpirationMs = jwtProperties.getRefreshExpirationMs();
    }

    /**
     * Generates a signed access token for the given user.
     * Claims included: sub (username), iat (issued at), exp (expiry).
     */
    public String generateAccessToken(UserDetails userDetails) {
        return buildToken(Map.of(), userDetails.getUsername(), expirationMs);
    }

    /**
     * Generates a refresh token with longer expiry.
     * Refresh tokens should be stored server-side (DB or Redis) for rotation.
     */
    public String generateRefreshToken(UserDetails userDetails) {
        return buildToken(Map.of("type", "refresh"), userDetails.getUsername(), refreshExpirationMs);
    }

    /**
     * Validates token: checks signature integrity and expiry.
     * Does NOT check revocation list — add that as a delegated call here when ready.
     *
     * Returns false (not throws) on any failure — callers decide how to handle.
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException ex) {
            log.warn("JWT token expired: subject={}", ex.getClaims().getSubject());
        } catch (UnsupportedJwtException ex) {
            log.warn("JWT token unsupported algorithm");
        } catch (MalformedJwtException ex) {
            log.warn("JWT token malformed");
        } catch (SecurityException ex) {
            log.warn("JWT signature validation failed");
        } catch (IllegalArgumentException ex) {
            log.warn("JWT token is empty or null");
        }
        return false;
    }

    /**
     * Extracts the username (subject claim) from a token.
     * Callers must call validateToken() first.
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // ---- Private helpers ----

    private String buildToken(Map<String, Object> extraClaims, String subject, long ttlMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ttlMs);

        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
        // NOTE: Never log the returned token string
    }

    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(parseToken(token));
    }
}