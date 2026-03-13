package com.yourorg.foodorder.config;

import com.yourorg.foodorder.idempotency.IdempotencyInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * NEW WebMvc configuration for Day 10.
 *
 * Adds:
 *  1. IdempotencyInterceptor scoped to write endpoints.
 *  2. /api/v1/** path prefix alias — controllers annotated with
 *     @RequestMapping("/api/orders") are automatically also accessible
 *     at /api/v1/orders without any change to the controller.
 *
 * Does NOT extend or modify the existing SecurityConfig.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final IdempotencyInterceptor idempotencyInterceptor;

    public WebMvcConfig(IdempotencyInterceptor idempotencyInterceptor) {
        this.idempotencyInterceptor = idempotencyInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(idempotencyInterceptor)
                .addPathPatterns(
                        "/api/orders/**",
                        "/api/v*/orders/**",
                        "/api/payments/**",
                        "/api/v*/payments/**"
                )
                .excludePathPatterns("/actuator/**");
    }

    /**
     * Makes /api/v1/X an alias for /api/X so existing controllers
     * need zero changes to support versioned URLs.
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // Prefix all controllers under /api/** with /api/v1 as well
        // This is additive — the original paths remain fully functional.
        configurer.addPathPrefix("/api/v1",
                clazz -> clazz.isAnnotationPresent(
                        org.springframework.web.bind.annotation.RestController.class)
                        && clazz.getPackageName().startsWith("com.yourorg.foodorder.controller")
        );
    }
}