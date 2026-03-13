package com.yourorg.foodorder.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payment entity — records a payment attempt against an Order.
 *
 * Maps to the {@code payments} table (V5 migration).
 *
 * Relationship to Order — OneToOne:
 *   In the current simulation model, one order has at most one payment attempt.
 *   The FK is on payments.order_id (not on orders). This allows orders to exist
 *   without payments (before payment is initiated) without requiring a nullable
 *   FK on the orders table.
 *
 *   The @OneToOne mapping here uses the FK-owning side (Payment owns the FK to Order).
 *   Order does NOT have a @OneToOne back-reference to Payment — this prevents
 *   Hibernate from issuing an extra SELECT for Payment on every Order load.
 *   PaymentService.findByOrderId() is the lookup path when the payment for an
 *   order is needed.
 *
 * transactionId:
 *   Set by simulatePaymentGateway() — represents the gateway's reference ID.
 *   Null until the gateway responds. Stored as VARCHAR(100) — sufficient for
 *   UUID-format gateway references or short alphanumeric codes.
 *
 * amount must equal order.totalAmount:
 *   PaymentService validates this before calling the gateway. A mismatch
 *   indicates a tampered request and throws BusinessException (400).
 *
 * status is stored as STRING (@Enumerated(STRING)) for DB readability
 * and immunity to enum ordinal changes.
 */
@Entity
@Table(
    name = "payments",
    indexes = {
        @Index(name = "idx_payments_order_id", columnList = "order_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * The order this payment is for. LAZY — not needed for payment status queries.
     * PaymentService always loads via findByOrderId() when the order context is needed.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /**
     * Amount tendered. Must match Order.totalAmount at the time of payment creation.
     * Validated by PaymentService.createPayment() before gateway call.
     */
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /**
     * Current payment lifecycle state. Set to PENDING on creation;
     * updated to SUCCESS or FAILED after gateway simulation.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    /**
     * Payment instrument type. Determines gateway simulation behaviour:
     * COD always succeeds; others use random simulation logic.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    /**
     * Gateway-assigned transaction reference. Null until the gateway responds.
     * Set by simulatePaymentGateway() on both success and failure.
     */
    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Domain behaviour ──────────────────────────────────────────────────────

    /** Returns true if the payment can still be processed (not yet terminal). */
    public boolean isPending() {
        return this.status == PaymentStatus.PENDING;
    }

    /** Marks payment as successful with the given transaction reference. */
    public void markSuccess(String txnId) {
        this.status        = PaymentStatus.SUCCESS;
        this.transactionId = txnId;
    }

    /** Marks payment as failed with a gateway-generated failure reference. */
    public void markFailed(String txnId) {
        this.status        = PaymentStatus.FAILED;
        this.transactionId = txnId;
    }

    /** Marks payment as refunded (admin action post-success). */
    public void markRefunded() {
        this.status = PaymentStatus.REFUNDED;
    }
}
