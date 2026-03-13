package com.yourorg.foodorder.dto.response;

import com.yourorg.foodorder.domain.MenuItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Outbound representation of a MenuItem.
 *
 * vendorId is included so clients can group items by vendor without
 * needing to embed the full VendorResponse (reduces payload size).
 *
 * deletedAt is excluded — soft-deleted items are never returned to clients.
 */
public record MenuItemResponse(
    UUID       id,
    UUID       vendorId,
    String     vendorName,
    String     name,
    String     description,
    BigDecimal price,
    String     category,
    boolean    available,
    Instant    createdAt,
    Instant    updatedAt
) {
    public static MenuItemResponse from(MenuItem m) {
        return new MenuItemResponse(
            m.getId(),
            m.getVendor().getId(),
            m.getVendor().getName(),
            m.getName(),
            m.getDescription(),
            m.getPrice(),
            m.getCategory(),
            m.isAvailable(),
            m.getCreatedAt(),
            m.getUpdatedAt()
        );
    }
}