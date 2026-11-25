-- The admin zone keeps the tracked companies. See decisions/jobs.md.

-- Set when an admin stops tracking a company. It is kept, so the CVs and summaries for its jobs keep them,
-- but not crawled, and its jobs are closed. Cleared when it is tracked again.
ALTER TABLE tracked_companies ADD COLUMN untracked_at TIMESTAMP(6) WITH TIME ZONE;

-- Each company's latest crawl, replaced by the next: how it went, and what it changed.
CREATE TABLE crawl_results (
    company_id BIGINT       PRIMARY KEY REFERENCES tracked_companies (id),
    crawled_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    outcome    VARCHAR(16)  NOT NULL,
    found      INTEGER      NOT NULL,
    added      INTEGER      NOT NULL,
    closed     INTEGER      NOT NULL,
    error      VARCHAR(1000)
);
