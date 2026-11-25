ALTER TABLE cash_sessions ADD COLUMN operator_user_id VARCHAR(64) REFERENCES users(id);
ALTER TABLE credit_movements ADD COLUMN payment_method VARCHAR(16);
