package com.yourorg.foodorder.dto.response;

import com.yourorg.foodorder.domain.OrderItem;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Outbound representation of a single order line item.
 *
 * price is the snapshotted price at order time — not the current
 * MenuItem.price (which may have changed since placement).
 * lineTotal is computed for client convenience.
 */
public record OrderItemResponse(
    UUID       id,
    UUID       menuItemId,
    String     menuItemName,
    int        quantity,
    BigDecimal unitPrice,
    BigDecimal lineTotal
) {
    public static OrderItemResponse from(OrderItem oi) {
        return new OrderItemResponse(
            oi.getId(),
            oi.getMenuItem().getId(),
            oi.getMenuItem().getName(),
            oi.getQuantity(),
            oi.getPrice(),
            oi.lineTotal()
        );
    }
}