-- NexusPOS PostgreSQL Initial Schema (V1)

CREATE TABLE IF NOT EXISTS departments (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS products (
    id VARCHAR(64) PRIMARY KEY,
    sku VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(256) NOT NULL,
    department_id VARCHAR(64) REFERENCES departments(id),
    base_unit VARCHAR(16) NOT NULL, -- PZA, KG, LT
    cost_cents BIGINT NOT NULL DEFAULT 0,
    tax_rate NUMERIC(5,4) NOT NULL DEFAULT 0.0,
    uses_inventory BOOLEAN NOT NULL DEFAULT TRUE,
    min_stock_millis BIGINT NOT NULL DEFAULT 0,
    max_stock_millis BIGINT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS product_presentations (
    id VARCHAR(64) PRIMARY KEY,
    product_id VARCHAR(64) NOT NULL REFERENCES products(id),
    barcode VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(256) NOT NULL,
    factor_millis BIGINT NOT NULL DEFAULT 1000, -- 1000 = 1 base unit, 24000 = caja 24
    price_cents BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS stock_balances (
    product_id VARCHAR(64) PRIMARY KEY REFERENCES products(id),
    quantity_millis BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS stock_movements (
    id VARCHAR(64) PRIMARY KEY,
    product_id VARCHAR(64) NOT NULL REFERENCES products(id),
    type VARCHAR(32) NOT NULL, -- SALE, REFUND, ENTRY, ADJUSTMENT
    quantity_millis BIGINT NOT NULL,
    reason VARCHAR(256) NOT NULL,
    reference VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS customers (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    phone VARCHAR(32) NOT NULL DEFAULT '',
    credit_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    credit_limit_cents BIGINT NOT NULL DEFAULT 0,
    current_balance_cents BIGINT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS credit_movements (
    id VARCHAR(64) PRIMARY KEY,
    customer_id VARCHAR(64) NOT NULL REFERENCES customers(id),
    type VARCHAR(32) NOT NULL, -- CHARGE, PAYMENT, REFUND, ADJUSTMENT
    amount_cents BIGINT NOT NULL,
    balance_after_cents BIGINT NOT NULL,
    sale_id VARCHAR(64),
    cash_session_id VARCHAR(64),
    description VARCHAR(256) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS cash_registers (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS cash_sessions (
    id VARCHAR(64) PRIMARY KEY,
    register_id VARCHAR(64) NOT NULL REFERENCES cash_registers(id),
    user_id VARCHAR(64) NOT NULL,
    initial_fund_cents BIGINT NOT NULL,
    opened_at BIGINT NOT NULL,
    closed_at BIGINT,
    expected_cash_cents BIGINT NOT NULL DEFAULT 0,
    counted_cash_cents BIGINT,
    difference_cents BIGINT,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN', -- OPEN, CLOSED
    notes TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS cash_movements (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL REFERENCES cash_sessions(id),
    type VARCHAR(32) NOT NULL, -- CASH_IN, CASH_OUT
    amount_cents BIGINT NOT NULL,
    concept VARCHAR(256) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS counters (
    name VARCHAR(64) PRIMARY KEY,
    current_value BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS sales (
    id VARCHAR(64) PRIMARY KEY,
    folio BIGINT NOT NULL UNIQUE,
    cash_session_id VARCHAR(64) NOT NULL REFERENCES cash_sessions(id),
    user_id VARCHAR(64) NOT NULL,
    customer_id VARCHAR(64) REFERENCES customers(id),
    status VARCHAR(32) NOT NULL, -- COMPLETED, CANCELLED, REFUNDED
    subtotal_cents BIGINT NOT NULL,
    tax_cents BIGINT NOT NULL,
    total_cents BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS sale_lines (
    id VARCHAR(64) PRIMARY KEY,
    sale_id VARCHAR(64) NOT NULL REFERENCES sales(id),
    product_id VARCHAR(64) REFERENCES products(id),
    presentation_id VARCHAR(64) REFERENCES product_presentations(id),
    description VARCHAR(256) NOT NULL,
    quantity_millis BIGINT NOT NULL,
    unit_price_cents BIGINT NOT NULL,
    tax_rate NUMERIC(5,4) NOT NULL,
    tax_cents BIGINT NOT NULL,
    total_cents BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS sale_payments (
    id VARCHAR(64) PRIMARY KEY,
    sale_id VARCHAR(64) NOT NULL REFERENCES sales(id),
    method VARCHAR(32) NOT NULL, -- CASH, CARD, TRANSFER, CREDIT
    amount_cents BIGINT NOT NULL,
    received_cents BIGINT NOT NULL DEFAULT 0,
    change_cents BIGINT NOT NULL DEFAULT 0,
    reference VARCHAR(128) NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    pin_hash VARCHAR(128) NOT NULL,
    role VARCHAR(32) NOT NULL, -- CASHIER, SUPERVISOR, ADMIN
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS devices (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    type VARCHAR(32) NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at BIGINT NOT NULL,
    last_seen_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS pairing_codes (
    code VARCHAR(16) PRIMARY KEY,
    expires_at BIGINT NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS settings (
    setting_key VARCHAR(64) PRIMARY KEY,
    setting_value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS idempotency_keys (
    key_id VARCHAR(128) PRIMARY KEY,
    request_hash VARCHAR(128) NOT NULL,
    response_body TEXT NOT NULL,
    created_at BIGINT NOT NULL
);

-- Initial Counters
INSERT INTO counters (name, current_value) VALUES ('sale_folio', 1000) ON CONFLICT DO NOTHING;

-- Initial Settings
INSERT INTO settings (setting_key, setting_value) VALUES ('allow_negative_stock', 'true') ON CONFLICT DO NOTHING;
INSERT INTO settings (setting_key, setting_value) VALUES ('store_name', 'Mi Tiendita de Abarrotes') ON CONFLICT DO NOTHING;
INSERT INTO settings (setting_key, setting_value) VALUES ('store_address', 'Av. Principal #100') ON CONFLICT DO NOTHING;
INSERT INTO settings (setting_key, setting_value) VALUES ('store_phone', '555-987-6543') ON CONFLICT DO NOTHING;
INSERT INTO settings (setting_key, setting_value) VALUES ('ticket_footer', '¡Gracias por su compra! Vuelva pronto.') ON CONFLICT DO NOTHING;

-- Initial Cash Register
INSERT INTO cash_registers (id, name, is_active) VALUES ('caja-1', 'Caja Principal', TRUE) ON CONFLICT DO NOTHING;
