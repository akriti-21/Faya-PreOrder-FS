package com.yourorg.foodorder.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cart entity — a user's in-progress item selection before checkout.
 *
 * One cart per user (unique constraint on user_id). The cart persists across
 * sessions — closing the browser does not clear it. Checkout converts it to
 * an Order and clears all CartItems (not the Cart itself, so the FK/ID remains
 * stable for the next shopping session).
 *
 * CartItems are owned by the Cart via CascadeType.ALL + orphanRemoval.
 * Removing an item from the items list deletes the CartItem row.
 * clearCart() removes all items but leaves the Cart row intact for reuse.
 *
 * LAZY fetch on user:
 *   The User entity is not needed for most cart operations (add, remove, view).
 *   CartService resolves the user from SecurityContext before any DB call,
 *   so the LAZY proxy is never accessed within a closed session.
 */
@Entity
@Table(
    name = "carts",
    indexes = {
        @Index(name = "idx_carts_user_id", columnList = "user_id", unique = true)
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * The owner of this cart. One-to-one in practice (unique index on user_id),
     * modelled as ManyToOne to avoid the JPA one-to-one proxy pitfalls and
     * to allow future guest-cart or multi-device scenarios.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Line items in this cart.
     * CascadeType.ALL + orphanRemoval: Cart owns CartItems fully.
     * Adding/removing from this list is the only way to mutate items.
     */
    @OneToMany(
        mappedBy      = "cart",
        cascade       = CascadeType.ALL,
        orphanRemoval = true,
        fetch         = FetchType.LAZY
    )
    @Builder.Default
    private List<CartItem> items = new ArrayList<>();

    // ── Domain behaviour ──────────────────────────────────────────────────────

    /** Adds a CartItem and sets the back-reference. */
    public void addItem(CartItem item) {
        items.add(item);
        item.setCart(this);
    }

    /** Removes all items from the cart. Triggers orphanRemoval deletes on flush. */
    public void clearItems() {
        items.clear();
    }

    /**
     * Returns the CartItem for the given menuItemId, or empty if not present.
     * Used by addItemToCart() to detect duplicates and merge quantities.
     */
    public java.util.Optional<CartItem> findItemByMenuItemId(UUID menuItemId) {
        return items.stream()
                .filter(ci -> ci.getMenuItem().getId().equals(menuItemId))
                .findFirst();
    }
}