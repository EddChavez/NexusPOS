-- ============================================================================
-- V5: Devoluciones Parciales de Mercancía y Notas de Devolución
-- ============================================================================

CREATE TABLE IF NOT EXISTS sale_refunds (
    id VARCHAR(64) PRIMARY KEY,
    folio BIGINT NOT NULL UNIQUE,
    sale_id VARCHAR(64) NOT NULL REFERENCES sales(id),
    cash_session_id VARCHAR(64) NOT NULL REFERENCES cash_sessions(id),
    user_id VARCHAR(64) NOT NULL REFERENCES users(id),
    authorized_by_user_id VARCHAR(64) REFERENCES users(id),
    refund_method VARCHAR(32) NOT NULL DEFAULT 'CASH', -- CASH, CREDIT
    reason VARCHAR(64) NOT NULL, -- DEFECTO, CADUCADO, CAMBIO, INCONFORMIDAD, OTRO
    notes TEXT NOT NULL DEFAULT '',
    subtotal_cents BIGINT NOT NULL,
    tax_cents BIGINT NOT NULL,
    total_cents BIGINT NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS sale_refund_lines (
    id VARCHAR(64) PRIMARY KEY,
    refund_id VARCHAR(64) NOT NULL REFERENCES sale_refunds(id) ON DELETE CASCADE,
    sale_line_id VARCHAR(64) NOT NULL REFERENCES sale_lines(id),
    product_id VARCHAR(64) REFERENCES products(id),
    presentation_id VARCHAR(64) REFERENCES product_presentations(id),
    description VARCHAR(255) NOT NULL,
    quantity_millis BIGINT NOT NULL,
    unit_price_cents BIGINT NOT NULL,
    tax_rate DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    tax_cents BIGINT NOT NULL DEFAULT 0,
    total_cents BIGINT NOT NULL,
    reenter_stock BOOLEAN NOT NULL DEFAULT TRUE,
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sale_refunds_sale_id ON sale_refunds(sale_id);
CREATE INDEX IF NOT EXISTS idx_sale_refunds_session_id ON sale_refunds(cash_session_id);
CREATE INDEX IF NOT EXISTS idx_sale_refund_lines_sale_line ON sale_refund_lines(sale_line_id);

INSERT INTO counters (name, current_value)
VALUES ('sale_refund_folio', 0)
ON CONFLICT DO NOTHING;
