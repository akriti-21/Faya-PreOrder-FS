package com.foodorder.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password encoding configuration — intentionally isolated from
 * {@link SecurityConfig}.
 *
 * <h3>Why a dedicated class?</h3>
 * {@code SecurityConfig} depends on {@code JwtAuthenticationFilter}, which
 * depends on {@code UserDetailsService}. If {@code PasswordEncoder} were
 * defined inside {@code SecurityConfig}, and any auth service injected both
 * {@code SecurityConfig} beans and {@code PasswordEncoder}, Spring would
 * detect a circular dependency at startup. Isolating {@code PasswordEncoder}
 * in its own {@code @Configuration} class breaks that cycle entirely.
 *
 * <h3>Algorithm choice: BCrypt</h3>
 * BCrypt is the correct default for password storage:
 * <ul>
 *   <li><b>Adaptive cost</b>: the work factor can be increased as hardware
 *       improves, without invalidating existing hashes.</li>
 *   <li><b>Built-in salt</b>: each hash embeds a unique salt; rainbow-table
 *       attacks are infeasible even with a database breach.</li>
 *   <li><b>Intentionally slow</b>: brute-force cost is architecturally high.</li>
 * </ul>
 *
 * <h3>Cost factor 12</h3>
 * Spring's default is 10 (calibrated for 2011 hardware). Factor 12 is
 * approximately 4× slower than 10 and produces ~250–400 ms/hash on a modern
 * server — acceptable for an authentication endpoint. Benchmark your target
 * hardware and increase the factor when it drops below 200 ms.
 *
 * <p><b>Never use</b>: MD5, SHA-1, SHA-256 (raw), or plain text for passwords.
 * These are too fast, lack adaptive cost, and offer no built-in salting.
 */
@Configuration
public class PasswordEncoderConfig {

    /**
     * BCrypt encoder with cost factor 12.
     *
     * <p>Injection pattern:
     * <pre>{@code
     *   @Autowired
     *   private final PasswordEncoder passwordEncoder;
     *
     *   // Encode:  String hash   = passwordEncoder.encode(rawPassword);
     *   // Verify:  boolean match = passwordEncoder.matches(raw, hash);
     * }</pre>
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}