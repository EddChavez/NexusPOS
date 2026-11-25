ALTER TABLE sales ADD COLUMN cancellation_reason TEXT;
ALTER TABLE sales ADD COLUMN cancelled_at BIGINT;
CREATE INDEX idx_sales_session_folio ON sales(cash_session_id, folio DESC);
