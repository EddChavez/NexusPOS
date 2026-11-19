-- ============================================================================
-- V6: Control de Lotes y Fechas de Caducidad (Perecederos)
-- ============================================================================

ALTER TABLE products 
ADD COLUMN IF NOT EXISTS is_perishable BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE products 
ADD COLUMN IF NOT EXISTS shelf_life_alert_days INT NOT NULL DEFAULT 7;

CREATE TABLE IF NOT EXISTS product_lots (
    id VARCHAR(64) PRIMARY KEY,
    product_id VARCHAR(64) NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    lot_number VARCHAR(64) NOT NULL DEFAULT 'S/L',
    expiration_date VARCHAR(10) NOT NULL, -- Formato ISO: YYYY-MM-DD
    quantity_millis BIGINT NOT NULL DEFAULT 0,
    initial_quantity_millis BIGINT NOT NULL DEFAULT 0,
    cost_cents BIGINT NOT NULL DEFAULT 0,
    notes TEXT NOT NULL DEFAULT '',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    CONSTRAINT uq_product_lot_exp UNIQUE (product_id, lot_number, expiration_date)
);

CREATE INDEX IF NOT EXISTS idx_product_lots_product_id ON product_lots(product_id);
CREATE INDEX IF NOT EXISTS idx_product_lots_expiration_date ON product_lots(expiration_date);
CREATE INDEX IF NOT EXISTS idx_product_lots_active ON product_lots(is_active);
