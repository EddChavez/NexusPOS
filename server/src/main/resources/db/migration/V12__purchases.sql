CREATE TABLE suppliers (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    tax_id VARCHAR(32) NOT NULL DEFAULT '',
    phone VARCHAR(64) NOT NULL DEFAULT '',
    email VARCHAR(254) NOT NULL DEFAULT '',
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE purchase_receipts (
    id VARCHAR(64) PRIMARY KEY,
    supplier_id VARCHAR(64) NOT NULL REFERENCES suppliers(id),
    supplier_name VARCHAR(256) NOT NULL,
    document_type VARCHAR(24) NOT NULL,
    document_number VARCHAR(64) NOT NULL,
    total_cents BIGINT NOT NULL CHECK (total_cents >= 0),
    notes VARCHAR(1000) NOT NULL DEFAULT '',
    received_by VARCHAR(64) NOT NULL REFERENCES users(id),
    created_at BIGINT NOT NULL,
    UNIQUE (supplier_id, document_type, document_number)
);
CREATE INDEX idx_purchase_receipts_created ON purchase_receipts(created_at);

CREATE TABLE purchase_receipt_lines (
    id VARCHAR(64) PRIMARY KEY,
    receipt_id VARCHAR(64) NOT NULL REFERENCES purchase_receipts(id),
    position INT NOT NULL,
    product_id VARCHAR(64) NOT NULL REFERENCES products(id),
    product_name VARCHAR(256) NOT NULL,
    sku VARCHAR(64) NOT NULL,
    base_unit VARCHAR(16) NOT NULL,
    quantity_millis BIGINT NOT NULL CHECK (quantity_millis > 0),
    unit_cost_cents BIGINT NOT NULL CHECK (unit_cost_cents >= 0),
    total_cents BIGINT NOT NULL CHECK (total_cents >= 0),
    lot_id VARCHAR(64) REFERENCES product_lots(id),
    lot_number VARCHAR(64),
    expiration_date VARCHAR(10)
);
CREATE INDEX idx_purchase_lines_receipt ON purchase_receipt_lines(receipt_id);

CREATE TABLE purchase_returns (
    id VARCHAR(64) PRIMARY KEY,
    receipt_id VARCHAR(64) NOT NULL REFERENCES purchase_receipts(id),
    reason VARCHAR(256) NOT NULL,
    total_cents BIGINT NOT NULL CHECK (total_cents >= 0),
    returned_by VARCHAR(64) NOT NULL REFERENCES users(id),
    created_at BIGINT NOT NULL
);
CREATE INDEX idx_purchase_returns_receipt ON purchase_returns(receipt_id);

CREATE TABLE purchase_return_lines (
    return_id VARCHAR(64) NOT NULL REFERENCES purchase_returns(id),
    receipt_line_id VARCHAR(64) NOT NULL REFERENCES purchase_receipt_lines(id),
    quantity_millis BIGINT NOT NULL CHECK (quantity_millis > 0),
    total_cents BIGINT NOT NULL CHECK (total_cents >= 0),
    PRIMARY KEY (return_id, receipt_line_id)
);
CREATE INDEX idx_purchase_return_line ON purchase_return_lines(receipt_line_id);
