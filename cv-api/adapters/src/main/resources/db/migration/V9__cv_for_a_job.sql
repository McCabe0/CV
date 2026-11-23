-- The job a CV is aimed at; null for a general CV, aimed at the profile's target role. See decisions/agents.md.
ALTER TABLE cv_requests ADD COLUMN job_id BIGINT REFERENCES jobs (id);
