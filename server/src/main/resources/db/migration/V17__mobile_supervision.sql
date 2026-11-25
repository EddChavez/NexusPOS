CREATE TABLE mobile_pairing_codes (
    code_hash VARCHAR(64) PRIMARY KEY,
    expires_at BIGINT NOT NULL
);

CREATE TABLE mobile_sessions (
    token_hash VARCHAR(64) PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL REFERENCES devices(id),
    user_id VARCHAR(64) NOT NULL REFERENCES users(id),
    expires_at BIGINT NOT NULL,
    UNIQUE (device_id)
);
CREATE INDEX idx_mobile_device_token ON devices(token_hash);
