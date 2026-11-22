-- The days whose crawl has been queued. The date is the key, so of every job-worker asking each hour, only
-- the first each day inserts it and queues the crawl. See decisions/jobs.md.
CREATE TABLE crawl_runs (
    run_date   DATE PRIMARY KEY,
    claimed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now()
);
