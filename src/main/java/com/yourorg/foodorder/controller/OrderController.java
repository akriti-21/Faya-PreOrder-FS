package com.yourorg.foodorder.controller;

import com.yourorg.foodorder.dto.OrderTimelineResponse;
import com.yourorg.foodorder.service.OrderLifecycleService;
import com.yourorg.foodorder.util.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderLifecycleService orderLifecycleService;

    public OrderController(OrderLifecycleService orderLifecycleService) {
        this.orderLifecycleService = orderLifecycleService;
    }

    /**
     * GET /api/orders/{orderId}/timeline
     *
     * Users can view their own order timeline.
     * Admins can view any order timeline.
     */
    @GetMapping("/{orderId}/timeline")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderTimelineResponse>> getOrderTimeline(
            @PathVariable UUID orderId,
            Authentication authentication) {

        // Additional ownership check for non-admin users can be enforced
        // in the service layer by comparing userId from JWT with order.getUserId()
        OrderTimelineResponse timeline = orderLifecycleService.getOrderTimeline(orderId);
        return ResponseEntity.ok(ApiResponse.success(timeline));
    }

    /**
     * POST /api/orders/{orderId}/confirm  (Admin only)
     */
    @PostMapping("/{orderId}/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> confirmOrder(@PathVariable UUID orderId) {
        orderLifecycleService.confirmOrder(orderId);
        return ResponseEntity.ok(ApiResponse.success("Order confirmed"));
    }

    /**
     * POST /api/orders/{orderId}/deliver  (Admin only)
     */
    @PostMapping("/{orderId}/deliver")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> deliverOrder(@PathVariable UUID orderId) {
        orderLifecycleService.markDelivered(orderId);
        return ResponseEntity.ok(ApiResponse.success("Order marked as delivered"));
    }

    /**
     * POST /api/orders/{orderId}/cancel
     */
    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> cancelOrder(@PathVariable UUID orderId) {
        orderLifecycleService.cancelOrder(orderId);
        return ResponseEntity.ok(ApiResponse.success("Order cancelled"));
    }
}