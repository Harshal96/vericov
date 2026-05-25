# Kong API Gateway Contract

Status: Implemented scaffold with gateway-heavy local guardrails
Runtime: Kong Gateway
Backend services: Helidon 4
Auth provider: Supabase Auth

## Purpose

Kong is the only public ingress for Vericov product APIs. It routes external traffic to Helidon services, enforces cross-cutting controls, and keeps Supabase platform APIs separate from Vericov product APIs.

Decision: Vericov runs a dedicated product Kong instance under `infra/kong`. The Supabase-bundled Kong instance remains scoped to Supabase Auth, REST, Storage, and Studio APIs.

Kong owns routing, TLS termination, request IDs, coarse rate limits, CORS, request size limits, webhook path routing, internal route protection, Supabase Auth JWT verification for user API routes, and verified user-header injection for services that need edge-derived identity context. Business authorization stays in Helidon services.

Implementation files:

- `infra/kong/docker-compose.yml`
- `infra/kong/kong.yml`
- `infra/kong/kong-entrypoint.sh`
- `infra/kong/.env.example`
- `infra/kong/scripts/validate-config.mjs`
- `infra/kong/README.md`

## Route Groups

| Public path | Upstream service | Notes |
| --- | --- | --- |
| `/api/v1/auth/**` | Organization Service | Implemented; Supabase Auth JWT-backed current-user APIs |
| `/api/v1/orgs/**` | Organization / API Control Plane | Implemented for user/org membership management plus repository setup, config, policies, gates, coverage badges, report reads, trends, gate evaluations, and dashboards; future API keys and repo-scoped agent APIs stay nested under orgs |
| `/api/v1/orgs/{org_id}/repositories/{repository_id}/badges/coverage.{svg,json}` | Organization / API Control Plane | Implemented; public/tokenized badge read route with separate rate limit |
| `/api/v1/uploads/**` | Upload / Ingestion | Implemented; gateway requires a bearer credential and upload service validates Vericov API keys |
| `/api/v1/integration-providers/**` | Integrations Config | Implemented; provider catalog behind user JWT edge verification |
| `/api/v1/integrations/**` | Integrations Config | Implemented; connection lifecycle and bindings behind user JWT edge verification |
| `/api/v1/git/**` | Git Integration | Provider action status and Git-specific public APIs |
| `/webhooks/github/**` | Git Integration | GitHub webhook receiver |
| `/webhooks/gitlab/**` | Git Integration | GitLab webhook receiver |
| `/webhooks/bitbucket/**` | Git Integration | Bitbucket webhook receiver |
| `/api/v1/runners/**` | Agent / Runner Control Plane | Runner fleet APIs |
| `/runner/v1/**` | Agent / Runner Control Plane | Runner polling protocol |
| `/internal/v1/coverage-analysis/**` | Coverage Analysis | Implemented; private network only |
| `/internal/v1/authz/**` | Organization Service | Implemented; private network only |
| `/internal/v1/integrations/**` | Integrations Config | Implemented; private network and service auth only |
| `/internal/v1/**` | Not public | Public ingress catch-all blocked by default |

Route status notes: `Implemented` rows reflect the current scaffold. `Planned` rows are contract-level routes to add as the corresponding service surfaces are wired into Kong.

Public ingress blocks catch-all `/internal/v1/**` traffic by default. Explicitly allowlisted internal route groups, such as `/internal/v1/authz/**`, `/internal/v1/coverage-analysis/**`, and `/internal/v1/integrations/**`, may be routed only on private network or service-to-service listeners with service authentication; they are not public internet routes.

## Gateway-Owned Endpoints

These endpoints are handled by Kong or platform glue, not a Helidon business service.

### `GET /healthz`

Gateway health probe.

Response:

```json
{
  "data": {
    "status": "ok",
    "gateway": "kong"
  }
}
```

### `GET /api/v1/openapi`

Aggregated OpenAPI document for public Vericov APIs.

Current implementation returns `501 service_not_implemented` until the OpenAPI aggregation service exists.

Response:

```json
{
  "openapi": "3.1.0",
  "info": {
    "title": "Vericov Public API",
    "version": "v1"
  },
  "paths": {}
}
```

### `GET /api/v1/openapi/ui`

Aggregated OpenAPI UI for public Vericov APIs.

Current implementation returns `501 service_not_implemented` until the OpenAPI aggregation service exists.

## Kong Plugins

| Plugin | Scope | Purpose |
| --- | --- | --- |
| Correlation ID | Global | Adds or forwards `X-Request-Id` |
| CORS | Public API routes | Browser access for app and docs |
| Rate limiting | Public API routes | Tenant/user/IP limits |
| Request size limiting | Upload routes | Protect upload ingress |
| Pre-function | Gateway-owned routes | Returns health and explicit not-implemented responses |
| IP restriction | Internal routes | Restricts internal routes to loopback and private network ranges |
| JWT validation | User routes | Validate Supabase Auth JWT at the edge |
| Request transformer / pre-function | Public routes | Strip spoofable identity/service headers and inject verified user context after JWT validation |
| Bot detection / IP allowlist | Future webhooks and runner routes | Reduce abuse surface |
| Prometheus | Global | Gateway metrics |

## Authentication Boundary

`/api/v1/uploads/**` intentionally does not use Kong key-auth. The upload service authenticates `Authorization: Bearer <api-key>` directly so CI upload clients can send one credential and receive a processing identifier immediately.

`/api/v1/auth/**`, `/api/v1/orgs/**`, and Integrations Config user routes require a Supabase Auth JWT at Kong. Kong validates the token with the configured local Supabase JWT secret, strips spoofable incoming identity headers, and forwards `X-Vericov-User-Id` / `X-Vericov-User-Email` derived from the verified token. Organization still validates Supabase Auth JWTs and enforces Vericov membership/role authorization from application tables. Local header-based auth is available only behind `VERICOV_DEV_AUTH_BYPASS=true`.

`/api/v1/uploads/**` intentionally does not use Kong key-auth. Kong checks that a bearer credential is present and the upload service authenticates repository API keys, runner upload tokens, Supabase user JWTs, or trusted CI identity directly.

Unknown `/api/v1/**`, `/webhooks/**`, and `/internal/v1/**` paths are blocked by default after explicit route groups.

## Request Models

### GatewayRoute

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `name` | string | yes | Stable route name |
| `path_prefix` | string | yes | Public path prefix |
| `upstream_service` | string | yes | Target service name |
| `auth_mode` | enum | yes | `public`, `user_jwt`, `api_key`, `runner_token`, `webhook_signature`, `internal` |
| `rate_limit_policy` | string | no | Gateway policy key |
| `max_body_bytes` | integer | no | Upload/webhook protection |

### GatewayAuthContext

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `subject` | string | yes | User, runner, service, or API key identity |
| `tenant_id` | uuid | no | Tenant when known at gateway |
| `auth_mode` | enum | yes | Auth mode used |
| `scopes` | string[] | no | Gateway-level scopes |

## Response Models

### GatewayError

```json
{
  "error": {
    "code": "gateway_unauthorized",
    "message": "Authentication is required"
  }
}
```

### GatewayRateLimitError

```json
{
  "error": {
    "code": "rate_limited",
    "message": "Too many requests",
    "retry_after_seconds": 60
  }
}
```

## Database Models

Kong does not own application data. Gateway configuration is stored as declarative config in repo and deployed through the gateway deployment pipeline.

Optional operational tables in Supabase Postgres:

### `gateway_route_registry`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `name` | text | Route name |
| `path_prefix` | text | Public prefix |
| `upstream_service` | text | Service target |
| `auth_mode` | text | Required auth mode |
| `status` | text | `active`, `disabled` |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

### `gateway_audit_events`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Nullable for unauthenticated events |
| `request_id` | text | Correlation ID |
| `route_name` | text | Matched route |
| `actor_type` | text | `user`, `runner`, `api_key`, `service`, `anonymous` |
| `actor_id` | text | Provider/user/runner identifier |
| `status_code` | integer | Response status |
| `metadata` | jsonb | Redacted gateway metadata |
| `created_at` | timestamptz | Event time |

## Open Questions

- Should aggregated OpenAPI be generated at build time or proxied dynamically from Helidon `/openapi` endpoints?
- Which routes should support anonymous public access for open source repositories?
