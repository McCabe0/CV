#!/usr/bin/env bash
# The deploy test on this machine, with nothing to install but docker: run.sh runs in playwright's linux
# image, on the docker vm's network so it reaches kind's cluster, with the docker socket. On the images
# `docker compose build` made, so build them first. Takes the working tree, uncommitted changes and all.
# KEEP=1 leaves the cluster running. See decisions/deploy.md.
set -euo pipefail
cd "$(dirname "$0")/.."

# What ci installs, so the test is the same.
script=$(cat <<'EOF'
set -eu
mkdir -p /opt/bin && cd /opt/bin
curl -sSLo kind https://github.com/kubernetes-sigs/kind/releases/download/v0.33.0/kind-linux-amd64
curl -sSLo kubectl https://dl.k8s.io/release/v1.37.2/bin/linux/amd64/kubectl
curl -sSL https://get.helm.sh/helm-v4.3.1-linux-amd64.tar.gz | tar -xz --strip-components 1 linux-amd64/helm
curl -sSL https://download.docker.com/linux/static/stable/x86_64/docker-28.5.2.tgz |
  tar -xz --strip-components 1 docker/docker
chmod +x kind kubectl helm docker
export PATH=/opt/bin:$PATH CI=true
mkdir -p /work && cd /work && tar -x
# The image's node has no corepack, so the pnpm cv-ui names, from npm.
(cd cv-ui && npm install -g --silent "$(node -p "require('./package.json').packageManager")" &&
  pnpm install --frozen-lockfile > /dev/null)
./deploy-test/run.sh
EOF
)

# The tree goes in on stdin, as a mount of it would bring this machine's node_modules.
git ls-files -co --exclude-standard | tar -c -T - |
  MSYS_NO_PATHCONV=1 docker run --rm -i --network host -e KEEP="${KEEP:-}" \
    -v /var/run/docker.sock:/var/run/docker.sock \
    mcr.microsoft.com/playwright:v1.64.1-noble bash -c "$script"
