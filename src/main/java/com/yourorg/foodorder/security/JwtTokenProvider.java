package com.yourorg.foodorder.security;

import com.yourorg.foodorder.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * JWT token lifecycle management using JJWT 0.12+.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Access token generation with {@code jti} claim</li>
 *   <li>Refresh token generation</li>
 *   <li>Token signature and expiry validation</li>
 *   <li>Claims extraction (username, jti)</li>
 * </ul>
 *
 * <h2>Public API surface — intentionally minimal</h2>
 * <pre>
 *   generateAccessToken(UserDetails)   → AuthService (login, refresh)
 *   generateRefreshToken(UserDetails)  → AuthService (login)
 *   validateToken(String)              → JwtAuthenticationFilter
 *   extractUsername(String)            → JwtAuthenticationFilter
 *   extractJti(String)                 → TokenBlocklistService (Day 2)
 * </pre>
 *
 * All other methods are {@code private}. In particular:
 * <ul>
 *   <li>{@code isTokenExpired()} is private — {@link #validateToken} is the
 *       single public validation entry point, always returning boolean.</li>
 *   <li>{@code parseToken()} is private — all parse exceptions are caught at
 *       the {@link #validateToken} and {@link #extractUsername} boundary.</li>
 * </ul>
 *
 * <h2>JJWT 0.12 API changes from 0.11</h2>
 * <ul>
 *   <li>{@code Jwts.parserBuilder()} → {@code Jwts.parser()}</li>
 *   <li>{@code .setSigningKey()} → {@code .verifyWith(SecretKey)}</li>
 *   <li>{@code .parseClaimsJws()} → {@code .parseSignedClaims()}</li>
 *   <li>{@code .getBody()} → {@code .getPayload()}</li>
 * </ul>
 *
 * <h2>Key derivation</h2>
 * Base64-decoded secret bytes are wrapped in an HMAC key via
 * {@link Keys#hmacShaKeyFor(byte[])}. Algorithm (HS256/384/512) is inferred
 * from key length. {@link JwtProperties#validate()} guarantees ≥ 32 decoded
 * bytes before this constructor runs — no redundant check here.
 *
 * <h2>Security invariants</h2>
 * <ul>
 *   <li>Token value is <b>never</b> logged — only subject/jti are logged.</li>
 *   <li>Signing key is {@code private final} — never exposed outside this class.</li>
 *   <li>Token revocation hook: call {@link #extractJti} then check a Redis blocklist
 *       inside {@link #validateToken} when logout/forced-expiry is required.</li>
 * </ul>
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final long      expirationMs;
    private final long      refreshExpirationMs;

    /**
     * Constructor injection — all fields are final; class is immutable after construction.
     *
     * <p>{@link JwtProperties#validate()} runs via {@code @PostConstruct} and is
     * guaranteed to complete before this constructor executes (Spring resolves the
     * {@code jwtProperties} bean before this bean). The secret is valid Base64 and
     * decodes to ≥ 32 bytes by the time we reach this line.
     */
    public JwtTokenProvider(JwtProperties props) {
        byte[] keyBytes         = Base64.getDecoder().decode(props.getSecret());
        this.signingKey         = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs       = props.getExpirationMs();
        this.refreshExpirationMs = props.getRefreshExpirationMs();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Generates a signed JWT access token.
     *
     * <p>Claims included:
     * <ul>
     *   <li>{@code sub} — email/username from {@code userDetails.getUsername()}</li>
     *   <li>{@code jti} — UUID, unique per token. Required for revocation blocklist.
     *       Store this value server-side when implementing logout.</li>
     *   <li>{@code iat} — issued-at timestamp (millisecond precision)</li>
     *   <li>{@code exp} — expiry timestamp</li>
     * </ul>
     *
     * <p>Roles are intentionally absent from the token. They are loaded fresh from
     * the database on every authenticated request by {@link JwtAuthenticationFilter},
     * ensuring role changes and account suspensions take effect immediately without
     * requiring token re-issue.
     *
     * @param userDetails the authenticated user principal
     * @return compact, URL-safe signed JWT (do not log this value)
     */
    public String generateAccessToken(UserDetails userDetails) {
        return buildToken(
            Map.of("jti", UUID.randomUUID().toString()),
            userDetails.getUsername(),
            expirationMs
        );
    }

    /**
     * Generates a signed JWT refresh token.
     *
     * <p>Extra claims:
     * <ul>
     *   <li>{@code type: "refresh"} — distinguishes refresh tokens from access
     *       tokens. AuthService should reject access token use as refresh token.</li>
     *   <li>{@code jti} — unique ID for blocklist-based revocation on logout.</li>
     * </ul>
     *
     * <p>Refresh tokens should be persisted server-side (DB or Redis) to enable
     * rotation and single-use enforcement.
     *
     * @param userDetails the authenticated user principal
     * @return compact, URL-safe signed JWT (do not log this value)
     */
    public String generateRefreshToken(UserDetails userDetails) {
        return buildToken(
            Map.of("type", "refresh", "jti", UUID.randomUUID().toString()),
            userDetails.getUsername(),
            refreshExpirationMs
        );
    }

    /**
     * Validates a token's signature and expiry in one atomic operation.
     *
     * <p>This is the <b>single public validation entry point</b>. Always returns
     * {@code boolean} — never throws. Failure reasons are logged server-side only:
     * <ul>
     *   <li>Expired: WARN with subject claim (audit trail)</li>
     *   <li>Malformed/tampered: WARN with exception type only (no token content)</li>
     *   <li>Null/empty: WARN</li>
     * </ul>
     *
     * <p><b>Revocation hook</b> — add blocklist check here when ready:
     * <pre>{@code
     *   if (!jwtTokenProvider.validateToken(token)) return false;
     *   String jti = jwtTokenProvider.extractJti(token);
     *   if (tokenBlocklist.isRevoked(jti)) return false;
     *   return true;
     * }</pre>
     *
     * @param token raw JWT from the Authorization header
     * @return {@code true} if signature valid and not expired; {@code false} otherwise
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException ex) {
            // Log subject for audit — but never the token value itself
            log.warn("JWT expired: subject={}, jti={}",
                ex.getClaims().getSubject(),
                ex.getClaims().get("jti", String.class));
        } catch (JwtException ex) {
            // JwtException covers: UnsupportedJwtException, MalformedJwtException,
            // SignatureException. Log exception type only — token content must not
            // appear in logs.
            log.warn("JWT validation failed: {}", ex.getClass().getSimpleName());
        } catch (IllegalArgumentException ex) {
            log.warn("JWT is null, empty, or whitespace-only");
        }
        return false;
    }

    /**
     * Extracts the username (subject claim) from a validated token.
     *
     * <p>Contract: callers ({@link JwtAuthenticationFilter}) always call
     * {@link #validateToken} first and only reach this method on success.
     * The {@link ExpiredJwtException} catch is a defensive fallback —
     * JJWT throws on expired tokens even for claim extraction.
     *
     * @param token a JWT that has passed {@link #validateToken}
     * @return username/email from the subject claim; {@code null} on any parse failure
     */
    public String extractUsername(String token) {
        try {
            return extractClaim(token, Claims::getSubject);
        } catch (ExpiredJwtException ex) {
            // Defensive: validateToken() should prevent this path in normal flow.
            // Claims are accessible from the exception even on expiry.
            log.debug("Extracting subject from expired token (defensive fallback)");
            return ex.getClaims().getSubject();
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Failed to extract username from token: {}", ex.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * Extracts the JWT ID ({@code jti}) claim from a validated token.
     *
     * <p>The {@code jti} is a UUID set at token generation time. Use this value
     * as the key in a Redis revocation blocklist to implement logout or
     * forced token invalidation without waiting for the token to expire.
     *
     * <p>Example blocklist integration inside {@link #validateToken}:
     * <pre>{@code
     *   String jti = extractJti(token);
     *   if (jti != null && tokenBlocklistService.isRevoked(jti)) return false;
     * }</pre>
     *
     * @param token a JWT that has passed {@link #validateToken}
     * @return the jti UUID string; {@code null} if claim absent or parse failed
     */
    public String extractJti(String token) {
        try {
            return extractClaim(token, claims -> claims.get("jti", String.class));
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Failed to extract jti from token: {}", ex.getClass().getSimpleName());
            return null;
        }
    }

    // ── Private implementation ────────────────────────────────────────────────

    /**
     * Builds and signs a JWT token.
     *
     * <p>Uses a single {@code System.currentTimeMillis()} snapshot as the base
     * for both {@code iat} and {@code exp} to avoid a race where two successive
     * {@code new Date()} calls straddle a millisecond boundary.
     *
     * <p>SECURITY: the returned compact string is a bearer credential.
     * It MUST NOT be logged anywhere in the call chain — not here, not in callers.
     */
    private String buildToken(Map<String, Object> extraClaims, String subject, long ttlMs) {
        long nowMs = System.currentTimeMillis();   // single clock snapshot
        Date issuedAt = new Date(nowMs);
        Date expiry   = new Date(nowMs + ttlMs);

        Map<String, Object> claims = new HashMap<>(extraClaims);

        return Jwts.builder()
            .claims(claims)
            .subject(subject)
            .issuedAt(issuedAt)
            .expiration(expiry)
            .signWith(signingKey)   // JJWT 0.12: algorithm inferred from key length
            .compact();
    }

    /**
     * Parses and verifies a signed JWT, returning the claims payload.
     *
     * <p>Throws JJWT-specific exceptions on any failure. Private — all call sites
     * are inside this class and handle exceptions appropriately.
     *
     * <p>JJWT 0.12 parser API:
     * <ul>
     *   <li>{@code .parser()} — not {@code .parserBuilder()}</li>
     *   <li>{@code .verifyWith(SecretKey)} — not {@code .setSigningKey()}</li>
     *   <li>{@code .parseSignedClaims()} — not {@code .parseClaimsJws()}</li>
     *   <li>{@code .getPayload()} — not {@code .getBody()}</li>
     * </ul>
     */
    private Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(parseToken(token));
    }

    /**
     * Internal expiry check. Private — external callers use {@link #validateToken}.
     *
     * <p>JJWT throws {@link ExpiredJwtException} from {@link #parseToken} before
     * we can read {@code Claims::getExpiration}, so we catch it and return true.
     */
    private boolean isTokenExpired(String token) {
        try {
            return extractClaim(token, Claims::getExpiration).before(new Date());
        } catch (ExpiredJwtException ex) {
            return true;
        }
    }
}