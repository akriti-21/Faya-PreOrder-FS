package com.yourorg.foodorder.dto.response;

import com.yourorg.foodorder.domain.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Outbound representation of an Order.
 *
 * Includes the full item list — callers should use the list endpoint
 * (which returns summary-level OrderResponse without items) for list views,
 * and the detail endpoint for the full item breakdown.
 *
 * Two factory methods:
 *   summary()  — excludes items (used in list responses to reduce payload)
 *   from()     — full representation including all order items
 *
 * vendor is embedded as a nested VendorResponse for client convenience
 * (clients need vendor name and address to display the order card without
 * a second API call).
 */
public record OrderResponse(
    UUID                  orderId,
    VendorResponse        vendor,
    List<OrderItemResponse> items,
    BigDecimal            totalAmount,
    String                status,
    String                deliveryAddress,
    String                notes,
    Instant               createdAt,
    Instant               updatedAt
) {

    /** Full representation — used for GET /api/v1/orders/{id}. */
    public static OrderResponse from(Order o) {
        List<OrderItemResponse> itemResponses = o.getItems().stream()
                .map(OrderItemResponse::from)
                .collect(Collectors.toUnmodifiableList());

        return new OrderResponse(
            o.getId(),
            VendorResponse.from(o.getVendor()),
            itemResponses,
            o.getTotalAmount(),
            o.getStatus().name(),
            o.getDeliveryAddress(),
            o.getNotes(),
            o.getCreatedAt(),
            o.getUpdatedAt()
        );
    }

    /**
     * Summary representation — used for GET /api/v1/orders (list).
     * Omits items to reduce payload; clients call /{id} for line details.
     */
    public static OrderResponse summary(Order o) {
        return new OrderResponse(
            o.getId(),
            VendorResponse.from(o.getVendor()),
            List.of(),
            o.getTotalAmount(),
            o.getStatus().name(),
            o.getDeliveryAddress(),
            o.getNotes(),
            o.getCreatedAt(),
            o.getUpdatedAt()
        );
    }
}