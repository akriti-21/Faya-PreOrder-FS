package com.yourorg.foodorder.dto.response;

import com.yourorg.foodorder.domain.Cart;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Outbound representation of a user's Cart.
 *
 * totalAmount is computed from CartItem snapshots — it reflects prices at
 * the time each item was added (not live menu prices). If a vendor changes
 * a price after the item was added, totalAmount reflects the snapshotted price.
 *
 * An empty cart has items=[] and totalAmount=0.00.
 */
public record CartResponse(
    UUID                   cartId,
    List<CartItemResponse> items,
    BigDecimal             totalAmount,
    Instant                updatedAt
) {
    public static CartResponse from(Cart cart) {
        List<CartItemResponse> itemResponses = cart.getItems().stream()
                .map(CartItemResponse::from)
                .collect(Collectors.toUnmodifiableList());

        BigDecimal total = itemResponses.stream()
                .map(CartItemResponse::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartResponse(
            cart.getId(),
            itemResponses,
            total,
            cart.getUpdatedAt()
        );
    }
}