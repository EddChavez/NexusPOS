ALTER TABLE supplier_payments ADD COLUMN cash_movement_id VARCHAR(64) REFERENCES cash_movements(id);
ALTER TABLE supplier_payments ADD CONSTRAINT uq_supplier_payment_cash_movement UNIQUE (cash_movement_id);
