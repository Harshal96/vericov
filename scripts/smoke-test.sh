#!/usr/bin/env sh
set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
START_STACK="${VERICOV_SMOKE_START_STACK:-false}"
TIMEOUT_SECONDS="${VERICOV_SMOKE_TIMEOUT_SECONDS:-120}"
ENV_FILE="$ROOT_DIR/.env"

if [ ! -f "$ENV_FILE" ]; then
  echo "smoke-test: .env is missing; run ./vericov init" >&2
  exit 2
fi

set -a
. "$ENV_FILE"
set +a

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

upload_response="$(curl -fsS \
  -X POST \
  -H "Authorization: Bearer ${VERICOV_DEV_API_KEY}" \
  -H "Idempotency-Key: smoke-$(date +%s)-$$" \
  -H "Content-Type: application/json" \
  "http://localhost:${VERICOV_UPLOAD_HTTP_PORT:-8080}/api/v1/uploads" \
  --data '{
    "commit_sha": "smoke-test",
    "branch": "main",
    "ci_provider": "local",
    "artifacts": [{
      "name": "coverage.lcov",
      "kind": "coverage",
      "format": "lcov",
      "content_type": "text/plain",
      "content_base64": "VE46ClNGOnNyYy9NYWluLmphdmEKREE6MSwxCmVuZF9vZl9yZWNvcmQK"
    }]
  }')"

upload_id="$(printf '%s' "$upload_response" \
  | sed -n 's/.*"upload_id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
if [ "$upload_id" = "" ]; then
  echo "smoke-test: upload response did not contain upload_id: $upload_response" >&2
  exit 1
fi

deadline=$(( $(date +%s) + TIMEOUT_SECONDS ))
while [ "$(date +%s)" -lt "$deadline" ]; do
  status_response="$(curl -fsS \
    -H "Authorization: Bearer ${VERICOV_DEV_API_KEY}" \
    "http://localhost:${VERICOV_UPLOAD_HTTP_PORT:-8080}/api/v1/uploads/$upload_id")"
  status="$(printf '%s' "$status_response" \
    | sed -n 's/.*"status"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
  case "$status" in
    completed)
      report_response="$(curl -fsS \
        -H "Authorization: Bearer ${VERICOV_DEV_API_KEY}" \
        "http://localhost:${VERICOV_UPLOAD_HTTP_PORT:-8080}/api/v1/uploads/$upload_id/report")"
      report_status="$(printf '%s' "$report_response" \
        | sed -n 's/.*"status"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
      if [ "$report_status" != "complete" ]; then
        echo "smoke-test: coverage report was not complete: $report_response" >&2
        exit 1
      fi
      printf '%s\n' "ok: upload $upload_id was analyzed"
      printf '%s\n' "ok: coverage report is available"
      printf '%s\n' "smoke-test: upload-to-report flow is working"
      exit 0
      ;;
    failed)
      echo "smoke-test: upload analysis failed: $status_response" >&2
      exit 1
      ;;
  esac
  sleep 2
done

echo "smoke-test: timed out waiting for upload $upload_id to complete" >&2
exit 1
