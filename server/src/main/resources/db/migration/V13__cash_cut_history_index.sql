CREATE INDEX idx_cash_sessions_history ON cash_sessions(status, closed_at DESC, id DESC);
