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

print_urls() {
  echo "Vericov local stack is starting on direct service ports:"
  echo "  upload:            http://localhost:8080"
  echo "  coverage-analysis: http://localhost:8081"
  echo "  control-plane:     http://localhost:8082"
  echo "  git-integration:   http://localhost:8083"
  echo "  integrations:      http://localhost:8084"
  echo "  agent-runner:      http://localhost:8085"
}

if [ ! -f "$SUPABASE_ENV" ]; then
  node "$ROOT_DIR/infra/supabase/scripts/generate-env.mjs"
fi

node "$ROOT_DIR/infra/local/scripts/generate-env.mjs"

(cd "$ROOT_DIR/infra/supabase" && docker compose up -d)

if [ "$MODE" = "container" ]; then
  docker compose --env-file "$LOCAL_ENV" -f "$ROOT_DIR/infra/local/docker-compose.yml" up -d --build
  print_urls
  exit 0
fi

mvn -q -DskipTests package
mkdir -p "$RUNTIME_DIR/logs" "$RUNTIME_DIR/pids"

set -a
. "$LOCAL_ENV"
set +a

SUPABASE_URL="$VERICOV_HOST_SUPABASE_URL"
SUPABASE_STORAGE_URL="$VERICOV_HOST_SUPABASE_STORAGE_URL"
VERICOV_DB_URL="$VERICOV_HOST_SUPABASE_DB_URL"

export SUPABASE_URL SUPABASE_STORAGE_URL VERICOV_DB_URL
export VERICOV_DB_USER VERICOV_DB_PASSWORD SUPABASE_SERVICE_ROLE_KEY
export VERICOV_REPO_API_KEY_PEPPER VERICOV_RUNNER_JWT_SECRET
export VERICOV_RUNNER_JWT_ISSUER VERICOV_RUNNER_JWT_AUDIENCE
export VERICOV_SERVICE_JWT_PUBLIC_KEY VERICOV_SERVICE_JWT_SECRET
export VERICOV_SERVICE_JWT_ISSUER VERICOV_SERVICE_JWT_AUDIENCE
export VERICOV_DEV_AUTH_BYPASS VERICOV_DEV_USER_ID VERICOV_DEV_USER_EMAIL
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
  VERICOV_UPLOAD_DB_URL="$VERICOV_DB_URL" \
  VERICOV_UPLOAD_DB_USER="$VERICOV_DB_USER" \
  VERICOV_UPLOAD_DB_PASSWORD="$VERICOV_DB_PASSWORD" \
  VERICOV_ARTIFACT_STORAGE_BACKEND=supabase

start_service coverage-analysis services/coverage-analysis/target/coverage-analysis.jar 8081 \
  VERICOV_ANALYSIS_DB_URL="$VERICOV_DB_URL" \
  VERICOV_ANALYSIS_DB_USER="$VERICOV_DB_USER" \
  VERICOV_ANALYSIS_DB_PASSWORD="$VERICOV_DB_PASSWORD" \
  VERICOV_CONTROL_PLANE_BASE_URL=http://127.0.0.1:8082 \
  VERICOV_GIT_BASE_URL=http://127.0.0.1:8083

start_service agent-runner services/agent-runner/target/agent-runner.jar 8085 \
  VERICOV_DATABASE_URL="$VERICOV_DB_URL" \
  VERICOV_DATABASE_USER="$VERICOV_DB_USER" \
  VERICOV_DATABASE_PASSWORD="$VERICOV_DB_PASSWORD"

start_service control-plane services/control-plane/target/control-plane.jar 8082 \
  VERICOV_CONTROL_PLANE_DB_URL="$VERICOV_DB_URL" \
  VERICOV_CONTROL_PLANE_DB_USER="$VERICOV_DB_USER" \
  VERICOV_CONTROL_PLANE_DB_PASSWORD="$VERICOV_DB_PASSWORD"

start_service integrations services/integrations/target/integrations.jar 8084 \
  VERICOV_DATABASE_URL="$VERICOV_DB_URL" \
  VERICOV_DATABASE_USER="$VERICOV_DB_USER" \
  VERICOV_DATABASE_PASSWORD="$VERICOV_DB_PASSWORD"

start_service git-integration services/git-integration/target/git-integration.jar 8083 \
  VERICOV_DATABASE_URL="$VERICOV_DB_URL" \
  VERICOV_DATABASE_USER="$VERICOV_DB_USER" \
  VERICOV_DATABASE_PASSWORD="$VERICOV_DB_PASSWORD" \
  VERICOV_INTEGRATIONS_BASE_URL=http://127.0.0.1:8084

print_urls
echo "Host-run service logs are in $RUNTIME_DIR/logs"
