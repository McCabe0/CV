#!/usr/bin/env sh
# Scans the images for known vulnerabilities with grype, in docker: see decisions/cve.md. Fails on a high
# or critical one that has a fix. Accepted ones, with their reasons, are in cve/grype.yaml. Grype gets a
# saved copy of each image, not the docker socket or any credentials.
#
# CVE_TAG and CVE_REGISTRY pick the images pushed for a commit, eg ghcr.io/mccabe0 and its sha, which
# must already be pulled. Without them, the images docker compose built locally: cv-api, and the ui's
# as <project>-<app>, the project being CVE_PROJECT (cv by default, cv-e2e for the e2e stack).
# Reports go to ci/cve-report: a table and json per image, and each image's every match in <image>-all.json.
set -u
cd "$(dirname "$0")"

# On windows, git bash rewrites anything that looks like a path, which breaks the docker arguments, so
# it is told not to, and docker is given the host folders as windows paths instead. Neither does
# anything elsewhere.
export MSYS_NO_PATHCONV=1
host() { if command -v cygpath > /dev/null; then cygpath -w "$1"; else echo "$1"; fi; }

IMAGE=anchore/grype@sha256:6a36234da13f9faa327306ac0b3dab3405cfa2b4eb20c8f904125a8707863913

image_of() {
  if [ -n "${CVE_TAG:-}" ]; then
    echo "${CVE_REGISTRY:?CVE_REGISTRY names the registry the tagged images are in}/cv-$1:$CVE_TAG"
  elif [ "$1" = api ]; then
    echo "cv-api:latest"
  else
    echo "${CVE_PROJECT:-cv}-$1:latest"
  fi
}

rm -rf cve-report
mkdir -p cve-report
chmod 777 cve-report
archives=$(mktemp -d)
chmod 755 "$archives"
trap 'rm -rf "$archives"' EXIT

result=0
for service in api shell profile cv jobs admin; do
  image=$(image_of "$service")
  docker save "$image" -o "$(host "$archives/$service.tar")" || exit 1
  chmod 644 "$archives/$service.tar"
  echo "### $service ($image)"
  # The gate: a high or critical vulnerability with a fix fails the run.
  docker run --rm \
    -v "$(host "$archives"):/images:ro" \
    -v cv-grype-db:/db -e GRYPE_DB_CACHE_DIR=/db \
    -v "$(host "$PWD/cve"):/config:ro" \
    -v "$(host "$PWD/cve-report"):/report" \
    "$IMAGE" "docker-archive:/images/$service.tar" \
    --config /config/grype.yaml --only-fixed --fail-on high \
    --output table --output "json=/report/$service.json" --output "table=/report/$service.txt" ||
    result=$?
  # Every match, fixed or not, for the summary of the ones with no fix. It fails nothing.
  docker run --rm \
    -v "$(host "$archives"):/images:ro" \
    -v cv-grype-db:/db -e GRYPE_DB_CACHE_DIR=/db \
    -v "$(host "$PWD/cve"):/config:ro" \
    -v "$(host "$PWD/cve-report"):/report" \
    "$IMAGE" "docker-archive:/images/$service.tar" \
    --config /config/grype.yaml --quiet --output "json=/report/$service-all.json" || result=$?
done
exit "$result"
