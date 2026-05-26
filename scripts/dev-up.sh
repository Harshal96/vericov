#!/usr/bin/env sh
set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
MODE="${1:-container}"

if [ "$MODE" = "--host-java" ]; then
  MODE="host-java"
fi

if [ "$MODE" != "container" ] && [ "$MODE" != "host-java" ]; then
  echo "Usage: ./scripts/dev-up.sh [--host-java]" >&2
  exit 2
fi

SUPABASE_ENV="$ROOT_DIR/infra/supabase/.env"
LOCAL_ENV="$ROOT_DIR/infra/local/.env"
RUNTIME_DIR="$ROOT_DIR/.vericov/dev"

if [ ! -f "$SUPABASE_ENV" ]; then
  node "$ROOT_DIR/infra/supabase/scripts/generate-env.mjs"
fi

node "$ROOT_DIR/infra/local/scripts/generate-env.mjs"

(cd "$ROOT_DIR/infra/supabase" && docker compose up -d)

if [ "$MODE" = "container" ]; then
  docker compose --env-file "$LOCAL_ENV" -f "$ROOT_DIR/infra/local/docker-compose.yml" up -d --build
  echo "Vericov local stack is starting at http://localhost:9000"
  exit 0
fi

mvn -q -DskipTests package
mkdir -p "$RUNTIME_DIR/logs" "$RUNTIME_DIR/pids"

ORGANIZATION_SERVICE_URL=http://host.docker.internal:8082 \
UPLOAD_SERVICE_URL=http://host.docker.internal:8080 \
COVERAGE_ANALYSIS_SERVICE_URL=http://host.docker.internal:8081 \
AGENT_RUNNER_SERVICE_URL=http://host.docker.internal:8085 \
INTEGRATIONS_SERVICE_URL=http://host.docker.internal:8084 \
GIT_INTEGRATION_SERVICE_URL=http://host.docker.internal:8083 \
docker compose --env-file "$LOCAL_ENV" -f "$ROOT_DIR/infra/local/docker-compose.yml" up -d product-gateway

set -a
. "$LOCAL_ENV"
set +a

SUPABASE_URL="$VERICOV_HOST_SUPABASE_URL"
SUPABASE_STORAGE_URL="$VERICOV_HOST_SUPABASE_STORAGE_URL"
SUPABASE_DB_URL="$VERICOV_HOST_SUPABASE_DB_URL"

export SUPABASE_URL SUPABASE_STORAGE_URL SUPABASE_DB_URL
export SUPABASE_DB_USER SUPABASE_DB_PASSWORD SUPABASE_SERVICE_ROLE_KEY
export SUPABASE_JWT_SECRET SUPABASE_JWT_ISSUER SUPABASE_JWT_AUDIENCE
export VERICOV_REPO_API_KEY_PEPPER VERICOV_RUNNER_JWT_SECRET
export VERICOV_RUNNER_JWT_ISSUER VERICOV_RUNNER_JWT_AUDIENCE
export VERICOV_INTERNAL_SERVICE_TOKEN VERICOV_INTERNAL_SERVICE_TOKEN_SHA256
export VERICOV_GITHUB_WEBHOOK_SECRET

start_service() {
  name="$1"
  jar="$2"
  port="$3"
  shift 3

  pid_file="$RUNTIME_DIR/pids/$name.pid"
  if [ -f "$pid_file" ]; then
    old_pid="$(cat "$pid_file")"
    if kill -0 "$old_pid" 2>/dev/null; then
      kill "$old_pid"
    fi
  fi

  nohup env "$@" java -Dserver.host=127.0.0.1 -Dserver.port="$port" -jar "$ROOT_DIR/$jar" \
    > "$RUNTIME_DIR/logs/$name.log" 2>&1 &
  printf '%s\n' "$!" > "$pid_file"
}

start_service upload services/upload/target/upload.jar 8080 \
  VERICOV_UPLOAD_DB_URL="$SUPABASE_DB_URL" \
  VERICOV_UPLOAD_DB_USER="$SUPABASE_DB_USER" \
  VERICOV_UPLOAD_DB_PASSWORD="$SUPABASE_DB_PASSWORD" \
  VERICOV_ARTIFACT_STORAGE_BACKEND=supabase

start_service coverage-analysis services/coverage-analysis/target/coverage-analysis.jar 8081 \
  VERICOV_ANALYSIS_DB_URL="$SUPABASE_DB_URL" \
  VERICOV_ANALYSIS_DB_USER="$SUPABASE_DB_USER" \
  VERICOV_ANALYSIS_DB_PASSWORD="$SUPABASE_DB_PASSWORD" \
  VERICOV_GIT_BASE_URL=http://127.0.0.1:8083

start_service agent-runner services/agent-runner/target/agent-runner.jar 8085 \
  VERICOV_DATABASE_URL="$SUPABASE_DB_URL" \
  VERICOV_DATABASE_USER="$SUPABASE_DB_USER" \
  VERICOV_DATABASE_PASSWORD="$SUPABASE_DB_PASSWORD"

start_service organization services/organization/target/organization.jar 8082 \
  VERICOV_ORGANIZATION_DB_URL="$SUPABASE_DB_URL" \
  VERICOV_ORGANIZATION_DB_USER="$SUPABASE_DB_USER" \
  VERICOV_ORGANIZATION_DB_PASSWORD="$SUPABASE_DB_PASSWORD"

start_service integrations services/integrations/target/integrations.jar 8084 \
  VERICOV_DATABASE_URL="$SUPABASE_DB_URL" \
  VERICOV_DATABASE_USER="$SUPABASE_DB_USER" \
  VERICOV_DATABASE_PASSWORD="$SUPABASE_DB_PASSWORD"

start_service git-integration services/git-integration/target/git-integration.jar 8083 \
  VERICOV_DATABASE_URL="$SUPABASE_DB_URL" \
  VERICOV_DATABASE_USER="$SUPABASE_DB_USER" \
  VERICOV_DATABASE_PASSWORD="$SUPABASE_DB_PASSWORD" \
  VERICOV_INTEGRATIONS_BASE_URL=http://127.0.0.1:8084

echo "Vericov local stack is starting at http://localhost:9000"
echo "Host-run service logs are in $RUNTIME_DIR/logs"
