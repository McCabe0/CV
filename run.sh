#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"

cleanup() {
  local exit_code=$?

  if [[ -n "${BACKEND_PID:-}" ]] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    kill "$BACKEND_PID" 2>/dev/null || true
    wait "$BACKEND_PID" 2>/dev/null || true
  fi

  exit "$exit_code"
}

trap cleanup EXIT INT TERM

if ! command -v gradle >/dev/null 2>&1; then
  echo "Error: gradle is not installed or not in PATH."
  exit 1
fi

if ! command -v npm >/dev/null 2>&1; then
  echo "Error: npm is not installed or not in PATH."
  exit 1
fi

echo "Starting backend (gradle bootRun)..."
(
  cd "$BACKEND_DIR"
  gradle bootRun
) &
BACKEND_PID=$!

echo "Waiting for backend to initialize..."
sleep 3

echo "Starting frontend (npm run dev)..."
cd "$FRONTEND_DIR"
npm run dev
