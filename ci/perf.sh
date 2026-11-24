#!/usr/bin/env sh
# The load test, with k6 in docker: see decisions/performance.md. Over the stack's compose files (the e2e
# stack's locally, ci's in ci), docker-compose.perf.yml starts only the api and what it needs, with 2
# cores each for it and the database, and the database behind toxiproxy. It seeds the jobs (perf/jobs.sql),
# then k6 makes its candidates and sends the load (perf/load.ts). Reports go to ci/perf-report: k6's
# summary and report.html, and stats.csv, each container's cpu and memory, which shows if the machine
# was the limit rather than the api. It adds data, and keeps it for the next run, so only point it at a
# stack you own, such as the e2e one: COMPOSE_PROJECT_NAME=cv-e2e ci/perf.sh
#   PERF_RATE=100 ci/perf.sh                 another rate than 50 a second
#   PERF_PROFILE=step ci/perf.sh             steps the rate up until it breaks, to find the ceiling
set -u
cd "$(dirname "$0")/.." || exit 1

# The stack's own files, as ci's stack action set them, or the e2e stack's, with the load test's over them.
# One separator everywhere, as windows' default is ;.
export COMPOSE_PATH_SEPARATOR=:
base=${COMPOSE_FILE:-docker-compose.yml:docker-compose.e2e.yml}
case ":$base:" in
  *:docker-compose.perf.yml:*) export COMPOSE_FILE="$base" ;;
  *) export COMPOSE_FILE="$base:docker-compose.perf.yml" ;;
esac

mkdir -p ci/perf-report
# k6's image has a user of its own.
chmod 777 ci/perf-report
rm -f ci/perf-report/stats.csv

# The api through toxiproxy, on its 2 cores, and only what it needs. Recreated locally, and already so in
# ci, where the stack action started it with these files.
docker compose up --detach --no-build --wait api || exit 1

echo 'seeding the jobs'
docker compose exec -T db psql --quiet -v ON_ERROR_STOP=1 -U skill2career -d skill2career < ci/perf/jobs.sql || exit 1

containers=$(docker compose ps -q)
(
  while :; do
    # shellcheck disable=SC2086 # one argument per container
    docker stats --no-stream --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}}' $containers |
      sed "s/^/$(date +%T),/" >> ci/perf-report/stats.csv
    sleep 5
  done
) > /dev/null 2>&1 &
stats=$!

docker compose run --rm perf
result=$?
kill "$stats"

# Locally, the stack goes back to its own database, on the machine's cores. Ci's ends with the job.
if [ -z "${CI:-}" ]; then
  COMPOSE_FILE="$base" docker compose up --detach --no-build --wait api
  docker compose rm --stop --force toxiproxy
fi

# In ci, the results in the run's summary.
if [ -n "${GITHUB_STEP_SUMMARY:-}" ] && [ -f ci/perf-report/summary.md ]; then
  cat ci/perf-report/summary.md >> "$GITHUB_STEP_SUMMARY"
fi
exit "$result"
