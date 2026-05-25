# Vericov Product Kong Gateway

This is the public product edge for Vericov APIs. It intentionally runs as a separate Kong instance from the Supabase stack so Supabase Auth, REST, and Storage keep their own platform gateway while Vericov owns product routing, CORS, request IDs, rate limits, request-size limits, and internal route protection.

## Run Locally

Copy the environment template if you want to override defaults:

```bash
cp infra/kong/.env.example infra/kong/.env
```

Start the Helidon services first, then start Kong:

```bash
cd infra/kong
docker compose up -d
```

For the full local stack, prefer the repository-level command:

```bash
./scripts/dev-up.sh
```

Health check:

```bash
curl http://localhost:9000/healthz
```

The gateway listens on `http://localhost:9000`. The Kong Admin API is bound to `127.0.0.1:9001` for local inspection only.

## Upstreams

Default upstreams assume the Helidon services are running on the host machine:

| Variable | Default | Purpose |
| --- | --- | --- |
| `ORGANIZATION_SERVICE_URL` | `http://host.docker.internal:8082` | Organization API service |
| `UPLOAD_SERVICE_URL` | `http://host.docker.internal:8080` | Upload API service |
| `COVERAGE_ANALYSIS_SERVICE_URL` | `http://host.docker.internal:8081` | Internal coverage-analysis service |
| `GIT_INTEGRATION_SERVICE_URL` | `http://host.docker.internal:8083` | Git API, internal Git action API, and Git provider webhooks |
| `INTEGRATIONS_SERVICE_URL` | `http://host.docker.internal:8084` | Integrations Config public and internal APIs |
| `SUPABASE_JWT_SECRET` | required | Shared local Supabase Auth JWT secret for edge JWT verification |
| `SUPABASE_JWT_ISSUER` | `http://localhost:8000/auth/v1` | JWT issuer used as the Kong JWT credential key |

On Linux, Compose maps `host.docker.internal` to the Docker host through `host-gateway`.

## Routes

| Path | Upstream | Status |
| --- | --- | --- |
| `/healthz` | Kong pre-function | Implemented |
| `/api/v1/auth/**` | Organization service | Implemented; Supabase Auth JWT-backed user context |
| `/api/v1/orgs/**` | Organization / API Control Plane | Implemented for orgs, memberships, repository setup, config, policies, gates, coverage badges, report reads, trends, gate evaluations, and dashboards; future API key and repo-scoped agent APIs remain nested here |
| `/api/v1/integration-providers/**` | Integrations Config service | Implemented provider catalog |
| `/api/v1/integrations/**` | Integrations Config service | Implemented connection, credential, webhook endpoint, and binding APIs |
| `/api/v1/orgs/{org_id}/integrations/**` | Integrations Config service | Implemented org-scoped integration connection APIs |
| `/api/v1/uploads/**` | Upload service | Implemented |
| `/internal/v1/authz/**` | Organization service | Implemented, private-network restricted |
| `/internal/v1/coverage-analysis/**` | Coverage-analysis service | Implemented, private-network restricted |
| `/internal/v1/integrations/**` | Integrations Config service | Implemented, private-network restricted |
| `/api/v1/git/**` | Git Integration service | Implemented for provider status |
| `/internal/v1/git/**` | Git Integration service | Implemented, private-network restricted |
| `/api/v1/runners/**` | Placeholder | Returns `501` |
| `/runner/v1/**` | Placeholder | Returns `501` |
| `/webhooks/github/**` | Git Integration service | Implemented with HMAC signature verification and delivery dedupe |
| `/webhooks/gitlab/**` | Git Integration service | Routed; returns unsupported provider until implemented |
| `/webhooks/bitbucket/**` | Git Integration service | Routed; returns unsupported provider until implemented |
| `/api/v1/openapi/**` | Placeholder | Returns `501` until aggregation exists |

## Authentication Boundary

The upload route does not use Kong key-auth. The upload service authenticates Vericov API keys from the `Authorization: Bearer <api-key>` header and performs tenant-level validation itself.

Kong validates Supabase Auth JWTs on user API routes before proxying and injects `X-Vericov-User-Id` / `X-Vericov-User-Email` from the verified token for services that need edge-derived identity context. The organization service still validates Supabase Auth JWTs from `Authorization: Bearer <jwt>` using the self-hosted Supabase JWT secret. Vericov roles and permissions are not read from Supabase `user_metadata`; they come from the `vericov.memberships` and `vericov.organization_invitations` tables. Local header-based auth is available only when the service is started with `VERICOV_DEV_AUTH_BYPASS=true`.

## Validation

Run the local config-contract test:

```bash
node infra/kong/scripts/validate-config.mjs
```

Validate Compose syntax:

```bash
cd infra/kong
docker compose config --quiet
```

Render the Kong template without starting Kong:

```bash
env \
  VERICOV_KONG_TEMPLATE_PATH=infra/kong/kong.yml \
  KONG_DECLARATIVE_CONFIG=/private/tmp/vericov-kong-rendered.yml \
  VERICOV_KONG_RENDER_ONLY=true \
  ORGANIZATION_SERVICE_URL=http://organization:8082 \
  UPLOAD_SERVICE_URL=http://upload:8080 \
  COVERAGE_ANALYSIS_SERVICE_URL=http://coverage-analysis:8081 \
  GIT_INTEGRATION_SERVICE_URL=http://git-integration:8083 \
  INTEGRATIONS_SERVICE_URL=http://integrations:8084 \
  SUPABASE_JWT_SECRET=local-development-secret \
  SUPABASE_JWT_ISSUER=http://localhost:8000/auth/v1 \
  VERICOV_CORS_ORIGIN='*' \
  VERICOV_USER_RATE_LIMIT_MINUTE=120 \
  VERICOV_UPLOAD_RATE_LIMIT_MINUTE=60 \
  VERICOV_UPLOAD_RATE_LIMIT_HOUR=1000 \
  VERICOV_UPLOAD_MAX_BODY_MB=110 \
  VERICOV_WEBHOOK_RATE_LIMIT_MINUTE=120 \
  VERICOV_BADGE_RATE_LIMIT_MINUTE=600 \
  VERICOV_PUBLIC_RATE_LIMIT_MINUTE=120 \
  VERICOV_INTERNAL_RATE_LIMIT_MINUTE=120 \
  sh infra/kong/kong-entrypoint.sh
```
