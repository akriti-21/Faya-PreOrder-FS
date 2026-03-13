package com.yourorg.foodorder.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Set;

/**
 * Idempotency interceptor for mutating endpoints.
 *
 * How it works:
 *  1. Client sends  Idempotency-Key: <uuid>  header on POST requests.
 *  2. On first request  → mark key as PROCESSING in Redis, proceed normally.
 *  3. After response    → store the response body under the key.
 *  4. On duplicate      → return the cached response immediately (200/201).
 *
 * Redis key format : "idempotency:<key>"
 * TTL              : 24 hours
 *
 * Endpoints protected (configured in WebMvcConfig):
 *   POST /api/v*/orders/**
 *   POST /api/v*/payments/**
 */
@Component
public class IdempotencyInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyInterceptor.class);

    static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String KEY_PREFIX             = "idempotency:";
    static final String PROCESSING_SENTINEL    = "__PROCESSING__";
    static final Duration TTL                  = Duration.ofHours(24);

    private static final Set<String> PROTECTED_METHODS =
            Set.of(HttpMethod.POST.name(), HttpMethod.PUT.name(), HttpMethod.PATCH.name());

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper                  objectMapper;

    public IdempotencyInterceptor(RedisTemplate<String, Object> redisTemplate,
                                   ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper  = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        if (!PROTECTED_METHODS.contains(request.getMethod())) return true;

        String idempotencyKey = request.getHeader(HEADER_IDEMPOTENCY_KEY);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            // Key is optional — proceed without idempotency protection
            return true;
        }

        String redisKey = KEY_PREFIX + idempotencyKey;
        Object cached   = redisTemplate.opsForValue().get(redisKey);

        if (cached == null) {
            // First time — reserve the key
            Boolean set = redisTemplate.opsForValue()
                    .setIfAbsent(redisKey, PROCESSING_SENTINEL, TTL);
            if (Boolean.TRUE.equals(set)) {
                // Attach key to request for postHandle
                request.setAttribute(HEADER_IDEMPOTENCY_KEY, redisKey);
                return true;
            }
            // Race condition — another thread is processing the same key
            cached = PROCESSING_SENTINEL;
        }

        if (PROCESSING_SENTINEL.equals(cached)) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"success\":false,\"message\":\"Request is still being processed. Retry shortly.\"}"
            );
            return false;
        }

        // Cached response exists — replay it
        log.info("Idempotency cache HIT [key={}]", idempotencyKey);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("X-Idempotency-Replayed", "true");
        response.getWriter().write(cached.toString());
        return false;
    }

    /**
     * Store the response body after a successful write.
     * Called by IdempotencyResponseWrapper (see below).
     */
    public void storeResponse(String redisKey, String responseBody) {
        try {
            redisTemplate.opsForValue().set(redisKey, responseBody, TTL);
            log.debug("Idempotency response stored [key={}]", redisKey);
        } catch (Exception e) {
            log.warn("Failed to store idempotency response [key={}]: {}", redisKey, e.getMessage());
        }
    }
}