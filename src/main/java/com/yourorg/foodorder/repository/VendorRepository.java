package com.yourorg.foodorder.repository;

import com.yourorg.foodorder.domain.Vendor;
import com.yourorg.foodorder.repository.projection.VendorRevenueProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VendorRepository extends JpaRepository<Vendor, UUID> {

    /**
     * Aggregates total revenue and order count per vendor from DELIVERED orders.
     */
    @Query(value = """
            SELECT v.id           AS vendorId,
                   v.name         AS vendorName,
                   COALESCE(SUM(o.total_amount), 0) AS revenue,
                   COUNT(o.id)    AS totalOrders
            FROM vendors v
            LEFT JOIN orders o ON o.vendor_id = v.id
                               AND o.status = 'DELIVERED'
            GROUP BY v.id, v.name
            ORDER BY revenue DESC
            """, nativeQuery = true)
    List<VendorRevenueProjection> findVendorRevenue();
}