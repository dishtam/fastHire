CREATE TABLE source (
    id              BIGSERIAL PRIMARY KEY,
    region          VARCHAR(8)   NOT NULL CHECK (region IN ('IN', 'UAE')),
    url             TEXT         NOT NULL,
    label           VARCHAR(255),
    kind            VARCHAR(24)  NOT NULL DEFAULT 'UNSUPPORTED'
                    CHECK (kind IN ('ATS', 'BOARD_EMAIL_ONLY', 'UNSUPPORTED')),
    ats_type        VARCHAR(32),
    ats_token       VARCHAR(255),
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    last_scraped_at TIMESTAMPTZ,
    scrape_status   VARCHAR(32),
    UNIQUE (region, url)
);

CREATE TABLE contact (
    id            BIGSERIAL PRIMARY KEY,
    employer_name VARCHAR(255) NOT NULL,
    name          VARCHAR(255),
    linkedin_url  TEXT,
    role_type     VARCHAR(24)  NOT NULL CHECK (role_type IN ('HIRING_MANAGER', 'HR', 'EMPLOYEE'))
);

CREATE TABLE job (
    id            BIGSERIAL PRIMARY KEY,
    source_id     BIGINT       NOT NULL REFERENCES source (id),
    external_id   VARCHAR(255) NOT NULL,
    employer_name VARCHAR(255),
    title         TEXT         NOT NULL,
    location      TEXT,
    description   TEXT,
    url           TEXT,
    posted_at     TIMESTAMPTZ,
    first_seen_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (source_id, external_id)
);

CREATE TABLE job_score (
    job_id        BIGINT PRIMARY KEY REFERENCES job (id),
    keyword_score INT,
    llm_score     INT,
    reason        TEXT,
    model         VARCHAR(64),
    scored_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE job_status (
    job_id     BIGINT PRIMARY KEY REFERENCES job (id),
    status     VARCHAR(16) NOT NULL DEFAULT 'NEW'
               CHECK (status IN ('NEW', 'APPLIED', 'REFERRED', 'INTERVIEW')),
    notes      TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE draft_message (
    id         BIGSERIAL PRIMARY KEY,
    job_id     BIGINT      NOT NULL REFERENCES job (id),
    contact_id BIGINT REFERENCES contact (id),
    type       VARCHAR(16) NOT NULL CHECK (type IN ('HM', 'EMPLOYEE')),
    body       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE scrape_run (
    id          BIGSERIAL PRIMARY KEY,
    source_id   BIGINT      NOT NULL REFERENCES source (id),
    started_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    jobs_found  INT,
    jobs_new    INT,
    error       TEXT
);

CREATE INDEX idx_job_first_seen ON job (first_seen_at);
CREATE INDEX idx_contact_employer ON contact (lower(employer_name));
