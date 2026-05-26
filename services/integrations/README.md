# Integrations Config Service

The Integrations Config service owns provider-neutral integration setup:
provider definitions, organization connections, repository bindings,
credential metadata, webhook endpoint metadata, sync state, and integration
audit events.

The fuller contract lives in
`docs/backend/services/07-integrations-config-service.md`.

## Why This Service Exists

Git provider execution, coverage analysis, and organization management all need
to know whether a repository is connected to GitHub, which capabilities are
enabled, and which credential kind should be leased. Keeping that state in one
service avoids duplicating provider setup logic and keeps long-lived secrets out
of action executors such as Git Integration.

## Current Architecture

```text
 User API calls through Kong                  Internal service calls
          |                                             |
          v                                             v
+---------+-----------+                      +----------+-----------+
| IntegrationResource |                      | InternalIntegration  |
| public CRUD         |                      | Resource             |
+---------+-----------+                      +----------+-----------+
          |                                             |
          +----------------------+----------------------+
                                 |
                                 v
                    +------------+------------+
                    | IntegrationApplication |
                    | Service                |
                    +------------+------------+
                                 |
          +----------------------+------------------------+
          |                       |                        |
          v                       v                        v
+---------+----------+  +---------+----------+   +---------+----------+
| IntegrationRepo    |  | CredentialVault    |   | ProviderRegistry   |
| JDBC or in-memory  |  | env/dev or real    |   | static catalog     |
+--------------------+  +--------------------+   +--------------------+
```

## Where It Is Called From

```text
Web app setup flow
  -> GET /api/v1/integration-providers
  -> POST /api/v1/orgs/{org_id}/integrations
  -> POST /api/v1/integrations/{connection_id}/credentials
  -> PUT /api/v1/integrations/{connection_id}/bindings/repository/{repository_id}

Git provider action
  -> git-integration asks /internal/v1/integrations/resolve
  -> integrations verifies active connection and binding
  -> git-integration asks /credential-leases for required credential kind
  -> integrations returns a short-lived lease, not raw stored metadata

Provider sync or webhook audit
  -> git-integration records /internal/v1/integrations/events
  -> integrations stores sync/event state for audit and troubleshooting
```

## Data Model

```text
integration_provider_definitions
  provider_key, type, display_name, auth_strategy, capabilities
  default_config_json, credential_kind_by_capability_json

integration_connections
  id, tenant_id, org_id, provider_key, display_name
  external_account_id, external_account_name, status, config_json
  created_by, last_verified_at, created_at, updated_at

integration_credentials
  id, tenant_id, org_id, connection_id, credential_kind
  secret_ref, secret_version, status, expires_at, created_at

integration_bindings
  id, tenant_id, org_id, connection_id, scope_type, scope_id
  capabilities, config_json, status, created_at, updated_at

integration_webhook_endpoints
  id, connection_id, external_webhook_id, endpoint_url
  event_types, signing_secret_ref, config_json, status

integration_sync_states
  id, connection_id, sync_type, scope_type, scope_id
  status, cursor_json, checkpoint_json, last_error_json

integration_events
  id, connection_id, provider_key, event_type, external_event_id
  scope_type, scope_id, status, payload_json, error_json
```

JSON metadata fields reject secret-bearing keys recursively. Raw credential
material is accepted only on credential creation and is stored through the
`CredentialVault` port.

## APIs

Public:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/integration-providers` | List provider catalog |
| `GET` | `/api/v1/orgs/{org_id}/integrations` | List org connections |
| `POST` | `/api/v1/orgs/{org_id}/integrations` | Create connection |
| `GET` | `/api/v1/integrations/{connection_id}` | Read connection |
| `PATCH` | `/api/v1/integrations/{connection_id}` | Update connection |
| `POST` | `/api/v1/integrations/{connection_id}/disable` | Disable connection |
| `POST` | `/api/v1/integrations/{connection_id}/credentials` | Store credential metadata and vault secret |
| `GET` | `/api/v1/integrations/{connection_id}/credentials` | List credential metadata |
| `POST` | `/api/v1/integrations/{connection_id}/webhook-endpoints` | Register webhook endpoint |
| `GET` | `/api/v1/integrations/{connection_id}/webhook-endpoints` | List webhook endpoints |
| `GET` | `/api/v1/integrations/{connection_id}/bindings` | List scope bindings |
| `PUT` | `/api/v1/integrations/{connection_id}/bindings/{scope_type}/{scope_id}` | Upsert binding |
| `DELETE` | `/api/v1/integrations/{connection_id}/bindings/{scope_type}/{scope_id}` | Disable binding |

Internal:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/internal/v1/integrations/connections/{connection_id}` | Resolve connection metadata |
| `GET` | `/internal/v1/integrations/resolve` | Resolve binding and credential kind |
| `POST` | `/internal/v1/integrations/connections/{connection_id}/credential-leases` | Lease credential material |
| `POST` | `/internal/v1/integrations/connections/{connection_id}/sync-state` | Upsert sync state |
| `POST` | `/internal/v1/integrations/events` | Record integration event |

## Source Map

```text
api/
  Public and internal JAX-RS resources plus HTTP request/response records

application/
  Connection, binding, credential, webhook, sync-state, event, and provider
  catalog behavior

application/port/
  Authorization, scope validation, vault, repository, provider registry,
  and internal service auth ports

adapter/jdbc/
  JDBC repository, scope validator, and JSON codec

config/
  Development CDI wiring, static provider registry, env-backed auth/vault
```

## Tests

```text
src/test/java/dev/vericov/integrations/application
  Main service and provider registry behavior

src/test/java/dev/vericov/integrations/api
  Public and internal resource behavior

src/test/java/dev/vericov/integrations/adapter/jdbc
  JSON codec and optional database-backed repository tests

src/test/java/dev/vericov/integrations/config
  Local component wiring, in-memory credential vault, and fail-closed auth
```

The JDBC repository tests are skipped unless database configuration is present.
Run this service only:

```bash
mvn -pl services/integrations test
```
