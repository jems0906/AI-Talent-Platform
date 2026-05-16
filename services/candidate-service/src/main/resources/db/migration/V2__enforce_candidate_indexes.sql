CREATE INDEX IF NOT EXISTS ix_candidates_tenant_score
    ON candidates (tenant_id, score DESC);
