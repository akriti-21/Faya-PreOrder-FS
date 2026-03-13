package com.yourorg.foodorder.repository;

import com.yourorg.foodorder.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Role reference data.
 *
 * Roles are seeded by Flyway (V2 migration) and are read-only at runtime.
 * The application never creates or deletes roles — only reads them.
 *
 * findByName is the primary lookup: AuthService uses it to assign the
 * default ROLE_USER to newly registered users.
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    /**
     * Looks up a role by its authority name (e.g., "ROLE_USER").
     * Returns Optional.empty() if the role has not been seeded — which
     * indicates a migration failure and should be treated as a fatal
     * configuration error (AuthService throws if empty).
     */
    Optional<Role> findByName(String name);
}