package com.yourorg.foodorder.service;

import com.yourorg.foodorder.domain.User;
import com.yourorg.foodorder.dto.response.UserResponse;
import com.yourorg.foodorder.exception.ResourceNotFoundException;
import com.yourorg.foodorder.repository.UserRepository;
import com.yourorg.foodorder.security.SecurityPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * User management service — operations on existing user accounts.
 *
 * Responsibilities:
 *   getCurrentUser()  — return the authenticated user's profile
 *   getUserById()     — look up any user by UUID (admin use)
 *   createUser()      — programmatic user creation (admin / seeding)
 *
 * This service is intentionally separate from AuthService:
 *   AuthService    = authentication lifecycle (tokens, credentials)
 *   UserService    = user profile management (read, update, admin ops)
 *
 * SecurityContextHolder access:
 *   getCurrentUser() reads the Authentication from the SecurityContext.
 *   This works because JwtAuthenticationFilter has already validated the
 *   token and populated the context before any controller method runs.
 *   The principal is cast to SecurityPrincipal — if the context holds a
 *   different principal type (e.g., anonymous), the cast will fail and
 *   Spring Security's access control would have already blocked the request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * Returns the authenticated user's profile.
     *
     * Reads identity from SecurityContextHolder — no request parameter needed.
     * Controllers call this directly: the current user is implicit from the JWT.
     *
     * @return UserResponse for the authenticated user
     * @throws ResourceNotFoundException if the user no longer exists in the DB
     *         (e.g., account was deleted between token issue and this request)
     */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        SecurityPrincipal principal = (SecurityPrincipal) authentication.getPrincipal();

        // Re-query to ensure we return the latest state (roles may have changed
        // since the token was issued; the SecurityPrincipal snapshot may be stale)
        User user = userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User", "email", principal.getUsername()));

        return UserResponse.from(user);
    }

    /**
     * Looks up a user by their UUID primary key.
     * Intended for admin endpoints and internal service-to-service calls.
     *
     * @param userId UUID of the user to retrieve
     * @return UserResponse (never includes password)
     * @throws ResourceNotFoundException if no user with the given ID exists
     */
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        return UserResponse.from(user);
    }

    /**
     * Programmatic user creation — for admin-initiated accounts or seeding.
     *
     * Unlike AuthService.registerUser(), this method:
     *   - Does not issue a JWT (admin creates the account; user logs in separately)
     *   - Accepts a pre-hashed password (caller is responsible for hashing)
     *   - Does not enforce the ROLE_USER default (caller specifies roles)
     *
     * Callers must validate uniqueness themselves or catch the
     * DataIntegrityViolationException mapped by GlobalExceptionHandler.
     *
     * @param user fully constructed User entity (with hashed password and roles)
     * @return UserResponse of the persisted entity
     */
    @Transactional
    public UserResponse createUser(User user) {
        User saved = userRepository.save(user);
        log.info("User created programmatically: id={}, email={}", saved.getId(), saved.getEmail());
        return UserResponse.from(saved);
    }
}