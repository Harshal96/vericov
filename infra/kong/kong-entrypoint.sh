#!/usr/bin/env sh
set -eu

template_path="${VERICOV_KONG_TEMPLATE_PATH:-/home/kong/kong.yml.template}"
rendered_path="${KONG_DECLARATIVE_CONFIG:-/tmp/vericov-kong/kong.yml}"

mkdir -p "$(dirname "$rendered_path")"

awk '{
  result = ""
  rest = $0
  while (match(rest, /\$\{[A-Za-z_][A-Za-z_0-9]*\}|\$[A-Za-z_][A-Za-z_0-9]*/)) {
    token = substr(rest, RSTART, RLENGTH)
    if (substr(token, 2, 1) == "{") {
      varname = substr(token, 3, length(token) - 3)
    } else {
      varname = substr(token, 2)
    }

    if (varname in ENVIRON) {
      result = result substr(rest, 1, RSTART - 1) ENVIRON[varname]
    } else {
      result = result substr(rest, 1, RSTART - 1) token
    }
    rest = substr(rest, RSTART + RLENGTH)
  }
  print result rest
}' "$template_path" > "$rendered_path"

if grep -q '\${[A-Za-z_][A-Za-z_0-9]*}' "$rendered_path"; then
  echo "Kong declarative config contains unresolved environment variables:" >&2
  grep -o '\${[A-Za-z_][A-Za-z_0-9]*}' "$rendered_path" | sort -u >&2
  exit 1
fi

if [ "${VERICOV_KONG_RENDER_ONLY:-false}" = "true" ]; then
  exit 0
fi

if [ -x /docker-entrypoint.sh ]; then
  exec /docker-entrypoint.sh kong docker-start
fi

exec /entrypoint.sh kong docker-start
