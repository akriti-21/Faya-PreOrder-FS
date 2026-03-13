package com.yourorg.foodorder.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inbound DTO for POST /api/v1/vendors (admin only).
 *
 * Java record: immutable, no Lombok needed, compact syntax.
 * All fields validated before VendorService is called.
 */
public record VendorRequest(

    @NotBlank(message = "Vendor name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    String name,

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    String description,

    @Size(max = 100, message = "Cuisine type must not exceed 100 characters")
    String cuisineType,

    @Size(max = 30, message = "Phone must not exceed 30 characters")
    String phone,

    @Size(max = 1000, message = "Address must not exceed 1000 characters")
    String address
) {}