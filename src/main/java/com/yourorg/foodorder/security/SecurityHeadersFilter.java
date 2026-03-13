package com.yourorg.foodorder.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Adds OWASP-recommended HTTP security headers to every response.
 *
 * Order(1) — runs immediately after RequestSizeFilter(0).
 *
 * Note: Spring Security's built-in header support (HeadersConfigurer) handles
 * many of these automatically when SecurityConfig is active. This filter adds
 * any headers not covered by the existing SecurityConfig without touching it.
 */
@Component
@Order(1)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        // Prevent MIME-type sniffing
        response.setHeader("X-Content-Type-Options", "nosniff");

        // Disallow framing (clickjacking protection)
        response.setHeader("X-Frame-Options", "DENY");

        // Legacy XSS filter for older browsers
        response.setHeader("X-XSS-Protection", "1; mode=block");

        // Enforce HTTPS for 1 year, include subdomains
        response.setHeader("Strict-Transport-Security",
                "max-age=31536000; includeSubDomains; preload");

        // Prevent information leakage via Referrer
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // Restrict browser feature access
        response.setHeader("Permissions-Policy",
                "geolocation=(), microphone=(), camera=()");

        // Basic Content-Security-Policy — tighten per environment
        response.setHeader("Content-Security-Policy",
                "default-src 'self'; frame-ancestors 'none'");

        chain.doFilter(request, response);
    }
}