# Git Integration Service

The Git Integration service owns provider-facing Git behavior: webhook intake,
provider action execution, PR comments, checks, annotations, branch creation,
pull-request creation, and provider diff reads.

The fuller contract lives in
`docs/backend/services/05-git-integration-service.md`.

## Why This Service Exists

Coverage and policy services should not know GitHub, GitLab, or Bitbucket API
details. This service translates Vericov domain requests into provider actions
and translates provider webhooks back into normalized Vericov events. It does
not calculate coverage and does not own long-lived integration configuration or
secrets; those belong to the Integrations Config service.

## Current Architecture

```text
 Public webhooks                         Internal service calls
       |                                           |
       v                                           v
+------+--------------+                  +---------+----------+
| GitWebhookResource  |                  | InternalGitResource|
+------+--------------+                  +---------+----------+
       |                                           |
       v                                           v
+------+--------------+                  +---------+----------+
| GitWebhookService   |                  | GitProviderAction  |
| verify/dedupe/store |                  | Service            |
+------+--------------+                  +---------+----------+
       |                                           |
       +-------------------+-----------------------+
                           |
                           v
             +-------------+--------------+
             | GitActionRepository        |
             | JDBC or in-memory          |
             +-------------+--------------+
                           |
       +-------------------+--------------------+
       |                                        |
       v                                        v
+------+-------------------+        +-----------+----------------+
| Integrations Config      |        | GitProviderClient          |
| resolve binding + lease  |        | GitHub now, others planned |
+--------------------------+        +----------------------------+
```

## Where It Is Called From

```text
GitHub webhook
  -> Kong /webhooks/github
  -> GitWebhookResource verifies signature and delivery id
  -> GitWebhookService stores raw + normalized payload
  -> Integrations Config receives git.webhook.* audit/event notification

Coverage gate result
  -> coverage-analysis or control plane wants a check/comment
  -> POST /internal/v1/git/check-runs or /pr-comments
  -> Git service resolves active integration binding
  -> GitHub client posts provider action
  -> action record is stored for audit and retry visibility

Pull request analysis
  -> coverage-analysis needs true provider diff
  -> GET /internal/v1/git/repositories/{repository_id}/pull-requests/{number}/diff
  -> Git service resolves repository binding and fetches provider compare diff
```

## Data Model

Git-owned records are operational provider artifacts:

```text
git_webhook_events
  id, tenant_id, provider_key, event_type, delivery_id
  repository_id, signature_valid, payload_sha256
  payload_json, normalized_payload_json, status, created_at

git_pull_requests
  id, tenant_id, repository_id, provider_pull_request_id, number
  title, author, base_branch, base_sha, head_branch, head_sha, state

git_check_runs
  id, tenant_id, repository_id, commit_sha, name
  provider_check_id, status, conclusion, details_url

git_pr_comments
  id, tenant_id, repository_id, pull_request_number
  provider_comment_id, comment_key, body_hash, status

git_pr_annotations
  id, check_run_id, path, start_line, end_line, annotation_level, message

git_provider_actions
  id, action_type, provider_key, repository_id, status
  request_json, response_json, error_json, created_at, updated_at
```

Integration connection state, repository bindings, credential metadata, and
credential leases are owned by `services/integrations`.

## APIs

Public:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/git/providers` | Show provider support status |
| `POST` | `/webhooks/{provider_key}` | Receive Git provider webhooks |

Internal:

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/internal/v1/git/check-runs` | Create or update provider check |
| `POST` | `/internal/v1/git/pr-comments` | Create or update PR comment |
| `POST` | `/internal/v1/git/pr-annotations` | Create inline annotations |
| `POST` | `/internal/v1/git/branches` | Create branch |
| `POST` | `/internal/v1/git/pull-requests` | Open pull request |
| `GET` | `/internal/v1/git/repositories/{repository_id}/pull-requests/{number}/diff` | Fetch provider PR diff |

Internal endpoints require `X-Vericov-Service-Name` and
`X-Vericov-Service-Token`.

## Source Map

```text
api/
  JAX-RS provider status, webhook, and internal action resources

application/
  Provider action orchestration, webhook processing, query services,
  commands, domain records, and in-memory repository

application/port/
  Provider client, integration config, credential lease, repository,
  webhook verifier, and internal auth ports

adapter/provider/github/
  GitHub API client, installation token exchange, webhook signature
  verifier, and unified diff parser

adapter/integrations/
  HTTP client/publisher for Integrations Config service

adapter/jdbc/
  Git action persistence and JSON encoding
```

## Tests

```text
src/test/java/dev/vericov/git/application
  Provider action, query, and webhook behavior

src/test/java/dev/vericov/git/api
  Internal resource and webhook resource behavior

src/test/java/dev/vericov/git/adapter/provider/github
  GitHub HTTP, JWT/token, signature, and diff parser behavior

src/test/java/dev/vericov/git/adapter/integrations
  Integration Config HTTP client and event publisher behavior

src/test/java/dev/vericov/git/adapter/jdbc
  Schema-oriented JDBC coverage and JSON codec behavior
```

Run this service only:

```bash
mvn -pl services/git-integration test
```
