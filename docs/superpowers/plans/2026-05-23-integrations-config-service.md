# Integrations Config Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a provider-neutral integrations configuration service that stores integration lifecycle, credentials metadata, bindings, and resolution rules, with GitHub/GitLab/Bitbucket as the first integration family.

**Architecture:** Add a new Helidon 4 service at `services/integrations` using the existing clean application/port/api layout. The Integrations Config Service owns integration connections, scope bindings, capability grants, credential references, webhook endpoint metadata, sync state, and audit events; Git provider action code remains in the Git Integration Service and resolves its configuration through internal APIs. Secrets are never stored in plaintext in Postgres: the service stores `secret_ref` metadata and uses a `CredentialVault` port for create, rotate, revoke, and lease workflows.

**Tech Stack:** Java 25, Helidon 4 MP, JAX-RS, MicroProfile OpenAPI, JUnit 5, Supabase Postgres in the `vericov` schema, optional JDBC repository following the coverage-analysis service pattern.

---

## Service Boundary

The existing `docs/backend/services/05-git-integration-service.md` should be narrowed to provider actions: webhook normalization, PR comments, checks, annotations, branch creation, PR creation, and provider API calls.

The new service should own durable configuration for all current and future integrations:

- Git providers: GitHub, GitLab, Bitbucket.
- CI providers: GitHub Actions, GitLab CI, CircleCI, Buildkite.
- Issue trackers: Jira, Linear, GitHub Issues.
- Chat/notification providers: Slack, Microsoft Teams, email/webhook sinks.
- Identity/ownership providers: Google Workspace, Okta, SCIM/SAML directories.
- AI/runtime providers: BYO LLM, runner backends, model gateways.

## Data Ownership

The Integrations Config Service owns:

- Provider catalog and capabilities.
- Organization-level integration connections.
- Scope bindings from a connection to organization, repository, component, or path selectors.
- Credential metadata and vault references.
- Webhook endpoint metadata and signature-secret references.
- Provider sync cursors and health state.
- Integration audit events.

It does not own:

- Pull request records, provider check runs, provider comments, or slash command execution. Those stay in the Git Integration Service.
- Core repository registration. The Control Plane owns canonical repositories, while integration bindings can attach provider connections to repositories.
- Coverage, gate, report, or agent decisions.

## API Shape

Public base path: `/api/v1/integrations`

Internal base path: `/internal/v1/integrations`

Primary public endpoints:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/integration-providers` | List provider definitions, optionally filtered by `type` |
| `GET` | `/api/v1/orgs/{org_id}/integrations` | List integration connections for an org |
| `POST` | `/api/v1/orgs/{org_id}/integrations` | Create a connection or complete a provider installation |
| `GET` | `/api/v1/integrations/{connection_id}` | Get connection details |
| `PATCH` | `/api/v1/integrations/{connection_id}` | Update display name, status, config, or metadata |
| `POST` | `/api/v1/integrations/{connection_id}/verify` | Verify provider connectivity without exposing secrets |
| `POST` | `/api/v1/integrations/{connection_id}/disable` | Disable a connection and bindings |
| `GET` | `/api/v1/integrations/{connection_id}/bindings` | List scope bindings |
| `PUT` | `/api/v1/integrations/{connection_id}/bindings/{scope_type}/{scope_id}` | Upsert a binding |
| `DELETE` | `/api/v1/integrations/{connection_id}/bindings/{scope_type}/{scope_id}` | Disable a binding |

Primary internal endpoints:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/internal/v1/integrations/connections/{connection_id}` | Resolve full connection metadata for service callers |
| `GET` | `/internal/v1/integrations/resolve` | Resolve active connection and binding for provider/type/scope/capability |
| `POST` | `/internal/v1/integrations/connections/{connection_id}/credential-leases` | Issue a short-lived credential lease to an authorized service |
| `POST` | `/internal/v1/integrations/connections/{connection_id}/sync-state` | Update sync cursor, status, and last error |
| `POST` | `/internal/v1/integrations/events` | Record provider action/audit events |

## Database Model

Add these tables to `infra/supabase/volumes/db/vericov.sql`:

- `vericov.integration_providers`
- `vericov.integration_connections`
- `vericov.integration_credentials`
- `vericov.integration_bindings`
- `vericov.integration_webhook_endpoints`
- `vericov.integration_sync_states`
- `vericov.integration_events`

Use UUID primary keys, `tenant_id` on tenant-owned rows, `created_at` and `updated_at` on mutable tables, RLS enabled, and indexes for tenant, provider, status, and scope resolution.

## File Structure

- Create `services/integrations/pom.xml`: Helidon service module matching `services/upload/pom.xml`.
- Modify `pom.xml`: add `<module>services/integrations</module>`.
- Create `services/integrations/src/main/resources/application.yaml`: local port `8082`.
- Create `services/integrations/src/main/resources/META-INF/beans.xml`: CDI marker.
- Create `services/integrations/src/main/java/dev/vericov/integrations/Main.java`: service entrypoint.
- Create `services/integrations/src/main/java/dev/vericov/integrations/api/*`: public/internal resources, request records, response records, API envelope, error envelope.
- Create `services/integrations/src/main/java/dev/vericov/integrations/application/*`: commands, details records, service, validation, exceptions.
- Create `services/integrations/src/main/java/dev/vericov/integrations/application/port/*`: repository, provider registry, credential vault, event publisher.
- Create `services/integrations/src/main/java/dev/vericov/integrations/adapter/jdbc/*`: JDBC persistence once app tests pass.
- Create `services/integrations/src/main/java/dev/vericov/integrations/config/DevelopmentIntegrationComponents.java`: local producers and in-memory adapters.
- Create `services/integrations/src/test/java/dev/vericov/integrations/application/IntegrationApplicationServiceTest.java`.
- Create `services/integrations/src/test/java/dev/vericov/integrations/api/IntegrationResourceTest.java`.
- Create `services/integrations/src/test/java/dev/vericov/integrations/api/InternalIntegrationResourceTest.java`.
- Create `services/integrations/src/test/java/dev/vericov/integrations/adapter/jdbc/JdbcIntegrationRepositoryTest.java` if a local test database is available.
- Modify `docs/backend/README.md`: add Integrations Config Service and clarify Git Integration Service split.
- Create `docs/backend/services/05-integrations-config-service.md`: formal service contract.
- Modify `docs/backend/services/05-git-integration-service.md`: mark Git as provider action adapter and link to the base config service.

---

### Task 1: Add the Service Skeleton

**Files:**
- Create: `services/integrations/pom.xml`
- Create: `services/integrations/src/main/java/dev/vericov/integrations/Main.java`
- Create: `services/integrations/src/main/resources/application.yaml`
- Create: `services/integrations/src/main/resources/META-INF/beans.xml`
- Modify: `pom.xml`
- Test: `mvn -pl services/integrations test`

- [ ] **Step 1: Create the module POM**

Use the upload service POM as the baseline and set:

```xml
<artifactId>integrations</artifactId>
<name>vericov-integrations</name>
<mainClass>dev.vericov.integrations.Main</mainClass>
```

Include `helidon-microprofile`, `helidon-microprofile-openapi`, `helidon-integrations-openapi-ui`, `microprofile-openapi-api`, `postgresql`, and `junit-jupiter`.

- [ ] **Step 2: Add the root Maven module**

Add this line to the root `pom.xml` modules block:

```xml
<module>services/integrations</module>
```

- [ ] **Step 3: Add the entrypoint**

Create `services/integrations/src/main/java/dev/vericov/integrations/Main.java`:

```java
package dev.vericov.integrations;

import io.helidon.microprofile.server.Server;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        Server.create().start();
    }
}
```

- [ ] **Step 4: Add local config**

Create `services/integrations/src/main/resources/application.yaml`:

```yaml
server:
  host: 127.0.0.1
  port: 8082
```

- [ ] **Step 5: Add CDI marker**

Create `services/integrations/src/main/resources/META-INF/beans.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="https://jakarta.ee/xml/ns/jakartaee"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/beans_4_0.xsd"
       bean-discovery-mode="annotated"
       version="4.0"/>
```

- [ ] **Step 6: Run the module tests**

Run:

```bash
mvn -pl services/integrations test
```

Expected: Maven resolves and reports zero tests run or build success.

- [ ] **Step 7: Commit**

```bash
git add pom.xml services/integrations
git commit -m "feat: add integrations service module"
```

### Task 2: Define Provider Catalog and Domain Records

**Files:**
- Create: `services/integrations/src/main/java/dev/vericov/integrations/application/ProviderDefinition.java`
- Create: `services/integrations/src/main/java/dev/vericov/integrations/application/IntegrationConnectionDetails.java`
- Create: `services/integrations/src/main/java/dev/vericov/integrations/application/IntegrationBindingDetails.java`
- Create: `services/integrations/src/main/java/dev/vericov/integrations/application/IntegrationCredentialDetails.java`
- Create: `services/integrations/src/main/java/dev/vericov/integrations/application/IntegrationException.java`
- Create: `services/integrations/src/main/java/dev/vericov/integrations/application/port/ProviderRegistry.java`
- Test: `services/integrations/src/test/java/dev/vericov/integrations/application/ProviderRegistryTest.java`

- [ ] **Step 1: Write the failing provider catalog test**

```java
package dev.vericov.integrations.application;

import dev.vericov.integrations.config.StaticProviderRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderRegistryTest {
    @Test
    void includesGitProvidersWithCommonCapabilities() {
        StaticProviderRegistry registry = StaticProviderRegistry.defaultRegistry();

        ProviderDefinition github = registry.requireProvider("github");

        assertEquals("git", github.type());
        assertEquals("GitHub", github.displayName());
        assertTrue(github.capabilities().contains("git.webhooks"));
        assertTrue(github.capabilities().contains("git.checks"));
        assertTrue(github.capabilities().contains("git.pull_requests"));
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
mvn -pl services/integrations -Dtest=ProviderRegistryTest test
```

Expected: FAIL because `StaticProviderRegistry` and domain records do not exist.

- [ ] **Step 3: Add domain records**

Use Java records with compact constructors that copy collection fields:

```java
package dev.vericov.integrations.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ProviderDefinition(
        String providerKey,
        String type,
        String displayName,
        String authStrategy,
        List<String> capabilities,
        Map<String, Object> defaultConfig) {

    public ProviderDefinition {
        Objects.requireNonNull(providerKey, "providerKey");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(authStrategy, "authStrategy");
        capabilities = List.copyOf(capabilities == null ? List.of() : capabilities);
        defaultConfig = Map.copyOf(defaultConfig == null ? Map.of() : defaultConfig);
    }
}
```

Apply the same style for `IntegrationConnectionDetails`, `IntegrationBindingDetails`, and `IntegrationCredentialDetails`; each record must include `id`, `tenantId`, `orgId` or `connectionId`, status, `createdAt`, and `updatedAt` where applicable.

- [ ] **Step 4: Add provider registry port and static registry**

Create `ProviderRegistry` with:

```java
List<ProviderDefinition> listProviders(String type);
Optional<ProviderDefinition> findProvider(String providerKey);
ProviderDefinition requireProvider(String providerKey);
```

Create `StaticProviderRegistry.defaultRegistry()` with Git providers first:

```java
new ProviderDefinition(
        "github",
        "git",
        "GitHub",
        "github_app",
        List.of("git.webhooks", "git.checks", "git.comments", "git.pull_requests", "git.repository_sync"),
        Map.of("webhook_events", List.of("pull_request", "check_suite", "issue_comment")))
```

Also add `gitlab` and `bitbucket` with equivalent Git capabilities. Add future provider stubs for `slack`, `jira`, `linear`, and `openai` with conservative capabilities.

- [ ] **Step 5: Run the provider catalog test**

Run:

```bash
mvn -pl services/integrations -Dtest=ProviderRegistryTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add services/integrations/src/main/java services/integrations/src/test/java
git commit -m "feat: add integration provider catalog"
```

### Task 3: Implement Connection Lifecycle Service

**Files:**
- Create: `services/integrations/src/main/java/dev/vericov/integrations/application/CreateIntegrationConnectionCommand.java`
- Create: `services/integrations/src/main/java/dev/vericov/integrations/application/UpdateIntegrationConnectionCommand.java`
- Create: `services/integrations/src/main/java/dev/vericov/integrations/application/IntegrationApplicationService.java`
- Create: `services/integrations/src/main/java/dev/vericov/integrations/application/port/IntegrationRepository.java`
- Create: `services/integrations/src/main/java/dev/vericov/integrations/application/InMemoryIntegrationRepository.java`
- Test: `services/integrations/src/test/java/dev/vericov/integrations/application/IntegrationApplicationServiceTest.java`

- [ ] **Step 1: Write failing tests for create, duplicate protection, and disable**

Cover:

- Creating a GitHub connection normalizes provider key and status.
- Creating a duplicate active provider connection for the same org and external account fails with `conflict`.
- Disabling a connection returns a new immutable details object with status `disabled`.

Use fixed UUIDs and `Clock.fixed(Instant.parse("2026-05-23T10:00:00Z"), ZoneOffset.UTC)`.

- [ ] **Step 2: Run the tests and verify they fail**

Run:

```bash
mvn -pl services/integrations -Dtest=IntegrationApplicationServiceTest test
```

Expected: FAIL because the service does not exist.

- [ ] **Step 3: Add commands**

`CreateIntegrationConnectionCommand` fields:

```java
UUID requesterUserId;
UUID tenantId;
UUID orgId;
String providerKey;
String displayName;
String externalAccountId;
String externalAccountName;
Map<String, Object> config;
```

`UpdateIntegrationConnectionCommand` fields:

```java
UUID requesterUserId;
UUID connectionId;
String displayName;
String status;
Map<String, Object> config;
```

- [ ] **Step 4: Add repository port**

Include:

```java
List<IntegrationConnectionDetails> listConnections(UUID orgId);
Optional<IntegrationConnectionDetails> findConnection(UUID connectionId);
Optional<IntegrationConnectionDetails> findActiveConnection(UUID orgId, String providerKey, String externalAccountId);
IntegrationConnectionDetails saveConnection(IntegrationConnectionDetails connection);
IntegrationConnectionDetails updateConnection(IntegrationConnectionDetails connection);
```

- [ ] **Step 5: Add application validation**

Validation rules:

- `requesterUserId`, `tenantId`, and `orgId` are required.
- Provider key must exist in `ProviderRegistry`.
- Display name must be 1 to 120 characters.
- Status values are `draft`, `active`, `needs_reauth`, `disabled`, `revoked`.
- Config keys may contain lowercase letters, numbers, `_`, `-`, and `.` only.
- Duplicate active connection by org, provider, and external account raises `IntegrationException("conflict", "Integration connection already exists")`.

- [ ] **Step 6: Run tests**

Run:

```bash
mvn -pl services/integrations -Dtest=IntegrationApplicationServiceTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add services/integrations/src/main/java services/integrations/src/test/java
git commit -m "feat: manage integration connections"
```

### Task 4: Add Scope Bindings and Resolution Semantics

**Files:**
- Create: `services/integrations/src/main/java/dev/vericov/integrations/application/UpsertIntegrationBindingCommand.java`
- Modify: `services/integrations/src/main/java/dev/vericov/integrations/application/IntegrationApplicationService.java`
- Modify: `services/integrations/src/main/java/dev/vericov/integrations/application/port/IntegrationRepository.java`
- Modify: `services/integrations/src/main/java/dev/vericov/integrations/application/InMemoryIntegrationRepository.java`
- Test: `services/integrations/src/test/java/dev/vericov/integrations/application/IntegrationApplicationServiceTest.java`

- [ ] **Step 1: Write failing binding tests**

Cover:

- A repository binding can grant `git.checks` and `git.comments`.
- Binding a capability the provider does not support fails with `validation_error`.
- Resolving by `provider_key=github`, `scope_type=repository`, `scope_id`, and `capability=git.checks` returns the active connection and binding.
- Disabled bindings are ignored by resolution.

- [ ] **Step 2: Run the tests and verify they fail**

Run:

```bash
mvn -pl services/integrations -Dtest=IntegrationApplicationServiceTest test
```

Expected: FAIL because bindings are not implemented.

- [ ] **Step 3: Add binding command and validation**

`UpsertIntegrationBindingCommand` fields:

```java
UUID requesterUserId;
UUID connectionId;
String scopeType;
UUID scopeId;
List<String> capabilities;
Map<String, Object> config;
String status;
```

Allowed scope types:

```java
Set.of("organization", "repository", "component")
```

Allowed binding statuses:

```java
Set.of("active", "disabled")
```

- [ ] **Step 4: Add repository methods**

```java
List<IntegrationBindingDetails> listBindings(UUID connectionId);
Optional<IntegrationBindingDetails> findBinding(UUID connectionId, String scopeType, UUID scopeId);
IntegrationBindingDetails saveBinding(IntegrationBindingDetails binding);
IntegrationBindingDetails updateBinding(IntegrationBindingDetails binding);
Optional<ResolvedIntegration> resolve(String providerKey, String scopeType, UUID scopeId, String capability);
```

- [ ] **Step 5: Implement resolution order**

For v1, exact scope wins. Repository resolution must not fall back to org silently because a repository could require stricter credentials. Organization fallback is excluded from this implementation and should require an explicit `allow_org_fallback` config flag in a separate plan.

- [ ] **Step 6: Run tests**

Run:

```bash
mvn -pl services/integrations -Dtest=IntegrationApplicationServiceTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add services/integrations/src/main/java services/integrations/src/test/java
git commit -m "feat: resolve integration bindings"
```

### Task 5: Add Credential Metadata and Vault Port

**Files:**
- Create: `services/integrations/src/main/java/dev/vericov/integrations/application/CreateCredentialCommand.java`
- Create: `services/integrations/src/main/java/dev/vericov/integrations/application/CredentialLease.java`
- Create: `services/integrations/src/main/java/dev/vericov/integrations/application/port/CredentialVault.java`
- Modify: `services/integrations/src/main/java/dev/vericov/integrations/application/IntegrationApplicationService.java`
- Modify: `services/integrations/src/main/java/dev/vericov/integrations/application/InMemoryIntegrationRepository.java`
- Test: `services/integrations/src/test/java/dev/vericov/integrations/application/IntegrationApplicationServiceTest.java`

- [ ] **Step 1: Write failing credential tests**

Cover:

- Creating a credential stores only `secretRef`, `credentialKind`, `expiresAt`, and status.
- Application service response never includes the raw secret.
- Requesting a credential lease for an active connection returns a short-lived lease object.
- Disabled or revoked connections cannot lease credentials.

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
mvn -pl services/integrations -Dtest=IntegrationApplicationServiceTest test
```

Expected: FAIL because credential handling does not exist.

- [ ] **Step 3: Add vault port**

```java
public interface CredentialVault {
    String store(UUID tenantId, UUID connectionId, String credentialKind, char[] secret);
    CredentialLease lease(UUID tenantId, UUID connectionId, String secretRef, String requestedBy);
    void revoke(UUID tenantId, String secretRef);
}
```

`CredentialLease` fields:

```java
String secretRef;
char[] secret;
Instant expiresAt;
```

Zero out local `char[]` values after storing where the implementation has direct access to secret material.

- [ ] **Step 4: Add in-memory vault for local development**

The in-memory vault may store secrets in memory for tests only. It must not log secret values and must return defensive copies of `char[]`.

- [ ] **Step 5: Add credential metadata rules**

Allowed credential kinds:

```java
Set.of("oauth_access_token", "oauth_refresh_token", "github_app_private_key", "webhook_secret", "api_token")
```

Allowed credential statuses:

```java
Set.of("active", "rotating", "revoked", "expired")
```

- [ ] **Step 6: Run tests**

Run:

```bash
mvn -pl services/integrations -Dtest=IntegrationApplicationServiceTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add services/integrations/src/main/java services/integrations/src/test/java
git commit -m "feat: add integration credential references"
```

### Task 6: Add Public API Resource

**Files:**
- Create: `services/integrations/src/main/java/dev/vericov/integrations/api/ApiResponse.java`
- Create: `services/integrations/src/main/java/dev/vericov/integrations/api/ApiError.java`
- Create: `services/integrations/src/main/java/dev/vericov/integrations/api/CreateIntegrationConnectionHttpRequest.java`
- Create: `services/integrations/src/main/java/dev/vericov/integrations/api/UpdateIntegrationConnectionHttpRequest.java`
- Create: `services/integrations/src/main/java/dev/vericov/integrations/api/UpsertIntegrationBindingHttpRequest.java`
- Create: `services/integrations/src/main/java/dev/vericov/integrations/api/IntegrationConnectionHttpResponse.java`
- Create: `services/integrations/src/main/java/dev/vericov/integrations/api/IntegrationBindingHttpResponse.java`
- Create: `services/integrations/src/main/java/dev/vericov/integrations/api/ProviderDefinitionHttpResponse.java`
- Create: `services/integrations/src/main/java/dev/vericov/integrations/api/IntegrationResource.java`
- Test: `services/integrations/src/test/java/dev/vericov/integrations/api/IntegrationResourceTest.java`

- [ ] **Step 1: Write failing resource tests**

Cover:

- `GET /api/v1/integration-providers?type=git` returns GitHub, GitLab, and Bitbucket in an API envelope.
- Creating a connection returns HTTP 201 and `data.status = "active"`.
- Duplicate connection returns HTTP 409 and `{ "error": { "code": "conflict" } }`.
- Upserting a repository binding returns HTTP 200 and the granted capabilities.

- [ ] **Step 2: Run the tests and verify they fail**

Run:

```bash
mvn -pl services/integrations -Dtest=IntegrationResourceTest test
```

Expected: FAIL because API records and resource do not exist.

- [ ] **Step 3: Add request records**

Use `@JsonbProperty` for snake_case fields, matching upload service style:

```java
public record CreateIntegrationConnectionHttpRequest(
        @JsonbProperty("tenant_id") UUID tenantId,
        @JsonbProperty("provider_key") String providerKey,
        @JsonbProperty("display_name") String displayName,
        @JsonbProperty("external_account_id") String externalAccountId,
        @JsonbProperty("external_account_name") String externalAccountName,
        Map<String, Object> config) {
}
```

- [ ] **Step 4: Add resource methods**

`IntegrationResource` paths:

- `GET /api/v1/integration-providers`
- `GET /api/v1/orgs/{org_id}/integrations`
- `POST /api/v1/orgs/{org_id}/integrations`
- `GET /api/v1/integrations/{connection_id}`
- `PATCH /api/v1/integrations/{connection_id}`
- `POST /api/v1/integrations/{connection_id}/disable`
- `GET /api/v1/integrations/{connection_id}/bindings`
- `PUT /api/v1/integrations/{connection_id}/bindings/{scope_type}/{scope_id}`
- `DELETE /api/v1/integrations/{connection_id}/bindings/{scope_type}/{scope_id}`

Use `@HeaderParam("X-Vericov-User-Id") UUID requesterUserId` for local tests until shared auth middleware exists.

- [ ] **Step 5: Map errors**

Map `IntegrationException` codes:

- `unauthorized` to 401.
- `forbidden` to 403.
- `not_found` to 404.
- `conflict` to 409.
- all other codes to 400.

- [ ] **Step 6: Run tests**

Run:

```bash
mvn -pl services/integrations -Dtest=IntegrationResourceTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add services/integrations/src/main/java services/integrations/src/test/java
git commit -m "feat: expose integrations public api"
```

### Task 7: Add Internal Resolution API

**Files:**
- Create: `services/integrations/src/main/java/dev/vericov/integrations/api/InternalIntegrationResource.java`
- Create: `services/integrations/src/main/java/dev/vericov/integrations/api/ResolvedIntegrationHttpResponse.java`
- Create: `services/integrations/src/main/java/dev/vericov/integrations/api/CreateCredentialLeaseHttpRequest.java`
- Create: `services/integrations/src/main/java/dev/vericov/integrations/api/CredentialLeaseHttpResponse.java`
- Test: `services/integrations/src/test/java/dev/vericov/integrations/api/InternalIntegrationResourceTest.java`

- [ ] **Step 1: Write failing internal resource tests**

Cover:

- Resolve active GitHub repository binding by provider, scope, and capability.
- Resolve returns 404 when required capability is missing.
- Credential lease requires `X-Vericov-Service-Name`.
- Credential lease response contains lease expiration and does not serialize raw secret in normal logs or errors.

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
mvn -pl services/integrations -Dtest=InternalIntegrationResourceTest test
```

Expected: FAIL because internal resource does not exist.

- [ ] **Step 3: Add internal endpoints**

Implement:

- `GET /internal/v1/integrations/connections/{connection_id}`
- `GET /internal/v1/integrations/resolve?provider_key=github&scope_type=repository&scope_id=<uuid>&capability=git.checks`
- `POST /internal/v1/integrations/connections/{connection_id}/credential-leases`
- `POST /internal/v1/integrations/connections/{connection_id}/sync-state`
- `POST /internal/v1/integrations/events`

- [ ] **Step 4: Add service caller checks**

For now, require:

```java
@HeaderParam("X-Vericov-Service-Name") String serviceName
```

Reject blank values with `IntegrationException("unauthorized", "Service identity is required")`. Replace this with service JWT/mTLS validation when shared auth is available.

- [ ] **Step 5: Run tests**

Run:

```bash
mvn -pl services/integrations -Dtest=InternalIntegrationResourceTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add services/integrations/src/main/java services/integrations/src/test/java
git commit -m "feat: expose internal integration resolution"
```

### Task 8: Add Supabase Postgres Schema

**Files:**
- Modify: `infra/supabase/volumes/db/vericov.sql`
- Test: `infra/supabase/README.md` local startup instructions plus SQL validation

- [ ] **Step 1: Add integration tables**

Append DDL for provider catalog, connections, credentials, bindings, webhooks, sync state, and events:

```sql
CREATE TABLE IF NOT EXISTS vericov.integration_providers (
    provider_key text PRIMARY KEY,
    integration_type text NOT NULL,
    display_name text NOT NULL,
    auth_strategy text NOT NULL,
    capabilities text[] NOT NULL DEFAULT ARRAY[]::text[],
    default_config jsonb NOT NULL DEFAULT '{}'::jsonb,
    status text NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'disabled')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS vericov.integration_connections (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid NOT NULL,
    provider_key text NOT NULL REFERENCES vericov.integration_providers (provider_key),
    integration_type text NOT NULL,
    display_name text NOT NULL,
    external_account_id text NOT NULL,
    external_account_name text,
    status text NOT NULL DEFAULT 'active'
        CHECK (status IN ('draft', 'active', 'needs_reauth', 'disabled', 'revoked')),
    config_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_by uuid,
    last_verified_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (org_id, provider_key, external_account_id)
);

CREATE TABLE IF NOT EXISTS vericov.integration_credentials (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    connection_id uuid NOT NULL REFERENCES vericov.integration_connections (id) ON DELETE CASCADE,
    credential_kind text NOT NULL
        CHECK (credential_kind IN ('oauth_access_token', 'oauth_refresh_token', 'github_app_private_key', 'webhook_secret', 'api_token')),
    secret_ref text NOT NULL,
    key_version integer NOT NULL DEFAULT 1 CHECK (key_version > 0),
    status text NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'rotating', 'revoked', 'expired')),
    expires_at timestamptz,
    last_rotated_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (connection_id, credential_kind, secret_ref)
);

CREATE TABLE IF NOT EXISTS vericov.integration_bindings (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    connection_id uuid NOT NULL REFERENCES vericov.integration_connections (id) ON DELETE CASCADE,
    scope_type text NOT NULL CHECK (scope_type IN ('organization', 'repository', 'component')),
    scope_id uuid NOT NULL,
    capabilities text[] NOT NULL DEFAULT ARRAY[]::text[],
    config_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    status text NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'disabled')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (connection_id, scope_type, scope_id)
);

CREATE TABLE IF NOT EXISTS vericov.integration_webhook_endpoints (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    connection_id uuid NOT NULL REFERENCES vericov.integration_connections (id) ON DELETE CASCADE,
    provider_key text NOT NULL REFERENCES vericov.integration_providers (provider_key),
    event_types text[] NOT NULL DEFAULT ARRAY[]::text[],
    secret_ref text NOT NULL,
    status text NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'disabled')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS vericov.integration_sync_states (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    connection_id uuid NOT NULL UNIQUE REFERENCES vericov.integration_connections (id) ON DELETE CASCADE,
    cursor_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    status text NOT NULL DEFAULT 'idle'
        CHECK (status IN ('idle', 'running', 'succeeded', 'failed')),
    last_sync_at timestamptz,
    last_error text,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS vericov.integration_events (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    connection_id uuid REFERENCES vericov.integration_connections (id) ON DELETE SET NULL,
    event_type text NOT NULL,
    actor_type text NOT NULL DEFAULT 'service'
        CHECK (actor_type IN ('user', 'service', 'provider')),
    actor_id text,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);
```

- [ ] **Step 2: Add uniqueness and indexes**

Append indexes:

```sql
CREATE INDEX IF NOT EXISTS integration_connections_tenant_provider_status_idx
    ON vericov.integration_connections (tenant_id, provider_key, status);

CREATE INDEX IF NOT EXISTS integration_connections_org_idx
    ON vericov.integration_connections (org_id);

CREATE INDEX IF NOT EXISTS integration_bindings_scope_status_idx
    ON vericov.integration_bindings (scope_type, scope_id, status);

CREATE INDEX IF NOT EXISTS integration_bindings_connection_idx
    ON vericov.integration_bindings (connection_id);

CREATE INDEX IF NOT EXISTS integration_credentials_connection_status_idx
    ON vericov.integration_credentials (connection_id, status);

CREATE INDEX IF NOT EXISTS integration_webhook_endpoints_connection_status_idx
    ON vericov.integration_webhook_endpoints (connection_id, status);

CREATE INDEX IF NOT EXISTS integration_events_connection_created_idx
    ON vericov.integration_events (tenant_id, connection_id, created_at);
```

- [ ] **Step 3: Enable RLS and revoke public access**

Append:

```sql
ALTER TABLE vericov.integration_providers ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.integration_connections ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.integration_credentials ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.integration_bindings ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.integration_webhook_endpoints ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.integration_sync_states ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.integration_events ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON vericov.integration_providers FROM anon;
REVOKE ALL ON vericov.integration_providers FROM authenticated;
REVOKE ALL ON vericov.integration_connections FROM anon;
REVOKE ALL ON vericov.integration_connections FROM authenticated;
REVOKE ALL ON vericov.integration_credentials FROM anon;
REVOKE ALL ON vericov.integration_credentials FROM authenticated;
REVOKE ALL ON vericov.integration_bindings FROM anon;
REVOKE ALL ON vericov.integration_bindings FROM authenticated;
REVOKE ALL ON vericov.integration_webhook_endpoints FROM anon;
REVOKE ALL ON vericov.integration_webhook_endpoints FROM authenticated;
REVOKE ALL ON vericov.integration_sync_states FROM anon;
REVOKE ALL ON vericov.integration_sync_states FROM authenticated;
REVOKE ALL ON vericov.integration_events FROM anon;
REVOKE ALL ON vericov.integration_events FROM authenticated;
```

- [ ] **Step 4: Validate SQL**

Run the existing Supabase startup or direct SQL validation flow documented in `infra/supabase/README.md`.

Expected: all tables create successfully and no existing table is dropped.

- [ ] **Step 5: Commit**

```bash
git add infra/supabase/volumes/db/vericov.sql
git commit -m "feat: add integrations database schema"
```

### Task 9: Add JDBC Repository

**Files:**
- Create: `services/integrations/src/main/java/dev/vericov/integrations/adapter/jdbc/JdbcIntegrationRepository.java`
- Create: `services/integrations/src/main/java/dev/vericov/integrations/adapter/jdbc/IntegrationJsonCodec.java`
- Modify: `services/integrations/src/main/java/dev/vericov/integrations/config/DevelopmentIntegrationComponents.java`
- Test: `services/integrations/src/test/java/dev/vericov/integrations/adapter/jdbc/JdbcIntegrationRepositoryTest.java`

- [ ] **Step 1: Write repository tests**

Cover:

- Save and fetch connection.
- Save and resolve binding.
- Save credential metadata without raw secret material.
- Update sync state.
- Record integration event.

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
mvn -pl services/integrations -Dtest=JdbcIntegrationRepositoryTest test
```

Expected: FAIL until JDBC repository exists or SKIPPED if `VERICOV_TEST_DATABASE_URL` is absent.

- [ ] **Step 3: Implement JSON codec**

Use JSON-B for `Map<String, Object>` and `List<String>` fields. Do not build JSON with string concatenation.

- [ ] **Step 4: Implement JDBC repository**

Use parameterized `PreparedStatement` queries for all reads and writes. All inserts must set `tenant_id`; all queries must filter by tenant or connection id that resolves to tenant-owned records.

- [ ] **Step 5: Wire repository selection**

In `DevelopmentIntegrationComponents`, use JDBC when these env vars are present:

```text
VERICOV_DATABASE_URL
VERICOV_DATABASE_USER
VERICOV_DATABASE_PASSWORD
```

Otherwise use `InMemoryIntegrationRepository`.

- [ ] **Step 6: Run tests**

Run:

```bash
mvn -pl services/integrations test
```

Expected: PASS, with JDBC tests skipped only when no test database is configured.

- [ ] **Step 7: Commit**

```bash
git add services/integrations/src/main/java services/integrations/src/test/java
git commit -m "feat: persist integrations in postgres"
```

### Task 10: Update Backend Contracts

**Files:**
- Modify: `docs/backend/README.md`
- Create: `docs/backend/services/05-integrations-config-service.md`
- Modify: `docs/backend/services/05-git-integration-service.md`

- [ ] **Step 1: Update service list**

Add Integrations Config Service before Git Integration Service. Rename the Git doc to `06-git-integration-service.md` only if the team wants numbered docs to remain sequential; otherwise add the new contract as `07-integrations-config-service.md` to avoid noisy renames.

- [ ] **Step 2: Write Integrations Config Service contract**

Include:

- Purpose.
- Public endpoints.
- Internal endpoints.
- Request and response models.
- Database tables.
- Events consumed and published.
- Security posture.
- Open questions.

- [ ] **Step 3: Narrow Git service contract**

In the Git service doc, add:

```markdown
Integration lifecycle, credentials metadata, provider capability configuration, and repository bindings are owned by the Integrations Config Service. This service resolves active provider configuration through `/internal/v1/integrations/resolve` before executing provider actions.
```

- [ ] **Step 4: Commit**

```bash
git add docs/backend
git commit -m "docs: plan integrations service boundary"
```

### Task 11: Wire Git as the First Consumer

**Files:**
- Create: `services/git-integration` files if Git service implementation has not started.
- Or modify: existing Git service once present.
- Create: `services/git-integration/src/main/java/dev/vericov/git/application/port/IntegrationConfigClient.java`
- Test: Git service application tests.

- [ ] **Step 1: Define the client port**

```java
public interface IntegrationConfigClient {
    ResolvedGitIntegration resolveRepositoryIntegration(UUID repositoryId, String providerKey, String capability);
    CredentialLease leaseCredential(UUID connectionId, String serviceName);
}
```

- [ ] **Step 2: Use the client in provider actions**

Before creating checks, comments, annotations, branches, or PRs, resolve:

- provider key.
- repository scope.
- required capability.
- connection status.
- credential lease.

- [ ] **Step 3: Add tests**

Cover:

- Check run fails fast when no active Git binding exists.
- Comment update fails fast when `git.comments` is not granted.
- Provider client receives only credential lease data from the internal API, not stored raw secrets.

- [ ] **Step 4: Commit**

```bash
git add services/git-integration
git commit -m "feat: resolve git provider config from integrations"
```

### Task 12: Verification Gate

**Files:**
- All files touched by Tasks 1-11

- [ ] **Step 1: Run service tests**

```bash
mvn -pl services/integrations test
```

Expected: PASS.

- [ ] **Step 2: Run full Maven test suite**

```bash
mvn test
```

Expected: PASS.

- [ ] **Step 3: Review security-sensitive diff**

Check:

- No secrets are committed.
- Secret values never appear in API responses except credential lease responses for authorized internal callers.
- Credential lease endpoints require service identity.
- SQL uses parameterized statements.
- Integration config validation rejects unexpected status and scope values.

- [ ] **Step 4: Review docs**

Confirm docs answer:

- Which service owns integration lifecycle.
- Which service owns Git provider actions.
- How future integrations attach without schema redesign.
- How credentials are referenced and rotated.

- [ ] **Step 5: Commit final adjustments**

```bash
git add pom.xml services/integrations docs/backend infra/supabase/volumes/db/vericov.sql
git commit -m "test: verify integrations service"
```

---

## Open Decisions

- Whether to number the new service contract before Git and rename the existing Git doc, or add the new doc without renumbering.
- Whether v1 should allow organization-level binding fallback for repository resolution. The plan defaults to no silent fallback.
- Which secret backend should implement `CredentialVault` first. The plan supports an in-memory test vault now, and the port allows a managed vault implementation without changing application code.
- Whether OAuth callbacks should be owned entirely by Integrations Config Service or split with provider action services. For GitHub Apps, the provider-specific callback parser can live in the Git service while Integrations stores the resulting connection and credential references.

## Self-Review

- Spec coverage: The plan creates a base provider-neutral config service, makes Git the first supported integration family, and preserves extension points for future provider types.
- Placeholder scan: No implementation step depends on an unspecified deferred behavior for v1. Future provider backends are represented as catalog entries and capability names only.
- Type consistency: `connection_id`, `provider_key`, `scope_type`, `scope_id`, `capability`, `tenant_id`, and `org_id` are used consistently across service, API, and database tasks.
