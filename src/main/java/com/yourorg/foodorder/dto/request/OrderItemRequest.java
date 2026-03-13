package com.yourorg.foodorder.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * A single line item within an OrderRequest.
 *
 * quantity minimum is 1 — zero-quantity items are rejected before reaching
 * OrderService, preventing a class of bugs where zero-quantity items cause
 * incorrect total calculations.
 */
public record OrderItemRequest(

    @NotNull(message = "Menu item ID is required")
    UUID menuItemId,

    @Min(value = 1, message = "Quantity must be at least 1")
    int quantity
) {}