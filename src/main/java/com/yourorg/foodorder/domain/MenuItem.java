package com.yourorg.foodorder.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * MenuItem entity — a single item available for order from a Vendor.
 *
 * Maps to the {@code menu_items} table (V2 migration).
 *
 * @ManyToOne vendor:
 *   FetchType.LAZY — menu items are always queried by vendor, so the vendor
 *   is always available in context. LAZY prevents an extra vendor SELECT
 *   when loading items for a single vendor's menu (the common case).
 *   Use JOIN FETCH in repository queries when the vendor is needed alongside
 *   the item (e.g., order placement validation).
 *
 * price — BigDecimal:
 *   Monetary amounts must NEVER use float/double. IEEE 754 floating-point
 *   cannot represent 0.10 exactly, causing rounding errors in totals.
 *   BigDecimal with precision(10,2) matches the DB NUMERIC(10,2) column.
 *   The @Column precision/scale annotation ensures Hibernate validates the
 *   constraint matches the DDL.
 *
 * Soft delete (deletedAt):
 *   Menu items on historical orders must remain queryable even after removal
 *   from the active menu. Soft-delete + available=false achieves this.
 */
@Entity
@Table(
    name = "menu_items",
    indexes = {
        @Index(name = "idx_menu_items_restaurant", columnList = "restaurant_id"),
        @Index(name = "idx_menu_items_available",  columnList = "available")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * The vendor this item belongs to. LAZY-fetched — the vendor is not
     * needed for most menu item operations (listing, pricing checks).
     * OrderService uses JOIN FETCH when validating items cross-vendor.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Vendor vendor;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Price at time of menu creation. Orders snapshot this price into
     * OrderItem.price — changes to this field do NOT retroactively affect
     * historical order totals.
     */
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "category", length = 100)
    private String category;

    /**
     * Whether this item can currently be ordered.
     * False = temporarily out of stock or removed from active menu.
     * OrderService rejects items where available=false.
     */
    @Column(name = "available", nullable = false)
    @Builder.Default
    private boolean available = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }
}