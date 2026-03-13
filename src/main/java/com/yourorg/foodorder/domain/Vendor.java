package com.yourorg.foodorder.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Vendor entity — represents a restaurant or food vendor on the platform.
 *
 * Maps to the {@code restaurants} table (V2 migration).
 * The table name "restaurants" is retained for DB consistency; the domain
 * uses "Vendor" as the bounded-context term to avoid coupling the domain
 * model to the physical food-service concept (enables future B2B vendors).
 *
 * owner_id FK:
 *   Links the vendor to the User who owns/manages it. Only the owner or
 *   an ADMIN may update vendor details. This FK is not mapped as a JPA
 *   relationship here to avoid circular loading — ownership is enforced
 *   at the service layer by comparing principal.getUserId().
 *
 * Soft delete (deletedAt):
 *   Deleted vendors still appear on historical orders. Never hard-delete a
 *   vendor that has fulfilled orders — it breaks the order audit trail.
 *
 * active flag:
 *   Controls whether the vendor appears in public listings. A vendor can be
 *   temporarily inactive (holiday closure) without being deleted.
 */
@Entity
@Table(
    name = "restaurants",
    indexes = {
        @Index(name = "idx_restaurants_active", columnList = "active")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vendor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * UUID of the owning User. Stored as a raw UUID (not a @ManyToOne) to
     * prevent accidental eager loading of User on every vendor query.
     * Service layer validates ownership explicitly when needed.
     */
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "cuisine_type", length = 100)
    private String cuisineType;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    /**
     * Whether this vendor is currently open/accepting orders.
     * Distinct from deletedAt: an inactive vendor can be re-activated;
     * a soft-deleted vendor is logically gone.
     */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Soft-delete. Preserves FK references from orders and menu items. */
    public void softDelete() {
        this.deletedAt = Instant.now();
        this.active    = false;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}