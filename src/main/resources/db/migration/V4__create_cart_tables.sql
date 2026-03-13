-- =============================================================================
-- V4__create_cart_tables.sql
--
-- Creates the cart and cart_items tables for the Day 5 Cart System.
--
-- Design notes:
--
-- carts — one row per user.
--   The unique constraint on user_id enforces the one-cart-per-user model at
--   the DB level. CartService.getOrCreateCart() is safe to call concurrently
--   because the UNIQUE constraint prevents double-insert races (the second
--   INSERT will fail and be retried, returning the existing row).
--
-- cart_items — one row per (cart_id, menu_item_id) in normal operation.
--   The UNIQUE constraint on (cart_id, menu_item_id) enforces the merge rule
--   at the DB level. If CartService somehow calls INSERT with a duplicate,
--   the constraint throws DataIntegrityViolationException which
--   GlobalExceptionHandler maps to HTTP 409.
--
-- unit_price NUMERIC(10,2):
--   Monetary values must never use FLOAT. NUMERIC(10,2) matches the
--   precision of menu_items.price and order_items.unit_price — consistent
--   across the entire schema.
--
-- No ON DELETE CASCADE from carts to cart_items:
--   Cart rows are intentionally retained after checkout. CartService clears
--   items via a bulk DELETE query rather than relying on cascade. However,
--   we add ON DELETE CASCADE as a safety net for hard deletes (admin cleanup).
--
-- =============================================================================

-- =============================================================================
-- carts
-- =============================================================================

CREATE TABLE carts (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_carts_user_id UNIQUE (user_id)
);

CREATE INDEX idx_carts_user_id ON carts (user_id);

CREATE TRIGGER trg_carts_updated_at
    BEFORE UPDATE ON carts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE  carts IS 'One persistent cart per user. Retained after checkout for next session.';
COMMENT ON COLUMN carts.user_id    IS 'FK to users.id — one cart per user enforced by unique constraint.';
COMMENT ON COLUMN carts.updated_at IS 'Refreshed on every item add/remove/update via set_updated_at trigger.';

-- =============================================================================
-- cart_items
-- =============================================================================

CREATE TABLE cart_items (
    id           UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id      UUID           NOT NULL REFERENCES carts(id)      ON DELETE CASCADE,
    menu_item_id UUID           NOT NULL REFERENCES menu_items(id) ON DELETE CASCADE,
    quantity     INTEGER        NOT NULL,
    unit_price   NUMERIC(10, 2) NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_cart_items_cart_menu UNIQUE (cart_id, menu_item_id),
    CONSTRAINT chk_cart_item_qty   CHECK (quantity   >= 1),
    CONSTRAINT chk_cart_item_price CHECK (unit_price >= 0)
);

CREATE INDEX idx_cart_items_cart_id      ON cart_items (cart_id);
CREATE INDEX idx_cart_items_menu_item_id ON cart_items (menu_item_id);

COMMENT ON TABLE  cart_items IS 'Line items in a cart. One row per (cart, menu_item) — duplicate adds merge via CartService.';
COMMENT ON COLUMN cart_items.unit_price IS 'Price snapshot at add-to-cart time. Not a live FK to menu_items.price.';
COMMENT ON COLUMN cart_items.quantity   IS 'Current quantity. Updated in place on merge; minimum 1 enforced by check constraint.';