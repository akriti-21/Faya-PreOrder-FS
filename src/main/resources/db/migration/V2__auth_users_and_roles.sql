-- =============================================================================
-- V2__auth_users_and_roles.sql
--
-- Replaces the V1 placeholder users table with the production auth schema.
--
-- Why drop and recreate?
--   V1 defined a simplified users table (single-column role, full_name,
--   active boolean) as a structural placeholder. V2 implements the real
--   auth model (firstName/lastName split, enabled flag, soft delete,
--   ManyToMany roles). The differences are incompatible with ALTER TABLE.
--
-- Drop order respects FK constraints (most dependent → least dependent).
-- Recreate order is the reverse (least dependent → most dependent).
--
-- IMPORTANT: This migration is safe on a new/empty database.
-- If you have existing data in V1 tables, backup first and migrate data
-- manually after this migration runs.
-- =============================================================================

-- =============================================================================
-- DROP dependent tables first (FK order)
-- =============================================================================

DROP TABLE IF EXISTS order_items   CASCADE;
DROP TABLE IF EXISTS orders        CASCADE;
DROP TABLE IF EXISTS menu_items    CASCADE;
DROP TABLE IF EXISTS restaurants   CASCADE;
DROP TABLE IF EXISTS user_roles    CASCADE;
DROP TABLE IF EXISTS users         CASCADE;
DROP TABLE IF EXISTS roles         CASCADE;

-- =============================================================================
-- roles — reference data, seeded below
-- =============================================================================

CREATE TABLE roles (
    id   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL,

    CONSTRAINT uq_roles_name UNIQUE (name)
);

COMMENT ON TABLE  roles      IS 'Named security authorities. Seeded by migration; not modified at runtime.';
COMMENT ON COLUMN roles.name IS 'Authority name. Must be prefixed with ROLE_ for Spring Security hasRole() compatibility.';

-- =============================================================================
-- users — core identity and authentication
-- =============================================================================

CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_users_email UNIQUE (email)
);

COMMENT ON TABLE  users                IS 'User accounts. Soft-deleted via deleted_at; never hard-deleted.';
COMMENT ON COLUMN users.email          IS 'Stored lowercase. Unique constraint enforces one account per address.';
COMMENT ON COLUMN users.password_hash  IS 'BCrypt(12) hash. The raw password is never stored.';
COMMENT ON COLUMN users.enabled        IS 'FALSE prevents login without deleting the record (suspension, fraud hold).';
COMMENT ON COLUMN users.deleted_at     IS 'NULL = active. Non-null = soft-deleted. Soft-delete preserves FK references.';

-- Authentication lookup — every login and JWT validation hits this index
CREATE UNIQUE INDEX idx_users_email
    ON users (email);

-- Partial index — only active accounts; WHERE clause matches UserRepository queries
CREATE INDEX idx_users_enabled
    ON users (enabled)
    WHERE enabled = TRUE;

-- Cleanup / admin queries — filter soft-deleted without full scan
CREATE INDEX idx_users_deleted_at
    ON users (deleted_at)
    WHERE deleted_at IS NOT NULL;

-- Auto-update updated_at on every row modification
-- Uses the trigger function created in V1 (set_updated_at).
-- If V1 did not create the function, define it here:
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW IS DISTINCT FROM OLD THEN
        NEW.updated_at = NOW();
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================================
-- user_roles — many-to-many join between users and roles
-- =============================================================================

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,

    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id)
);

COMMENT ON TABLE user_roles IS 'Join table for User ↔ Role many-to-many. Cascade delete removes memberships when user is deleted.';

CREATE INDEX idx_user_roles_user_id ON user_roles (user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles (role_id);

-- =============================================================================
-- Restore dependent tables from V1 (updated to reference new users.id)
-- =============================================================================

CREATE TABLE restaurants (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id      UUID         NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    name          VARCHAR(255) NOT NULL,
    description   TEXT,
    cuisine_type  VARCHAR(100),
    phone         VARCHAR(30),
    address       TEXT,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_restaurants_owner  ON restaurants (owner_id);
CREATE INDEX idx_restaurants_active ON restaurants (active) WHERE active = TRUE;

CREATE TRIGGER trg_restaurants_updated_at
    BEFORE UPDATE ON restaurants
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE menu_items (
    id            UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id UUID           NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    name          VARCHAR(255)   NOT NULL,
    description   TEXT,
    price         NUMERIC(10, 2) NOT NULL,
    category      VARCHAR(100),
    available     BOOLEAN        NOT NULL DEFAULT TRUE,
    deleted_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_menu_item_price CHECK (price >= 0)
);

CREATE INDEX idx_menu_items_restaurant ON menu_items (restaurant_id);
CREATE INDEX idx_menu_items_available  ON menu_items (available) WHERE available = TRUE;

CREATE TRIGGER trg_menu_items_updated_at
    BEFORE UPDATE ON menu_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE orders (
    id               UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id      UUID           NOT NULL REFERENCES users(id)       ON DELETE RESTRICT,
    restaurant_id    UUID           NOT NULL REFERENCES restaurants(id) ON DELETE RESTRICT,
    status           VARCHAR(50)    NOT NULL DEFAULT 'PENDING',
    total_amount     NUMERIC(10, 2) NOT NULL,
    delivery_address TEXT           NOT NULL,
    notes            TEXT,
    placed_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_order_status CHECK (
        status IN ('PENDING','CONFIRMED','PREPARING','READY',
                   'OUT_FOR_DELIVERY','DELIVERED','CANCELLED')
    ),
    CONSTRAINT chk_order_total CHECK (total_amount >= 0)
);

CREATE INDEX idx_orders_customer   ON orders (customer_id);
CREATE INDEX idx_orders_restaurant ON orders (restaurant_id);
CREATE INDEX idx_orders_status     ON orders (status);
CREATE INDEX idx_orders_placed_at  ON orders (placed_at DESC);

CREATE TRIGGER trg_orders_updated_at
    BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE order_items (
    id           UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id     UUID           NOT NULL REFERENCES orders(id)     ON DELETE CASCADE,
    menu_item_id UUID           NOT NULL REFERENCES menu_items(id) ON DELETE RESTRICT,
    quantity     INTEGER        NOT NULL,
    unit_price   NUMERIC(10, 2) NOT NULL,

    CONSTRAINT chk_order_item_qty   CHECK (quantity   > 0),
    CONSTRAINT chk_order_item_price CHECK (unit_price >= 0)
);

CREATE INDEX idx_order_items_order     ON order_items (order_id);
CREATE INDEX idx_order_items_menu_item ON order_items (menu_item_id);

-- =============================================================================
-- Seed data — roles
-- =============================================================================

INSERT INTO roles (name) VALUES
    ('ROLE_USER'),
    ('ROLE_ADMIN'),
    ('ROLE_RESTAURANT_OWNER')
ON CONFLICT (name) DO NOTHING;