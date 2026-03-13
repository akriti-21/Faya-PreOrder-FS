package com.yourorg.foodorder.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Enriches /actuator/info with application metadata.
 *
 * Build version is populated at compile time via Maven resource filtering
 * or the spring-boot-maven-plugin build-info goal.
 */
@Configuration
public class AppInfoConfig {

    @Value("${spring.application.name:foodorder-api}")
    private String applicationName;

    @Value("${info.application.version:unknown}")
    private String version;

    @Value("${info.environment.name:local}")
    private String environment;

    @Bean
    public InfoContributor customInfoContributor() {
        return (Info.Builder builder) -> {
            Map<String, Object> app = new LinkedHashMap<>();
            app.put("name",        applicationName);
            app.put("version",     version);
            app.put("environment", environment);
            app.put("startedAt",   Instant.now().toString());

            Map<String, Object> support = new LinkedHashMap<>();
            support.put("docs",    "https://docs.yourorg.com/foodorder");
            support.put("contact", "platform-team@yourorg.com");

            builder.withDetail("application", app)
                   .withDetail("support",     support);
        };
    }
}