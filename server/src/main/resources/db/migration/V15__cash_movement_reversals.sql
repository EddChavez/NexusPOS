ALTER TABLE cash_movements ADD COLUMN reverses_movement_id VARCHAR(64) REFERENCES cash_movements(id);
ALTER TABLE cash_movements ADD COLUMN authorized_by_user_id VARCHAR(64) REFERENCES users(id);
ALTER TABLE cash_movements ADD CONSTRAINT uq_cash_movement_reversal UNIQUE (reverses_movement_id);
ALTER TABLE cash_movements ADD CONSTRAINT ck_cash_movement_reversal_audit
    CHECK (reverses_movement_id IS NULL OR (reverses_movement_id <> id AND authorized_by_user_id IS NOT NULL));
CREATE INDEX idx_cash_movements_history ON cash_movements(session_id, created_at DESC, id DESC);
