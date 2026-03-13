package com.yourorg.foodorder.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * OrderItem entity — a single line item within an Order.
 *
 * Maps to the {@code order_items} table (V2 migration).
 *
 * price snapshot:
 *   The price field stores the MenuItem price AT THE TIME OF ORDER PLACEMENT,
 *   not a live reference to MenuItem.price. This is critical for financial
 *   integrity — if a vendor raises a menu item's price, historical order totals
 *   must remain unchanged. OrderService reads MenuItem.price and writes it here.
 *
 *   DB column: unit_price (mapped via @Column(name=) to the domain field "price").
 *
 * No @CreationTimestamp / @UpdateTimestamp:
 *   Order items are immutable once created — quantity and price are fixed at
 *   order placement. No audit timestamps are stored at the item level;
 *   the parent Order's timestamps cover the lifecycle.
 *
 * LAZY fetch on menuItem:
 *   When rendering an order response, menu item details are fetched via a
 *   JOIN FETCH in the repository query — not via automatic EAGER loading.
 *   This prevents N+1 queries when listing orders with many items.
 *
 * order field setter access — package-private via Lombok:
 *   Order.addItem() is the only way to establish the bidirectional link.
 *   The Lombok @Setter on order allows Order to call item.setOrder(this).
 */
@Entity
@Table(
    name = "order_items",
    indexes = {
        @Index(name = "idx_order_items_order",     columnList = "order_id"),
        @Index(name = "idx_order_items_menu_item", columnList = "menu_item_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Parent order. LAZY — items are always accessed through their order,
     * so the order object is already in the persistence context.
     * Set via Order.addItem() to maintain bidirectional consistency.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /**
     * The menu item ordered. LAZY — use JOIN FETCH in repository queries
     * when item details are needed in the response.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    /** Number of units ordered. Must be > 0 (enforced by V2 DB constraint). */
    @Column(name = "quantity", nullable = false)
    private int quantity;

    /**
     * Price snapshot — the MenuItem price at placement time.
     * DB column: unit_price. Precision(10,2) matches NUMERIC(10,2).
     * NEVER updated after order placement.
     */
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** Convenience: total cost for this line item. Not persisted — computed on read. */
    @Transient
    public BigDecimal lineTotal() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }
}