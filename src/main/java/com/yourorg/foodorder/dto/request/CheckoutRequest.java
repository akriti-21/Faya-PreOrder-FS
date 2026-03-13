package com.yourorg.foodorder.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inbound DTO for POST /api/v1/cart/checkout.
 *
 * deliveryAddress is required at checkout time — not stored on the Cart.
 * notes is optional (special instructions for the vendor).
 */
public record CheckoutRequest(

    @NotBlank(message = "Delivery address is required")
    @Size(max = 1000, message = "Delivery address must not exceed 1000 characters")
    String deliveryAddress,

    @Size(max = 500, message = "Notes must not exceed 500 characters")
    String notes
) {}