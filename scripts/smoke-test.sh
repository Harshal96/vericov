#!/usr/bin/env sh
set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
API_URL="${VERICOV_SMOKE_API_URL:-http://localhost:9000}"
START_STACK="${VERICOV_SMOKE_START_STACK:-false}"
TIMEOUT_SECONDS="${VERICOV_SMOKE_TIMEOUT_SECONDS:-180}"
BRANCH="${VERICOV_SMOKE_BRANCH:-main}"
RUN_ID="$(date +%Y%m%d%H%M%S)-$$"
ORG_SLUG="${VERICOV_SMOKE_ORG_SLUG:-smoke-${RUN_ID}}"
COMMIT_SHA="${VERICOV_SMOKE_COMMIT_SHA:-smoke-${RUN_ID}}"
USER_ID="${VERICOV_SMOKE_USER_ID:-11111111-1111-1111-1111-111111111111}"
USER_EMAIL="${VERICOV_SMOKE_USER_EMAIL:-smoke@example.com}"
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/vericov-smoke.XXXXXX")"

if [ "${1:-}" = "--start-stack" ]; then
  START_STACK=true
fi

trap 'rm -rf "$WORK_DIR"' EXIT

log() {
  printf '%s\n' "==> $*"
}

fail() {
  printf '%s\n' "smoke-test: $*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "$1 is required"
}

json_get() {
  python3 -c '
import json
import sys

data = json.load(sys.stdin)
value = data
for part in sys.argv[1].split("."):
    if isinstance(value, list):
        value = value[int(part)]
    else:
        value = value[part]
print(value)
' "$1"
}

json_assert() {
  python3 -c '
import json
import sys
from decimal import Decimal, InvalidOperation

data = json.load(sys.stdin)
path = sys.argv[1]
expected = sys.argv[2]
value = data
for part in path.split("."):
    if isinstance(value, list):
        value = value[int(part)]
    else:
        value = value[part]
if isinstance(value, (int, float)) and not isinstance(value, bool):
    try:
        if Decimal(str(value)) == Decimal(expected):
            raise SystemExit(0)
    except InvalidOperation:
        pass
if str(value) != expected:
    raise SystemExit(f"{path} expected {expected!r}, got {value!r}")
' "$1" "$2"
}

url_encode() {
  python3 - "$1" <<'PY'
import sys
from urllib.parse import quote

print(quote(sys.argv[1], safe=""))
PY
}

make_jwt() {
  python3 - "$USER_ID" "$USER_EMAIL" "$SUPABASE_JWT_SECRET" "$SUPABASE_JWT_ISSUER" "$SUPABASE_JWT_AUDIENCE" <<'PY'
import base64
import hashlib
import hmac
import json
import sys
import time

user_id, email, secret, issuer, audience = sys.argv[1:6]

def b64url(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")

header = {"alg": "HS256", "typ": "JWT"}
payload = {
    "sub": user_id,
    "email": email,
    "iss": issuer,
    "aud": audience,
    "iat": int(time.time()),
    "exp": int(time.time()) + 7200,
}
signing_input = ".".join(
    b64url(json.dumps(part, separators=(",", ":"), sort_keys=True).encode("utf-8"))
    for part in (header, payload)
)
signature = hmac.new(secret.encode("utf-8"), signing_input.encode("ascii"), hashlib.sha256).digest()
print(f"{signing_input}.{b64url(signature)}")
PY
}

api_json() {
  method="$1"
  url="$2"
  body="${3:-}"
  if [ "$body" = "" ]; then
    curl -fsS -X "$method" \
      -H "Accept: application/json" \
      -H "Authorization: Bearer $AUTH_JWT" \
      "$url"
  else
    curl -fsS -X "$method" \
      -H "Accept: application/json" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $AUTH_JWT" \
      --data "$body" \
      "$url"
  fi
}

wait_for_json() {
  label="$1"
  url="$2"
  deadline=$(( $(date +%s) + TIMEOUT_SECONDS ))
  while [ "$(date +%s)" -lt "$deadline" ]; do
    if response="$(api_json GET "$url" 2>/dev/null)"; then
      printf '%s' "$response"
      return 0
    fi
    sleep 2
  done
  fail "timed out waiting for $label"
}

wait_for_gate_evaluation() {
  url="$1"
  deadline=$(( $(date +%s) + TIMEOUT_SECONDS ))
  while [ "$(date +%s)" -lt "$deadline" ]; do
    response="$(api_json GET "$url")"
    if printf '%s' "$response" | python3 -c '
import json
import sys

payload = json.load(sys.stdin)
items = payload.get("data", [])
raise SystemExit(0 if items else 1)
'
    then
      printf '%s' "$response"
      return 0
    fi
    sleep 2
  done
  fail "timed out waiting for gate evaluation"
}

wait_for_analysis_job() {
  job_id="$1"
  db_url="${VERICOV_SMOKE_DB_URL:-${VERICOV_HOST_SUPABASE_DB_URL:-}}"
  if [ "$db_url" = "" ] || ! command -v psql >/dev/null 2>&1; then
    log "Skipping direct analysis_jobs table check; set VERICOV_SMOKE_DB_URL and install psql to enable it"
    return 0
  fi

  pg_url="${db_url#jdbc:}"
  deadline=$(( $(date +%s) + TIMEOUT_SECONDS ))
  while [ "$(date +%s)" -lt "$deadline" ]; do
    status="$(PGPASSWORD="${SUPABASE_DB_PASSWORD:-}" PGUSER="${SUPABASE_DB_USER:-postgres}" \
      psql "$pg_url" -tAc "select status from vericov.analysis_jobs where id = '${job_id}'" 2>/dev/null || true)"
    status="$(printf '%s' "$status" | tr -d '[:space:]')"
    if [ "$status" = "succeeded" ]; then
      return 0
    fi
    if [ "$status" = "failed" ] || [ "$status" = "cancelled" ]; then
      fail "analysis job $job_id ended with status $status"
    fi
    sleep 2
  done
  fail "timed out waiting for analysis job $job_id in database"
}

require_cmd curl
require_cmd python3
require_cmd uv

if [ "$START_STACK" = "true" ]; then
  log "Starting local stack"
  "$ROOT_DIR/scripts/dev-up.sh" --host-java
fi

if [ -f "$ROOT_DIR/infra/local/.env" ]; then
  set -a
  . "$ROOT_DIR/infra/local/.env"
  set +a
fi

[ "${SUPABASE_JWT_SECRET:-}" != "" ] || fail "SUPABASE_JWT_SECRET is required; run scripts/dev-up.sh or source infra/local/.env"
[ "${SUPABASE_JWT_ISSUER:-}" != "" ] || fail "SUPABASE_JWT_ISSUER is required"
[ "${SUPABASE_JWT_AUDIENCE:-}" != "" ] || fail "SUPABASE_JWT_AUDIENCE is required"

AUTH_JWT="$(make_jwt)"
EXPIRES_AT="$(python3 - <<'PY'
from datetime import datetime, timedelta, timezone

print((datetime.now(timezone.utc) + timedelta(hours=2)).isoformat().replace("+00:00", "Z"))
PY
)"

log "Creating organization and repository"
ORG_JSON="$(api_json POST "$API_URL/api/v1/orgs" "{\"name\":\"Smoke Engineering\",\"slug\":\"$ORG_SLUG\",\"plan\":\"team\"}")"
ORG_ID="$(printf '%s' "$ORG_JSON" | json_get data.id)"

REPO_JSON="$(api_json POST "$API_URL/api/v1/orgs/$ORG_ID/repositories" "{\"provider\":\"github\",\"provider_repository_id\":\"smoke-$RUN_ID\",\"full_name\":\"vericov/smoke-$RUN_ID\",\"default_branch\":\"$BRANCH\",\"visibility\":\"private\"}")"
REPO_ID="$(printf '%s' "$REPO_JSON" | json_get data.id)"

log "Configuring coverage gate and repository API key"
api_json PUT "$API_URL/api/v1/orgs/$ORG_ID/repositories/$REPO_ID/gates" "[{\"name\":\"Smoke line coverage\",\"gate_type\":\"project_coverage\",\"metric\":\"line\",\"threshold\":50,\"blocking\":true,\"config\":{},\"status\":\"active\"}]" >/dev/null

KEY_JSON="$(api_json POST "$API_URL/api/v1/orgs/$ORG_ID/repositories/$REPO_ID/api-keys" "{\"name\":\"Smoke upload\",\"scopes\":[\"uploads:create\",\"uploads:read\"],\"branch_allow_patterns\":[\"$BRANCH\"],\"expires_at\":\"$EXPIRES_AT\"}")"
REPO_API_KEY="$(printf '%s' "$KEY_JSON" | json_get data.api_key)"

mkdir -p "$WORK_DIR/coverage"
cat > "$WORK_DIR/coverage/jacoco.xml" <<'XML'
<report name="smoke">
  <package name="dev/vericov">
    <class name="dev/vericov/App" sourcefilename="App.java">
      <method name="covered" desc="()V" line="10">
        <counter type="METHOD" missed="0" covered="1" />
      </method>
      <method name="missed" desc="()V" line="20">
        <counter type="METHOD" missed="1" covered="0" />
      </method>
    </class>
    <sourcefile name="App.java">
      <line nr="10" mi="0" ci="4" mb="1" cb="1" />
      <line nr="20" mi="2" ci="0" mb="0" cb="0" />
    </sourcefile>
  </package>
</report>
XML

log "Uploading coverage artifact through CLI"
UPLOAD_JSON="$(
  VERICOV_API_KEY="$REPO_API_KEY" \
  uv run --project "$ROOT_DIR/clis/coverage-upload" --directory "$WORK_DIR" \
    vericov upload \
      --api-url "$API_URL" \
      --repository-id "$REPO_ID" \
      --commit-sha "$COMMIT_SHA" \
      --branch "$BRANCH" \
      --coverage coverage/jacoco.xml \
      --wait \
      --timeout "${TIMEOUT_SECONDS}s" \
      --json
)"
printf '%s' "$UPLOAD_JSON" | json_assert ok True
printf '%s' "$UPLOAD_JSON" | json_assert status completed

UPLOAD_STATUS_JSON="$(curl -fsS \
  -H "Accept: application/json" \
  -H "Authorization: Bearer $REPO_API_KEY" \
  "$API_URL/api/v1/uploads/$(printf '%s' "$UPLOAD_JSON" | json_get upload_id)")"
ANALYSIS_JOB_ID="$(printf '%s' "$UPLOAD_STATUS_JSON" | json_get data.analysis_job_id)"
wait_for_analysis_job "$ANALYSIS_JOB_ID"

ENC_BRANCH="$(url_encode "$BRANCH")"
REPORT_URL="$API_URL/api/v1/orgs/$ORG_ID/repositories/$REPO_ID/commits/$COMMIT_SHA/report?include_files=true&limit=100"
TREND_URL="$API_URL/api/v1/orgs/$ORG_ID/repositories/$REPO_ID/trends?branch=$ENC_BRANCH&metric=line&from=1970-01-01T00%3A00%3A00Z&to=2100-01-01T00%3A00%3A00Z&limit=10"
GATE_URL="$API_URL/api/v1/orgs/$ORG_ID/repositories/$REPO_ID/gate-evaluations?branch=$ENC_BRANCH&status=passed&limit=10"

log "Verifying report, trend, gate, and badge endpoints"
REPORT_JSON="$(wait_for_json "commit coverage report" "$REPORT_URL")"
printf '%s' "$REPORT_JSON" | json_assert data.commit_sha "$COMMIT_SHA"
printf '%s' "$REPORT_JSON" | json_assert data.line.percent 50

TREND_JSON="$(wait_for_json "coverage trend" "$TREND_URL")"
printf '%s' "$TREND_JSON" | json_assert data.points.0.commit_sha "$COMMIT_SHA"
printf '%s' "$TREND_JSON" | json_assert data.points.0.percent 50

GATE_JSON="$(wait_for_gate_evaluation "$GATE_URL")"
printf '%s' "$GATE_JSON" | json_assert data.0.status passed

api_json PUT "$API_URL/api/v1/orgs/$ORG_ID/repositories/$REPO_ID/badge-settings" "{\"enabled\":true,\"branch\":\"$BRANCH\",\"metric\":\"line\",\"label\":\"coverage\",\"thresholds\":{}}" >/dev/null
BADGE_TOKEN_JSON="$(api_json POST "$API_URL/api/v1/orgs/$ORG_ID/repositories/$REPO_ID/badge-settings/rotate-token")"
BADGE_TOKEN="$(printf '%s' "$BADGE_TOKEN_JSON" | json_get data.token)"
BADGE_JSON="$(curl -fsS -H "Accept: application/json" "$API_URL/api/v1/orgs/$ORG_ID/repositories/$REPO_ID/badges/coverage.json?token=$BADGE_TOKEN&metric=line")"
printf '%s' "$BADGE_JSON" | json_assert data.message "50%"

log "Smoke test passed for org=$ORG_ID repo=$REPO_ID commit=$COMMIT_SHA"
