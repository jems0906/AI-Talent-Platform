CREATE TABLE IF NOT EXISTS app_users (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(64) NOT NULL,
    created_at VARCHAR(64) NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_app_users_tenant_email
    ON app_users (tenant_id, email);
