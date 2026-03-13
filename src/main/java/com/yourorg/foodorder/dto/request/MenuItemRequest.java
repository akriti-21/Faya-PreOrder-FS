package com.yourorg.foodorder.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Inbound DTO for POST /api/v1/vendors/{vendorId}/menu (admin only).
 *
 * price validation:
 *   @DecimalMin("0.01") — items must cost at least 1 cent (free items
 *   should be modelled as discounts, not zero-price menu items).
 *   @Digits(integer=8, fraction=2) — matches the NUMERIC(10,2) DB column.
 */
public record MenuItemRequest(

    @NotBlank(message = "Item name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    String name,

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    String description,

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be at least 0.01")
    @Digits(integer = 8, fraction = 2, message = "Price must have at most 8 integer digits and 2 decimal places")
    BigDecimal price,

    @Size(max = 100, message = "Category must not exceed 100 characters")
    String category
) {}