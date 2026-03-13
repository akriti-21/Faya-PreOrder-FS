-- =============================================================================
-- V5__create_payment_tables.sql
--
-- Creates the payments table for the Day 6 Payment System.
--
-- Design notes:
--
-- payments.order_id FK:
--   References orders(id). One payment per order in the normal flow,
--   but no UNIQUE constraint on order_id — this allows a failed payment to
--   be followed by a new payment attempt on the same order.
--   The application layer (PaymentService) prevents duplicate SUCCESS payments
--   via its existsByOrderIdAndStatus() guard.
--
-- amount NUMERIC(10,2):
--   Monetary values must never use FLOAT. Matches orders.total_amount precision.
--   Validated by PaymentService to equal orders.total_amount at creation time.
--
-- status VARCHAR(20):
--   Stored as the Java enum name (PENDING, SUCCESS, FAILED, REFUNDED).
--   CHECK constraint mirrors the Java PaymentStatus enum values.
--
-- payment_method VARCHAR(20):
--   Stored as the Java enum name (CARD, UPI, WALLET, COD).
--   CHECK constraint mirrors the Java PaymentMethod enum values.
--
-- transaction_id VARCHAR(100):
--   Nullable — populated after gateway responds. Sufficient for UUID-format
--   gateway references and short alphanumeric codes from real providers.
-- =============================================================================

CREATE TABLE payments (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID           NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    amount          NUMERIC(10, 2) NOT NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    payment_method  VARCHAR(20)    NOT NULL,
    transaction_id  VARCHAR(100),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_payment_amount  CHECK (amount > 0),
    CONSTRAINT chk_payment_status  CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED')),
    CONSTRAINT chk_payment_method  CHECK (payment_method IN ('CARD', 'UPI', 'WALLET', 'COD'))
);

-- Primary lookup path: order detail page loads payment alongside order
CREATE INDEX idx_payments_order_id ON payments (order_id);

-- Status-based queries for admin monitoring and refund processing
CREATE INDEX idx_payments_status ON payments (status);

-- Trigger for updated_at
CREATE TRIGGER trg_payments_updated_at
    BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Column documentation
COMMENT ON TABLE  payments IS 'Payment records for orders. One PENDING+SUCCESS per order; multiple attempts allowed on FAILED.';
COMMENT ON COLUMN payments.order_id       IS 'FK to orders. No unique constraint — allows retry after FAILED payment.';
COMMENT ON COLUMN payments.amount         IS 'Must equal orders.total_amount — validated by PaymentService.';
COMMENT ON COLUMN payments.status         IS 'PENDING → SUCCESS or FAILED. SUCCESS → REFUNDED.';
COMMENT ON COLUMN payments.transaction_id IS 'Gateway reference. NULL until gateway responds.';
COMMENT ON COLUMN payments.payment_method IS 'COD always succeeds in gateway simulation.';