#!/usr/bin/env sh
set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
START_STACK="${VERICOV_SMOKE_START_STACK:-false}"
TIMEOUT_SECONDS="${VERICOV_SMOKE_TIMEOUT_SECONDS:-120}"

if [ "${1:-}" = "--start-stack" ]; then
  START_STACK=true
fi

if [ "$START_STACK" = "true" ]; then
  "$ROOT_DIR/vericov" up
fi

check_url() {
  label="$1"
  url="$2"
  deadline=$(( $(date +%s) + TIMEOUT_SECONDS ))
  while [ "$(date +%s)" -lt "$deadline" ]; do
    if curl -fsS "$url" >/dev/null 2>&1; then
      printf '%s\n' "ok: $label"
      return 0
    fi
    sleep 2
  done
  printf '%s\n' "smoke-test: timed out waiting for $label at $url" >&2
  return 1
}

check_url upload "http://localhost:${VERICOV_UPLOAD_HTTP_PORT:-8080}/health/ready"
check_url coverage-analysis "http://localhost:${VERICOV_ANALYSIS_HTTP_PORT:-8081}/health/ready"
check_url control-plane "http://localhost:${VERICOV_CONTROL_PLANE_HTTP_PORT:-8082}/health/ready"
check_url git-integration "http://localhost:${VERICOV_GIT_HTTP_PORT:-8083}/health/ready"
check_url integrations "http://localhost:${VERICOV_INTEGRATIONS_HTTP_PORT:-8084}/health/ready"
check_url agent-runner "http://localhost:${VERICOV_AGENT_HTTP_PORT:-8085}/health/ready"

printf '%s\n' "smoke-test: direct service topology is reachable"
