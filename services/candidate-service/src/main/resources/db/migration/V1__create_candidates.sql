CREATE TABLE IF NOT EXISTS candidates (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    job_id VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    resume_path VARCHAR(1024) NOT NULL,
    status VARCHAR(64) NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    created_at VARCHAR(64) NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_candidates_tenant_score
    ON candidates (tenant_id, score DESC);
