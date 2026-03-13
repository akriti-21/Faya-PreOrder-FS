package com.yourorg.foodorder.controller;

import com.yourorg.foodorder.dto.request.OrderStatusUpdateRequest;
import com.yourorg.foodorder.dto.response.ApiResponse;
import com.yourorg.foodorder.dto.response.OrderResponse;
import com.yourorg.foodorder.service.OrderLifecycleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * AdminOrderController — admin endpoints for order management.
 *
 * All endpoints require ROLE_ADMIN. No customer-level operations here.
 *
 * PUT /api/v1/admin/orders/{orderId}/status — advance order through lifecycle
 * GET /api/v1/admin/orders                   — all orders across all vendors/users
 *
 * Design note on /admin prefix:
 *   Admin endpoints are grouped under /api/v1/admin/ for clear separation from
 *   customer-facing /api/v1/orders. This makes it easy to apply a separate
 *   rate limit, audit log, or IP allowlist at the API gateway level without
 *   complex path-matching.
 *
 * Specific lifecycle shortcuts (confirm, prepare, deliver, cancel) are handled
 * by OrderLifecycleService. The generic PUT /status endpoint supports any valid
 * transition, giving admins full control without needing separate endpoints
 * for every state.
 */
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")  // class-level — all endpoints in this controller are admin-only
public class AdminOrderController {

    private final OrderLifecycleService orderLifecycleService;

    /**
     * GET /api/v1/admin/orders
     * Returns all orders across the platform, newest first.
     * Returns summaries — call the existing GET /api/v1/orders/{id} for detail.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getAllOrders() {
        List<OrderResponse> orders = orderLifecycleService.getAllOrders();
        return ResponseEntity.ok(
                ApiResponse.success("Orders retrieved successfully.", orders));
    }

    /**
     * PUT /api/v1/admin/orders/{orderId}/status
     * Updates the order to the requested status.
     * Validates the transition is legal — invalid transitions return 409.
     */
    @PutMapping("/{orderId}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderStatusUpdateRequest request) {

        OrderResponse order = orderLifecycleService.updateOrderStatus(orderId, request.status());
        return ResponseEntity.ok(
                ApiResponse.success("Order status updated to " + request.status() + ".", order));
    }

    /**
     * PUT /api/v1/admin/orders/{orderId}/confirm
     * Shortcut: PENDING → CONFIRMED
     */
    @PutMapping("/{orderId}/confirm")
    public ResponseEntity<ApiResponse<OrderResponse>> confirmOrder(@PathVariable UUID orderId) {
        OrderResponse order = orderLifecycleService.updateOrderStatus(
                orderId, com.yourorg.foodorder.domain.OrderStatus.CONFIRMED);
        return ResponseEntity.ok(ApiResponse.success("Order confirmed.", order));
    }

    /**
     * PUT /api/v1/admin/orders/{orderId}/prepare
     * Shortcut: CONFIRMED → PREPARING
     */
    @PutMapping("/{orderId}/prepare")
    public ResponseEntity<ApiResponse<OrderResponse>> prepareOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success("Order now preparing.",
                orderLifecycleService.markOrderPreparing(orderId)));
    }

    /**
     * PUT /api/v1/admin/orders/{orderId}/ready
     * Shortcut: PREPARING → READY
     */
    @PutMapping("/{orderId}/ready")
    public ResponseEntity<ApiResponse<OrderResponse>> readyOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success("Order is ready.",
                orderLifecycleService.markOrderReady(orderId)));
    }

    /**
     * PUT /api/v1/admin/orders/{orderId}/deliver
     * Shortcut: READY → DELIVERED
     */
    @PutMapping("/{orderId}/deliver")
    public ResponseEntity<ApiResponse<OrderResponse>> deliverOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success("Order delivered.",
                orderLifecycleService.markOrderDelivered(orderId)));
    }

    /**
     * PUT /api/v1/admin/orders/{orderId}/cancel
     * Cancels an order from any non-terminal state.
     */
    @PutMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success("Order cancelled.",
                orderLifecycleService.cancelOrder(orderId)));
    }
}