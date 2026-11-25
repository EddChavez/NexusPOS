CREATE TABLE custom_roles (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    normalized_name VARCHAR(80) NOT NULL UNIQUE
);

CREATE TABLE custom_role_permissions (
    role_id VARCHAR(32) NOT NULL REFERENCES custom_roles(id) ON DELETE CASCADE,
    permission_key VARCHAR(100) NOT NULL,
    PRIMARY KEY (role_id, permission_key)
);
