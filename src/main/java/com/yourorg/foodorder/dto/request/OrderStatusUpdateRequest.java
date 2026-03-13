package com.yourorg.foodorder.dto.request;

import com.yourorg.foodorder.domain.OrderStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Inbound DTO for PUT /api/v1/admin/orders/{orderId}/status.
 * Admin-only endpoint for advancing order lifecycle state.
 */
public record OrderStatusUpdateRequest(

    @NotNull(message = "Status is required")
    OrderStatus status
) {}
