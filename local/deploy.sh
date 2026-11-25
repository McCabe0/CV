#!/usr/bin/env bash
# A deployment to the local stack, as the DORA dashboard counts one: builds this commit's images, starts
# them, and sends the deployment's events to the collector. The running api says what it replaces, so a
# commit older than it is a rollback. Takes the services to deploy, everything by default. See
# decisions/dora.md.
#
# Usage: local/deploy.sh [service...]
set -euo pipefail
cd "$(dirname "$0")/.."

[ -z "$(git status --porcelain)" ] ||
  echo "Uncommitted changes are deployed as $(git rev-parse --short HEAD), which does not have them."

running=unknown
running_since=
app=$(docker compose ps -q api 2>/dev/null || true)
if [ -n "$app" ]; then
  running=$(docker inspect "$app" -f '{{index .Config.Labels "org.opencontainers.image.revision"}}')
  running_since=$(docker inspect "$app" -f '{{.State.StartedAt}}')
fi

version=$(git rev-parse HEAD)
started=$(date -u +%FT%TZ)
result=success
GIT_SHA=$version docker compose up -d --build --wait "$@" || result=failure

# The collector may only now have started.
for _ in $(seq 1 30); do
  curl -s -o /dev/null http://localhost:4318/ && break
  sleep 1
done
node ci/dora/deployment-event.mjs --environment local --version "$version" \
  --running "${running:-unknown}" ${running_since:+--running-since "$running_since"} \
  --started "$started" --result "$result"
[ "$result" = success ]
