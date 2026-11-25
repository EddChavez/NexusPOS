ALTER TABLE users ADD COLUMN is_root BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN email VARCHAR(254) NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN phone VARCHAR(40) NOT NULL DEFAULT '';

CREATE TABLE store_setup (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    root_user_id VARCHAR(64) REFERENCES users(id),
    business_id VARCHAR(64),
    branch_id VARCHAR(64),
    completed_at BIGINT
);
INSERT INTO store_setup (id) VALUES (1);

CREATE TABLE store_setup_codes (
    purpose VARCHAR(16) PRIMARY KEY,
    code_hash VARCHAR(64) NOT NULL,
    expires_at BIGINT NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE desktop_sessions (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL REFERENCES users(id),
    expires_at BIGINT NOT NULL
);
CREATE INDEX idx_desktop_sessions_user ON desktop_sessions(user_id);

CREATE TABLE security_audit (
    id VARCHAR(64) PRIMARY KEY,
    action VARCHAR(64) NOT NULL,
    actor_user_id VARCHAR(64) REFERENCES users(id),
    target_id VARCHAR(64),
    created_at BIGINT NOT NULL
);
