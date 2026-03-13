package com.yourorg.foodorder.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * CartItem entity — a single line item within a Cart.
 *
 * Maps to the {@code cart_items} table (V4 migration).
 *
 * price snapshot:
 *   The price field captures the MenuItem price at the moment the item is
 *   added to the cart. This serves two purposes:
 *
 *   1. Display consistency — the cart UI shows the price the user saw when
 *      they added the item, not a live price that may have changed.
 *
 *   2. Checkout foundation — CartService.checkoutCart() uses this snapshot
 *      to build OrderItem.price without re-fetching live menu prices.
 *      This prevents a race condition where a price change between "add to cart"
 *      and "checkout" causes the order total to differ from what the user saw.
 *
 *   The trade-off: if a vendor lowers a price after the item is in the cart,
 *   the user will pay the higher price. CartService.addItemToCart() refreshes
 *   the price snapshot on every add/merge operation to mitigate this.
 *
 * No @UpdateTimestamp:
 *   quantity changes are tracked via createdAt of the cart itself (updatedAt).
 *   CartItem has no updatedAt — CartService replaces the quantity and price in
 *   place on the existing row when updating.
 *
 * LAZY fetch on menuItem:
 *   CartService always JOIN FETCHes menuItem when loading cart items
 *   (CartItemRepository.findByCartId). LAZY prevents accidental loads.
 */
@Entity
@Table(
    name = "cart_items",
    indexes = {
        @Index(name = "idx_cart_items_cart_id",      columnList = "cart_id"),
        @Index(name = "idx_cart_items_menu_item_id", columnList = "menu_item_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Parent cart. Set via Cart.addItem() to maintain bidirectional consistency.
     * LAZY — the cart is always already loaded when items are accessed.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    /**
     * The menu item selected. LAZY — JOIN FETCH in CartItemRepository queries
     * loads it when item details are needed for CartResponse.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    /** Number of units selected. Must be >= 1. Validated in CartService. */
    @Column(name = "quantity", nullable = false)
    private int quantity;

    /**
     * Price snapshot — MenuItem price at the time this item was added.
     * DB column: unit_price. Matches NUMERIC(10,2).
     * Refreshed (overwritten) on each addItemToCart() call for this item.
     */
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Convenience — total cost for this line item. Not persisted. */
    @Transient
    public BigDecimal subtotal() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }
}