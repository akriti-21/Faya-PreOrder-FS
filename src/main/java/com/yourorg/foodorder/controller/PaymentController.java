package com.yourorg.foodorder.controller;

import com.yourorg.foodorder.dto.request.PaymentRequest;
import com.yourorg.foodorder.dto.response.ApiResponse;
import com.yourorg.foodorder.dto.response.PaymentResponse;
import com.yourorg.foodorder.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * PaymentController — payment initiation and verification endpoints.
 *
 * POST /api/v1/payments                   — user initiates payment for an order
 * POST /api/v1/payments/{paymentId}/verify — user/admin verifies payment status
 * GET  /api/v1/payments/order/{orderId}    — user/admin retrieves payment for order
 *
 * All endpoints require authentication. POST (initiate payment) is restricted
 * to ROLE_USER — only the order owner may pay for their order (enforced in
 * PaymentService via ownership check in addition to the @PreAuthorize).
 *
 * No business logic in this controller. All validation and processing is
 * delegated to PaymentService.
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * POST /api/v1/payments
     * Initiates payment for a PENDING order. Runs the full payment flow:
     * create payment → gateway simulation → update order status.
     * Returns 201 Created with the final PaymentResponse (SUCCESS or FAILED).
     */
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<PaymentResponse>> initiatePayment(
            @Valid @RequestBody PaymentRequest request) {

        PaymentResponse payment = paymentService.createPayment(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Payment processed.", payment));
    }

    /**
     * POST /api/v1/payments/{paymentId}/verify
     * Returns the current status of a payment.
     * In a real system this would re-query the gateway. In the simulation
     * it returns the persisted status (already final after creation).
     */
    @PostMapping("/{paymentId}/verify")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<PaymentResponse>> verifyPayment(
            @PathVariable UUID paymentId) {

        PaymentResponse payment = paymentService.verifyPayment(paymentId);
        return ResponseEntity.ok(
                ApiResponse.success("Payment status retrieved.", payment));
    }

    /**
     * GET /api/v1/payments/order/{orderId}
     * Retrieves the payment record for a specific order.
     * Useful for the order detail page to show payment info alongside order info.
     */
    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentByOrder(
            @PathVariable UUID orderId) {

        PaymentResponse payment = paymentService.getPaymentByOrderId(orderId);
        return ResponseEntity.ok(
                ApiResponse.success("Payment retrieved.", payment));
    }
}