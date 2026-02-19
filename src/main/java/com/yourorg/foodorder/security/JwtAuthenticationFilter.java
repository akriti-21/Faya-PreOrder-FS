package com.foodorder.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Authentication Filter — runs once per HTTP request.
 *
 * Extends OncePerRequestFilter: Spring guarantees single execution per
 * request even across filter chain forwarding (important for /error routes).
 *
 * Responsibilities:
 *   1. Extract Bearer token from Authorization header
 *   2. Validate the token (signature, expiry, claims)
 *   3. Load UserDetails and set SecurityContext if token is valid
 *   4. On missing or invalid token: do nothing — pass through silently.
 *      The endpoint's security rule then decides whether to return 401.
 *      This design avoids the filter throwing 401 for public endpoints
 *      that don't require authentication.
 *
 * What this filter does NOT do:
 *   - Throw exceptions on missing/invalid tokens (downstream handles 401)
 *   - Write to the response body
 *   - Log passwords, tokens, or credentials
 *   - Cache authentication between requests (stateless — each request stands alone)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            String token = extractTokenFromRequest(request);

            if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
                String username = jwtTokenProvider.extractUsername(token);

                // Load UserDetails fresh from the database on each request.
                // This ensures revoked users or changed roles are reflected immediately.
                // Note: For high-throughput scenarios, consider short-lived tokens
                // over per-request DB lookups. Token revocation via blocklist (Redis)
                // is the recommended Day 2 addition.
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,                       // No credentials stored post-auth
                                userDetails.getAuthorities()
                        );

                // Attach request metadata (IP, session ID) to authentication details
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));

                // Set authentication in SecurityContext for this request's thread
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // Log auth event: user identity only — NEVER log the token value
                log.debug("Authenticated user: {}, URI: {}", username, request.getRequestURI());
            }

        } catch (Exception ex) {
            // Log the failure but do NOT stop the filter chain.
            // The request continues without authentication — endpoint security
            // rules will return 401 if the endpoint requires auth.
            log.warn("Could not set user authentication for request to {}: {}",
                    request.getRequestURI(), ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the Bearer token from the Authorization header.
     * Returns null if header is missing or not in Bearer format.
     *
     * We do NOT log the extracted token value here — ever.
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}