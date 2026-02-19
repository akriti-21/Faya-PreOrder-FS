package com.foodorder.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password encoding configuration.
 *
 * Architecture decisions:
 *
 * 1. BCrypt is the correct choice for password storage:
 *    - Adaptive: cost factor can be increased as hardware gets faster
 *    - Built-in salt: eliminates rainbow table attacks without managing salt separately
 *    - Intentionally slow: brute-force cost is high by design
 *
 * 2. Strength 12 (not the Spring default of 10):
 *    - Strength 10 was specified when average hardware was much slower
 *    - Strength 12 provides meaningfully higher brute-force resistance on modern hardware
 *    - Still fast enough for login UX (typically 200-400ms — acceptable for auth)
 *    - Rule of thumb: increase strength when hardware allows hashing below 250ms
 *
 * 3. Registered as a @Bean in its own @Configuration class:
 *    - Avoids circular bean dependency if SecurityConfig itself needs PasswordEncoder
 *    - Separates concerns: encoding policy is independent of authentication flow
 *    - Easy to swap (e.g., to Argon2) by changing only this class
 *
 * 4. Never use: MD5, SHA-1, SHA-256 alone, or plain text. These are not
 *    suitable for passwords (too fast, no built-in salting/stretching).
 */
@Configuration
public class PasswordEncoderConfig {

    /**
     * BCrypt password encoder with cost factor 12.
     *
     * Inject this bean wherever password encoding or verification is needed:
     *   private final PasswordEncoder passwordEncoder;
     *
     * Usage:
     *   String hash = passwordEncoder.encode(rawPassword);
     *   boolean matches = passwordEncoder.matches(rawPassword, storedHash);
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}