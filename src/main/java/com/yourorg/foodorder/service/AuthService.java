package com.yourorg.foodorder.service;

import com.yourorg.foodorder.config.JwtProperties;
import com.yourorg.foodorder.domain.Role;
import com.yourorg.foodorder.domain.User;
import com.yourorg.foodorder.dto.request.LoginRequest;
import com.yourorg.foodorder.dto.request.RegisterRequest;
import com.yourorg.foodorder.dto.response.AuthResponse;
import com.yourorg.foodorder.dto.response.UserResponse;
import com.yourorg.foodorder.exception.BusinessException;
import com.yourorg.foodorder.repository.RoleRepository;
import com.yourorg.foodorder.repository.UserRepository;
import com.yourorg.foodorder.security.JwtTokenProvider;
import com.yourorg.foodorder.security.SecurityPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication service — registration and login orchestration.
 *
 * Responsibilities:
 *   registerUser() — validate uniqueness, hash password, persist user, issue token
 *   loginUser()    — delegate credential check to Spring Security, issue token
 *
 * Exception strategy:
 *
 *   Registration failures:
 *     Duplicate email → BusinessException (409 CONFLICT) via GlobalExceptionHandler.
 *     Missing role seed → IllegalStateException (500) — configuration error, not user error.
 *
 *   Login failures:
 *     Wrong credentials / disabled / locked account → Spring Security's
 *     AuthenticationException subtype (BadCredentialsException, DisabledException, etc.)
 *     is allowed to propagate UNCAUGHT from loginUser(). GlobalExceptionHandler
 *     maps AuthenticationException → 401 UNAUTHORIZED. This is intentional:
 *       - No catch-and-rethrow avoids wrapping to BusinessException (which is 409).
 *       - The 401 mapping lives in one place (GlobalExceptionHandler), not scattered
 *         across every service that calls AuthenticationManager.
 *       - Spring Security publishes AuthenticationFailureBadCredentialsEvent for audit.
 *
 * Why delegate login to AuthenticationManager?
 *   Direct BCrypt comparison would bypass Spring Security's account status checks
 *   (disabled, locked, expired) and event publication. AuthenticationManager runs
 *   the full DaoAuthenticationProvider chain, ensuring consistent security semantics.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String DEFAULT_ROLE = "ROLE_USER";

    private final UserRepository        userRepository;
    private final RoleRepository        roleRepository;
    private final PasswordEncoder       passwordEncoder;
    private final JwtTokenProvider      jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final JwtProperties         jwtProperties;

    /**
     * Registers a new user account and returns an access token.
     *
     * Flow:
     *   1. Normalise email to lowercase (canonical form throughout the system).
     *   2. Reject duplicates before insert — cleaner 409 than constraint violation.
     *   3. Hash password with BCrypt(12).
     *   4. Assign ROLE_USER from DB (fail-fast if V2 migration has not run).
     *   5. Persist user.
     *   6. Issue JWT — user is immediately authenticated post-registration.
     *
     * @param request validated RegisterRequest
     * @return AuthResponse with JWT and safe UserResponse
     * @throws BusinessException (409) if email already registered
     */
    @Transactional
    public AuthResponse registerUser(RegisterRequest request) {
        String normalisedEmail = request.email().toLowerCase().trim();

        if (userRepository.existsByEmail(normalisedEmail)) {
            throw new BusinessException(
                "An account with this email address already exists.");
        }

        Role defaultRole = roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                    "Role '" + DEFAULT_ROLE + "' not found in database. " +
                    "Ensure V2__auth_users_and_roles.sql migration has run."));

        User user = User.builder()
                .email(normalisedEmail)
                .password(passwordEncoder.encode(request.password()))
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .enabled(true)
                .build();

        user.addRole(defaultRole);

        User saved = userRepository.save(user);
        log.info("User registered: id={}, email={}", saved.getId(), saved.getEmail());

        String token = jwtTokenProvider.generateAccessToken(new SecurityPrincipal(saved));

        return AuthResponse.of(
            token,
            jwtProperties.getExpirationMs() / 1000L,
            UserResponse.from(saved)
        );
    }

    /**
     * Authenticates a user and returns an access token.
     *
     * Delegates to AuthenticationManager which runs DaoAuthenticationProvider:
     *   1. Calls UserDetailsServiceImpl.loadUserByUsername(email)
     *   2. Verifies BCrypt hash
     *   3. Checks account status (enabled, non-locked, non-expired)
     *
     * Authentication failures propagate as Spring Security AuthenticationException
     * subtypes (BadCredentialsException, DisabledException, LockedException).
     * These are caught by GlobalExceptionHandler → 401 UNAUTHORIZED.
     * We do NOT catch them here — this keeps the 401 logic centralised.
     *
     * @param request validated LoginRequest
     * @return AuthResponse with JWT and safe UserResponse
     */
    @Transactional(readOnly = true)
    public AuthResponse loginUser(LoginRequest request) {
        String normalisedEmail = request.email().toLowerCase().trim();

        // AuthenticationException subtypes propagate to GlobalExceptionHandler → 401
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(normalisedEmail, request.password())
        );

        SecurityPrincipal principal = (SecurityPrincipal) authentication.getPrincipal();
        String token = jwtTokenProvider.generateAccessToken(principal);

        log.info("User authenticated: id={}, email={}", principal.getUserId(), normalisedEmail);

        return AuthResponse.of(
            token,
            jwtProperties.getExpirationMs() / 1000L,
            UserResponse.from(principal.getUser())
        );
    }
}