#!/usr/bin/env bash
# Crawls every tracked company's job board now, on the local compose stack, rather than waiting for the
# next day. It clears today's claim (crawl_runs) and restarts the job-worker, which asks for the day's
# crawl a minute after it starts. Then it shows the jobs as they arrive. See decisions/jobs.md.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

psql() {
  docker compose exec -T db psql -U skill2career -d skill2career -v ON_ERROR_STOP=1 "$@"
}

psql -q -c "DELETE FROM skill2career.crawl_runs WHERE run_date = (now() AT TIME ZONE 'UTC')::date"
docker compose up -d --no-deps --force-recreate job-worker
echo "The job-worker asks for the crawl a minute after it starts. Counting jobs for two minutes..."

for _ in $(seq 1 12); do
  sleep 10
  psql -q -c "
    SELECT c.name AS company, c.source,
           count(j.id) FILTER (WHERE j.inactive_since IS NULL) AS active,
           count(j.id) FILTER (WHERE j.inactive_since IS NOT NULL) AS inactive
      FROM skill2career.tracked_companies c
      LEFT JOIN skill2career.jobs j ON j.company_id = c.id
     GROUP BY c.id ORDER BY c.id"
done
