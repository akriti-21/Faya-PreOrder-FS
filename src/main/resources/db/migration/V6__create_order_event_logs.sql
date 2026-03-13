-- V6__create_order_event_logs.sql
-- Order event timeline table for domain event tracking

CREATE TABLE order_event_logs
(
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    order_id    UUID         NOT NULL,
    event_type  VARCHAR(100) NOT NULL,
    description TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_order_event_logs PRIMARY KEY (id)
);

-- Index for fast timeline lookups by order
CREATE INDEX idx_order_event_logs_order_id
    ON order_event_logs (order_id);

-- Optional: foreign key to orders table if it exists
-- ALTER TABLE order_event_logs
--     ADD CONSTRAINT fk_order_event_logs_order
--     FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE;