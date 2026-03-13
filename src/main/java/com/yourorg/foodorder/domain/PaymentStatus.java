package com.yourorg.foodorder.domain;

/**
 * Lifecycle states for a payment.
 *
 * State machine:
 *   PENDING ──▶ SUCCESS
 *     └───────▶ FAILED
 *   SUCCESS ──▶ REFUNDED
 *
 * PENDING   Payment record created; gateway processing has not completed.
 * SUCCESS   Gateway confirmed funds captured. Triggers order CONFIRMED.
 * FAILED    Gateway rejected or timed out. Triggers order CANCELLED.
 * REFUNDED  Payment reversed after successful capture (post-delivery dispute,
 *           admin-initiated refund). Terminal — no further transitions.
 *
 * Terminal states: SUCCESS transitions to REFUNDED only. FAILED and REFUNDED
 * are terminal. A FAILED payment cannot be retried — a new payment must be
 * created for the same order (not implemented in the simulation, but the
 * data model supports it via no unique constraint on order_id).
 */
public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    REFUNDED;

    public boolean isTerminal() {
        return this == FAILED || this == REFUNDED;
    }

    public boolean isSuccessful() {
        return this == SUCCESS;
    }
}
