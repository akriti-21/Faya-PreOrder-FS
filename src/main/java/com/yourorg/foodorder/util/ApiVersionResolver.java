package com.yourorg.foodorder.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the API version from inbound requests.
 *
 * Resolution order:
 *  1. URI path segment  → /api/v1/orders  (preferred, canonical)
 *  2. Accept header     → application/vnd.foodorder.v1+json
 *  3. X-API-Version     → custom header fallback
 *  4. Default           → "v1"
 *
 * Controllers remain backward-compatible: a request to /api/orders
 * resolves to v1 automatically, while /api/v2/orders maps to v2.
 */
@Component
public class ApiVersionResolver {

    private static final Pattern URI_VERSION_PATTERN =
            Pattern.compile("^/api/(v\\d+)/.*$");

    private static final Pattern ACCEPT_VERSION_PATTERN =
            Pattern.compile("application/vnd\\.foodorder\\.(v\\d+)\\+json");

    public static final String DEFAULT_VERSION = "v1";

    /**
     * Returns the resolved API version string (e.g. "v1", "v2").
     */
    public String resolve(HttpServletRequest request) {
        // 1. URI path
        String uri = request.getRequestURI();
        Matcher uriMatcher = URI_VERSION_PATTERN.matcher(uri);
        if (uriMatcher.matches()) {
            return uriMatcher.group(1);
        }

        // 2. Accept header
        String accept = request.getHeader("Accept");
        if (accept != null) {
            Matcher acceptMatcher = ACCEPT_VERSION_PATTERN.matcher(accept);
            if (acceptMatcher.find()) {
                return acceptMatcher.group(1);
            }
        }

        // 3. X-API-Version header
        String headerVersion = request.getHeader("X-API-Version");
        if (headerVersion != null && headerVersion.matches("v\\d+")) {
            return headerVersion;
        }

        return DEFAULT_VERSION;
    }

    /**
     * Convenience: returns the integer version number from a resolved version string.
     * e.g. "v2" → 2
     */
    public int resolveAsInt(HttpServletRequest request) {
        String version = resolve(request);
        try {
            return Integer.parseInt(version.substring(1));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * Builds a versioned base path for use in controller mappings.
     * e.g. basePath("orders") → "/api/v1/orders"
     */
    public static String basePath(String resource) {
        return "/api/" + DEFAULT_VERSION + "/" + resource;
    }
}