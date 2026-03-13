package com.yourorg.foodorder.security;

import com.yourorg.foodorder.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Security UserDetailsService — loads user credentials and authorities
 * from the database for JWT validation on every authenticated request.
 *
 * Replaces the Day 2 stub with a live implementation backed by UserRepository.
 *
 * Called by:
 *   1. JwtAuthenticationFilter — per authenticated request, extracts email
 *      from JWT, loads UserDetails, populates SecurityContext.
 *   2. DaoAuthenticationProvider (via AuthenticationManager) — during login,
 *      loads user by email, compares stored BCrypt hash against submitted password.
 *
 * @Transactional(readOnly=true):
 *   Opens a read-only transaction for the duration of the DB query.
 *   readOnly=true hints to PostgreSQL and Hibernate to skip dirty checking
 *   and use read-committed isolation, reducing overhead.
 *   The JOIN FETCH in UserRepository.findByEmail() loads roles in a single
 *   query within this transaction, avoiding LazyInitializationException.
 *
 * Security contract:
 *   - Returns SecurityPrincipal (wraps User) — not Spring's User builder.
 *   - The generic error message "User not found" prevents email enumeration
 *     — do NOT change the message to leak whether an account exists.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Email is normalised to lowercase at registration time.
        // Lowercasing here defends against case-sensitivity drift between
        // token generation and validation (e.g. "User@Example.com" in the JWT).
        String normalisedEmail = email.toLowerCase().trim();

        return userRepository.findByEmail(normalisedEmail)
                .map(SecurityPrincipal::new)
                .orElseThrow(() -> {
                    // Log at DEBUG — INFO would fill logs during credential-stuffing attacks
                    log.debug("Authentication attempt for unknown email: {}", normalisedEmail);
                    // Generic message — never reveal whether the email is registered
                    return new UsernameNotFoundException("User not found");
                });
    }
}