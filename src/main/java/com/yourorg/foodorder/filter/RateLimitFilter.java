package com.yourorg.foodorder.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Sliding-window rate limiter backed by Redis.
 *
 * Strategy:
 *  - Key  = "rate_limit:<identifier>" (identifier = userId from JWT header claim, else IP)
 *  - Value = request count within the current window
 *  - TTL   = WINDOW_SECONDS (resets the window on first request of each period)
 *
 * Limits: 100 requests / 60 seconds per identity.
 * Returns 429 with Retry-After header when exceeded.
 *
 * Order(3) runs after RequestLoggingFilter(1) and MetricsFilter(2).
 */
@Component
@Order(3)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final int    MAX_REQUESTS    = 100;
    private static final long   WINDOW_SECONDS  = 60L;
    private static final String KEY_PREFIX      = "rate_limit:";

    private final RedisTemplate<String, Object> redisTemplate;

    public RateLimitFilter(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String identifier = resolveIdentifier(request);
        String redisKey   = KEY_PREFIX + identifier;

        Long count = redisTemplate.opsForValue().increment(redisKey);

        if (count == null) {
            // Redis unavailable — fail open (don't block traffic)
            chain.doFilter(request, response);
            return;
        }

        if (count == 1) {
            // First request in this window — set expiry
            redisTemplate.expire(redisKey, Duration.ofSeconds(WINDOW_SECONDS));
        }

        long remaining = Math.max(0, MAX_REQUESTS - count);
        response.setHeader("X-RateLimit-Limit",     String.valueOf(MAX_REQUESTS));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Window",    WINDOW_SECONDS + "s");

        if (count > MAX_REQUESTS) {
            Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
            response.setHeader("Retry-After", String.valueOf(ttl != null ? ttl : WINDOW_SECONDS));
            sendError(response, HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded. Max " + MAX_REQUESTS + " requests per " + WINDOW_SECONDS + "s.");
            log.warn("Rate limit exceeded [identifier={} count={}]", identifier, count);
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Prefers userId extracted from the Authorization header's JWT subject claim.
     * Falls back to client IP address.
     */
    private String resolveIdentifier(HttpServletRequest request) {
        // Extract "sub" from JWT without a full parse — header is already validated by JwtAuthFilter
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try {
                String payload = auth.split("\\.")[1];
                String decoded = new String(java.util.Base64.getUrlDecoder().decode(payload));
                // Fast substring extraction avoids ObjectMapper dependency
                int subIdx = decoded.indexOf("\"sub\":\"");
                if (subIdx >= 0) {
                    int start = subIdx + 7;
                    int end   = decoded.indexOf('"', start);
                    if (end > start) return "user:" + decoded.substring(start, end);
                }
            } catch (Exception ignored) {
                // Malformed token — fall through to IP
            }
        }
        return "ip:" + getClientIp(request);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void sendError(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"success\":false,\"message\":\"" + message + "\"}"
        );
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator");
    }
}