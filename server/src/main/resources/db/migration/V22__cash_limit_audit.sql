ALTER TABLE security_audit ADD COLUMN approved_by_user_id VARCHAR(64) REFERENCES users(id);
