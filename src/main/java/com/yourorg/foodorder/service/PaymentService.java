package com.yourorg.foodorder.service;

import com.yourorg.foodorder.domain.*;
import com.yourorg.foodorder.dto.request.PaymentRequest;
import com.yourorg.foodorder.dto.response.PaymentResponse;
import com.yourorg.foodorder.exception.BusinessException;
import com.yourorg.foodorder.exception.ResourceNotFoundException;
import com.yourorg.foodorder.repository.OrderRepository;
import com.yourorg.foodorder.repository.PaymentRepository;
import com.yourorg.foodorder.security.SecurityPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * PaymentService — payment initiation, processing, and verification.
 *
 * Payment flow (all within a single @Transactional boundary):
 *
 *   1. createPayment()         — validates the order, prevents double-payment,
 *                                creates a PENDING Payment record.
 *   2. processPayment()        — calls simulatePaymentGateway(), sets SUCCESS
 *                                or FAILED, then delegates to OrderLifecycleService
 *                                to transition the order status.
 *   3. verifyPayment()         — read-only lookup of payment status by paymentId.
 *
 * createPayment() + processPayment() are intentionally separate:
 *   In a real integration, createPayment() would redirect the client to the
 *   payment provider's hosted page. The provider calls back to processPayment()
 *   via a webhook with the result. This separation also allows retry of
 *   processPayment() if the webhook is replayed.
 *   In the simulation, the client calls POST /api/v1/payments (createPayment)
 *   which internally calls processPayment() in the same transaction.
 *
 * Gateway simulation:
 *   simulatePaymentGateway() uses a seeded random to return SUCCESS ~80% of the
 *   time for CARD/UPI/WALLET, and always returns SUCCESS for COD.
 *   The random seed uses the payment UUID for determinism in tests — the same
 *   payment ID always produces the same simulated outcome.
 *
 * Double-payment guard:
 *   If a SUCCESS payment already exists for the order, createPayment() throws
 *   BusinessException. A FAILED payment allows a new attempt (new Payment row).
 *
 * Amount validation:
 *   The client does NOT supply the amount — it is read from Order.totalAmount.
 *   This prevents tampering (e.g., submitting ₹0.01 for a ₹500 order).
 *
 * Order ownership validation:
 *   Only the user who placed the order may initiate payment. AdminController
 *   bypasses this check for admin operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository      paymentRepository;
    private final OrderRepository        orderRepository;
    private final OrderLifecycleService  orderLifecycleService;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Creates a payment for an order and immediately processes it via the
     * gateway simulation. The entire flow (create + process) is atomic.
     *
     * @param request validated PaymentRequest (orderId + paymentMethod)
     * @return PaymentResponse with final status (SUCCESS or FAILED)
     * @throws ResourceNotFoundException if the order does not exist
     * @throws BusinessException         if the order cannot be paid
     *                                   (wrong owner, wrong status, duplicate payment)
     */
    @Transactional
    public PaymentResponse createPayment(PaymentRequest request) {

        // ── Step 1: Load and validate the order ───────────────────────────────
        Order order = orderRepository.findByIdWithItems(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", request.orderId()));

        validateOrderForPayment(order);

        // ── Step 2: Double-payment guard ──────────────────────────────────────
        if (paymentRepository.existsByOrderIdAndStatus(request.orderId(), PaymentStatus.SUCCESS)) {
            throw new BusinessException(
                "Order " + request.orderId() + " has already been paid successfully.");
        }

        // ── Step 3: Create PENDING payment record ─────────────────────────────
        Payment payment = Payment.builder()
                .order(order)
                .amount(order.getTotalAmount())   // authoritative amount from order
                .paymentMethod(request.paymentMethod())
                .status(PaymentStatus.PENDING)
                .build();

        Payment saved = paymentRepository.save(payment);
        log.info("Payment created: paymentId={}, orderId={}, amount={}, method={}",
                saved.getId(), order.getId(), order.getTotalAmount(), request.paymentMethod());

        // ── Step 4: Process via gateway simulation ────────────────────────────
        return processPayment(saved, order);
    }

    /**
     * Retrieves payment details for a given order.
     * Used by users to check payment status and by admins for order management.
     *
     * @param orderId UUID of the order
     * @return PaymentResponse
     * @throws ResourceNotFoundException if no payment exists for this order
     */
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrderId(UUID orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "orderId", orderId));
        return PaymentResponse.from(payment);
    }

    /**
     * Retrieves payment details by payment ID for the verify endpoint.
     *
     * @param paymentId UUID of the payment
     * @return PaymentResponse
     * @throws ResourceNotFoundException if no payment exists with this ID
     */
    @Transactional(readOnly = true)
    public PaymentResponse verifyPayment(UUID paymentId) {
        Payment payment = paymentRepository.findByIdWithOrder(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));
        return PaymentResponse.from(payment);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Runs the gateway simulation and updates both the Payment and Order status.
     *
     * This method is called within the @Transactional boundary of createPayment().
     * If the gateway call (or any DB write) fails, the entire transaction rolls back.
     *
     * @param payment newly persisted PENDING payment
     * @param order   the order being paid for
     * @return PaymentResponse with final status
     */
    private PaymentResponse processPayment(Payment payment, Order order) {
        GatewayResult result = simulatePaymentGateway(payment);

        if (result.success()) {
            payment.markSuccess(result.transactionId());
            paymentRepository.save(payment);
            orderLifecycleService.confirmOrder(order);
            log.info("Payment SUCCESS: paymentId={}, txnId={}, orderId={}",
                    payment.getId(), result.transactionId(), order.getId());
        } else {
            payment.markFailed(result.transactionId());
            paymentRepository.save(payment);
            orderLifecycleService.cancelOrder(order.getId());
            log.warn("Payment FAILED: paymentId={}, txnId={}, orderId={}",
                    payment.getId(), result.transactionId(), order.getId());
        }

        return PaymentResponse.from(payment);
    }

    /**
     * Simulates a payment gateway response.
     *
     * Simulation rules:
     *   COD     → always SUCCESS (cash collected at delivery)
     *   CARD    → SUCCESS with 85% probability
     *   UPI     → SUCCESS with 90% probability
     *   WALLET  → SUCCESS with 95% probability
     *
     * The random seed is derived from the payment UUID so the outcome is
     * deterministic for a given paymentId — idempotent in replay scenarios.
     *
     * A real implementation would:
     *   - Call the gateway REST API (Razorpay, Stripe, etc.)
     *   - Handle HTTP timeouts → set PENDING, await webhook
     *   - Handle gateway errors → set FAILED
     *   - Validate HMAC signature on the response
     *
     * @param payment the PENDING payment record
     * @return GatewayResult with success flag and transaction ID
     */
    private GatewayResult simulatePaymentGateway(Payment payment) {
        // Always succeed for COD
        if (payment.getPaymentMethod() == PaymentMethod.COD) {
            return new GatewayResult(true, "COD-" + payment.getId().toString().substring(0, 8).toUpperCase());
        }

        // Seeded random — deterministic per paymentId
        long seed = payment.getId().getLeastSignificantBits();
        java.util.Random rng = new java.util.Random(seed);
        double roll = rng.nextDouble();  // [0.0, 1.0)

        double successRate = switch (payment.getPaymentMethod()) {
            case CARD   -> 0.85;
            case UPI    -> 0.90;
            case WALLET -> 0.95;
            default     -> 0.85;
        };

        String txnPrefix = payment.getPaymentMethod().name().substring(0, 3);
        String txnId = txnPrefix + "-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();

        boolean success = roll < successRate;
        return new GatewayResult(success, txnId);
    }

    /**
     * Validates an order is eligible for payment.
     *
     * Rules:
     *   - Order must be in PENDING status (CONFIRMED = already paid,
     *     CANCELLED = no longer payable, etc.)
     *   - Order must belong to the authenticated user
     *     (prevents one user paying another's order)
     */
    private void validateOrderForPayment(Order order) {
        // Ownership check — load principal
        UUID currentUserId = currentUserId();
        if (!order.getUser().getId().equals(currentUserId)) {
            // 404 instead of 403 — don't reveal that the order exists
            throw new ResourceNotFoundException("Order", "id", order.getId());
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(
                "Order " + order.getId() + " cannot be paid — current status is '" +
                order.getStatus() + "'. Only PENDING orders can be paid.");
        }
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SecurityPrincipal principal = (SecurityPrincipal) auth.getPrincipal();
        return principal.getUserId();
    }

    // ── Inner record for gateway result ──────────────────────────────────────

    /**
     * Value object representing the gateway simulation outcome.
     * Java record — immutable, auto-generated equals/hashCode/toString.
     */
    private record GatewayResult(boolean success, String transactionId) {}
}