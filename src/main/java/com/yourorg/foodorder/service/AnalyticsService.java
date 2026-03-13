package com.yourorg.foodorder.service;

import com.yourorg.foodorder.dto.*;
import com.yourorg.foodorder.repository.*;
import com.yourorg.foodorder.repository.projection.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private static final int TOP_MENU_ITEMS_LIMIT = 10;

    private final OrderRepository orderRepository;
    private final MenuItemRepository menuItemRepository;
    private final VendorRepository vendorRepository;
    private final UserRepository userRepository;

    public AnalyticsService(OrderRepository orderRepository,
                             MenuItemRepository menuItemRepository,
                             VendorRepository vendorRepository,
                             UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.menuItemRepository = menuItemRepository;
        this.vendorRepository = vendorRepository;
        this.userRepository = userRepository;
    }

    // ─── Order Analytics ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public OrderAnalyticsResponse getOrderAnalytics() {
        long totalOrders = orderRepository.countAllOrders();

        Map<String, Long> ordersByStatus = orderRepository.countOrdersByStatus()
                .stream()
                .collect(Collectors.toMap(
                        OrderStatusCountProjection::getStatus,
                        OrderStatusCountProjection::getCount
                ));

        List<DailyOrderCountDto> ordersPerDay = orderRepository.countOrdersPerDay()
                .stream()
                .map(p -> new DailyOrderCountDto(p.getDate(), p.getCount()))
                .collect(Collectors.toList());

        return new OrderAnalyticsResponse(totalOrders, ordersByStatus, ordersPerDay);
    }

    // ─── Revenue Analytics ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RevenueAnalyticsResponse getRevenueAnalytics() {
        BigDecimal totalRevenue = orderRepository.sumTotalRevenue();
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;

        List<VendorRevenueDto> revenueByVendor = vendorRepository.findVendorRevenue()
                .stream()
                .map(p -> new VendorRevenueDto(p.getVendorId(), p.getVendorName(), p.getRevenue()))
                .collect(Collectors.toList());

        return new RevenueAnalyticsResponse(totalRevenue, revenueByVendor);
    }

    // ─── Top Menu Items ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TopMenuItemResponse> getTopMenuItems() {
        return menuItemRepository.findTopSellingMenuItems(TOP_MENU_ITEMS_LIMIT)
                .stream()
                .map(p -> new TopMenuItemResponse(p.getMenuItemId(), p.getName(), p.getTotalOrders()))
                .collect(Collectors.toList());
    }

    // ─── Vendor Performance ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<VendorPerformanceResponse> getVendorPerformance() {
        return vendorRepository.findVendorRevenue()
                .stream()
                .map(p -> new VendorPerformanceResponse(
                        p.getVendorId(),
                        p.getVendorName(),
                        p.getTotalOrders(),
                        p.getRevenue()))
                .collect(Collectors.toList());
    }

    // ─── System Metrics ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SystemMetricsResponse getSystemMetrics() {
        long activeUsers = userRepository.countActiveUsers();
        long totalOrders = orderRepository.countAllOrders();
        BigDecimal totalRevenue = orderRepository.sumTotalRevenue();
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;

        return new SystemMetricsResponse(activeUsers, totalOrders, totalRevenue);
    }
}