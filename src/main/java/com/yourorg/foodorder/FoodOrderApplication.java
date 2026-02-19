package com.foodorder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Application entry point.
 *
 * @SpringBootApplication is a meta-annotation combining:
 *   - @Configuration: marks this as a source of bean definitions
 *   - @EnableAutoConfiguration: activates Spring Boot's auto-wiring
 *   - @ComponentScan: scans com.foodorder and all sub-packages
 *
 * Architecture note: No business logic here. This class is intentionally
 * minimal. Configuration is distributed to dedicated @Configuration classes
 * to follow the Single Responsibility Principle and keep this class readable.
 */
@SpringBootApplication
public class FoodOrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(FoodOrderApplication.class, args);
    }
}