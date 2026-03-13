package com.yourorg.foodorder.dto.response;

import com.yourorg.foodorder.domain.Vendor;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbound representation of a Vendor — safe to serialise in API responses.
 *
 * ownerId is included so admins can identify the owning user.
 * deletedAt and internal audit fields are excluded from the public response.
 *
 * Static from() factory keeps mapping logic co-located with the DTO.
 */
public record VendorResponse(
    UUID    id,
    String  name,
    String  description,
    String  cuisineType,
    String  phone,
    String  address,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
    public static VendorResponse from(Vendor v) {
        return new VendorResponse(
            v.getId(),
            v.getName(),
            v.getDescription(),
            v.getCuisineType(),
            v.getPhone(),
            v.getAddress(),
            v.isActive(),
            v.getCreatedAt(),
            v.getUpdatedAt()
        );
    }
}