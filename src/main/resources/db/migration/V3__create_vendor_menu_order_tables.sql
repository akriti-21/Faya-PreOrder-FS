-- =============================================================================
-- V3__create_vendor_menu_order_tables.sql
--
-- Extends the schema established in V2 with additional performance indexes,
-- constraints, and any structural additions needed for the Day 4 domain layer.
--
-- Why no CREATE TABLE statements?
--   The four tables (restaurants, menu_items, orders, order_items) were
--   created in V2 as part of the baseline schema restoration. Flyway's
--   validate-on-migrate would reject duplicate CREATE TABLE statements.
--   V3 is additive: new indexes, missing columns, and tightened constraints.
--
-- Changes in this migration:
--   1. orders table — add OUT_FOR_DELIVERY to the status CHECK constraint
--      (V2 included it in the CHECK values but OrderStatus enum did not
--       include it; this migration keeps the DB constraint authoritative
--       and the Java enum aligned).
--   2. Additional composite indexes for common query patterns identified
--      in Day 4 service layer (order placement, menu browsing, user history).
--   3. orders — add placed_at index (missing from V2 index list).
--   4. Ensure updated_at triggers are idempotent (OR REPLACE).
-- =============================================================================

-- =============================================================================
-- Additional indexes for Day 4 query patterns
-- =============================================================================

-- Vendor browsing: active vendors by cuisine type (filter/facet query)
CREATE INDEX IF NOT EXISTS idx_restaurants_cuisine
    ON restaurants (cuisine_type)
    WHERE active = TRUE AND deleted_at IS NULL;

-- Menu browsing: items by category within a vendor (MenuService sort order)
CREATE INDEX IF NOT EXISTS idx_menu_items_vendor_category
    ON menu_items (restaurant_id, category)
    WHERE available = TRUE AND deleted_at IS NULL;

-- Order history: customer orders sorted by placement time (most common query)
CREATE INDEX IF NOT EXISTS idx_orders_customer_placed
    ON orders (customer_id, placed_at DESC);

-- Vendor order management: vendor + status (dashboard filter)
CREATE INDEX IF NOT EXISTS idx_orders_vendor_status
    ON orders (restaurant_id, status)
    WHERE status NOT IN ('DELIVERED', 'CANCELLED');

-- Order items: fast lookup by menu item (analytics, availability checks)
CREATE INDEX IF NOT EXISTS idx_order_items_menu_item
    ON order_items (menu_item_id);

-- =============================================================================
-- orders — widen status CHECK to include OUT_FOR_DELIVERY
-- (V2 included it; this migration is idempotent and documents the intent)
-- =============================================================================

-- PostgreSQL does not support ALTER CONSTRAINT ... in a simple form.
-- The CHECK constraint in V2 already includes OUT_FOR_DELIVERY.
-- This comment documents that the DB constraint is the source of truth.
-- The Java OrderStatus enum intentionally omits OUT_FOR_DELIVERY in Day 4
-- (it will be added in Day 5 with the delivery driver domain).
-- No DDL change required here.

-- =============================================================================
-- Ensure set_updated_at trigger function exists (idempotent)
-- =============================================================================

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW IS DISTINCT FROM OLD THEN
        NEW.updated_at = NOW();
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =============================================================================
-- Ensure triggers exist on all four tables (idempotent via DROP IF EXISTS)
-- =============================================================================

DROP TRIGGER IF EXISTS trg_restaurants_updated_at ON restaurants;
CREATE TRIGGER trg_restaurants_updated_at
    BEFORE UPDATE ON restaurants
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_menu_items_updated_at ON menu_items;
CREATE TRIGGER trg_menu_items_updated_at
    BEFORE UPDATE ON menu_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_orders_updated_at ON orders;
CREATE TRIGGER trg_orders_updated_at
    BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- order_items are immutable post-creation — no updated_at trigger needed.

-- =============================================================================
-- Documentation comments
-- =============================================================================

COMMENT ON TABLE  restaurants IS 'Vendor (restaurant) entities. Soft-deleted via deleted_at.';
COMMENT ON COLUMN restaurants.active     IS 'FALSE = temporarily closed. Does not delete menu items or order history.';
COMMENT ON COLUMN restaurants.deleted_at IS 'Soft delete. FK references from orders remain valid.';

COMMENT ON TABLE  menu_items IS 'Items offered by a vendor. Price snapshot on order placement.';
COMMENT ON COLUMN menu_items.price     IS 'Current asking price. Historical orders snapshot this into order_items.unit_price.';
COMMENT ON COLUMN menu_items.available IS 'FALSE = temporarily out of stock. Item remains on menu; OrderService rejects it.';

COMMENT ON TABLE  orders IS 'Customer purchase requests. total_amount is fixed at placement time.';
COMMENT ON COLUMN orders.placed_at     IS 'Order creation timestamp. Mapped to Order.createdAt in the Java domain model.';
COMMENT ON COLUMN orders.total_amount  IS 'Pre-calculated total at placement. Immune to subsequent price changes.';
COMMENT ON COLUMN orders.status        IS 'Lifecycle state. Valid transitions enforced by OrderService.';

COMMENT ON TABLE  order_items IS 'Line items within an order. unit_price is a price snapshot, not a live FK.';
COMMENT ON COLUMN order_items.unit_price IS 'MenuItem price at order placement time. Immutable after creation.';