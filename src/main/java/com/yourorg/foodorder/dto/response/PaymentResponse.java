package com.yourorg.foodorder.dto.response;

import com.yourorg.foodorder.domain.Payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Outbound representation of a Payment.
 *
 * Does not expose the full Order — orderId is sufficient for client correlation.
 * Clients can call GET /api/v1/orders/{orderId} for order details.
 *
 * transactionId may be null if the payment is still PENDING (gateway has not
 * responded). @JsonInclude(NON_NULL) on ApiResponse will omit it cleanly.
 */
public record PaymentResponse(
    UUID        paymentId,
    UUID        orderId,
    BigDecimal  amount,
    String      status,
    String      paymentMethod,
    String      transactionId,
    Instant     createdAt,
    Instant     updatedAt
) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
            p.getId(),
            p.getOrder().getId(),
            p.getAmount(),
            p.getStatus().name(),
            p.getPaymentMethod().name(),
            p.getTransactionId(),
            p.getCreatedAt(),
            p.getUpdatedAt()
        );
    }
}
