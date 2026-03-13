package com.yourorg.foodorder.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * User entity — core identity and authentication record.
 *
 * Design decisions:
 *
 * UUID primary key:
 *   Sequential UUIDs (GenerationType.UUID, Hibernate 6+) avoid the
 *   performance issue of random UUID inserts into B-tree indexes.
 *   UUIDs never leak insertion order, user count, or DB internals to clients.
 *
 * password field:
 *   Stores the BCrypt hash. The field name "password" maps to column
 *   "password_hash" via @Column(name=) to make the storage intent explicit
 *   in the schema while keeping the domain model idiomatic.
 *   The raw password NEVER touches this entity — it is hashed before
 *   being set (AuthService responsibility).
 *
 * Soft delete (deletedAt):
 *   Hard deletes break audit trails and FK constraints from orders/reviews.
 *   A non-null deletedAt means logically deleted. The enabled flag controls
 *   active login independently — accounts can be disabled without deleting.
 *
 * ManyToMany roles:
 *   FetchType.EAGER for roles is intentional here: Spring Security's
 *   UserDetailsService must return all granted authorities synchronously.
 *   Loading roles lazily would require an open Hibernate session during the
 *   filter chain, which violates the stateless architecture.
 *   The role set is small (typically 1–2 roles per user), so EAGER is safe.
 *
 *   CascadeType is intentionally empty — roles are reference data managed
 *   by Flyway, never created/deleted through the User entity.
 */
@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_users_email",      columnList = "email"),
        @Index(name = "idx_users_enabled",    columnList = "enabled"),
        @Index(name = "idx_users_deleted_at", columnList = "deleted_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /**
     * BCrypt hash — NEVER the raw password.
     * AuthService hashes before persisting; this field is never serialised
     * to JSON (no getter exposed via DTO, @JsonIgnore on entity level).
     */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String password;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /**
     * Account enabled flag — false prevents login without deleting the record.
     * Used for: email verification, admin suspension, fraud hold.
     * Spring Security reads this via SecurityPrincipal.isEnabled().
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /**
     * Soft delete timestamp. NULL = active. Non-null = logically deleted.
     * Queries that should exclude deleted users must add:
     *   WHERE deleted_at IS NULL
     * or use a Hibernate @Filter for cross-cutting application.
     */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Granted roles. EAGER-loaded because Spring Security needs authorities
     * at filter time (before a Hibernate session is guaranteed open).
     *
     * join table: user_roles
     * No cascade — roles are Flyway-managed reference data.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns        = @JoinColumn(name = "user_id",  referencedColumnName = "id"),
        inverseJoinColumns = @JoinColumn(name = "role_id",  referencedColumnName = "id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    // ── Domain behaviour ──────────────────────────────────────────────────────

    /** Adds a role to this user. Idempotent — safe to call if role already assigned. */
    public void addRole(Role role) {
        this.roles.add(role);
    }

    /** Soft-deletes this user. Reversible by clearing deletedAt. */
    public void softDelete() {
        this.deletedAt = Instant.now();
        this.enabled   = false;
    }

    /** Returns true if the account has not been soft-deleted. */
    public boolean isActive() {
        return deletedAt == null;
    }
}