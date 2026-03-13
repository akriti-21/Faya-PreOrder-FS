package com.yourorg.foodorder.util;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects inbound requests whose Content-Length exceeds MAX_BYTES (1 MB).
 *
 * Runs early at Order(0) to short-circuit oversized payloads before any
 * deserialization or business logic occurs.
 */
@Component
@Order(0)
public class RequestSizeFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestSizeFilter.class);

    /** 1 MB */
    private static final long MAX_BYTES = 1024L * 1024L;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        int contentLength = request.getContentLength();

        if (contentLength > MAX_BYTES) {
            log.warn("Request rejected — payload too large [uri={} size={}B limit={}B]",
                    request.getRequestURI(), contentLength, MAX_BYTES);
            response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"success\":false,\"message\":\"Request payload exceeds the 1 MB limit.\"}"
            );
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only apply to requests with a body
        String method = request.getMethod();
        return "GET".equals(method) || "DELETE".equals(method) || "HEAD".equals(method);
    }
}
