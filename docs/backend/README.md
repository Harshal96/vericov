# Vericov Backend Service Contracts

Status: Draft for review
Scope: Backend service boundaries, endpoints, request/response models, and database models only
Implementation: Organization, upload, and coverage-analysis slices are started

## Technology Decisions

- Backend services use Helidon 4.
- Runtime baseline is Java 25+ where available; Helidon 4 requires Java 21+.
- Every Helidon service exposes OpenAPI at `/openapi`.
- Every Helidon service exposes OpenAPI UI at `/openapi/ui` when enabled.
- Every Helidon service exposes health endpoints:
  - `GET /health/live`
  - `GET /health/ready`
  - `GET /health/started`
- Every Helidon service exposes metrics at `/observe/metrics`.
- Kong is the external API gateway.
- Self-hosted Supabase is the platform substrate before adding other managed services.

## Supabase Usage

Use self-hosted Supabase components first:

- Supabase Postgres: source-of-truth relational database.
- Supabase Auth: user identity, sessions, JWT issuance, OAuth/SAML integration where available.
- Supabase Storage: raw coverage reports, normalized coverage blobs, test artifacts, agent dry-run artifacts.
- Supabase Realtime: dashboard updates for gate evaluations, agent runs, runner fleet status, and PR report changes.
- Supabase Studio: admin/database visibility during development.
- Supabase Kong: Supabase's own gateway for Supabase APIs; Vericov still uses a dedicated Kong layer for product APIs.
- Supavisor: connection pooling for service-to-Postgres access.
- Edge Functions: reserved for thin platform glue only; core backend remains Helidon.

Authorization rule: do not rely on user-editable Supabase `user_metadata` for authorization. Vericov authorization lives in app tables such as memberships, roles, policies, and service permissions.

## Backend Services

The first backend slice has seven reviewable service contracts:

1. [Kong API Gateway](services/01-kong-api-gateway.md)
2. [Organization Service and API / Control Plane](services/02-api-control-plane-service.md)
3. [Upload / Ingestion Service](services/03-upload-ingestion-service.md)
4. [Coverage Analysis Service](services/04-coverage-analysis-service.md)
5. [Integrations Config Service](services/07-integrations-config-service.md)
6. [Git Integration Service](services/05-git-integration-service.md)
7. [Agent / Runner Control Plane Service](services/06-agent-runner-control-plane-service.md)

## Shared API Conventions

Public APIs use `/api/v1`. Internal service APIs use `/internal/v1`.

Success response:

```json
{
  "data": {}
}
```

Collection response:

```json
{
  "data": [],
  "page": {
    "limit": 50,
    "next_cursor": "opaque-cursor"
  }
}
```

Error response:

```json
{
  "error": {
    "code": "validation_error",
    "message": "Request validation failed",
    "details": [
      {
        "field": "repository_id",
        "code": "required",
        "message": "repository_id is required"
      }
    ]
  }
}
```

Common headers:

| Header | Required | Purpose |
| --- | --- | --- |
| `Authorization: Bearer <jwt>` | User APIs | Supabase Auth JWT or service JWT |
| `X-Vericov-Tenant-Id` | Service APIs | Tenant routing and isolation |
| `Idempotency-Key` | Mutating APIs | Safe retries for uploads, comments, tasks |
| `X-Request-Id` | All APIs | Trace correlation |
| `X-Git-Provider-Delivery` | Webhooks | Provider delivery ID |

## Shared Authentication Modes

| Mode | Used by | Credential | Primary verifier |
| --- | --- | --- | --- |
| User session | Web app, CLI user commands | Supabase Auth JWT | Organization service validates token; Helidon validates membership and permissions |
| Repository API key | CI coverage uploads | Repo-scoped Vericov API key, stored hashed | Upload / Ingestion Service |
| Tokenless CI | Trusted CI providers later | OIDC/provider identity token | Upload / Ingestion Service |
| Runner | Self-hosted runners | Short-lived runner JWT | Agent / Runner Control Plane |
| Service | Internal service calls | Service JWT or mTLS-bound token | Receiving Helidon service |
| Webhook | Git providers | Provider signature header | Git Integration Service |

Supabase Auth is the source of human identity. Vericov service tokens, repository API keys, and runner tokens are application credentials stored and authorized through Vericov tables in Supabase Postgres.

## Shared Database Principles

- Use Supabase Postgres as the source of truth.
- Use UUID primary keys.
- Include `tenant_id` on every tenant-owned table.
- Include `created_at` and `updated_at` on mutable tables.
- Enable RLS for exposed schemas.
- Prefer private schemas for service-only tables.
- Store large files and large normalized coverage maps in Supabase Storage, not Postgres rows.
- Store flexible provider payloads and metadata in `jsonb`, but keep authorization and lifecycle state relational.
