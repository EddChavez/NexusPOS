-- V4__denominations_and_discounts.sql
-- Tabla para registrar el desglose físico de billetes y monedas en el arqueo de cada sesión de caja
CREATE TABLE IF NOT EXISTS cash_session_denominations (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL REFERENCES cash_sessions(id) ON DELETE CASCADE,
    denomination_cents BIGINT NOT NULL,
    is_coin BOOLEAN NOT NULL DEFAULT FALSE,
    pieces INT NOT NULL DEFAULT 0,
    total_cents BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_cash_denom_session ON cash_session_denominations(session_id);

-- Soporte de descuentos en ventas
ALTER TABLE sales ADD COLUMN IF NOT EXISTS discount_cents BIGINT NOT NULL DEFAULT 0;

-- Soporte de descuentos en partidas de venta
ALTER TABLE sale_lines ADD COLUMN IF NOT EXISTS discount_cents BIGINT NOT NULL DEFAULT 0;
ALTER TABLE sale_lines ADD COLUMN IF NOT EXISTS discount_percent NUMERIC(5,2) NOT NULL DEFAULT 0.0;
