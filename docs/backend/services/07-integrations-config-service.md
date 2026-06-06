# Integrations Config Service Contract

Status: Draft for review
Runtime: Helidon 4 on Java 25+
Public base path: `/api/v1`
Internal base path: `/internal/v1/integrations`
OpenAPI: `/openapi`

## Purpose

The Integrations Config Service owns provider-neutral integration configuration for Vericov. It stores the provider catalog, organization connections, credential metadata and vault references, scope bindings, webhook endpoint metadata, sync state, and integration audit events.

This service does not execute provider actions. Git provider webhooks, checks, comments, annotations, branch creation, pull request creation, and provider API calls remain in the Git Integration Service. The Control Plane remains the source of truth for canonical organizations and repositories; this service validates scope ownership before binding integration connections or recording scoped sync/event state. Component scopes return `not_found` until a canonical component owner exists.

## Public Endpoints

Public endpoints require a user identity and organization authorization.

Public tenancy boundary: every public request validates the requester user identity against the exact `tenant_id` and `org_id` in the request path, query string, or body. Caller-supplied `tenant_id` is routing context only and is not trusted by itself; tenant, organization, connection, and binding mismatches are rejected.

Service-owned JSON maps are metadata only. Connection config, binding config, provider defaults, sync cursors/checkpoints/last errors, and event payload/error maps reject secret-bearing keys recursively, including `secret`, `password`, `token`, `api_key`, `apikey`, `private_key`, `privatekey`, `authorization`, `credential`, `client_secret`, `access_token`, and `refresh_token`.

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/integration-providers?type={type}` | List provider definitions, optionally filtered by provider type |
| `GET` | `/api/v1/orgs/{org_id}/integrations?tenant_id={tenant_id}` | List integration connections for an organization |
| `POST` | `/api/v1/orgs/{org_id}/integrations` | Create a connection or complete a provider installation handoff |
| `GET` | `/api/v1/integrations/{connection_id}?tenant_id={tenant_id}&org_id={org_id}` | Get connection details |
| `PATCH` | `/api/v1/integrations/{connection_id}` | Update display name, status, or provider config |
| `POST` | `/api/v1/integrations/{connection_id}/disable` | Disable a connection |
| `GET` | `/api/v1/integrations/{connection_id}/bindings?tenant_id={tenant_id}&org_id={org_id}` | List scope bindings |
| `PUT` | `/api/v1/integrations/{connection_id}/bindings/{scope_type}/{scope_id}` | Upsert a binding |
| `DELETE` | `/api/v1/integrations/{connection_id}/bindings/{scope_type}/{scope_id}?tenant_id={tenant_id}&org_id={org_id}&expected_updated_at={expected_updated_at}` | Disable a binding |
| `POST` | `/api/v1/integrations/{connection_id}/credentials` | Store credential material in the vault and persist only credential metadata |
| `GET` | `/api/v1/integrations/{connection_id}/credentials?tenant_id={tenant_id}&org_id={org_id}` | List credential metadata without raw secrets |
| `POST` | `/api/v1/integrations/{connection_id}/webhook-endpoints` | Register webhook endpoint metadata and signing secret reference |
| `GET` | `/api/v1/integrations/{connection_id}/webhook-endpoints?tenant_id={tenant_id}&org_id={org_id}` | List webhook endpoint metadata |

## Internal Endpoints

Internal endpoints should be reached through propagated service JWTs or the new gRPC contracts. The legacy static-token REST bridge remains transitional during the gRPC migration and must not be exposed publicly.

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/internal/v1/integrations/connections/{connection_id}?tenant_id={tenant_id}&org_id={org_id}` | Resolve full connection metadata for service callers |
| `GET` | `/internal/v1/integrations/resolve?tenant_id={tenant_id}&org_id={org_id}&provider_key={provider_key}&scope_type={scope_type}&scope_id={scope_id}&capability={capability}` | Resolve active connection, binding, and credential kind for provider, scope, and capability |
| `POST` | `/internal/v1/integrations/connections/{connection_id}/credential-leases` | Issue a short-lived credential lease to an authorized service |
| `POST` | `/internal/v1/integrations/connections/{connection_id}/sync-state` | Upsert provider sync cursor, status, checkpoint, lease, and last error |
| `POST` | `/internal/v1/integrations/events` | Record provider action, webhook, sync, or audit events |

## Request Models

### CreateIntegrationConnectionRequest

```json
{
  "tenant_id": "22222222-2222-2222-2222-222222222222",
  "provider_key": "github",
  "display_name": "Engineering GitHub",
  "external_account_id": "123456",
  "external_account_name": "acme",
  "config": {
    "installation_id": "123456"
  }
}
```

### UpdateIntegrationConnectionRequest

```json
{
  "tenant_id": "22222222-2222-2222-2222-222222222222",
  "org_id": "33333333-3333-3333-3333-333333333333",
  "display_name": "Primary GitHub",
  "status": "active",
  "config": {
    "installation_id": "123456"
  },
  "expected_updated_at": "2026-05-23T10:00:00Z"
}
```

### DisableIntegrationConnectionRequest

```json
{
  "tenant_id": "22222222-2222-2222-2222-222222222222",
  "org_id": "33333333-3333-3333-3333-333333333333",
  "expected_updated_at": "2026-05-23T10:00:00Z"
}
```

### UpsertIntegrationBindingRequest

```json
{
  "tenant_id": "22222222-2222-2222-2222-222222222222",
  "org_id": "33333333-3333-3333-3333-333333333333",
  "capabilities": ["git.checks", "git.comments"],
  "config": {
    "events": ["pull_request"]
  },
  "status": "active",
  "expected_updated_at": "2026-05-23T10:00:00Z"
}
```

### DisableIntegrationBindingRequest

No request body. The caller supplies tenant and optimistic concurrency context as query parameters:

```text
DELETE /api/v1/integrations/{connection_id}/bindings/{scope_type}/{scope_id}?tenant_id={tenant_id}&org_id={org_id}&expected_updated_at={expected_updated_at}
```

### CreateIntegrationCredentialRequest

Raw `secret` is accepted only on create, stored through the configured vault, and never echoed in the response.

```json
{
  "tenant_id": "22222222-2222-2222-2222-222222222222",
  "org_id": "33333333-3333-3333-3333-333333333333",
  "credential_kind": "github_app_private_key",
  "secret": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----",
  "expires_at": null
}
```

### CreateIntegrationWebhookEndpointRequest

```json
{
  "tenant_id": "22222222-2222-2222-2222-222222222222",
  "org_id": "33333333-3333-3333-3333-333333333333",
  "external_webhook_id": "provider-webhook-id",
  "endpoint_url": "https://api.vericov.dev/webhooks/github",
  "event_types": ["pull_request", "check_suite", "check_run", "push", "issue_comment"],
  "signing_secret_ref": "vault://integrations/55555555-5555-5555-5555-555555555555/webhook_secret/1",
  "config": {
    "content_type": "json"
  }
}
```

### CredentialLeaseRequest

```json
{
  "tenant_id": "22222222-2222-2222-2222-222222222222",
  "org_id": "33333333-3333-3333-3333-333333333333",
  "credential_kind": "api_token"
}
```

`credential_kind` is the exact material needed by the provider action, such as `github_app_private_key`, `oauth_access_token`, or `api_token`.

### UpdateIntegrationSyncStateRequest

```json
{
  "tenant_id": "22222222-2222-2222-2222-222222222222",
  "org_id": "33333333-3333-3333-3333-333333333333",
  "sync_type": "repository_full",
  "scope_type": "repository",
  "scope_id": "44444444-4444-4444-4444-444444444444",
  "status": "running",
  "cursor": {
    "after": "opaque-provider-cursor"
  },
  "checkpoint": {
    "repository_count": 12
  },
  "last_error": {},
  "last_started_at": "2026-05-23T10:00:00Z",
  "last_completed_at": null,
  "next_run_at": "2026-05-23T10:10:00Z",
  "lease_expires_at": "2026-05-23T10:05:00Z"
}
```

### RecordIntegrationEventRequest

```json
{
  "tenant_id": "22222222-2222-2222-2222-222222222222",
  "org_id": "33333333-3333-3333-3333-333333333333",
  "connection_id": "55555555-5555-5555-5555-555555555555",
  "provider_key": "github",
  "event_type": "git.check.updated",
  "external_event_id": "provider-delivery-id",
  "scope_type": "repository",
  "scope_id": "44444444-4444-4444-4444-444444444444",
  "status": "processed",
  "payload": {
    "provider_check_id": "987654"
  },
  "error": {},
  "received_at": "2026-05-23T10:00:00Z",
  "processed_at": "2026-05-23T10:00:01Z"
}
```

## Response Models

### ProviderDefinitionResponse

```json
{
  "data": {
    "provider_key": "github",
    "type": "git",
    "display_name": "GitHub",
    "auth_strategy": "github_app",
    "capabilities": [
      "git.webhooks",
      "git.checks",
      "git.comments",
      "git.pull_requests",
      "git.repository_sync"
    ],
    "default_config": {
      "webhook_events": ["pull_request", "check_suite", "issue_comment"]
    },
    "credential_kind_by_capability": {
      "git.webhooks": "webhook_secret",
      "git.checks": "github_app_private_key",
      "git.comments": "github_app_private_key",
      "git.pull_requests": "github_app_private_key",
      "git.repository_sync": "github_app_private_key"
    }
  }
}
```

### IntegrationConnectionResponse

```json
{
  "data": {
    "id": "55555555-5555-5555-5555-555555555555",
    "tenant_id": "22222222-2222-2222-2222-222222222222",
    "org_id": "33333333-3333-3333-3333-333333333333",
    "provider_key": "github",
    "integration_type": "git",
    "display_name": "Engineering GitHub",
    "external_account_id": "123456",
    "external_account_name": "acme",
    "status": "active",
    "config": {
      "installation_id": "123456"
    },
    "created_by": "11111111-1111-1111-1111-111111111111",
    "last_verified_at": "2026-05-23T10:00:00Z",
    "created_at": "2026-05-23T10:00:00Z",
    "updated_at": "2026-05-23T10:00:00Z"
  }
}
```

### IntegrationBindingResponse

```json
{
  "data": {
    "id": "66666666-6666-6666-6666-666666666666",
    "tenant_id": "22222222-2222-2222-2222-222222222222",
    "connection_id": "55555555-5555-5555-5555-555555555555",
    "scope_type": "repository",
    "scope_id": "44444444-4444-4444-4444-444444444444",
    "capabilities": ["git.checks", "git.comments"],
    "config": {
      "events": ["pull_request"]
    },
    "status": "active",
    "created_at": "2026-05-23T10:00:00Z",
    "updated_at": "2026-05-23T10:00:00Z"
  }
}
```

### IntegrationCredentialResponse

```json
{
  "data": {
    "id": "99999999-9999-9999-9999-999999999999",
    "tenant_id": "22222222-2222-2222-2222-222222222222",
    "connection_id": "55555555-5555-5555-5555-555555555555",
    "credential_kind": "github_app_private_key",
    "secret_ref": "vault://integrations/55555555-5555-5555-5555-555555555555/github_app_private_key/1",
    "key_version": 1,
    "status": "active",
    "expires_at": null,
    "last_rotated_at": "2026-05-23T10:00:00Z",
    "created_at": "2026-05-23T10:00:00Z",
    "updated_at": "2026-05-23T10:00:00Z"
  }
}
```

### IntegrationWebhookEndpointResponse

```json
{
  "data": {
    "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "tenant_id": "22222222-2222-2222-2222-222222222222",
    "org_id": "33333333-3333-3333-3333-333333333333",
    "connection_id": "55555555-5555-5555-5555-555555555555",
    "provider_key": "github",
    "external_webhook_id": "provider-webhook-id",
    "endpoint_url": "https://api.vericov.dev/webhooks/github",
    "event_types": ["pull_request", "check_suite", "check_run", "push", "issue_comment"],
    "status": "active",
    "signing_secret_ref": "vault://integrations/55555555-5555-5555-5555-555555555555/webhook_secret/1",
    "config": {
      "content_type": "json"
    },
    "last_delivery": {},
    "last_delivered_at": null,
    "created_at": "2026-05-23T10:00:00Z",
    "updated_at": "2026-05-23T10:00:00Z"
  }
}
```

### ResolvedIntegrationResponse

```json
{
  "data": {
    "connection": {
      "id": "55555555-5555-5555-5555-555555555555",
      "provider_key": "github",
      "integration_type": "git",
      "status": "active",
      "config": {
        "installation_id": "123456"
      }
    },
    "binding": {
      "id": "66666666-6666-6666-6666-666666666666",
      "scope_type": "repository",
      "scope_id": "44444444-4444-4444-4444-444444444444",
      "capabilities": ["git.checks"]
    },
    "credential_kind": "github_app_private_key"
  }
}
```

### CredentialLeaseResponse

Only internal callers with service identity receive lease material. Normal logs and errors must redact the `secret` value.

```json
{
  "data": {
    "secret_ref": "vault://integrations/55555555-5555-5555-5555-555555555555/api_token/1",
    "secret": "short-lived-provider-token",
    "expires_at": "2026-05-23T10:05:00Z"
  }
}
```

### IntegrationSyncStateResponse

```json
{
  "data": {
    "id": "77777777-7777-7777-7777-777777777777",
    "tenant_id": "22222222-2222-2222-2222-222222222222",
    "org_id": "33333333-3333-3333-3333-333333333333",
    "connection_id": "55555555-5555-5555-5555-555555555555",
    "sync_type": "repository_full",
    "scope_type": "repository",
    "scope_id": "44444444-4444-4444-4444-444444444444",
    "status": "running",
    "cursor": {
      "after": "opaque-provider-cursor"
    },
    "checkpoint": {
      "repository_count": 12
    },
    "last_error": {},
    "last_started_at": "2026-05-23T10:00:00Z",
    "last_completed_at": null,
    "next_run_at": "2026-05-23T10:10:00Z",
    "lease_expires_at": "2026-05-23T10:05:00Z",
    "created_at": "2026-05-23T10:00:00Z",
    "updated_at": "2026-05-23T10:00:00Z"
  }
}
```

### IntegrationEventResponse

```json
{
  "data": {
    "id": "88888888-8888-8888-8888-888888888888",
    "tenant_id": "22222222-2222-2222-2222-222222222222",
    "org_id": "33333333-3333-3333-3333-333333333333",
    "connection_id": "55555555-5555-5555-5555-555555555555",
    "provider_key": "github",
    "event_type": "git.check.updated",
    "external_event_id": "provider-delivery-id",
    "scope_type": "repository",
    "scope_id": "44444444-4444-4444-4444-444444444444",
    "status": "processed",
    "payload": {
      "provider_check_id": "987654"
    },
    "error": {},
    "received_at": "2026-05-23T10:00:00Z",
    "processed_at": "2026-05-23T10:00:01Z",
    "created_at": "2026-05-23T10:00:00Z",
    "updated_at": "2026-05-23T10:00:01Z"
  }
}
```

## Database Models

### `integration_providers`

| Column | Type | Notes |
| --- | --- | --- |
| `provider_key` | text | Primary key; examples: `github`, `gitlab`, `bitbucket`, `slack`, `jira`, `linear`, `openai` |
| `integration_type` | text | `git`, `chat`, `issue_tracker`, `ai` |
| `display_name` | text | Provider display name |
| `auth_strategy` | text | `github_app`, `oauth_app`, `api_key` |
| `capabilities` | text[] | Capability keys used by resolution |
| `default_config` | jsonb | Provider defaults |
| `status` | text | `active`, `disabled` |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

### `integration_connections`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `org_id` | uuid | Organization boundary |
| `provider_key` | text | FK to provider catalog |
| `integration_type` | text | Denormalized provider type |
| `display_name` | text | User-facing name |
| `external_account_id` | text | Provider account, installation, or workspace ID |
| `external_account_name` | text | Nullable provider account name |
| `status` | text | `draft`, `active`, `needs_reauth`, `disabled`, `revoked` |
| `config_json` | jsonb | Provider config without raw secrets |
| `created_by` | uuid | Supabase user ID |
| `last_verified_at` | timestamptz | Last connectivity check |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

### `integration_credentials`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `connection_id` | uuid | FK to integration connection |
| `credential_kind` | text | `oauth_access_token`, `oauth_refresh_token`, `github_app_private_key`, `webhook_secret`, `api_token` |
| `secret_ref` | text | Vault reference; never plaintext secret material |
| `key_version` | integer | Rotation version |
| `status` | text | `active`, `rotating`, `revoked`, `expired` |
| `expires_at` | timestamptz | Nullable credential expiry |
| `last_rotated_at` | timestamptz | Last rotation time |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

### `integration_bindings`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `connection_id` | uuid | FK to integration connection |
| `scope_type` | text | `organization`, `repository`, `component` |
| `scope_id` | uuid | Canonical Control Plane scope identifier; organization scope must equal `org_id`, repository scope must belong to the same tenant/org, and component scope currently returns `not_found` |
| `capabilities` | text[] | Enabled capabilities for the scope |
| `config_json` | jsonb | Binding config |
| `status` | text | `active`, `disabled` |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

### `integration_webhook_endpoints`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `org_id` | uuid | Organization boundary |
| `connection_id` | uuid | FK to integration connection |
| `provider_key` | text | Provider key |
| `external_webhook_id` | text | Nullable provider webhook ID |
| `endpoint_url` | text | Public webhook URL |
| `event_types` | text[] | Provider event allowlist |
| `status` | text | `active`, `disabled`, `error`, `deleted` |
| `signing_secret_ref` | text | Vault reference for signature secret |
| `config_json` | jsonb | Webhook config |
| `last_delivery_json` | jsonb | Redacted last-delivery metadata |
| `last_delivered_at` | timestamptz | Last delivery time |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

### `integration_sync_states`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `org_id` | uuid | Organization boundary |
| `connection_id` | uuid | FK to integration connection |
| `sync_type` | text | Sync workflow name |
| `scope_type` | text | `organization`, `repository`, `component` |
| `scope_id` | uuid | Canonical Control Plane sync scope; organization and repository scopes are validated before persistence |
| `status` | text | `idle`, `running`, `succeeded`, `failed`, `paused` |
| `cursor_json` | jsonb | Provider cursor |
| `checkpoint_json` | jsonb | Progress checkpoint |
| `last_error_json` | jsonb | Redacted last error |
| `last_started_at` | timestamptz | Last start time |
| `last_completed_at` | timestamptz | Last completion time |
| `next_run_at` | timestamptz | Next scheduled sync |
| `lease_expires_at` | timestamptz | Active sync lease expiry |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

### `integration_events`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `org_id` | uuid | Nullable organization reference |
| `connection_id` | uuid | Nullable connection reference |
| `webhook_endpoint_id` | uuid | Nullable webhook endpoint reference |
| `provider_key` | text | Provider key |
| `event_type` | text | Event name |
| `external_event_id` | text | Provider idempotency key when available |
| `scope_type` | text | Nullable `organization`, `repository`, `component` |
| `scope_id` | uuid | Nullable canonical Control Plane scope identifier validated before persistence when present |
| `status` | text | `pending`, `processed`, `failed`, `ignored` |
| `payload` | jsonb | Redacted event payload |
| `error_json` | jsonb | Redacted error details |
| `received_at` | timestamptz | Provider or service receipt time |
| `processed_at` | timestamptz | Processing completion time |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

## Events Consumed

| Event | Action |
| --- | --- |
| `provider.installation.completed` | Create or update connection metadata and credential references |
| `provider.credential.rotated` | Update credential metadata and key version |
| `git.repository.discovered` | Upsert repository binding when an installation sync maps to a Vericov repository |
| `integration.connection.verify_requested` | Verify provider connectivity and update connection health |

## Events Published

| Event | Trigger |
| --- | --- |
| `integration.connection.created` | Connection created |
| `integration.connection.updated` | Connection status, display name, config, or verification state changes |
| `integration.connection.disabled` | Connection disabled |
| `integration.binding.updated` | Scope binding upserted or disabled |
| `integration.credential.rotated` | Credential metadata points at a new `secret_ref` |
| `integration.sync.updated` | Sync state changes |
| `integration.event.recorded` | Internal caller records an integration event |

## Security Posture

- Every tenant-owned table includes `tenant_id`; public APIs also require `org_id` where organization isolation is needed.
- Public endpoints require requester user identity plus exact tenant and organization authorization before reading or mutating integration configuration. Caller-supplied `tenant_id` is not trusted by itself, and tenant/org mismatches are rejected.
- Internal endpoints should use propagated service JWTs; legacy static-token REST calls are transitional.
- Raw provider secrets are never stored in Postgres. The service stores `secret_ref` metadata and grants short-lived credential leases only to authorized internal callers.
- Public responses never include raw secrets. Credential lease responses are internal-only, expire quickly, and must be redacted from logs and errors.
- Service-owned JSON maps reject secret-bearing keys recursively before persistence so secrets cannot be echoed through public/internal metadata responses.
- Binding, sync-state, and event scope IDs are validated against Control Plane ownership before storage.
- SQL access uses parameterized statements; RLS is enabled on integration tables and public roles are revoked.
- Provider configs and event payloads must remain redacted and schema-limited so provider PII does not leak into JSON fields.

## Open Questions

- Should OAuth callbacks be owned entirely by Integrations Config Service, or should provider-specific callback parsing stay with provider action services?
- Should organization-level bindings ever fall back for repository resolution, or should repository bindings stay explicit for v1?
- Which vault backend should implement production `CredentialVault` first?
- Should `integration_webhook_endpoints` be managed through public APIs in v1 or only by installation workflows?
