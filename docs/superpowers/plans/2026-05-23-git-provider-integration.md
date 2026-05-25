# Git Provider Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete Git provider installation, webhook ingestion, check runs, PR comments, annotations, branch creation, and PR creation for Vericov, with GitHub as the first working provider.

**Architecture:** Keep `services/integrations` as the source of truth for provider catalog, connections, credentials, bindings, webhook endpoint metadata, and event audit records. Make `services/git-integration` own provider-specific execution, webhook signature verification, normalized Git records, and action artifacts. The first real adapter is GitHub App based; GitLab and Bitbucket remain catalog entries until provider clients are implemented behind the same ports.

**Tech Stack:** Java 25, Helidon 4 MP, JAX-RS, JSON-B, Java `HttpClient`, JUnit 5, Supabase Postgres in `vericov`, Kong declarative config, GitHub App REST API.

---

## Current State

Implemented foundation:

- `services/integrations` lists Git providers and stores connections, credentials, bindings, sync state, and integration events.
- `services/git-integration` has application commands for check runs, PR comments, and open PR action envelopes.
- `infra/supabase/volumes/db/vericov.sql` has integration configuration tables.
- Tests pass with `mvn -pl services/git-integration,services/integrations test`.

Missing production behavior:

- No Git provider HTTP adapter exists.
- No Git Integration HTTP resources exist.
- No webhook receiver or signature verification exists.
- No annotations action exists.
- No branch creation action exists.
- No Git-owned action tables exist.
- Kong still routes `/api/v1/git` and `/webhooks/github|gitlab|bitbucket` to a `501` placeholder.

## Completion Definition

The feature is complete when all of these are true:

- A GitHub App installation can be represented as an active integration connection with required credentials and a repository binding.
- GitHub webhooks are accepted only with valid signatures, deduplicated by delivery ID, normalized, stored, and converted into internal events.
- Internal callers can create or update GitHub check runs, including annotations.
- Internal callers can create or update stable PR comments using a marker.
- Internal callers can create branches from a base SHA and open draft or ready pull requests.
- Provider action results are persisted in Git-owned tables with idempotency keys.
- Kong routes Git API and webhook paths to the Git Integration service.
- Unit, resource, JDBC, and local contract tests pass.

## Provider Scope

Implement these for GitHub first:

- GitHub App installation metadata and installation token creation.
- Webhook signature verification with `X-Hub-Signature-256`.
- `pull_request`, `check_suite`, `check_run`, `push`, and `issue_comment` event normalization.
- Checks API check run create/update with output annotations.
- Issues comments API create/update for PR comments.
- Git refs API for branch creation.
- Pulls API for PR creation.

Do not claim GitLab or Bitbucket execution is complete in this plan. Keep provider registry entries, but return `unsupported_provider` from the provider client factory until their adapters are implemented.

## File Structure

Create or modify these files.

Integrations Config:

- Modify `services/integrations/src/main/java/dev/vericov/integrations/application/IntegrationApplicationService.java`: add credential create/list API orchestration and webhook endpoint create/update/list methods.
- Modify `services/integrations/src/main/java/dev/vericov/integrations/application/port/IntegrationRepository.java`: add webhook endpoint persistence methods if not present.
- Modify `services/integrations/src/main/java/dev/vericov/integrations/application/InMemoryIntegrationRepository.java`: implement webhook endpoint storage.
- Modify `services/integrations/src/main/java/dev/vericov/integrations/adapter/jdbc/JdbcIntegrationRepository.java`: implement webhook endpoint persistence against existing `integration_webhook_endpoints`.
- Create `services/integrations/src/main/java/dev/vericov/integrations/application/IntegrationWebhookEndpointDetails.java`.
- Create `services/integrations/src/main/java/dev/vericov/integrations/application/CreateIntegrationWebhookEndpointCommand.java`.
- Create `services/integrations/src/main/java/dev/vericov/integrations/api/CreateIntegrationCredentialHttpRequest.java`.
- Create `services/integrations/src/main/java/dev/vericov/integrations/api/IntegrationCredentialHttpResponse.java`.
- Create `services/integrations/src/main/java/dev/vericov/integrations/api/CreateIntegrationWebhookEndpointHttpRequest.java`.
- Create `services/integrations/src/main/java/dev/vericov/integrations/api/IntegrationWebhookEndpointHttpResponse.java`.
- Modify `services/integrations/src/main/java/dev/vericov/integrations/api/IntegrationResource.java`: add admin credential and webhook endpoint endpoints.
- Modify `services/integrations/src/main/java/dev/vericov/integrations/api/InternalIntegrationResource.java`: expose internal webhook endpoint lookup by provider/delivery context if needed by webhooks.

Git Integration application:

- Modify `services/git-integration/pom.xml`: add PostgreSQL runtime dependency and any pinned JWT library only if Java-only signing becomes too costly.
- Modify `services/git-integration/src/main/java/dev/vericov/git/application/GitProviderActionType.java`: add `CREATE_BRANCH` and `CREATE_OR_UPDATE_PR_ANNOTATIONS`.
- Modify `services/git-integration/src/main/java/dev/vericov/git/application/CreateOrUpdateCheckRunCommand.java`: include summary, text, details URL, external ID, and annotations.
- Modify `services/git-integration/src/main/java/dev/vericov/git/application/OpenPullRequestCommand.java`: include draft flag and optional idempotency key.
- Create `services/git-integration/src/main/java/dev/vericov/git/application/CreateBranchCommand.java`.
- Create `services/git-integration/src/main/java/dev/vericov/git/application/CreateOrUpdatePrAnnotationsCommand.java`.
- Create `services/git-integration/src/main/java/dev/vericov/git/application/GitAnnotationInput.java`.
- Create `services/git-integration/src/main/java/dev/vericov/git/application/GitProviderActionResult.java`.
- Create `services/git-integration/src/main/java/dev/vericov/git/application/GitWebhookEventDetails.java`.
- Create `services/git-integration/src/main/java/dev/vericov/git/application/GitPullRequestDetails.java`.
- Create `services/git-integration/src/main/java/dev/vericov/git/application/GitCheckRunDetails.java`.
- Create `services/git-integration/src/main/java/dev/vericov/git/application/GitPrCommentDetails.java`.
- Create `services/git-integration/src/main/java/dev/vericov/git/application/GitBranchDetails.java`.
- Create `services/git-integration/src/main/java/dev/vericov/git/application/GitWebhookService.java`.
- Modify `services/git-integration/src/main/java/dev/vericov/git/application/GitProviderActionService.java`: route new action types, persist results, and enforce idempotency.

Git Integration ports and adapters:

- Create `services/git-integration/src/main/java/dev/vericov/git/application/port/GitActionRepository.java`.
- Create `services/git-integration/src/main/java/dev/vericov/git/application/port/GitProviderClient.java`.
- Create `services/git-integration/src/main/java/dev/vericov/git/application/port/GitProviderClientFactory.java`.
- Create `services/git-integration/src/main/java/dev/vericov/git/application/port/GitWebhookVerifier.java`.
- Create `services/git-integration/src/main/java/dev/vericov/git/application/port/GitEventPublisher.java`.
- Create `services/git-integration/src/main/java/dev/vericov/git/adapter/jdbc/DriverManagerDataSource.java`.
- Create `services/git-integration/src/main/java/dev/vericov/git/adapter/jdbc/GitJsonCodec.java`.
- Create `services/git-integration/src/main/java/dev/vericov/git/adapter/jdbc/JdbcGitActionRepository.java`.
- Create `services/git-integration/src/main/java/dev/vericov/git/adapter/integrations/InternalIntegrationConfigHttpClient.java`.
- Create `services/git-integration/src/main/java/dev/vericov/git/adapter/provider/github/GitHubProviderClient.java`.
- Create `services/git-integration/src/main/java/dev/vericov/git/adapter/provider/github/GitHubWebhookVerifier.java`.
- Create `services/git-integration/src/main/java/dev/vericov/git/adapter/provider/github/GitHubInstallationTokenProvider.java`.
- Create `services/git-integration/src/main/java/dev/vericov/git/adapter/provider/UnsupportedGitProviderClient.java`.
- Create `services/git-integration/src/main/java/dev/vericov/git/config/GitIntegrationComponents.java`.

Git Integration API:

- Create `services/git-integration/src/main/java/dev/vericov/git/api/ApiResponse.java`.
- Create `services/git-integration/src/main/java/dev/vericov/git/api/ApiError.java`.
- Create `services/git-integration/src/main/java/dev/vericov/git/api/InternalGitResource.java`.
- Create `services/git-integration/src/main/java/dev/vericov/git/api/GitWebhookResource.java`.
- Create `services/git-integration/src/main/java/dev/vericov/git/api/GitProviderStatusResource.java`.
- Create request/response records under `services/git-integration/src/main/java/dev/vericov/git/api/`.

Database and gateway:

- Modify `infra/supabase/volumes/db/vericov.sql`: add Git-owned tables, indexes, and RLS.
- Modify `infra/kong/kong.yml`: route `/api/v1/git`, `/internal/v1/git`, and `/webhooks/*` to the Git service.
- Modify `infra/kong/README.md`: mark Git routes as implemented where appropriate.
- Modify `infra/kong/scripts/validate-config.mjs`: include Git upstream and routes.
- Modify `docs/backend/services/05-git-integration-service.md`: update from contract-only to implemented status and exact endpoint behavior.
- Modify `docs/backend/services/07-integrations-config-service.md`: document credential and webhook endpoint management surfaces.

---

### Task 1: Add Git-Owned Database Tables

**Files:**
- Modify: `infra/supabase/volumes/db/vericov.sql`
- Test: `services/git-integration/src/test/java/dev/vericov/git/adapter/jdbc/JdbcGitActionRepositoryTest.java`

- [ ] **Step 1: Add failing schema contract test**

Create `services/git-integration/src/test/java/dev/vericov/git/adapter/jdbc/JdbcGitActionRepositoryTest.java`:

```java
package dev.vericov.git.adapter.jdbc;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcGitActionRepositoryTest {
    @Test
    void schemaDefinesGitOwnedActionTables() throws Exception {
        String sql = Files.readString(Path.of("infra/supabase/volumes/db/vericov.sql"));

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vericov.git_webhook_events"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vericov.git_pull_requests"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vericov.git_check_runs"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vericov.git_pr_comments"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vericov.git_pr_annotations"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS vericov.git_branches"));
        assertTrue(sql.contains("ALTER TABLE vericov.git_webhook_events ENABLE ROW LEVEL SECURITY"));
    }
}
```

- [ ] **Step 2: Run the failing schema test**

Run:

```bash
mvn -pl services/git-integration -Dtest=JdbcGitActionRepositoryTest test
```

Expected: FAIL because the Git-owned tables are not present.

- [ ] **Step 3: Add Git-owned tables**

Append these tables near the integration tables in `infra/supabase/volumes/db/vericov.sql`:

```sql
CREATE TABLE IF NOT EXISTS vericov.git_webhook_events (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid REFERENCES vericov.organizations (id) ON DELETE SET NULL,
    repository_id uuid REFERENCES vericov.repositories (id) ON DELETE SET NULL,
    connection_id uuid,
    webhook_endpoint_id uuid,
    provider_key text NOT NULL REFERENCES vericov.integration_providers (provider_key) ON UPDATE CASCADE ON DELETE RESTRICT,
    event_type text NOT NULL,
    delivery_id text NOT NULL,
    signature_valid boolean NOT NULL DEFAULT false,
    payload_sha256 text NOT NULL CHECK (length(payload_sha256) = 64),
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    normalized_payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    status text NOT NULL DEFAULT 'received'
        CHECK (status IN ('received', 'processed', 'ignored', 'failed')),
    error_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    received_at timestamptz NOT NULL DEFAULT now(),
    processed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (provider_key, delivery_id),
    CHECK (length(trim(event_type)) BETWEEN 1 AND 160),
    CHECK (length(trim(delivery_id)) BETWEEN 1 AND 255),
    CHECK (jsonb_typeof(payload) = 'object'),
    CHECK (jsonb_typeof(normalized_payload) = 'object'),
    CHECK (jsonb_typeof(error_json) = 'object')
);

CREATE TABLE IF NOT EXISTS vericov.git_pull_requests (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid NOT NULL REFERENCES vericov.organizations (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    provider_key text NOT NULL,
    provider_pull_request_id text NOT NULL,
    number integer NOT NULL CHECK (number > 0),
    title text NOT NULL,
    author text NOT NULL,
    base_branch text NOT NULL,
    base_sha text NOT NULL,
    head_branch text NOT NULL,
    head_sha text NOT NULL,
    state text NOT NULL CHECK (state IN ('open', 'closed', 'merged')),
    provider_url text,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (tenant_id, repository_id, provider_key, provider_pull_request_id),
    UNIQUE (tenant_id, repository_id, provider_key, number)
);

CREATE TABLE IF NOT EXISTS vericov.git_check_runs (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid NOT NULL REFERENCES vericov.organizations (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    provider_key text NOT NULL,
    commit_sha text NOT NULL,
    name text NOT NULL,
    provider_check_id text,
    status text NOT NULL CHECK (status IN ('queued', 'in_progress', 'completed')),
    conclusion text CHECK (conclusion IN ('success', 'failure', 'neutral', 'cancelled', 'skipped', 'timed_out', 'action_required')),
    details_url text,
    output_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    idempotency_key text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, repository_id, provider_key, idempotency_key),
    CHECK (length(trim(commit_sha)) BETWEEN 1 AND 128),
    CHECK (length(trim(name)) BETWEEN 1 AND 120),
    CHECK (jsonb_typeof(output_json) = 'object')
);

CREATE TABLE IF NOT EXISTS vericov.git_pr_comments (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid NOT NULL REFERENCES vericov.organizations (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    provider_key text NOT NULL,
    pull_request_number integer NOT NULL CHECK (pull_request_number > 0),
    comment_key text NOT NULL,
    provider_comment_id text,
    body_hash text NOT NULL CHECK (length(body_hash) = 64),
    status text NOT NULL CHECK (status IN ('posted', 'updated', 'unchanged', 'deleted', 'failed')),
    provider_url text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, repository_id, provider_key, pull_request_number, comment_key)
);

CREATE TABLE IF NOT EXISTS vericov.git_pr_annotations (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid NOT NULL REFERENCES vericov.organizations (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    provider_key text NOT NULL,
    pull_request_number integer NOT NULL CHECK (pull_request_number > 0),
    annotation_key text NOT NULL,
    provider_annotation_id text,
    path text NOT NULL,
    start_line integer NOT NULL CHECK (start_line > 0),
    end_line integer NOT NULL CHECK (end_line > 0),
    annotation_level text NOT NULL CHECK (annotation_level IN ('notice', 'warning', 'failure')),
    message_hash text NOT NULL CHECK (length(message_hash) = 64),
    status text NOT NULL CHECK (status IN ('posted', 'updated', 'unchanged', 'failed')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, repository_id, provider_key, pull_request_number, annotation_key),
    CHECK (end_line >= start_line)
);

CREATE TABLE IF NOT EXISTS vericov.git_branches (
    id uuid PRIMARY KEY DEFAULT extensions.gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES vericov.tenants (id) ON DELETE CASCADE,
    org_id uuid NOT NULL REFERENCES vericov.organizations (id) ON DELETE CASCADE,
    repository_id uuid NOT NULL REFERENCES vericov.repositories (id) ON DELETE CASCADE,
    provider_key text NOT NULL,
    branch_name text NOT NULL,
    base_sha text NOT NULL,
    provider_ref text,
    status text NOT NULL CHECK (status IN ('created', 'already_exists', 'failed')),
    idempotency_key text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, repository_id, provider_key, idempotency_key),
    UNIQUE (tenant_id, repository_id, provider_key, branch_name)
);
```

Add indexes:

```sql
CREATE INDEX IF NOT EXISTS git_webhook_events_repository_received_idx
    ON vericov.git_webhook_events (repository_id, received_at DESC);

CREATE INDEX IF NOT EXISTS git_check_runs_repository_commit_idx
    ON vericov.git_check_runs (repository_id, commit_sha, name);

CREATE INDEX IF NOT EXISTS git_pr_comments_repository_pr_idx
    ON vericov.git_pr_comments (repository_id, pull_request_number, comment_key);

CREATE INDEX IF NOT EXISTS git_pr_annotations_repository_pr_idx
    ON vericov.git_pr_annotations (repository_id, pull_request_number);

CREATE INDEX IF NOT EXISTS git_branches_repository_branch_idx
    ON vericov.git_branches (repository_id, branch_name);
```

Enable RLS:

```sql
ALTER TABLE vericov.git_webhook_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.git_pull_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.git_check_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.git_pr_comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.git_pr_annotations ENABLE ROW LEVEL SECURITY;
ALTER TABLE vericov.git_branches ENABLE ROW LEVEL SECURITY;
```

- [ ] **Step 4: Run the schema test**

Run:

```bash
mvn -pl services/git-integration -Dtest=JdbcGitActionRepositoryTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add infra/supabase/volumes/db/vericov.sql services/git-integration/src/test/java/dev/vericov/git/adapter/jdbc/JdbcGitActionRepositoryTest.java
git commit -m "feat: add git integration action tables"
```

### Task 2: Expose Credential and Webhook Endpoint Management in Integrations Config

**Files:**
- Modify: `services/integrations/src/main/java/dev/vericov/integrations/application/IntegrationApplicationService.java`
- Modify: `services/integrations/src/main/java/dev/vericov/integrations/application/port/IntegrationRepository.java`
- Modify: `services/integrations/src/main/java/dev/vericov/integrations/application/InMemoryIntegrationRepository.java`
- Modify: `services/integrations/src/main/java/dev/vericov/integrations/adapter/jdbc/JdbcIntegrationRepository.java`
- Modify: `services/integrations/src/main/java/dev/vericov/integrations/api/IntegrationResource.java`
- Create: `services/integrations/src/main/java/dev/vericov/integrations/application/IntegrationWebhookEndpointDetails.java`
- Create: `services/integrations/src/main/java/dev/vericov/integrations/application/CreateIntegrationWebhookEndpointCommand.java`
- Create: `services/integrations/src/main/java/dev/vericov/integrations/api/CreateIntegrationCredentialHttpRequest.java`
- Create: `services/integrations/src/main/java/dev/vericov/integrations/api/IntegrationCredentialHttpResponse.java`
- Create: `services/integrations/src/main/java/dev/vericov/integrations/api/CreateIntegrationWebhookEndpointHttpRequest.java`
- Create: `services/integrations/src/main/java/dev/vericov/integrations/api/IntegrationWebhookEndpointHttpResponse.java`
- Test: `services/integrations/src/test/java/dev/vericov/integrations/api/IntegrationResourceTest.java`

- [ ] **Step 1: Add failing API tests for credential creation**

Append to `IntegrationResourceTest`:

```java
@Test
void adminCanCreateCredentialWithoutEchoingSecret() {
    TestFixture fixture = new TestFixture();
    IntegrationConnectionHttpResponse connection = fixture.createConnection("github", "Engineering GitHub", "123456");

    Response response = fixture.resource.createCredential(
            USER_ID.toString(),
            connection.id().toString(),
            new CreateIntegrationCredentialHttpRequest(
                    TENANT_ID.toString(),
                    ORG_ID.toString(),
                    "github_app_private_key",
                    "private-key-value",
                    null));

    assertEquals(201, response.getStatus());
    IntegrationCredentialHttpResponse body = responseBody(response, IntegrationCredentialHttpResponse.class);
    assertEquals("github_app_private_key", body.credentialKind());
    assertEquals("active", body.status());
    assertTrue(body.secretRef().startsWith("vault://"));
}
```

Expected compile failure: `createCredential` and HTTP request/response records do not exist.

- [ ] **Step 2: Add failing API tests for webhook endpoint creation**

Append to `IntegrationResourceTest`:

```java
@Test
void adminCanCreateWebhookEndpointMetadata() {
    TestFixture fixture = new TestFixture();
    IntegrationConnectionHttpResponse connection = fixture.createConnection("github", "Engineering GitHub", "123456");

    Response response = fixture.resource.createWebhookEndpoint(
            USER_ID.toString(),
            connection.id().toString(),
            new CreateIntegrationWebhookEndpointHttpRequest(
                    TENANT_ID.toString(),
                    ORG_ID.toString(),
                    "github",
                    "external-hook-1",
                    "https://api.vericov.dev/webhooks/github",
                    List.of("pull_request", "check_run"),
                    "vault://memory/webhook-secret",
                    Map.of("content_type", "json")));

    assertEquals(201, response.getStatus());
    IntegrationWebhookEndpointHttpResponse body = responseBody(response, IntegrationWebhookEndpointHttpResponse.class);
    assertEquals("github", body.providerKey());
    assertEquals(List.of("pull_request", "check_run"), body.eventTypes());
    assertEquals("active", body.status());
}
```

- [ ] **Step 3: Run failing tests**

Run:

```bash
mvn -pl services/integrations -Dtest=IntegrationResourceTest test
```

Expected: FAIL at compile because the new API surface does not exist.

- [ ] **Step 4: Implement application details and commands**

Create `IntegrationWebhookEndpointDetails` with these fields:

```java
UUID id;
UUID tenantId;
UUID orgId;
UUID connectionId;
String providerKey;
String externalWebhookId;
String endpointUrl;
List<String> eventTypes;
String status;
String signingSecretRef;
Map<String, Object> config;
Map<String, Object> lastDelivery;
Instant lastDeliveredAt;
Instant createdAt;
Instant updatedAt;
```

Create `CreateIntegrationWebhookEndpointCommand` with requester, tenant, org, connection, provider, endpoint URL, events, signing secret ref, and config. Use the same validation style as `CreateIntegrationConnectionCommand`.

- [ ] **Step 5: Add repository methods**

Add to `IntegrationRepository`:

```java
IntegrationCredentialDetails saveCredential(IntegrationCredentialDetails credential);

List<IntegrationCredentialDetails> listCredentials(UUID tenantId, UUID orgId, UUID connectionId);

IntegrationWebhookEndpointDetails saveWebhookEndpoint(IntegrationWebhookEndpointDetails endpoint);

List<IntegrationWebhookEndpointDetails> listWebhookEndpoints(UUID tenantId, UUID orgId, UUID connectionId);

Optional<IntegrationWebhookEndpointDetails> findWebhookEndpoint(
        UUID tenantId,
        UUID orgId,
        UUID connectionId,
        UUID endpointId);
```

If `saveCredential` and `listCredentials` already exist, keep the existing signatures and add only the webhook endpoint methods.

- [ ] **Step 6: Add service methods**

Add to `IntegrationApplicationService`:

```java
public IntegrationCredentialDetails createCredential(CreateCredentialCommand command)

public List<IntegrationCredentialDetails> listCredentials(
        UUID requesterUserId,
        UUID tenantId,
        UUID orgId,
        UUID connectionId)

public IntegrationWebhookEndpointDetails createWebhookEndpoint(CreateIntegrationWebhookEndpointCommand command)

public List<IntegrationWebhookEndpointDetails> listWebhookEndpoints(
        UUID requesterUserId,
        UUID tenantId,
        UUID orgId,
        UUID connectionId)
```

Validation rules:

- `endpoint_url` must be nonblank and at most 2000 characters.
- `event_types` must be nonempty after trimming and deduplicating.
- `signing_secret_ref` must be nonblank and at most 500 characters.
- `provider_key` must match the connection provider.
- `config` must pass existing secret-key rejection.

- [ ] **Step 7: Add HTTP records and resource methods**

Add public endpoints to `IntegrationResource`:

```java
@POST
@Path("/integrations/{connection_id}/credentials")
public Response createCredential(...)

@GET
@Path("/integrations/{connection_id}/credentials")
public Response listCredentials(...)

@POST
@Path("/integrations/{connection_id}/webhook-endpoints")
public Response createWebhookEndpoint(...)

@GET
@Path("/integrations/{connection_id}/webhook-endpoints")
public Response listWebhookEndpoints(...)
```

Use authorization actions:

- `integrations:credentials:create`
- `integrations:credentials:list`
- `integrations:webhooks:create`
- `integrations:webhooks:list`

- [ ] **Step 8: Implement in-memory and JDBC adapters**

Implement `saveWebhookEndpoint`, `listWebhookEndpoints`, and `findWebhookEndpoint` in both repositories. Use parameterized SQL for JDBC and defensive copies for JSON/list fields.

- [ ] **Step 9: Run tests**

Run:

```bash
mvn -pl services/integrations test
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add services/integrations
git commit -m "feat: expose integration credentials and webhook endpoints"
```

### Task 3: Expand Git Application Commands for Checks, Comments, Annotations, Branches, and PRs

**Files:**
- Modify: `services/git-integration/src/main/java/dev/vericov/git/application/GitProviderActionType.java`
- Modify: `services/git-integration/src/main/java/dev/vericov/git/application/CreateOrUpdateCheckRunCommand.java`
- Modify: `services/git-integration/src/main/java/dev/vericov/git/application/OpenPullRequestCommand.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/application/GitAnnotationInput.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/application/CreateBranchCommand.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/application/CreateOrUpdatePrAnnotationsCommand.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/application/GitProviderActionResult.java`
- Modify: `services/git-integration/src/test/java/dev/vericov/git/application/GitProviderActionServiceTest.java`

- [ ] **Step 1: Add failing command validation tests**

Append tests to `GitProviderActionServiceTest`:

```java
@Test
void branchCreationRequiresPullRequestCapabilityAndPassesBaseSha() {
    RecordingIntegrationConfigClient integrationConfigClient = new RecordingIntegrationConfigClient();
    RecordingGitProviderActionPort providerActionPort = new RecordingGitProviderActionPort();
    integrationConfigClient.resolvedIntegration = resolvedIntegration(Set.of("git.pull_requests"));
    integrationConfigClient.credentialLease = new CredentialLease(
            LEASE_ID,
            "api_token",
            "token".toCharArray(),
            LEASE_EXPIRES_AT);
    GitProviderActionService service = new GitProviderActionService(integrationConfigClient, providerActionPort);

    service.createBranch(new CreateBranchCommand(
            TENANT_ID,
            ORG_ID,
            REPOSITORY_ID,
            "github",
            "vericov/add-tests",
            "abc123",
            "branch-vericov-add-tests"));

    GitProviderAction action = providerActionPort.actions.get(0);
    assertEquals(GitProviderActionType.CREATE_BRANCH, action.type());
    assertEquals("git.pull_requests", action.requiredCapability());
    assertEquals("abc123", action.details().get("base_sha"));
}

@Test
void checkRunCarriesAnnotationsAndDetailsUrl() {
    GitAnnotationInput annotation = new GitAnnotationInput(
            "src/App.java",
            10,
            12,
            "warning",
            "Changed branch is uncovered");

    CreateOrUpdateCheckRunCommand command = new CreateOrUpdateCheckRunCommand(
            TENANT_ID,
            ORG_ID,
            REPOSITORY_ID,
            "github",
            "Vericov Coverage",
            "abc123",
            "completed",
            "failure",
            "Patch coverage failed",
            "Line coverage dropped below threshold",
            "https://app.vericov.dev/reports/1",
            List.of(annotation),
            "check-abc123-coverage");

    assertEquals(List.of(annotation), command.annotations());
    assertEquals("https://app.vericov.dev/reports/1", command.detailsUrl());
}
```

- [ ] **Step 2: Run failing tests**

Run:

```bash
mvn -pl services/git-integration -Dtest=GitProviderActionServiceTest test
```

Expected: FAIL because the new commands and fields do not exist.

- [ ] **Step 3: Add command records**

Create immutable records with constructor validation:

```java
public record GitAnnotationInput(
        String path,
        int startLine,
        int endLine,
        String annotationLevel,
        String message) {
}
```

```java
public record CreateBranchCommand(
        UUID tenantId,
        UUID orgId,
        UUID repositoryId,
        String providerKey,
        String branchName,
        String baseSha,
        String idempotencyKey) {
}
```

```java
public record CreateOrUpdatePrAnnotationsCommand(
        UUID tenantId,
        UUID orgId,
        UUID repositoryId,
        String providerKey,
        int pullRequestNumber,
        String annotationBatchKey,
        List<GitAnnotationInput> annotations) {
}
```

```java
public record GitProviderActionResult(
        GitProviderActionType type,
        String providerId,
        String status,
        String providerUrl,
        Map<String, Object> metadata) {
}
```

Validation details:

- IDs use `GitValues.requireId`.
- Provider keys and statuses use `GitValues.requireCanonical`.
- Branch, SHA, keys, title, body, marker, and URL use `GitValues.requireTrimmed`.
- Pull request numbers and line numbers must be positive.
- Annotation `endLine` must be greater than or equal to `startLine`.
- Annotation level must be `notice`, `warning`, or `failure`.

- [ ] **Step 4: Expand existing commands**

Change `CreateOrUpdateCheckRunCommand` to include:

```java
String summary;
String text;
String detailsUrl;
List<GitAnnotationInput> annotations;
String idempotencyKey;
```

Change `OpenPullRequestCommand` to include:

```java
boolean draft;
String idempotencyKey;
```

- [ ] **Step 5: Update action types and service routing**

Add enum values:

```java
CREATE_BRANCH,
CREATE_OR_UPDATE_PR_ANNOTATIONS
```

Add service methods:

```java
public void createBranch(CreateBranchCommand command)

public void createOrUpdatePrAnnotations(CreateOrUpdatePrAnnotationsCommand command)
```

Both require `git.pull_requests`.

- [ ] **Step 6: Run tests**

Run:

```bash
mvn -pl services/git-integration -Dtest=GitProviderActionServiceTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add services/git-integration/src/main/java/dev/vericov/git/application services/git-integration/src/test/java/dev/vericov/git/application/GitProviderActionServiceTest.java
git commit -m "feat: expand git provider action commands"
```

### Task 4: Add Git Action Repository and Idempotency Persistence

**Files:**
- Create: `services/git-integration/src/main/java/dev/vericov/git/application/port/GitActionRepository.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/application/InMemoryGitActionRepository.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/adapter/jdbc/GitJsonCodec.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/adapter/jdbc/DriverManagerDataSource.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/adapter/jdbc/JdbcGitActionRepository.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/application/GitCheckRunDetails.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/application/GitPrCommentDetails.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/application/GitBranchDetails.java`
- Modify: `services/git-integration/src/main/java/dev/vericov/git/application/GitProviderActionService.java`
- Test: `services/git-integration/src/test/java/dev/vericov/git/application/GitProviderActionServiceTest.java`

- [ ] **Step 1: Add failing idempotency test**

Append:

```java
@Test
void checkRunDoesNotCallProviderWhenExistingIdempotencyKeyIsCompleted() {
    RecordingIntegrationConfigClient integrationConfigClient = new RecordingIntegrationConfigClient();
    RecordingGitProviderActionPort providerActionPort = new RecordingGitProviderActionPort();
    InMemoryGitActionRepository actionRepository = new InMemoryGitActionRepository();
    integrationConfigClient.resolvedIntegration = resolvedIntegration(Set.of("git.checks"));
    actionRepository.saveCheckRun(new GitCheckRunDetails(
            UUID.randomUUID(),
            TENANT_ID,
            ORG_ID,
            REPOSITORY_ID,
            "github",
            "abc123",
            "coverage",
            "provider-check-1",
            "completed",
            "success",
            "https://app.vericov.dev/report",
            Map.of("summary", "ok"),
            "coverage-abc123",
            Instant.now(),
            Instant.now()));
    GitProviderActionService service = new GitProviderActionService(
            integrationConfigClient,
            providerActionPort,
            actionRepository);

    service.createOrUpdateCheckRun(checkRunCommandWithIdempotencyKey("coverage-abc123"));

    assertTrue(providerActionPort.actions.isEmpty());
}
```

Expected compile failure: repository and details classes do not exist, and service constructor has not been expanded.

- [ ] **Step 2: Define repository port**

Create `GitActionRepository`:

```java
public interface GitActionRepository {
    Optional<GitCheckRunDetails> findCheckRunByIdempotencyKey(
            UUID tenantId, UUID repositoryId, String providerKey, String idempotencyKey);

    GitCheckRunDetails saveCheckRun(GitCheckRunDetails details);

    Optional<GitPrCommentDetails> findPrComment(
            UUID tenantId, UUID repositoryId, String providerKey, int pullRequestNumber, String commentKey);

    GitPrCommentDetails savePrComment(GitPrCommentDetails details);

    Optional<GitBranchDetails> findBranchByIdempotencyKey(
            UUID tenantId, UUID repositoryId, String providerKey, String idempotencyKey);

    GitBranchDetails saveBranch(GitBranchDetails details);
}
```

- [ ] **Step 3: Update action service**

Change provider port return type:

```java
GitProviderActionResult execute(GitProviderAction action);
```

Update `GitProviderActionService` to:

- Resolve integration and lease credentials as today.
- Check repository idempotency before executing check and branch actions.
- Persist provider result after execution.
- Treat unchanged PR comments as a persisted `unchanged` status when body hash matches.

- [ ] **Step 4: Implement in-memory repository**

Use `ConcurrentHashMap` keyed by tenant, repository, provider, and idempotency/comment key. Store immutable detail records and return defensive copies for map fields.

- [ ] **Step 5: Implement JDBC repository**

Implement parameterized `select`, `insert`, and `update` statements for:

- `vericov.git_check_runs`
- `vericov.git_pr_comments`
- `vericov.git_branches`

Map SQL failures through `GitIntegrationException("conflict", ...)` for unique constraint conflicts and `IllegalStateException` for unexpected database failures.

- [ ] **Step 6: Run tests**

Run:

```bash
mvn -pl services/git-integration test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add services/git-integration
git commit -m "feat: persist git provider action results"
```

### Task 5: Add Internal Git HTTP Resource

**Files:**
- Create: `services/git-integration/src/main/java/dev/vericov/git/api/InternalGitResource.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/api/ApiResponse.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/api/ApiError.java`
- Create request/response records in `services/git-integration/src/main/java/dev/vericov/git/api/`
- Test: `services/git-integration/src/test/java/dev/vericov/git/api/InternalGitResourceTest.java`

- [ ] **Step 1: Add failing resource tests**

Create `InternalGitResourceTest`:

```java
package dev.vericov.git.api;

import dev.vericov.git.application.GitProviderActionService;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InternalGitResourceTest {
    @Test
    void createCheckRunReturnsAcceptedEnvelope() {
        RecordingGitProviderActionService service = new RecordingGitProviderActionService();
        InternalGitResource resource = new InternalGitResource(service, new AllowingInternalAuthorizer());

        Response response = resource.createCheckRun(
                "coverage-analysis",
                "service-token",
                new CreateCheckRunHttpRequest(
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        "github",
                        "abc123",
                        "Vericov Coverage",
                        "completed",
                        "success",
                        "Coverage passed",
                        "Patch coverage passed",
                        "https://app.vericov.dev/reports/1",
                        List.of(),
                        "coverage-abc123"));

        assertEquals(202, response.getStatus());
        assertEquals(1, service.checkRunCalls);
    }
}
```

Use a tiny test subclass or fake wrapper so the resource can verify that it maps HTTP input to the service call.

- [ ] **Step 2: Run failing resource test**

Run:

```bash
mvn -pl services/git-integration -Dtest=InternalGitResourceTest test
```

Expected: FAIL because the resource and request records do not exist.

- [ ] **Step 3: Add request records**

Create:

- `CreateCheckRunHttpRequest`
- `CreatePrCommentHttpRequest`
- `CreatePrAnnotationsHttpRequest`
- `CreateBranchHttpRequest`
- `OpenPullRequestHttpRequest`
- `GitActionHttpResponse`
- `GitAnnotationHttpRequest`

Each request includes explicit `tenant_id`, `org_id`, `repository_id`, and `provider_key`. Do not infer tenant from headers.

- [ ] **Step 4: Add internal service authorization**

Create a small `InternalServiceAuthorizer` for Git Integration matching the integrations service behavior:

```java
public interface InternalServiceAuthorizer {
    String requireAuthorizedService(String serviceName, String serviceToken);
}
```

The environment implementation reads `VERICOV_INTERNAL_SERVICE_TOKEN_SHA256` and performs SHA-256 constant-time comparison.

- [ ] **Step 5: Implement routes**

Create `InternalGitResource` with:

```java
@POST @Path("/check-runs")
@POST @Path("/pr-comments")
@POST @Path("/pr-annotations")
@POST @Path("/branches")
@POST @Path("/pull-requests")
```

Class path:

```java
@Path("/internal/v1/git")
```

Return:

- `202 Accepted` for accepted provider actions.
- `400` for validation errors.
- `401` for missing or invalid internal service auth.
- `403` when integration binding lacks the required capability.
- `404` when the active integration or credential cannot be resolved.

- [ ] **Step 6: Run resource tests**

Run:

```bash
mvn -pl services/git-integration -Dtest=InternalGitResourceTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add services/git-integration/src/main/java/dev/vericov/git/api services/git-integration/src/test/java/dev/vericov/git/api
git commit -m "feat: add internal git action api"
```

### Task 6: Implement Internal Integrations Config HTTP Client

**Files:**
- Create: `services/git-integration/src/main/java/dev/vericov/git/adapter/integrations/InternalIntegrationConfigHttpClient.java`
- Create: `services/git-integration/src/test/java/dev/vericov/git/adapter/integrations/InternalIntegrationConfigHttpClientTest.java`
- Modify: `services/git-integration/src/main/resources/application.yaml`

- [ ] **Step 1: Add failing HTTP client tests**

Create tests with a fake `HttpClient` wrapper or local `HttpServer`:

```java
@Test
void resolveRepositoryIntegrationSendsServiceIdentityAndParsesCredentialKind() {
    RecordingHttpTransport transport = new RecordingHttpTransport("""
            {"data":{"connection":{"id":"44444444-4444-4444-4444-444444444444","provider_key":"github","status":"active","config":{"installation_id":"123456"}},"binding":{"scope_type":"repository","scope_id":"33333333-3333-3333-3333-333333333333","capabilities":["git.checks"]},"credential_kind":"github_app_private_key"}}
            """);
    InternalIntegrationConfigHttpClient client = new InternalIntegrationConfigHttpClient(
            URI.create("http://integrations:8084"),
            "git-integration",
            "service-token",
            transport);

    ResolvedGitIntegration resolved = client.resolveRepositoryIntegration(
            TENANT_ID,
            ORG_ID,
            REPOSITORY_ID,
            "github",
            "git.checks");

    assertEquals("github_app_private_key", resolved.credentialKind());
    assertEquals("X-Vericov-Service-Name", transport.lastRequest().headers().firstKey());
}
```

- [ ] **Step 2: Add configuration keys**

Update `application.yaml`:

```yaml
integrations:
  base-url: "http://127.0.0.1:8084"

internal-service:
  name: "git-integration"
```

Read service token from `VERICOV_INTERNAL_SERVICE_TOKEN`, not YAML.

- [ ] **Step 3: Implement client**

Use `java.net.http.HttpClient` through a small testable transport interface:

```java
public interface HttpTransport {
    HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;
}
```

Methods:

- `resolveRepositoryIntegration(...)` calls `GET /internal/v1/integrations/resolve`.
- `leaseCredential(...)` calls `POST /internal/v1/integrations/connections/{connection_id}/credential-leases`.
- Map non-2xx responses to `GitIntegrationException` using the error code from the response body when present.
- Never log or include `secret` in exception messages.

- [ ] **Step 4: Run tests**

Run:

```bash
mvn -pl services/git-integration -Dtest=InternalIntegrationConfigHttpClientTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add services/git-integration/src/main/java/dev/vericov/git/adapter/integrations services/git-integration/src/test/java/dev/vericov/git/adapter/integrations services/git-integration/src/main/resources/application.yaml
git commit -m "feat: resolve integrations config over internal api"
```

### Task 7: Implement GitHub Provider Client

**Files:**
- Create: `services/git-integration/src/main/java/dev/vericov/git/application/port/GitProviderClient.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/application/port/GitProviderClientFactory.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/adapter/provider/github/GitHubProviderClient.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/adapter/provider/github/GitHubInstallationTokenProvider.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/adapter/provider/UnsupportedGitProviderClient.java`
- Modify: `services/git-integration/src/main/java/dev/vericov/git/application/port/GitProviderActionPort.java`
- Test: `services/git-integration/src/test/java/dev/vericov/git/adapter/provider/github/GitHubProviderClientTest.java`

- [ ] **Step 1: Verify provider API docs before implementation**

Use official GitHub REST API documentation during implementation for:

- Creating installation access tokens.
- Creating and updating check runs.
- Creating refs.
- Creating pull requests.
- Listing, creating, and updating issue comments.

Record any endpoint/version assumptions in `docs/backend/services/05-git-integration-service.md`.

- [ ] **Step 2: Add failing provider payload tests**

Create tests asserting exact HTTP method, path, and JSON fields:

```java
@Test
void createBranchPostsRefToGitHubRefsApi() {
    RecordingGitHubTransport transport = new RecordingGitHubTransport(201, """
            {"ref":"refs/heads/vericov/add-tests","object":{"sha":"abc123"}}
            """);
    GitHubProviderClient client = new GitHubProviderClient("api.github.com", transport, tokenProvider("installation-token"));

    GitProviderActionResult result = client.createBranch(githubAction(Map.of(
            "branch_name", "vericov/add-tests",
            "base_sha", "abc123",
            "idempotency_key", "branch-abc123")));

    assertEquals("POST", transport.lastRequest().method());
    assertEquals("/repos/vericov/vericov/git/refs", transport.lastRequest().uri().getPath());
    assertTrue(transport.lastBody().contains("\"ref\":\"refs/heads/vericov/add-tests\""));
    assertEquals("created", result.status());
}
```

Add parallel tests for check run, PR comment update, and open PR.

- [ ] **Step 3: Define provider client interface**

```java
public interface GitProviderClient {
    GitProviderActionResult createOrUpdateCheckRun(GitProviderAction action);

    GitProviderActionResult createOrUpdatePrComment(GitProviderAction action);

    GitProviderActionResult createOrUpdatePrAnnotations(GitProviderAction action);

    GitProviderActionResult createBranch(GitProviderAction action);

    GitProviderActionResult openPullRequest(GitProviderAction action);
}
```

- [ ] **Step 4: Implement GitHub token provider**

`GitHubInstallationTokenProvider` inputs:

- GitHub App ID from connection config key `app_id` or env `VERICOV_GITHUB_APP_ID`.
- Installation ID from connection config key `installation_id`.
- Private key from the leased credential.

Output:

- Short-lived installation token string.

Security requirements:

- Do not log private key contents.
- Clear private-key char arrays after use where practical.
- Cache installation tokens only in memory and only until expiration minus 60 seconds.
- Include `X-GitHub-Api-Version` as a configurable value.

- [ ] **Step 5: Implement GitHub client actions**

Map action types:

- `CREATE_OR_UPDATE_CHECK_RUN`: create a check run when no provider ID exists; update when a provider check ID exists.
- `CREATE_OR_UPDATE_PR_COMMENT`: find existing comment by marker and update if present; create otherwise.
- `CREATE_OR_UPDATE_PR_ANNOTATIONS`: use check run annotations for GitHub because GitHub has no standalone PR annotation API.
- `CREATE_BRANCH`: create `refs/heads/{branch}` from base SHA; treat provider "reference already exists" as `already_exists`.
- `OPEN_PULL_REQUEST`: create PR with `head`, `base`, `title`, `body`, and `draft`.

- [ ] **Step 6: Wire provider action port**

Change `GitProviderActionPort` implementation to select provider client by `action.providerKey()` and dispatch by action type. For unsupported providers, throw:

```java
new GitIntegrationException("unsupported_provider", "Git provider is not implemented");
```

- [ ] **Step 7: Run tests**

Run:

```bash
mvn -pl services/git-integration -Dtest=GitHubProviderClientTest,GitProviderActionServiceTest test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add services/git-integration/src/main/java/dev/vericov/git/application/port services/git-integration/src/main/java/dev/vericov/git/adapter/provider services/git-integration/src/test/java/dev/vericov/git/adapter/provider
git commit -m "feat: add github provider action client"
```

### Task 8: Implement GitHub Webhook Verification and Normalization

**Files:**
- Create: `services/git-integration/src/main/java/dev/vericov/git/application/GitWebhookService.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/application/GitWebhookEventDetails.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/application/GitPullRequestDetails.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/application/port/GitWebhookVerifier.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/application/port/GitEventPublisher.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/adapter/provider/github/GitHubWebhookVerifier.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/api/GitWebhookResource.java`
- Test: `services/git-integration/src/test/java/dev/vericov/git/application/GitWebhookServiceTest.java`
- Test: `services/git-integration/src/test/java/dev/vericov/git/api/GitWebhookResourceTest.java`

- [ ] **Step 1: Add failing signature tests**

Create `GitHubWebhookVerifierTest`:

```java
@Test
void verifiesSha256Signature() {
    GitHubWebhookVerifier verifier = new GitHubWebhookVerifier();
    byte[] body = "{\"zen\":\"Keep it logically awesome.\"}".getBytes(StandardCharsets.UTF_8);
    char[] signingKey = "webhook-signing-fixture".toCharArray();
    String signature = "sha256=" + hmacSha256Hex(signingKey, body);

    assertTrue(verifier.verify(body, signature, signingKey));
}

@Test
void rejectsMismatchedSignature() {
    GitHubWebhookVerifier verifier = new GitHubWebhookVerifier();
    assertFalse(verifier.verify("{}".getBytes(StandardCharsets.UTF_8), "sha256=bad", "webhook-signing-fixture".toCharArray()));
}
```

- [ ] **Step 2: Add failing webhook resource tests**

Create `GitWebhookResourceTest`:

```java
@Test
void githubWebhookRequiresDeliveryEventAndSignatureHeaders() {
    GitWebhookResource resource = new GitWebhookResource(new RecordingGitWebhookService());

    Response response = resource.github(null, "pull_request", "sha256=abc", "{}");

    assertEquals(400, response.getStatus());
}
```

- [ ] **Step 3: Implement verifier**

`GitHubWebhookVerifier`:

- Accepts raw request bytes.
- Accepts signature header in `sha256=<hex>` format.
- Computes HMAC-SHA256 with the leased `webhook_secret`.
- Uses constant-time comparison.
- Clears local secret copies after use.

- [ ] **Step 4: Implement webhook service**

`GitWebhookService` flow:

1. Validate provider, event type, delivery ID, signature header, and raw body.
2. Parse provider repository ID and full name from payload.
3. Resolve repository and active webhook-capable integration binding.
4. Lease `webhook_secret` from Integrations Config.
5. Verify signature before trusting payload fields.
6. Deduplicate by provider and delivery ID.
7. Persist `git_webhook_events`.
8. Normalize supported event types:
   - `pull_request.opened`
   - `pull_request.synchronize`
   - `pull_request.closed`
   - `check_run.completed`
   - `issue_comment.created`
9. Publish internal event through `GitEventPublisher`.

Unsupported events are stored with status `ignored`.

- [ ] **Step 5: Implement resource**

Create:

```java
@Path("/webhooks")
public class GitWebhookResource {
    @POST
    @Path("/github")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response github(
            @HeaderParam("X-GitHub-Delivery") String deliveryId,
            @HeaderParam("X-GitHub-Event") String eventType,
            @HeaderParam("X-Hub-Signature-256") String signature,
            String body)
}
```

Return:

- `202 Accepted` when stored as processed or ignored.
- `400` for missing required headers.
- `401` for invalid signature.
- `409` for duplicate delivery ID that is already processed.

- [ ] **Step 6: Run tests**

Run:

```bash
mvn -pl services/git-integration -Dtest=GitHubWebhookVerifierTest,GitWebhookServiceTest,GitWebhookResourceTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add services/git-integration/src/main/java/dev/vericov/git/application services/git-integration/src/main/java/dev/vericov/git/api services/git-integration/src/main/java/dev/vericov/git/adapter/provider/github services/git-integration/src/test/java/dev/vericov/git
git commit -m "feat: receive and verify github webhooks"
```

### Task 9: Add GitHub Installation Handoff Flow

**Files:**
- Create: `services/git-integration/src/main/java/dev/vericov/git/api/GitInstallationResource.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/application/GitInstallationService.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/application/GitInstallationState.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/application/port/GitInstallationStateSigner.java`
- Create: `services/git-integration/src/main/java/dev/vericov/git/adapter/security/HmacGitInstallationStateSigner.java`
- Modify: `services/git-integration/src/main/resources/application.yaml`
- Test: `services/git-integration/src/test/java/dev/vericov/git/application/GitInstallationServiceTest.java`

- [ ] **Step 1: Add failing state signer tests**

Create:

```java
@Test
void signedInstallationStateRoundTrips() {
    HmacGitInstallationStateSigner signer = new HmacGitInstallationStateSigner("state-secret".toCharArray(), Clock.fixed(NOW, ZoneOffset.UTC));

    String signed = signer.sign(new GitInstallationState(TENANT_ID, ORG_ID, USER_ID, "https://app.vericov.dev/settings"));
    GitInstallationState parsed = signer.verify(signed);

    assertEquals(TENANT_ID, parsed.tenantId());
    assertEquals(ORG_ID, parsed.orgId());
    assertEquals(USER_ID, parsed.requesterUserId());
}
```

- [ ] **Step 2: Implement start endpoint**

Route:

```text
POST /api/v1/git/installations/github/start
```

Request:

```json
{
  "tenant_id": "...",
  "org_id": "...",
  "redirect_url": "https://app.vericov.dev/settings/integrations"
}
```

Response:

```json
{
  "data": {
    "install_url": "https://github.com/apps/{app_slug}/installations/new?state={signed_state}"
  }
}
```

Authorize requester with org admin semantics before issuing state.

- [ ] **Step 3: Implement callback endpoint**

Route:

```text
GET /api/v1/git/installations/github/callback?installation_id={id}&setup_action=install&state={signed_state}
```

Behavior:

- Verify state signature and expiry.
- Create or update an integration connection with provider `github`, external account ID equal to installation ID, and config containing `installation_id`, `app_id`, and provider account name when available.
- Create required credential records if the credential source is configured for the environment.
- Create default webhook endpoint metadata for `/webhooks/github`.
- Return a redirect to the signed state's `redirect_url` with `status=installed`.

- [ ] **Step 4: Add configuration**

Use these env/config names:

- `VERICOV_GITHUB_APP_SLUG`
- `VERICOV_GITHUB_APP_ID`
- `VERICOV_GITHUB_APP_PRIVATE_KEY`
- `VERICOV_GITHUB_WEBHOOK_SECRET`
- `VERICOV_GIT_INSTALLATION_STATE_SECRET`
- `VERICOV_PUBLIC_BASE_URL`

Secrets must come from env or a vault, not YAML.

- [ ] **Step 5: Run tests**

Run:

```bash
mvn -pl services/git-integration -Dtest=GitInstallationServiceTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add services/git-integration/src/main/java/dev/vericov/git/api/GitInstallationResource.java services/git-integration/src/main/java/dev/vericov/git/application/GitInstallationService.java services/git-integration/src/main/java/dev/vericov/git/adapter/security services/git-integration/src/test/java/dev/vericov/git/application/GitInstallationServiceTest.java
git commit -m "feat: add github app installation handoff"
```

### Task 10: Wire CDI Components and Runtime Configuration

**Files:**
- Create: `services/git-integration/src/main/java/dev/vericov/git/config/GitIntegrationComponents.java`
- Modify: `services/git-integration/src/main/resources/application.yaml`
- Modify: `services/git-integration/pom.xml`
- Test: `services/git-integration/src/test/java/dev/vericov/git/GitIntegrationComponentsTest.java`

- [ ] **Step 1: Add component smoke test**

Create:

```java
@Test
void componentsUseInMemoryAdaptersWithoutDatabaseUrl() {
    GitIntegrationComponents components = new GitIntegrationComponents();

    assertInstanceOf(InMemoryGitActionRepository.class, components.gitActionRepository());
}
```

- [ ] **Step 2: Add JDBC dependency**

Add to `services/git-integration/pom.xml`:

```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.8</version>
    <scope>runtime</scope>
</dependency>
```

- [ ] **Step 3: Implement producers**

`GitIntegrationComponents` should produce:

- `GitProviderActionService`
- `GitWebhookService`
- `GitActionRepository`
- `IntegrationConfigClient`
- `GitProviderActionPort`
- `GitProviderClientFactory`
- `InternalServiceAuthorizer`
- `Clock`

Database selection:

- Use `JdbcGitActionRepository` when `VERICOV_DATABASE_URL` or `SUPABASE_DB_URL` is present.
- Use `InMemoryGitActionRepository` otherwise.

- [ ] **Step 4: Run tests**

Run:

```bash
mvn -pl services/git-integration test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add services/git-integration/pom.xml services/git-integration/src/main/java/dev/vericov/git/config services/git-integration/src/main/resources/application.yaml services/git-integration/src/test/java/dev/vericov/git/GitIntegrationComponentsTest.java
git commit -m "feat: wire git integration runtime components"
```

### Task 11: Route Git Paths Through Kong

**Files:**
- Modify: `infra/kong/kong.yml`
- Modify: `infra/kong/docker-compose.yml`
- Modify: `infra/kong/README.md`
- Modify: `infra/kong/scripts/validate-config.mjs`

- [ ] **Step 1: Add failing Kong config validation**

Update `validate-config.mjs` expectations so rendered config must contain:

- `git-integration-service`
- `/api/v1/git`
- `/internal/v1/git`
- `/webhooks/github`
- `/webhooks/gitlab`
- `/webhooks/bitbucket`

Run:

```bash
node infra/kong/scripts/validate-config.mjs
```

Expected: FAIL because Git routes are still placeholders.

- [ ] **Step 2: Add Git upstream env**

Add:

```text
GIT_INTEGRATION_SERVICE_URL=http://host.docker.internal:8083
```

to Kong env documentation and compose defaults.

- [ ] **Step 3: Replace placeholder paths**

In `kong.yml`, add a service:

```yaml
- name: git-integration-service
  url: ${GIT_INTEGRATION_SERVICE_URL}
  connect_timeout: 5000
  read_timeout: 300000
  write_timeout: 300000
  routes:
    - name: git-public-api-v1
      paths:
        - /api/v1/git
      methods:
        - GET
        - POST
        - OPTIONS
      strip_path: false
    - name: git-webhooks
      paths:
        - /webhooks/github
        - /webhooks/gitlab
        - /webhooks/bitbucket
      methods:
        - POST
        - OPTIONS
      strip_path: false
    - name: git-internal-api-v1
      paths:
        - /internal/v1/git
      methods:
        - GET
        - POST
        - OPTIONS
      strip_path: false
```

Apply:

- CORS and public rate limiting on `/api/v1/git`.
- Request-size limiting on webhook routes.
- IP restriction and internal rate limiting on `/internal/v1/git`.

- [ ] **Step 4: Run validation**

Run:

```bash
node infra/kong/scripts/validate-config.mjs
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add infra/kong
git commit -m "feat: route git integration through kong"
```

### Task 12: Add End-to-End Local Contract Tests

**Files:**
- Create: `services/git-integration/src/test/java/dev/vericov/git/bdd/RunGitFeaturesTest.java`
- Create: `services/git-integration/src/test/java/dev/vericov/git/bdd/steps/GitSteps.java`
- Create: `services/git-integration/src/test/resources/features/git/git-provider-actions.feature`
- Modify: `services/git-integration/pom.xml`

- [ ] **Step 1: Add Cucumber dependencies**

Add to `services/git-integration/pom.xml`:

```xml
<dependency>
    <groupId>io.cucumber</groupId>
    <artifactId>cucumber-java</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.cucumber</groupId>
    <artifactId>cucumber-junit-platform-engine</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.junit.platform</groupId>
    <artifactId>junit-platform-suite</artifactId>
    <scope>test</scope>
</dependency>
```

Add the Cucumber BOM if it is not already present in the module.

- [ ] **Step 2: Add feature file**

Create:

```gherkin
Feature: Git provider actions

  Scenario: Create coverage check after resolving repository integration
    Given an active GitHub repository integration with checks capability
    When coverage analysis requests a completed coverage check
    Then the GitHub provider receives a check run request
    And the check run result is stored with the idempotency key

  Scenario: Receive signed GitHub pull request webhook
    Given an active GitHub repository integration with webhook capability
    When GitHub sends a signed pull_request webhook
    Then Vericov stores the webhook delivery
    And Vericov publishes a git.pull_request.updated event
```

- [ ] **Step 3: Implement step definitions with fake provider**

Use in-memory repositories and fake provider client. Do not make real network calls.

- [ ] **Step 4: Run BDD tests**

Run:

```bash
mvn -pl services/git-integration -Dtest=RunGitFeaturesTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add services/git-integration/pom.xml services/git-integration/src/test/java/dev/vericov/git/bdd services/git-integration/src/test/resources/features/git
git commit -m "test: cover git provider action workflows"
```

### Task 13: Update Service Contracts and Operational Docs

**Files:**
- Modify: `docs/backend/services/05-git-integration-service.md`
- Modify: `docs/backend/services/07-integrations-config-service.md`
- Modify: `docs/backend/services/01-kong-api-gateway.md`
- Modify: `docs/backend/README.md`

- [ ] **Step 1: Update Git Integration contract**

Document implemented endpoints:

- `POST /api/v1/git/installations/github/start`
- `GET /api/v1/git/installations/github/callback`
- `GET /api/v1/git/repositories/{repository_id}/provider-status`
- `POST /webhooks/github`
- `POST /internal/v1/git/check-runs`
- `POST /internal/v1/git/pr-comments`
- `POST /internal/v1/git/pr-annotations`
- `POST /internal/v1/git/branches`
- `POST /internal/v1/git/pull-requests`

Document exact required headers for webhooks and internal routes.

- [ ] **Step 2: Update Integrations Config contract**

Document:

- Public credential create/list endpoint.
- Public webhook endpoint create/list endpoint.
- Secret redaction and credential lease rules.
- GitHub App installation handoff ownership.

- [ ] **Step 3: Update Kong docs**

Mark Git routes as implemented:

```text
/api/v1/git/** -> Git Integration service
/internal/v1/git/** -> Git Integration service, private-network restricted
/webhooks/github/** -> Git Integration service
```

Keep GitLab and Bitbucket webhook routes documented as accepted paths returning `501 unsupported_provider` until adapters exist.

- [ ] **Step 4: Commit**

```bash
git add docs/backend
git commit -m "docs: document git provider integration rollout"
```

### Task 14: Verification and Security Review

**Files:**
- No source files unless the checks find issues.

- [ ] **Step 1: Run targeted tests**

Run:

```bash
mvn -pl services/integrations,services/git-integration test
```

Expected: PASS.

- [ ] **Step 2: Run full backend tests**

Run:

```bash
mvn test
```

Expected: PASS.

- [ ] **Step 3: Validate Kong config**

Run:

```bash
node infra/kong/scripts/validate-config.mjs
```

Expected: PASS.

- [ ] **Step 4: Check for secret leakage**

Run:

```bash
rg -n "private_key|access_token|refresh_token|client_secret|webhook-secret|private-key-value|do-not-store|lease-value" services infra docs
```

Expected: only test fixtures, docs examples, and secret-key denylist code appear. No real secrets appear.

- [ ] **Step 5: Manual security checklist**

Confirm:

- Webhook signatures are verified before payload trust.
- Duplicate webhook delivery IDs are idempotent.
- Internal routes require service identity and proof token.
- Credential lease secrets are never written to logs, DB JSON, action details, or exceptions.
- Provider metadata config rejects secret-bearing keys.
- GitHub installation state is signed, expires, and includes tenant/org/requester context.
- Branch and PR creation requires `git.pull_requests`.
- Check creation requires `git.checks`.
- Comment and annotation creation requires `git.comments` or `git.pull_requests` according to the action route.

- [ ] **Step 6: Final commit**

If verification required fixes:

```bash
git add services infra docs
git commit -m "fix: harden git provider integration"
```

If no fixes were required, do not create an empty commit.

## Self-Review

Spec coverage:

- Git provider installation: Task 2 and Task 9.
- Webhooks: Task 8 and Task 11.
- Checks: Task 3, Task 4, Task 5, and Task 7.
- PR comments: Task 3, Task 4, Task 5, and Task 7.
- Annotations: Task 3, Task 5, and Task 7.
- Branch creation: Task 3, Task 4, Task 5, and Task 7.
- PR creation: Task 3, Task 5, and Task 7.
- Gateway exposure: Task 11.
- Verification and security: Task 12 and Task 14.

Execution note:

- Use GitHub as the only provider marked implemented.
- Keep GitLab and Bitbucket behind explicit `unsupported_provider` errors until separate provider-client tasks are planned and executed.
