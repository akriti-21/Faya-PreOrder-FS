package com.yourorg.foodorder.repository;

import com.yourorg.foodorder.domain.Order;
import com.yourorg.foodorder.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Order persistence and querying.
 *
 * All queries JOIN FETCH user, vendor, and items to prevent N+1 in
 * response mapping. The ORDER BY placed_at DESC convention puts the
 * most recent orders first, matching expected customer-facing sort order.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * All orders for a customer, paginated, newest first.
     * JOIN FETCH vendor avoids N+1 when rendering the vendor name per order.
     * items are lazy — use findByIdWithItems() for the order detail view.
     */
    @Query("SELECT o FROM Order o JOIN FETCH o.vendor " +
           "WHERE o.user.id = :userId " +
           "ORDER BY o.createdAt DESC")
    Page<Order> findByUserId(UUID userId, Pageable pageable);

    /**
     * Non-paginated list for users with manageable order history.
     */
    @Query("SELECT o FROM Order o JOIN FETCH o.vendor " +
           "WHERE o.user.id = :userId " +
           "ORDER BY o.createdAt DESC")
    List<Order> findByUserId(UUID userId);

    /**
     * All orders for a vendor — for vendor management and admin views.
     */
    @Query("SELECT o FROM Order o JOIN FETCH o.user " +
           "WHERE o.vendor.id = :vendorId " +
           "ORDER BY o.createdAt DESC")
    List<Order> findByVendorId(UUID vendorId);

    /**
     * Order detail query — fetches the order with all items and their menu items
     * in a single SQL query using two JOIN FETCHes.
     *
     * Without JOIN FETCH on items: Hibernate issues one SELECT per item (N+1).
     * Without JOIN FETCH on menuItem: another SELECT per item for the item name.
     * This single query eliminates both N+1 paths.
     *
     * Note: fetching multiple LAZY collections with JOIN FETCH produces a
     * Cartesian product at the SQL level. For orders with moderate item counts
     * (< 50 items) this is acceptable. For very large orders, split into two
     * queries (one for order, one for items) with @EntityGraph.
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "JOIN FETCH o.user " +
           "JOIN FETCH o.vendor " +
           "LEFT JOIN FETCH o.items i " +
           "LEFT JOIN FETCH i.menuItem " +
           "WHERE o.id = :orderId")
    Optional<Order> findByIdWithItems(UUID orderId);

    /**
     * Orders by status — useful for vendor dashboards and admin monitoring.
     */
    @Query("SELECT o FROM Order o JOIN FETCH o.user JOIN FETCH o.vendor " +
           "WHERE o.vendor.id = :vendorId AND o.status = :status " +
           "ORDER BY o.createdAt DESC")
    List<Order> findByVendorIdAndStatus(UUID vendorId, OrderStatus status);
}