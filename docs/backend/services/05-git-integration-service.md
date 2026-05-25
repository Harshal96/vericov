# Git Integration Service

Status: Implemented for GitHub provider actions and webhooks; GitLab and Bitbucket remain routed but return `unsupported_provider`
Runtime: Helidon 4 on Java 25+
Public webhook base paths: `/webhooks/github`, `/webhooks/gitlab`, `/webhooks/bitbucket`
Public API base path: `/api/v1/git`
Internal base path: `/internal/v1/git`
OpenAPI: `/openapi`

## Purpose

The Git Integration Service owns Git provider actions: webhook normalization, PR comments, status checks, annotations, branch creation, pull request creation, and provider-specific API calls.

This service translates Vericov events into Git provider actions. It does not calculate coverage or decide policy.

Integration lifecycle, credentials metadata, provider capability configuration, and repository bindings are owned by the Integrations Config Service. This service resolves active provider configuration through `GET /internal/v1/integrations/resolve?tenant_id={tenant_id}&org_id={org_id}&provider_key={provider_key}&scope_type={scope_type}&scope_id={scope_id}&capability={capability}` before executing provider actions, then leases the returned `credential_kind` for that connection. Calls to Integrations Config internal endpoints include `X-Vericov-Service-Name: git-integration` plus `X-Vericov-Service-Token`; the token is verified there against SHA-256 hashes configured in `VERICOV_INTERNAL_SERVICE_TOKEN_SHA256`.

Resolved Git integration data carries the credential kind required for provider actions plus non-secret connection/binding config such as GitHub `installation_id`. GitHub App actions lease `github_app_private_key`, exchange it for an installation token, and use that token for Checks, Issues, Refs, and Pulls APIs. Dev or already-exchanged flows may lease `github_installation_token`, `oauth_access_token`, or `api_token`. Provider action execution receives only short-lived lease material, never stored secret refs or raw persistent credentials.

The Git service may parse provider-specific webhook or app callback payloads, but durable connection state, credential references, sync state, webhook endpoint metadata, and repository bindings are written through the Integrations Config Service.

## Public Endpoints

### Provider Action Status

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/git/providers` | List provider execution/webhook/installation support |

### Webhooks

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/webhooks/github` | GitHub webhook receiver using `X-Hub-Signature-256`, `X-GitHub-Delivery`, and `X-GitHub-Event` |
| `POST` | `/webhooks/gitlab` | Routed but unsupported until GitLab adapter exists |
| `POST` | `/webhooks/bitbucket` | Routed but unsupported until Bitbucket adapter exists |

## Internal Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/internal/v1/git/check-runs` | Create/update provider check |
| `POST` | `/internal/v1/git/pr-comments` | Create/update PR comment |
| `POST` | `/internal/v1/git/pr-annotations` | Create inline annotations |
| `POST` | `/internal/v1/git/branches` | Create branch |
| `POST` | `/internal/v1/git/pull-requests` | Open PR |
| `GET` | `/internal/v1/git/repositories/{repository_id}/pull-requests/{number}/diff` | Fetch the true provider base/head PR diff |

Internal endpoints require `X-Vericov-Service-Name` and `X-Vericov-Service-Token`; the service verifies token hashes from `VERICOV_INTERNAL_SERVICE_TOKEN_SHA256`.

The PR diff endpoint validates the requested head SHA against the stored provider PR metadata, resolves the repository binding with the `git.repository_sync` capability, and for GitHub reads the Compare API for `{base}...{head}`. It returns parsed diff line metadata only: file paths, provider status, old path for renames, line numbers, and line change types. Source line text is intentionally not persisted or returned.

## Request Models

### ProviderWebhookEnvelope

Provider-specific body is preserved, but normalized metadata is extracted. Connection, binding, and credential metadata are resolved from the Integrations Config Service.

```json
{
  "provider": "github",
  "event_type": "pull_request",
  "delivery_id": "b75e0d2c-f2b0-11ee-a951-0242ac120002",
  "signature_valid": true,
  "repository": {
    "provider_repository_id": "123456789",
    "full_name": "acme/payments-api"
  },
  "payload": {}
}
```

The GitHub receiver rejects invalid signatures before dedupe, deduplicates by `(provider_key, delivery_id)`, stores the raw JSON payload, stores a normalized payload, updates `git_pull_requests` when a pull request payload is complete, and publishes `git.webhook.{event}` to Integrations Config when tenant/org/connection context is present.

### CreateCheckRunRequest

Before creating or updating a check, Git resolves the active repository binding with `GET /internal/v1/integrations/resolve?tenant_id={tenant_id}&org_id={org_id}&provider_key={provider_key}&scope_type=repository&scope_id={repository_id}&capability=git.checks`.

```json
{
  "repository_id": "4d607f16-1af7-4d3b-ac38-06454cba463c",
  "commit_sha": "head456",
  "name": "Vericov Coverage",
  "status": "completed",
  "conclusion": "failure",
  "summary": "Patch coverage failed: 76.5% < 80%",
  "details_url": "https://app.vericov.dev/reports/pr/42",
  "annotations": [
    {
      "path": "services/payments/discounts.ts",
      "start_line": 88,
      "end_line": 94,
      "annotation_level": "warning",
      "message": "Changed branch is uncovered."
    }
  ]
}
```

### CreatePrCommentRequest

Before creating or updating a PR comment, Git resolves the active repository binding with `GET /internal/v1/integrations/resolve?tenant_id={tenant_id}&org_id={org_id}&provider_key={provider_key}&scope_type=repository&scope_id={repository_id}&capability=git.comments`.

```json
{
  "repository_id": "4d607f16-1af7-4d3b-ac38-06454cba463c",
  "pull_request_number": 42,
  "comment_key": "coverage-summary",
  "body_markdown": "## Vericov Coverage\n\nPatch coverage failed.",
  "update_existing": true
}
```

### OpenPullRequestRequest

Before creating a branch or PR, Git resolves the active repository binding with `GET /internal/v1/integrations/resolve?tenant_id={tenant_id}&org_id={org_id}&provider_key={provider_key}&scope_type=repository&scope_id={repository_id}&capability=git.pull_requests`.

```json
{
  "repository_id": "4d607f16-1af7-4d3b-ac38-06454cba463c",
  "source_branch": "vericov/add-tests-discounts",
  "target_branch": "main",
  "title": "test: add coverage for discount expiration",
  "body_markdown": "Generated by Vericov after detecting an uncovered high-risk branch.",
  "draft": true
}
```

### SlashCommandRequest

```json
{
  "provider": "github",
  "repository_id": "4d607f16-1af7-4d3b-ac38-06454cba463c",
  "pull_request_number": 42,
  "actor": "octocat",
  "command": "fix-tests",
  "arguments": {
    "mode": "dry-run"
  },
  "comment_id": "provider-comment-id"
}
```

## Response Models

### CheckRunResponse

```json
{
  "data": {
    "id": "97486afd-52ce-4c82-ae56-ec6a8387a66b",
    "provider_check_id": "987654",
    "status": "completed",
    "conclusion": "failure",
    "details_url": "https://app.vericov.dev/reports/pr/42"
  }
}
```

### PrCommentResponse

```json
{
  "data": {
    "id": "8e928d0b-9957-4f2d-a9c0-c6849eead5dd",
    "provider_comment_id": "123456",
    "comment_key": "coverage-summary",
    "status": "posted"
  }
}
```

### SlashCommandResponse

```json
{
  "data": {
    "command": "fix-tests",
    "accepted": true,
    "agent_task_id": "d0e35b27-5c32-43d1-8660-19e983653d1d",
    "message": "Vericov queued a dry-run test generation task."
  }
}
```

### PullRequestDiffResponse

```json
{
  "data": {
    "repository_id": "4d607f16-1af7-4d3b-ac38-06454cba463c",
    "pull_request_number": 42,
    "base_sha": "base123",
    "head_sha": "head456",
    "files": [
      {
        "file_path": "services/payments/discounts.ts",
        "old_file_path": null,
        "change_status": "modified",
        "lines": [
          {
            "base_line_number": null,
            "head_line_number": 88,
            "change_type": "added"
          },
          {
            "base_line_number": 94,
            "head_line_number": 95,
            "change_type": "context"
          }
        ]
      }
    ]
  }
}
```

## Database Models

Git-owned tables track provider action and webhook artifacts only. Installation lifecycle, connection status, credentials metadata, webhook endpoint metadata, provider capability configuration, repository bindings, and sync state are stored in Integrations Config Service tables.

### `git_webhook_events`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Nullable until repository is resolved |
| `provider_key` | text | Provider |
| `event_type` | text | Provider event type |
| `delivery_id` | text | Provider delivery ID |
| `repository_id` | uuid | Nullable |
| `signature_valid` | boolean | Signature verification |
| `payload_sha256` | text | SHA-256 hash of raw payload |
| `payload` | jsonb | Raw provider payload |
| `normalized_payload` | jsonb | Provider-neutral extracted metadata |
| `status` | text | `received`, `processed`, `ignored`, `failed` |
| `created_at` | timestamptz | Received time |

### `pull_requests`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `repository_id` | uuid | FK to repositories |
| `provider_pull_request_id` | text | Provider PR ID |
| `number` | integer | PR number |
| `title` | text | PR title |
| `author` | text | Provider actor |
| `base_branch` | text | Base branch |
| `base_sha` | text | Base commit |
| `head_branch` | text | Head branch |
| `head_sha` | text | Head commit |
| `state` | text | `open`, `closed`, `merged` |
| `created_at` | timestamptz | Provider created time |
| `updated_at` | timestamptz | Provider updated time |

### `git_check_runs`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `repository_id` | uuid | FK to repositories |
| `commit_sha` | text | Commit |
| `name` | text | Check name |
| `provider_check_id` | text | Provider check ID |
| `status` | text | `queued`, `in_progress`, `completed` |
| `conclusion` | text | `success`, `failure`, `neutral`, `cancelled`, `timed_out` |
| `details_url` | text | Report URL |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

### `git_pr_comments`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `repository_id` | uuid | FK to repositories |
| `pull_request_number` | integer | Provider PR number |
| `comment_key` | text | Stable Vericov key |
| `provider_comment_id` | text | Provider comment ID |
| `body_hash` | text | Idempotent update detection |
| `status` | text | `posted`, `updated`, `deleted`, `failed` |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

## Events Consumed

| Event | Action |
| --- | --- |
| `coverage.gates.evaluated` | Create/update check run |
| `coverage.pr_report.ready` | Create/update PR summary comment |
| `agent.run.completed` | Comment or open generated PR |
| `agent.task.policy_denied` | Comment when user-requested command is denied |

## Events Published

| Event | Trigger |
| --- | --- |
| `git.webhook.pull_request` | GitHub PR webhook |
| `git.webhook.check_suite` | GitHub check-suite webhook |
| `git.webhook.check_run` | GitHub check-run webhook |
| `git.webhook.push` | GitHub push webhook |
| `git.webhook.issue_comment` | GitHub issue-comment webhook |

## Runtime Configuration

| Variable | Purpose |
| --- | --- |
| `VERICOV_DATABASE_URL` or `SUPABASE_DB_URL` | Enables JDBC persistence; otherwise in-memory storage is used |
| `VERICOV_INTEGRATIONS_BASE_URL` | Integrations Config internal API base URL, default `http://127.0.0.1:8084` |
| `VERICOV_INTERNAL_SERVICE_TOKEN` | Service proof token sent to Integrations Config |
| `VERICOV_INTERNAL_SERVICE_TOKEN_SHA256` | Service token hash map used to authorize inbound internal calls |
| `VERICOV_GITHUB_WEBHOOK_SECRET` | GitHub webhook HMAC secret |
| `VERICOV_GITHUB_API_BASE_URL` | GitHub API base URL, default `https://api.github.com` |
| `VERICOV_GITHUB_APP_ID` | Fallback GitHub App ID when connection config does not include `app_id` |
