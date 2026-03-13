package com.yourorg.foodorder.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Inbound DTO for POST /api/v1/orders.
 *
 * items must be non-empty — an order with no items is rejected at the
 * validation layer before OrderService is called.
 *
 * @Valid on items: cascades validation to each OrderItemRequest in the list.
 * Without @Valid, the @Min on OrderItemRequest.quantity would not fire.
 *
 * vendorId is NOT in this request — it is derived from the menu items
 * themselves inside OrderService. All items must belong to the same vendor,
 * which the service validates. This prevents a class of attack where a
 * client crafts an order claiming vendor A but including items from vendor B.
 */
public record OrderRequest(

    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    List<OrderItemRequest> items,

    @NotBlank(message = "Delivery address is required")
    @Size(max = 1000, message = "Delivery address must not exceed 1000 characters")
    String deliveryAddress,

    @Size(max = 500, message = "Notes must not exceed 500 characters")
    String notes
) {}