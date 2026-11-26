#!/usr/bin/env sh
# OWASP ZAP against the running stack, signed in: see decisions/zap.md.
#   ci/zap.sh [candidate] [admin] [api]     the scans named, or all three
# candidate and admin are passive baseline scans of the ui, as a candidate (from home, through the
# profile, cv and jobs zones) and as an admin (the admin zone): they only read what the pages send, so
# they can share a stack, and run on every push, after the smoke tests. api scans the api from its
# openapi docs with the admin's token, and sends attacks. Signed in, they reach the api's code and change
# its data, so only point it at a throwaway stack you own, such as the e2e one, never a shared one. It
# runs nightly. The scans named run at the same time. Reports go to ci/zap-report/<scan>.
# Accepted warnings, with their reasons, are in zap/ui.tsv and zap/api.tsv; any other warning fails.
# The api scan's attacks for what the api is not built on skip themselves, from zap/api.context.
set -u
cd "$(dirname "$0")" || exit 1

# On windows, git bash rewrites anything that looks like a path, which breaks the docker arguments, so it
# is told not to, and docker is given the host folder as a windows path. Neither does anything elsewhere.
export MSYS_NO_PATHCONV=1
host() { if command -v cygpath > /dev/null; then cygpath -w "$1"; else echo "$1"; fi; }

UI_URL=${ZAP_UI_URL:-http://localhost:3000}
API_URL=${ZAP_API_URL:-http://localhost:8080/api}
IMAGE=ghcr.io/zaproxy/zaproxy:2.17.0

scans=${*:-candidate admin api}

# The host ZAP sends the sign in to, and nowhere else. ZAP matches the host name alone, without the port,
# so locally, where everything is on localhost, keycloak and the other scans' servers get it too.
site() { echo "$1" | sed -E 's#^[a-z]+://([^/:]+).*#\1#'; }

# Signed in before the scans start, with playwright, from the ui's workspace. The tokens stay out of the
# reports' folder, which ci uploads.
auth() { (cd ../cv-ui && node e2e/zap-auth.mts "$@"); }
for name in $scans; do
  case $name in
    candidate) candidate_cookie=$(auth session candidate /) || exit 1 ;;
    admin) admin_cookie=$(auth session admin /admin) || exit 1 ;;
    api) api_token=$(auth token admin) || exit 1 ;;
    *)
      echo "no scan called $name: candidate, admin or api"
      exit 2
      ;;
  esac
done

rm -rf zap-report && mkdir zap-report
# Pulled once up front, so the scans do not each pull it.
docker pull --quiet "$IMAGE" > /dev/null

# start <scan> <header> <value> <url> <zap command...>: one scan, in the background. ZAP reads its rules
# from, and writes its reports to, one folder, as its own user. Each scan gets a folder of its own, so
# the scans running together do not share one, and a log there, shown at the end so they do not
# interleave. ZAP sends <header>: <value> on every request to the url's host.
start() {
  name=$1 header=$2 value=$3 url=$4
  shift 4
  mkdir "zap-report/$name" && cp zap/* "zap-report/$name/" && chmod -R a+rwX "zap-report/$name"
  docker run --rm --network host -v "$(host "$PWD/zap-report/$name"):/zap/wrk:rw" \
    -e ZAP_AUTH_HEADER="$header" -e ZAP_AUTH_HEADER_VALUE="$value" -e ZAP_AUTH_HEADER_SITE="$(site "$url")" \
    "$IMAGE" "$@" > "zap-report/$name/scan.log" 2>&1 &
  eval "pid_$name=\$!"
}

for name in $scans; do
  case $name in
    candidate) start candidate Cookie "$candidate_cookie" "$UI_URL" \
      zap-baseline.py -t "$UI_URL/" -c ui.tsv -r candidate.html -J candidate.json ;;
    admin) start admin Cookie "$admin_cookie" "$UI_URL" \
      zap-baseline.py -t "$UI_URL/admin" -c ui.tsv -r admin.html -J admin.json ;;
    api) start api Authorization "Bearer $api_token" "$API_URL" \
      zap-api-scan.py -t "$API_URL/v3/api-docs" -f openapi -c api.tsv -n api.context -r api.html -J api.json ;;
  esac
done

result=0
exits=
for name in $scans; do
  eval "wait \$pid_$name"
  status=$?
  exits="$exits $name $status"
  [ "$status" -eq 0 ] || result=1
done

for name in $scans; do
  echo "===== $name scan"
  cat "zap-report/$name/scan.log"
done

echo "exits (0 is clean, 1 failed, 2 warned):$exits"

# A scan that was not signed in after all passes clean, having only met keycloak's page or 401s. So each
# must have been answered as a signed in user: the ui's pages, not keycloak's sign in form, and some of
# the api's calls with a 2xx.
for name in $scans; do
  case $name in
    candidate | admin)
      if grep -q 'kc-form-login' "zap-report/$name/$name.json"; then
        echo "the $name scan was not signed in: it met keycloak's sign in page"
        result=1
      fi
      ;;
    api)
      if ! grep -q '"insight.code.2xx"' zap-report/api/api.json; then
        echo "the api scan was not signed in: none of its calls succeeded"
        result=1
      fi
      ;;
  esac
done

# In ci, each scan's warnings in the run's summary.
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  for name in $scans; do
    printf '### ZAP: %s\n\n' "$name" >> "$GITHUB_STEP_SUMMARY"
    grep -E '^(WARN-NEW|FAIL-NEW|WARN-INPROG|FAIL-INPROG)' "zap-report/$name/scan.log" | sed 's/^/- /' >> "$GITHUB_STEP_SUMMARY" \
      || echo '- none' >> "$GITHUB_STEP_SUMMARY"
    printf '\n' >> "$GITHUB_STEP_SUMMARY"
  done
fi

exit "$result"
