# Vericov Backend Service Contracts

Status: Backend contracts
Scope: Backend service boundaries, endpoints, request/response models, and database models only
Implementation: Self-hostable Helidon services behind an external/customer gateway

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
- Vericov no longer bundles a product Kong gateway.
- Self-hosting can run without an auth provider by using `VERICOV_DEV_AUTH_BYPASS=true`.
- Gateway-authenticated deployments receive short-lived service JWTs from the operator gateway.
- Supabase is optional and is used only when the operator chooses it for storage or platform services.

## Storage and Auth

Postgres is required for durable self-hosting. Supabase storage remains a
supported object-storage option, but Supabase Auth is not required by Vericov
services. Authentication, if enabled, is pushed to the gateway and delegated to
services through the service-JWT contract in `docs/GATEWAY_AUTH.md`.

## Backend Services

Backend service contracts:

1. [Historical Kong API Gateway](services/01-kong-api-gateway.md)
2. [Control Plane Service](services/02-control-plane-service.md)
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
| `Authorization: Bearer <jwt>` | Gateway-authenticated APIs | Service JWT from the operator gateway |
| `X-Vericov-User-Id` | Dev bypass only | Local/self-host identity when bypass is enabled |
| `X-Vericov-Tenant-Id` | Managed service APIs | Tenant claim propagation |
| `Idempotency-Key` | Mutating APIs | Safe retries for uploads, comments, tasks |
| `X-Request-Id` | All APIs | Trace correlation |
| `X-Git-Provider-Delivery` | Webhooks | Provider delivery ID |

## Shared Authentication Modes

| Mode | Used by | Credential | Primary verifier |
| --- | --- | --- | --- |
| Self-host/no-auth | Private gateway or trusted network | `VERICOV_DEV_AUTH_BYPASS=true` | Operator boundary |
| Gateway user delegation | Operator gateway | Short-lived service JWT | Receiving Helidon service |
| Repository API key | CI coverage uploads | Repo-scoped Vericov API key, stored hashed | Upload / Ingestion Service |
| Tokenless CI | GitHub Actions in v1 | OIDC/provider identity token | Upload / Ingestion Service |
| Runner | Self-hosted runners | Short-lived runner upload JWT | Upload / Ingestion Service |
| Webhook | Git providers | Provider signature header | Git Integration Service |

Services do not validate Supabase Auth JWTs.

## Shared Database Principles

- Use Supabase Postgres as the source of truth.
- Use UUID primary keys.
- Managed tenant identity is a service-JWT claim. Self-host deployments can be single-tenant.
- Include `created_at` and `updated_at` on mutable tables.
- Enable RLS for exposed schemas.
- Prefer private schemas for service-only tables.
- Store large files and large normalized coverage maps in Supabase Storage, not Postgres rows.
- Store flexible provider payloads and metadata in `jsonb`, but keep authorization and lifecycle state relational.
