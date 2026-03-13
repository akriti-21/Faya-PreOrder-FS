package com.yourorg.foodorder.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Role entity — represents a named authority granted to users.
 *
 * Design decisions:
 *
 * String name (not enum):
 *   Roles are stored as VARCHAR, not a PostgreSQL ENUM or Java enum.
 *   Adding a new role requires a Flyway migration, not a code + ALTER TYPE
 *   deployment. The Spring Security GrantedAuthority contract works with
 *   strings natively, so there is no conversion overhead.
 *
 * No bidirectional mapping:
 *   Role does not hold a reference back to Set<User>. Navigating from a
 *   role to all its users is a query concern, not a domain model concern.
 *   Bidirectional ManyToMany mappings in Hibernate cause accidental
 *   full-table loads and are a common performance footgun.
 *
 * Seed data:
 *   ROLE_USER and ROLE_ADMIN are inserted by V2 Flyway migration.
 *   The application never creates roles at runtime.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Role name — must be prefixed with ROLE_ for Spring Security's
     * hasRole() / hasAuthority() to work correctly without custom
     * GrantedAuthority adapters.
     * Examples: ROLE_USER, ROLE_ADMIN, ROLE_RESTAURANT_OWNER
     */
    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    public Role(String name) {
        this.name = name;
    }
}