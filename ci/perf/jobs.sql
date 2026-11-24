-- The load test's jobs, run by ci/perf.sh before k6: 2,000 live jobs across the tracked companies, about
-- what a real crawl finds (1,431 on 2026-11-22), so the full text search runs over production's size.
-- Each description names 8 skills from the same list the test's candidates have, padded to the length a
-- real board's runs to. Run again, it adds nothing, as each job is held once by its board id.
-- See decisions/performance.md.
WITH words AS (
    SELECT
        ARRAY['Kotlin', 'Java', 'Spring', 'SQL', 'PostgreSQL', 'Kafka', 'Docker', 'Kubernetes', 'AWS',
              'GCP', 'Terraform', 'TypeScript', 'React', 'Next.js', 'Node.js', 'Python', 'Go', 'Rust',
              'GraphQL', 'Redis', 'Elasticsearch', 'Linux', 'CI/CD', 'Observability', 'Scala', 'Swift',
              'Android', 'Machine Learning', 'Airflow', 'dbt'] AS skills,
        ARRAY['Backend Engineer', 'Senior Software Engineer', 'Frontend Engineer', 'Full Stack Engineer',
              'Platform Engineer', 'Site Reliability Engineer', 'Data Engineer', 'Staff Engineer',
              'Engineering Manager', 'Mobile Engineer', 'Security Engineer', 'Machine Learning Engineer'] AS titles,
        ARRAY['London', 'Remote, UK', 'Manchester', 'Berlin', 'Dublin', 'New York', 'Remote', 'Edinburgh'] AS places,
        ARRAY['REMOTE', 'HYBRID', 'ONSITE'] AS workplaces
)
INSERT INTO jobs (company_id, source, external_id, title, location, description, apply_url, first_seen_at,
                  updated_at, workplace)
SELECT company.id,
       company.source,
       'perf-' || n,
       words.titles[1 + n % array_length(words.titles, 1)],
       -- One in 10 boards does not say where, nor one in 4 how.
       CASE WHEN n % 10 = 0 THEN NULL ELSE words.places[1 + (n / 3) % array_length(words.places, 1)] END,
       (SELECT 'We are looking for someone who knows ' ||
               string_agg(words.skills[1 + abs(hashtext(n || '-' || k)) % array_length(words.skills, 1)], ', ') ||
               '. ' || repeat('You will design, build and run the services our customers rely on, with a team '
                              'that reviews its work, ships often and looks after what it ships. ', 12)
        FROM generate_series(1, 8) AS k),
       'https://example.com/jobs/perf-' || n,
       now() - (n % 60) * INTERVAL '1 day',
       now(),
       CASE WHEN n % 4 = 0 THEN NULL ELSE words.workplaces[1 + n % 3] END
FROM generate_series(1, 2000) AS n
CROSS JOIN words
CROSS JOIN LATERAL (
    SELECT id, source FROM tracked_companies WHERE untracked_at IS NULL
    ORDER BY id OFFSET n % (SELECT count(*) FROM tracked_companies WHERE untracked_at IS NULL) LIMIT 1
) AS company
ON CONFLICT (company_id, external_id) DO NOTHING;
