CREATE TABLE supplier_payables (
    receipt_id VARCHAR(64) PRIMARY KEY REFERENCES purchase_receipts(id),
    due_date VARCHAR(10) NOT NULL,
    created_by VARCHAR(64) NOT NULL REFERENCES users(id),
    created_at BIGINT NOT NULL
);

CREATE INDEX idx_supplier_payables_due ON supplier_payables(due_date, receipt_id);

CREATE TABLE supplier_payments (
    id VARCHAR(64) PRIMARY KEY,
    receipt_id VARCHAR(64) NOT NULL REFERENCES supplier_payables(receipt_id),
    amount_cents BIGINT NOT NULL CHECK (amount_cents > 0),
    method VARCHAR(16) NOT NULL CHECK (method IN ('CASH', 'TRANSFER', 'CARD')),
    reference VARCHAR(120) NOT NULL,
    paid_by VARCHAR(64) NOT NULL REFERENCES users(id),
    created_at BIGINT NOT NULL
);

CREATE INDEX idx_supplier_payments_receipt ON supplier_payments(receipt_id, created_at);
