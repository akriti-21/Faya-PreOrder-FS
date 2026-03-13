package com.foodorder.domain;

/**
 * Lifecycle states for a food order.
 *
 * State machine (valid transitions):
 *
 *   PENDING ──▶ CONFIRMED ──▶ PREPARING ──▶ READY ──▶ DELIVERED
 *     │               │            │           │
 *     └───────────────┴────────────┴───────────┴──▶ CANCELLED
 *
 * PENDING      Order placed by customer; awaiting vendor acknowledgement.
 * CONFIRMED    Vendor has accepted the order; payment captured.
 * PREPARING    Kitchen is actively preparing the food.
 * READY        Order is ready for pickup or handoff to delivery.
 * DELIVERED    Order successfully delivered to the customer.
 * CANCELLED    Order cancelled — by customer (PENDING only) or vendor (any pre-DELIVERED).
 *
 * Stored as VARCHAR(50) in the DB — enum column names survive refactoring
 * more safely than ordinal integers. The CHECK constraint in V2 migration
 * enforces the allowed values at the DB level as a second line of defence.
 *
 * Terminal states: DELIVERED, CANCELLED — no further transitions allowed.
 * OrderService.cancelOrder() enforces this invariant before persisting.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PREPARING,
    READY,
    DELIVERED,
    CANCELLED;

    /** Returns true if this status allows no further state changes. */
    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED;
    }

    /**
     * Returns true if the order can be cancelled from this state.
     * Business rule: customers may only cancel PENDING orders.
     * Admins/vendors may cancel up to PREPARING.
     */
    public boolean isCancellable() {
        return this == PENDING || this == CONFIRMED || this == PREPARING;
    }
}