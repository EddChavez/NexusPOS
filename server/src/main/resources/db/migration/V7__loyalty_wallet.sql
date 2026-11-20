-- ============================================================================
-- V7: Monedero Electrónico, Puntos de Lealtad y Cashback
-- ============================================================================

-- 1. Campos de monedero en tabla customers
ALTER TABLE customers 
ADD COLUMN IF NOT EXISTS loyalty_balance_cents BIGINT NOT NULL DEFAULT 0;

ALTER TABLE customers 
ADD COLUMN IF NOT EXISTS total_loyalty_earned_cents BIGINT NOT NULL DEFAULT 0;

ALTER TABLE customers 
ADD COLUMN IF NOT EXISTS total_loyalty_redeemed_cents BIGINT NOT NULL DEFAULT 0;

-- 2. Valores iniciales de lealtad en settings (key-value)
INSERT INTO settings (setting_key, setting_value) VALUES ('loyalty_enabled', 'true') ON CONFLICT DO NOTHING;
INSERT INTO settings (setting_key, setting_value) VALUES ('loyalty_cashback_percent', '1.0') ON CONFLICT DO NOTHING;
INSERT INTO settings (setting_key, setting_value) VALUES ('loyalty_min_redeem_cents', '0') ON CONFLICT DO NOTHING;

-- 3. Tabla de movimientos de monedero electrónico
CREATE TABLE IF NOT EXISTS customer_loyalty_movements (
    id VARCHAR(64) PRIMARY KEY,
    customer_id VARCHAR(64) NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    sale_id VARCHAR(64) REFERENCES sales(id) ON DELETE SET NULL,
    type VARCHAR(32) NOT NULL, -- EARN, REDEEM, ADJUSTMENT, REFUND_REVERSAL, REFUND_CREDIT
    amount_cents BIGINT NOT NULL,
    balance_after_cents BIGINT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    user_id VARCHAR(64) NOT NULL DEFAULT 'usr-cajero',
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_loyalty_movements_customer ON customer_loyalty_movements(customer_id);
CREATE INDEX IF NOT EXISTS idx_loyalty_movements_sale ON customer_loyalty_movements(sale_id);
CREATE INDEX IF NOT EXISTS idx_loyalty_movements_created_at ON customer_loyalty_movements(created_at);
