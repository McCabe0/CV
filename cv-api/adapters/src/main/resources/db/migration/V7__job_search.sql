-- Matching: where each job is done, and a full text index of its title and description. See decisions/jobs.md.

-- REMOTE, HYBRID or ONSITE, as its board says; null when the board does not. Jobs crawled before this
-- migration are null until the next crawl, which sees the change and saves it.
ALTER TABLE jobs ADD COLUMN workplace VARCHAR(16);

-- The 'simple' configuration keeps words as written, lower cased, without stemming them as english, as
-- skills are names: Rust is not rusting. The title weighs more than the description.
ALTER TABLE jobs ADD COLUMN search TSVECTOR GENERATED ALWAYS AS (
    setweight(to_tsvector('simple', title), 'A') || setweight(to_tsvector('simple', description), 'B')
) STORED;

-- Only active jobs are ever searched.
CREATE INDEX jobs_search ON jobs USING GIN (search) WHERE inactive_since IS NULL;
