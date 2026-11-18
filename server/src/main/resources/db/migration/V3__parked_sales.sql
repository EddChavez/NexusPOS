-- NexusPOS PostgreSQL Migration V3: Parked Sales (Tickets en Espera Persistentes)

CREATE TABLE IF NOT EXISTS parked_sales (
    id VARCHAR(64) PRIMARY KEY,
    custom_label VARCHAR(128) NOT NULL,
    cashier_user_id VARCHAR(64) NOT NULL,
    cashier_name VARCHAR(128) NOT NULL,
    customer_id VARCHAR(64) REFERENCES customers(id) ON DELETE SET NULL,
    customer_name VARCHAR(256),
    register_id VARCHAR(64) NOT NULL DEFAULT 'caja-1',
    total_cents BIGINT NOT NULL,
    items_count INT NOT NULL DEFAULT 0,
    items_json TEXT NOT NULL,
    parked_at BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PARKED', -- PARKED, RETRIEVED, DISCARDED
    retrieved_at BIGINT,
    retrieved_by_user_id VARCHAR(64),
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_parked_sales_status ON parked_sales(status, parked_at DESC);
CREATE INDEX IF NOT EXISTS idx_parked_sales_register ON parked_sales(register_id, status);
