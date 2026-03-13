package com.yourorg.foodorder.dto.request;

import com.yourorg.foodorder.domain.PaymentMethod;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Inbound DTO for POST /api/v1/payments.
 *
 * amount is NOT included in the request — it is read directly from the Order
 * to prevent client-side amount tampering. PaymentService.createPayment()
 * uses Order.totalAmount as the authoritative payment amount.
 */
public record PaymentRequest(

    @NotNull(message = "Order ID is required")
    UUID orderId,

    @NotNull(message = "Payment method is required")
    PaymentMethod paymentMethod
) {}
