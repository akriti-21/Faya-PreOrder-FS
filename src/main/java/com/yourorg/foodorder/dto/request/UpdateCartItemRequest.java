package com.yourorg.foodorder.dto.request;

import jakarta.validation.constraints.Min;

/**
 * Inbound DTO for PUT /api/v1/cart/items/{itemId}.
 *
 * Updating to quantity 0 is equivalent to removal, but clients should use
 * DELETE /api/v1/cart/items/{itemId} for explicit removal.
 * CartService treats quantity 0 as a removal (returns 404 path) — clients
 * should not rely on this behaviour; use DELETE instead.
 */
public record UpdateCartItemRequest(

    @Min(value = 1, message = "Quantity must be at least 1")
    int quantity
) {}