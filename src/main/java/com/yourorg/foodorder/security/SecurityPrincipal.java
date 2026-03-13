package com.yourorg.foodorder.security;

import com.yourorg.foodorder.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adapter between the User domain entity and Spring Security's UserDetails contract.
 *
 * Why a separate class instead of implementing UserDetails on User?
 *
 * 1. Separation of concerns: User is a JPA entity (persistence concern).
 *    UserDetails is a Spring Security contract (web security concern).
 *    Mixing them couples the domain model to the security framework.
 *
 * 2. Testability: domain tests can instantiate User without Spring Security.
 *
 * 3. Flexibility: the adapter can evolve the security representation
 *    (e.g., add tenant claims) without touching the User entity.
 *
 * Immutability: SecurityPrincipal is constructed once per request and never
 * mutated. All UserDetails methods are derived from the wrapped User snapshot.
 * The User object is stored as a field for callers that need domain properties
 * beyond the UserDetails contract (e.g., AuthController accessing user.getId()).
 */
public class SecurityPrincipal implements UserDetails {

    private final User user;

    public SecurityPrincipal(User user) {
        this.user = user;
    }

    /**
     * Exposes the underlying domain User for cases where the controller
     * or service needs fields beyond the UserDetails interface.
     * Access via SecurityContextHolder:
     *   SecurityPrincipal principal = (SecurityPrincipal)
     *       SecurityContextHolder.getContext().getAuthentication().getPrincipal();
     *   UUID userId = principal.getUser().getId();
     */
    public User getUser() {
        return user;
    }

    /** Convenience accessor — avoids casting in controllers. */
    public UUID getUserId() {
        return user.getId();
    }

    /**
     * Maps Role entities to GrantedAuthority.
     * SimpleGrantedAuthority wraps the role name string directly.
     * Spring Security's hasRole("USER") strips the ROLE_ prefix, so
     * ROLE_USER in the DB ↔ hasRole("USER") in @PreAuthorize — consistent.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Returns the BCrypt hash — Spring Security uses this for credential comparison. */
    @Override
    public String getPassword() {
        return user.getPassword();
    }

    /** The username in Spring Security's model is the user's email address. */
    @Override
    public String getUsername() {
        return user.getEmail();
    }

    /** Account expiry — not implemented; always true. */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * Account lock — returns true only for non-soft-deleted accounts.
     * A soft-deleted user gets "account is locked" on login attempt,
     * which is appropriate (the account is no longer accessible).
     */
    @Override
    public boolean isAccountNonLocked() {
        return user.isActive();
    }

    /** Credential expiry — not implemented; always true. */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Enabled — maps directly to User.enabled.
     * Disabled accounts cannot authenticate regardless of valid credentials.
     */
    @Override
    public boolean isEnabled() {
        return user.isEnabled();
    }
}