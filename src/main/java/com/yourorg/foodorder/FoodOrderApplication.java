package com.foodorder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entry point — intentionally minimal.
 *
 * @SpringBootApplication combines:
 *   @Configuration       — marks this as a bean source
 *   @EnableAutoConfiguration — activates Spring Boot auto-wiring
 *   @ComponentScan       — scans com.foodorder.** sub-packages
 *
 * No beans or logic here. Configuration is distributed to dedicated
 * @Configuration classes in com.foodorder.config to follow SRP.
 */
@SpringBootApplication
public class FoodOrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(FoodOrderApplication.class, args);
    }
}