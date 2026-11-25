#!/usr/bin/env bash
# The deploy test: the charts, linted and policy scanned, then installed on a kind cluster, whose network
# plugin enforces network policies, and tested there: the network policies, then the playwright
# journeys through the gateway. The same locally and in ci. See decisions/deploy.md.
#
# Needs docker, kind, helm, kubectl, and cv-ui's packages and playwright's chromium installed. IMAGE_TAG
# and IMAGE_REGISTRY pick a commit's pushed images, which must already be pulled: without them, the ones
# docker compose built locally. KEEP=1 leaves the cluster running. Reports go to deploy-test/report.
set -euo pipefail
cd "$(dirname "$0")/.."

CHECKOV=bridgecrew/checkov@sha256:bfe9d70bb7f044aba49bd99675813445f106dee6072da583f51008cb667f3a63
ENVOY_GATEWAY=v1.10.0
CLUSTER=skill2career
NAMESPACE=skill2career
# Each release, and its image: the api's three roles are one image.
RELEASES='api:api cv-worker:api job-worker:api shell:shell profile:profile cv:cv jobs:jobs admin:admin'
IMAGES='api shell profile cv jobs admin'
REPORT=deploy-test/report
mkdir -p "$REPORT"
chmod 777 "$REPORT"

step() { printf '\n### %s\n' "$*"; }

image() {
  if [ -n "${IMAGE_TAG:-}" ]; then
    echo "${IMAGE_REGISTRY:?IMAGE_REGISTRY goes with IMAGE_TAG}/cv-$1:$IMAGE_TAG"
  else
    echo "cv-$1:latest"
  fi
}

# An image's registry digest, as production gives the charts. One built locally has none, so its id
# stands in, for the policy scan only.
digest() {
  local name
  name=$(image "$1")
  docker image inspect "$name" --format '{{range .RepoDigests}}{{.}} {{end}}' | tr ' ' '\n' | grep -m1 . | cut -d@ -f2 ||
    docker image inspect "$name" --format '{{.Id}}'
}

# By digest for the policy scan, as production would install them. By tag on kind, as `kind load` keeps an
# image's tag but not its registry digest, so a digest would send the cluster to a registry for it.
image_values() {
  for release in $RELEASES; do
    local name
    name=$(image "${release#*:}")
    printf -- '--set %s.image.repository=%s ' "${release%%:*}" "${name%:*}"
    if [ "$1" = digest ]; then
      printf -- '--set %s.image.digest=%s ' "${release%%:*}" "$(digest "${release#*:}")"
    else
      printf -- '--set %s.image.tag=%s ' "${release%%:*}" "${name##*:}"
    fi
  done
}

step "dependencies"
helm repo add open-telemetry https://open-telemetry.github.io/opentelemetry-helm-charts --force-update >/dev/null
for chart in api ui skill2career; do
  helm dependency build "charts/$chart" >/dev/null
done

step "lint"
helm lint charts/api charts/ui --set image.tag=lint
# shellcheck disable=SC2046
helm lint charts/skill2career -f deploy-test/values-kind.yaml $(image_values digest)

step "policy scan"
# shellcheck disable=SC2046
helm template skill2career charts/skill2career -n "$NAMESPACE" \
  -f deploy-test/values-kind.yaml $(image_values digest) > "$REPORT/rendered.yaml"
# The files go in on stdin, not mounted, as a mount's path is the docker daemon's, which is not this
# script's when it runs in a container itself.
tar -c -C deploy-test checkov.yaml -C report rendered.yaml |
  docker run --rm -i --entrypoint sh "$CHECKOV" -c \
    'mkdir /scan && cd /scan && tar -x && checkov --config-file checkov.yaml -f rendered.yaml'

step "cluster"
# One kept with KEEP=1 is used again, with its address, which this shell may not have.
if kind get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
  kind export kubeconfig --name "$CLUSTER" >/dev/null
else
  kind create cluster --config deploy-test/kind.yaml --wait 0s
fi
kubectl config use-context "kind-$CLUSTER" >/dev/null
# The cluster goes when the test ends, passed or failed, unless KEEP is set.
forward=
trap '[ -z "$forward" ] || kill "$forward" 2>/dev/null || true; [ -n "${KEEP:-}" ] || kind delete cluster --name "$CLUSTER" >/dev/null 2>&1' EXIT
kubectl wait --for=condition=Ready nodes --all --timeout=5m

step "gateway"
helm upgrade --install envoy-gateway oci://docker.io/envoyproxy/gateway-helm --version "$ENVOY_GATEWAY" \
  -n envoy-gateway-system --create-namespace --wait --timeout 5m >/dev/null
# A plain service for envoy's proxy, as kind has no load balancer: the test reaches it by port forward.
kubectl apply -f - <<'EOF' >/dev/null
apiVersion: gateway.envoyproxy.io/v1alpha1
kind: EnvoyProxy
metadata:
  name: cluster-ip
  namespace: envoy-gateway-system
spec:
  provider:
    type: Kubernetes
    kubernetes:
      envoyService:
        type: ClusterIP
---
apiVersion: gateway.networking.k8s.io/v1
kind: GatewayClass
metadata:
  name: envoy
spec:
  controllerName: gateway.envoyproxy.io/gatewayclass-controller
  parametersRef:
    group: gateway.envoyproxy.io
    kind: EnvoyProxy
    name: cluster-ip
    namespace: envoy-gateway-system
EOF

step "images"
for name in $IMAGES; do
  kind load docker-image --name "$CLUSTER" "$(image "$name")" >/dev/null
done

step "install"
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f - >/dev/null
kubectl create namespace platform --dry-run=client -o yaml | kubectl apply -f - >/dev/null
# Wiremock's mappings, from the directories the e2e stack mounts, so there is no copy.
kubectl create configmap gemini-mappings -n platform --from-file=cv-ui/e2e/wiremock/mappings \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null
kubectl create configmap boards-mappings -n platform --from-file=cv-ui/e2e/boards/mappings \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null
helm upgrade --install platform deploy-test/platform -n platform \
  --set appNamespace="$NAMESPACE" \
  --set-file files.realm=cv-api/app/src/test/resources/realm-export.json \
  --wait --timeout 10m
# helm says nothing while it waits, so the pods' state every 30 seconds, and why, if they never get ready.
(while sleep 30; do kubectl get pods -n "$NAMESPACE" --no-headers 2>/dev/null | sed 's/^/  /'; echo; done) &
watcher=$!
# shellcheck disable=SC2046
if ! helm upgrade --install skill2career charts/skill2career -n "$NAMESPACE" \
  -f deploy-test/values-kind.yaml $(image_values tag) --wait --timeout 10m; then
  kill "$watcher"
  kubectl describe pods -n "$NAMESPACE" | tee "$REPORT/pods-describe.txt" | grep -A20 '^Events:'
  kubectl logs -n "$NAMESPACE" -l app.kubernetes.io/part-of=skill2career --all-containers \
    --tail=50 --prefix 2>&1 | tee "$REPORT/logs.txt"
  exit 1
fi
kill "$watcher"
kubectl get pods -A -o wide > "$REPORT/pods.txt"

step "reach the gateway on compose's ports"
kubectl wait -n platform --for=condition=Programmed gateway/public --timeout=5m
envoy=$(kubectl get service -n envoy-gateway-system \
  -l gateway.envoyproxy.io/owning-gateway-name=public -o jsonpath='{.items[0].metadata.name}')
kubectl port-forward -n envoy-gateway-system "service/$envoy" 3000:80 8180:8180 \
  > "$REPORT/port-forward.log" 2>&1 &
forward=$!
until curl -sf -o /dev/null http://localhost:3000/api/health; do sleep 2; done

step "only the api's roles reach postgres and kafka"
# A pod that runs to the end and is read after, not attached to, as attaching can miss a short pod's
# output on a slow node. Its result, or why it has none.
probe() {
  local pod="probe-$1-$3"
  kubectl delete pod -n "$NAMESPACE" "$pod" --ignore-not-found >/dev/null
  kubectl run "$pod" -n "$NAMESPACE" --restart=Never --image=busybox:1.37 \
    --labels="app.kubernetes.io/name=$1" -- \
    sh -c "nc -z -w 10 $2 $3 && echo reached || echo blocked" >/dev/null
  if kubectl wait -n "$NAMESPACE" "pod/$pod" --for=jsonpath='{.status.phase}'=Succeeded --timeout=3m >/dev/null; then
    kubectl logs -n "$NAMESPACE" "$pod"
  else
    kubectl describe pod -n "$NAMESPACE" "$pod" | sed -n '/^Events:/,$p' >&2
    echo "no result"
  fi
  kubectl delete pod -n "$NAMESPACE" "$pod" --wait=false >/dev/null
}
# A wrong result fails the test at the end, so the journeys still run and one run says both.
result=0
expect() {
  local got
  got=$(probe "$1" "$2" "$3")
  if [ "$got" = "$4" ]; then
    echo "  $1 to $2: $got"
  else
    echo "  $1 to $2:$3: $got, not $4"
    result=1
  fi
}
# The probe's own network works, so a block is the policy's, not a broken network. Without it nothing
# after means anything, so it stops the test.
control=$(probe control keycloak.platform 8080)
[ "$control" = reached ] || { echo "the probe could not reach keycloak: $control"; exit 1; }
# And the same pod, labelled as a role, gets there, so the route is there and only the policy stops it.
expect job-worker postgres.platform 5432 reached
expect job-worker kafka.platform 9092 reached
expect not-the-api postgres.platform 5432 blocked
expect not-the-api kafka.platform 9092 blocked

step "playwright"
(cd cv-ui && pnpm e2e) || result=1

kubectl get pods -A -o wide > "$REPORT/pods.txt"
kubectl logs -n "$NAMESPACE" -l app.kubernetes.io/part-of=skill2career --all-containers \
  --tail=200 --prefix > "$REPORT/logs.txt" 2>&1 || true
exit "$result"
