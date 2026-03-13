package com.yourorg.foodorder.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Order entity — a customer's purchase request from a single Vendor.
 *
 * Maps to the {@code orders} table (V2 migration).
 *
 * Design invariants enforced at this layer:
 *   - An order belongs to exactly one customer (user) and one vendor.
 *   - All items in an order must belong to the same vendor (enforced in OrderService).
 *   - totalAmount is calculated by OrderService and stored — not recomputed on read.
 *     This preserves the order total even if item prices change later.
 *   - Status transitions are validated by OrderStatus.isCancellable() and the
 *     service layer before persisting.
 *
 * Column mapping notes:
 *   - customer_id → user field (domain term preferred over DB column naming)
 *   - restaurant_id → vendor field (domain term preferred)
 *   - placed_at → createdAt (creation-timestamp semantic, not a separate concept)
 *   - status is stored as a String using @Enumerated(STRING) — readable in the DB,
 *     immune to enum reordering bugs that ORDINAL causes.
 *
 * CascadeType.ALL on items:
 *   OrderItems are owned by the Order. Creating an Order with items, updating
 *   quantities, and deleting the order (if ever needed) all cascade through
 *   this relationship. orphanRemoval=true ensures removed items are deleted.
 *
 * LAZY fetch on user and vendor:
 *   Orders are frequently queried without needing the full User or Vendor graph.
 *   JOIN FETCH in repository queries loads them when needed (e.g., OrderResponse).
 */
@Entity
@Table(
    name = "orders",
    indexes = {
        @Index(name = "idx_orders_customer",   columnList = "customer_id"),
        @Index(name = "idx_orders_restaurant", columnList = "restaurant_id"),
        @Index(name = "idx_orders_status",     columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** The customer who placed this order. LAZY — not needed for status queries. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User user;

    /** The vendor fulfilling this order. LAZY — not needed for item queries. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Vendor vendor;

    /**
     * Current lifecycle state. Stored as STRING for DB readability and
     * immunity to enum ordinal changes. The V2 CHECK constraint enforces
     * valid values at the DB level.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    /**
     * Pre-calculated total at order placement time. BigDecimal for exact
     * monetary arithmetic. Stored value is authoritative — immune to
     * price changes after placement.
     */
    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "delivery_address", nullable = false, columnDefinition = "TEXT")
    private String deliveryAddress;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * placed_at in the DB — the moment the order was created.
     * Mapped to createdAt for domain consistency. Not managed by
     * @CreationTimestamp here because the column name differs from
     * the Hibernate convention; set explicitly by OrderService before save.
     */
    @Column(name = "placed_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Order line items. CascadeType.ALL + orphanRemoval: the order owns its
     * items. Persisting the order persists all items; removing an item from
     * the list deletes the OrderItem row.
     */
    @OneToMany(
        mappedBy      = "order",
        cascade       = CascadeType.ALL,
        orphanRemoval = true,
        fetch         = FetchType.LAZY
    )
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    // ── Domain behaviour ──────────────────────────────────────────────────────

    /** Adds an item to this order. Maintains bidirectional consistency. */
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    /**
     * Transitions the order to CANCELLED if the current status allows it.
     * Throws BusinessException (through callers) if in a terminal state.
     */
    public boolean cancel() {
        if (status.isCancellable()) {
            this.status = OrderStatus.CANCELLED;
            return true;
        }
        return false;
    }

    /** Advances status to the next logical state. Service validates the transition. */
    public void updateStatus(OrderStatus newStatus) {
        this.status = newStatus;
    }
}