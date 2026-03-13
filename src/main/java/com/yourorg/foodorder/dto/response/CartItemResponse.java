package com.yourorg.foodorder.dto.response;

import com.yourorg.foodorder.domain.CartItem;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Outbound representation of a single CartItem.
 *
 * price    — snapshotted unit price (at the time the item was added to cart)
 * subtotal — convenience field: price × quantity (computed, not stored)
 *
 * The menuItem name and vendorId are included so the cart UI can render
 * item cards without a separate menu API call per item.
 */
public record CartItemResponse(
    UUID       cartItemId,
    UUID       menuItemId,
    String     name,
    String     vendorName,
    int        quantity,
    BigDecimal price,
    BigDecimal subtotal
) {
    public static CartItemResponse from(CartItem ci) {
        return new CartItemResponse(
            ci.getId(),
            ci.getMenuItem().getId(),
            ci.getMenuItem().getName(),
            ci.getMenuItem().getVendor().getName(),
            ci.getQuantity(),
            ci.getPrice(),
            ci.subtotal()
        );
    }
}