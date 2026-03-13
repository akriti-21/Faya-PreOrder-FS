package com.yourorg.foodorder.controller;

import com.yourorg.foodorder.dto.*;
import com.yourorg.foodorder.service.AnalyticsService;
import com.yourorg.foodorder.util.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/analytics")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnalyticsController {

    private final AnalyticsService analyticsService;

    public AdminAnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * GET /api/admin/analytics/orders
     * Total orders, breakdown by status, and daily counts (last 30 days).
     */
    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<OrderAnalyticsResponse>> getOrderAnalytics() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getOrderAnalytics()));
    }

    /**
     * GET /api/admin/analytics/revenue
     * Total revenue and revenue broken down per vendor.
     */
    @GetMapping("/revenue")
    public ResponseEntity<ApiResponse<RevenueAnalyticsResponse>> getRevenueAnalytics() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getRevenueAnalytics()));
    }

    /**
     * GET /api/admin/analytics/menu-items
     * Top 10 selling menu items ranked by order frequency.
     */
    @GetMapping("/menu-items")
    public ResponseEntity<ApiResponse<List<TopMenuItemResponse>>> getTopMenuItems() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getTopMenuItems()));
    }

    /**
     * GET /api/admin/analytics/vendors
     * Per-vendor order count and revenue.
     */
    @GetMapping("/vendors")
    public ResponseEntity<ApiResponse<List<VendorPerformanceResponse>>> getVendorPerformance() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getVendorPerformance()));
    }

    /**
     * GET /api/admin/analytics/system
     * High-level system KPIs: active users, total orders, total revenue.
     */
    @GetMapping("/system")
    public ResponseEntity<ApiResponse<SystemMetricsResponse>> getSystemMetrics() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getSystemMetrics()));
    }
}