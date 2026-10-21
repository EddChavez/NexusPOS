-- NexusPOS Migration V2: Customers & Credit MVP Enhancements

ALTER TABLE customers ADD COLUMN IF NOT EXISTS folio VARCHAR(32);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS email VARCHAR(128) NOT NULL DEFAULT '';
ALTER TABLE customers ADD COLUMN IF NOT EXISTS rfc VARCHAR(20) NOT NULL DEFAULT '';
ALTER TABLE customers ADD COLUMN IF NOT EXISTS address TEXT NOT NULL DEFAULT '';
ALTER TABLE customers ADD COLUMN IF NOT EXISTS notes TEXT NOT NULL DEFAULT '';
ALTER TABLE customers ADD COLUMN IF NOT EXISTS has_credit_limit BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS created_at BIGINT NOT NULL DEFAULT 0;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS updated_at BIGINT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX IF NOT EXISTS idx_customers_folio ON customers(folio);
CREATE INDEX IF NOT EXISTS idx_customers_search ON customers(name, phone);

INSERT INTO counters (name, current_value) VALUES ('customer_folio', 100) ON CONFLICT DO NOTHING;

ALTER TABLE credit_movements ADD COLUMN IF NOT EXISTS receipt_folio VARCHAR(32);
CREATE INDEX IF NOT EXISTS idx_credit_mov_customer ON credit_movements(customer_id, created_at DESC);
