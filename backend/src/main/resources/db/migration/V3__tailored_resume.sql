-- LLM bullet selection per (job, profile version), so repeat downloads are instant and free.
CREATE TABLE tailored_resume (
    job_id       BIGINT      NOT NULL REFERENCES job (id),
    profile_hash VARCHAR(64) NOT NULL,
    selection    JSONB       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (job_id, profile_hash)
);
