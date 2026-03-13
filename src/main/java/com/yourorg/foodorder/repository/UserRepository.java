package com.yourorg.foodorder.repository;

import com.yourorg.foodorder.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for User persistence operations.
 *
 * Spring Data JPA derives all standard CRUD operations from JpaRepository.
 * Only domain-specific query methods are declared here.
 *
 * Soft-delete awareness:
 *   findByEmail intentionally returns users regardless of deleted_at status.
 *   The caller (UserDetailsServiceImpl, AuthService) decides whether a
 *   deleted/disabled user should be rejected. Filtering here would prevent
 *   returning a clear "account disabled" response vs "user not found".
 *
 * Query hint — JOIN FETCH roles:
 *   The User entity has EAGER-fetched roles (required for Spring Security).
 *   Without a JOIN FETCH, Hibernate issues a separate SELECT for roles
 *   on every User load (N+1). The custom JPQL avoids that with a single query.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find a user by email address (case-sensitive — emails are stored lowercase
     * by AuthService before persistence).
     *
     * JOIN FETCH roles: loads roles in a single query, preventing the N+1
     * that EAGER without JOIN FETCH causes. Critical for every authenticated
     * request (JwtAuthenticationFilter → UserDetailsService).
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles WHERE u.email = :email")
    Optional<User> findByEmail(String email);

    /**
     * Existence check without loading the entity — used by AuthService to
     * detect duplicate registration without a SELECT * load.
     */
    boolean existsByEmail(String email);
}