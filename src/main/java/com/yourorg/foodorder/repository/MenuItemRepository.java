package com.yourorg.foodorder.repository;

import com.yourorg.foodorder.domain.MenuItem;
import com.yourorg.foodorder.repository.projection.TopMenuItemProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {

    /**
     * Returns top N menu items ranked by the number of times they appear in order line items.
     * Assumes an order_items join table with columns: menu_item_id, order_id.
     */
    @Query(value = """
            SELECT oi.menu_item_id  AS menuItemId,
                   mi.name          AS name,
                   COUNT(oi.order_id) AS totalOrders
            FROM order_items oi
            JOIN menu_items mi ON mi.id = oi.menu_item_id
            GROUP BY oi.menu_item_id, mi.name
            ORDER BY totalOrders DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<TopMenuItemProjection> findTopSellingMenuItems(@Param("limit") int limit);
}