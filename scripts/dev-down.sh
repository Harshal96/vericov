#!/usr/bin/env sh
set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
LOCAL_ENV="$ROOT_DIR/infra/local/.env"
RUNTIME_DIR="$ROOT_DIR/.vericov/dev"

if [ -d "$RUNTIME_DIR/pids" ]; then
  for pid_file in "$RUNTIME_DIR"/pids/*.pid; do
    [ -f "$pid_file" ] || continue
    pid="$(cat "$pid_file")"
    if kill -0 "$pid" 2>/dev/null; then
      kill "$pid"
    fi
    printf '\n' > "$pid_file"
  done
fi

if [ -f "$LOCAL_ENV" ]; then
  docker compose --env-file "$LOCAL_ENV" -f "$ROOT_DIR/infra/local/docker-compose.yml" down
fi

(cd "$ROOT_DIR/infra/supabase" && docker compose down)

echo "Vericov local stack stopped."
