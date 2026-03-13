package com.yourorg.foodorder.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Inbound DTO for POST /api/v1/cart/items.
 *
 * quantity minimum is 1 — zero or negative quantities are rejected at the
 * validation layer before CartService is called.
 */
public record AddCartItemRequest(

    @NotNull(message = "Menu item ID is required")
    UUID menuItemId,

    @Min(value = 1, message = "Quantity must be at least 1")
    int quantity
) {}