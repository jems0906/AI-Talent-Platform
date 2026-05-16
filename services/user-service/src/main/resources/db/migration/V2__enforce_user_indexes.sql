CREATE UNIQUE INDEX IF NOT EXISTS ux_app_users_tenant_email
    ON app_users (tenant_id, email);
