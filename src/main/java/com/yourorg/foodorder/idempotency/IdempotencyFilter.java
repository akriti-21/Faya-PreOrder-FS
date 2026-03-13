package com.yourorg.foodorder.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

/**
 * Wraps the response in a ContentCachingResponseWrapper so the
 * IdempotencyInterceptor can read and store the response body after
 * the controller has written it.
 *
 * Order(4) — runs after RateLimitFilter(3).
 */
@Component
@Order(4)
public class IdempotencyFilter extends OncePerRequestFilter {

    private final IdempotencyInterceptor interceptor;

    public IdempotencyFilter(IdempotencyInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String idempotencyKey = (String) request.getAttribute(IdempotencyInterceptor.HEADER_IDEMPOTENCY_KEY);

        if (idempotencyKey == null) {
            chain.doFilter(request, response);
            return;
        }

        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request, wrapper);
        } finally {
            byte[] body = wrapper.getContentAsByteArray();
            if (body.length > 0 && wrapper.getStatus() < 400) {
                interceptor.storeResponse(idempotencyKey, new String(body));
            }
            wrapper.copyBodyToResponse();
        }
    }
}