-- Alert emails already read, so re-reading the mailbox never duplicates work.
CREATE TABLE processed_email (
    message_id   TEXT PRIMARY KEY,
    subject      TEXT,
    jobs_found   INT         NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
