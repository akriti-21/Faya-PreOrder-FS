package com.yourorg.foodorder.repository;

import com.yourorg.foodorder.domain.Payment;
import com.yourorg.foodorder.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Payment persistence.
 *
 * JOIN FETCH order on detail queries: the Payment → Order relationship is
 * LAZY, but most response mappings need the order's totalAmount and status.
 * JOIN FETCH eliminates those secondary SELECTs.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Find payment by its associated order ID.
     * JOIN FETCH order prevents N+1 when rendering PaymentResponse.
     * Returns Optional — an order may not have an initiated payment yet.
     */
    @Query("SELECT p FROM Payment p JOIN FETCH p.order WHERE p.order.id = :orderId")
    Optional<Payment> findByOrderId(UUID orderId);

    /**
     * Payment detail by payment ID with order loaded.
     * Used by verifyPayment() and PaymentController.
     */
    @Query("SELECT p FROM Payment p JOIN FETCH p.order WHERE p.id = :paymentId")
    Optional<Payment> findByIdWithOrder(UUID paymentId);

    /**
     * All payments by status — for admin monitoring and refund processing.
     */
    List<Payment> findByStatus(PaymentStatus status);

    /**
     * Existence check — used by processPayment() to prevent double-processing
     * without loading the full entity.
     */
    boolean existsByOrderIdAndStatus(UUID orderId, PaymentStatus status);
}