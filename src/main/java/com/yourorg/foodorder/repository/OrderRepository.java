package com.yourorg.foodorder.repository;

import com.yourorg.foodorder.domain.Order;
import com.yourorg.foodorder.repository.projection.DailyOrderCountProjection;
import com.yourorg.foodorder.repository.projection.OrderStatusCountProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    // ─── Analytics Queries ────────────────────────────────────────────────────

    @Query("SELECT COUNT(o) FROM Order o")
    long countAllOrders();

    @Query("""
            SELECT o.status AS status, COUNT(o) AS count
            FROM Order o
            GROUP BY o.status
            """)
    List<OrderStatusCountProjection> countOrdersByStatus();

    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0)
            FROM Order o
            WHERE o.status = 'DELIVERED'
            """)
    java.math.BigDecimal sumTotalRevenue();

    @Query(value = """
            SELECT CAST(created_at AS DATE) AS date,
                   COUNT(*)                 AS count
            FROM orders
            GROUP BY CAST(created_at AS DATE)
            ORDER BY date DESC
            LIMIT 30
            """, nativeQuery = true)
    List<DailyOrderCountProjection> countOrdersPerDay();
}