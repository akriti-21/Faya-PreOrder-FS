package com.yourorg.foodorder.config;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Separate security filter chain (order 1) for /actuator/** endpoints.
 * Runs before the main application security chain.
 *
 *  /actuator/health  → public
 *  /actuator/info    → public
 *  /actuator/metrics → ADMIN only
 *  /actuator/prometheus → ADMIN only
 */
@Configuration
public class ActuatorSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(EndpointRequest.toAnyEndpoint())
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers(EndpointRequest.to(HealthEndpoint.class, InfoEndpoint.class))
                    .permitAll()
                // Everything else (metrics, prometheus, env, etc.) → ADMIN
                .anyRequest()
                    .hasRole("ADMIN")
            )
            .csrf(csrf -> csrf.disable())
            // Actuator uses HTTP Basic by default if JWT filter is not applied here
            .httpBasic(org.springframework.security.config.Customizer.withDefaults());

        return http.build();
    }
}