package com.yourorg.foodorder.dto.response;

/**
 * Outbound DTO returned on successful login and registration.
 *
 * Fields:
 *   accessToken  — the signed JWT; clients send this as "Bearer <token>"
 *   tokenType    — always "Bearer" per RFC 6750
 *   expiresIn    — token TTL in seconds; clients use this to schedule
 *                  token refresh before expiry without parsing the JWT
 *   user         — safe user representation (no password)
 *
 * Why include expiresIn?
 *   Clients should not parse JWT claims themselves — the expiry is an
 *   implementation detail of the server. Exposing it here decouples the
 *   client from the JWT structure and lets the server change the claim
 *   format (e.g. switch to opaque tokens) transparently.
 *
 * refreshToken is intentionally absent in this iteration.
 * It will be added when a token revocation / blocklist mechanism is in place.
 */
public record AuthResponse(
    String       accessToken,
    String       tokenType,
    long         expiresIn,
    UserResponse user
) {

    /**
     * Convenience factory.
     *
     * @param accessToken  signed JWT string
     * @param expiresIn    TTL in seconds (not milliseconds — client convention)
     * @param user         safe user representation
     */
    public static AuthResponse of(String accessToken, long expiresIn, UserResponse user) {
        return new AuthResponse(accessToken, "Bearer", expiresIn, user);
    }
}