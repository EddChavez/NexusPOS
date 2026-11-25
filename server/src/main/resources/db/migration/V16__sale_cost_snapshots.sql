ALTER TABLE sale_lines ADD COLUMN cost_snapshot_cents BIGINT;
ALTER TABLE sale_lines ADD COLUMN base_factor_millis BIGINT;
ALTER TABLE sale_lines ADD COLUMN snapshot_product_name VARCHAR(256);
ALTER TABLE sale_lines ADD COLUMN snapshot_sku VARCHAR(64);
ALTER TABLE sale_lines ADD COLUMN snapshot_base_unit VARCHAR(16);
ALTER TABLE sale_lines ADD COLUMN snapshot_department_id VARCHAR(64);
ALTER TABLE sale_lines ADD COLUMN snapshot_department_name VARCHAR(256);

CREATE INDEX idx_sales_report_date ON sales(created_at);
CREATE INDEX idx_sales_cancelled_date ON sales(cancelled_at);
CREATE INDEX idx_sale_refunds_report_date ON sale_refunds(created_at);
CREATE INDEX idx_sale_lines_sale ON sale_lines(sale_id);
CREATE INDEX idx_sale_refund_lines_refund ON sale_refund_lines(refund_id);
