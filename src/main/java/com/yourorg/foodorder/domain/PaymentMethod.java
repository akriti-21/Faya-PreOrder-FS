package com.yourorg.foodorder.domain;

/**
 * Payment instrument types supported by the platform.
 *
 * CARD    Credit or debit card via a card processor (Stripe, Razorpay, etc.)
 * UPI     Unified Payments Interface — India-specific bank transfer.
 * WALLET  In-app wallet or third-party wallet (Paytm, PhonePe, etc.)
 * COD     Cash on delivery — no pre-payment; order confirmed immediately.
 *
 * COD special handling:
 *   COD payments bypass the gateway simulation. PaymentService.simulatePaymentGateway()
 *   always returns SUCCESS for COD — the order is confirmed immediately.
 *   Actual cash collection happens at delivery (outside the system).
 */
public enum PaymentMethod {
    CARD,
    UPI,
    WALLET,
    COD
}