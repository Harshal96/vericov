# Agent / Runner Control Plane Service Contract

Status: Draft for review
Runtime: Helidon 4 on Java 25+
Public API base path: `/api/v1`
Runner protocol base path: `/runner/v1`
Internal base path: `/internal/v1/agents`
OpenAPI: `/openapi`

## Purpose

The Agent / Runner Control Plane Service owns agent tasks, runner registration, outbound-only runner polling, runner heartbeats, task leasing, dry-run results, BYOLLM provider metadata, policy decisions, and agent run history.

This service does not execute source-bearing work in enterprise mode. The self-hosted runner executes tasks inside the customer's perimeter and returns only policy-allowed results.

## Public Endpoints

### Agent Runs

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/agent-runs` | List agent runs |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/agent-runs/{agent_run_id}` | Get agent run |
| `POST` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/agent-runs` | Request agent run |
| `POST` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/agent-runs/{agent_run_id}/cancel` | Cancel agent run |
| `POST` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/agent-runs/{agent_run_id}/promote-dry-run` | Promote dry-run to PR task |

### Runner Fleet

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/orgs/{org_id}/runners` | List runners |
| `POST` | `/api/v1/orgs/{org_id}/runner-registration-tokens` | Create runner registration token |
| `GET` | `/api/v1/runners/{runner_id}` | Get runner |
| `PATCH` | `/api/v1/runners/{runner_id}` | Update runner labels/status |
| `POST` | `/api/v1/runners/{runner_id}/revoke` | Revoke runner |
| `POST` | `/api/v1/runners/{runner_id}/drain` | Drain runner |

## Runner Protocol Endpoints

These endpoints are called by self-hosted runners over outbound HTTPS.

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/runner/v1/register` | Register runner with one-time token |
| `POST` | `/runner/v1/heartbeat` | Runner heartbeat and capability sync |
| `POST` | `/runner/v1/tasks/lease` | Lease next task |
| `POST` | `/runner/v1/tasks/{task_id}/ack` | Acknowledge task |
| `POST` | `/runner/v1/tasks/{task_id}/events` | Append task events |
| `POST` | `/runner/v1/tasks/{task_id}/complete` | Complete task |
| `POST` | `/runner/v1/tasks/{task_id}/fail` | Fail task |

## Internal Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/internal/v1/agents/tasks` | Create agent task from service event |
| `POST` | `/internal/v1/agents/tasks/{task_id}/policy-decision` | Record policy decision |
| `GET` | `/internal/v1/agents/tasks/{task_id}` | Get task state |

## Request Models

### CreateAgentRunRequest

```json
{
  "repository_id": "4d607f16-1af7-4d3b-ac38-06454cba463c",
  "pull_request_number": 42,
  "commit_sha": "head456",
  "task_type": "generate_tests",
  "mode": "dry_run",
  "target": {
    "file_path": "services/payments/discounts.ts",
    "line_start": 88,
    "line_end": 94,
    "risk_level": "high"
  },
  "requested_by": {
    "type": "user",
    "id": "supabase-user-id"
  }
}
```

### CreateRunnerRegistrationTokenRequest

```json
{
  "runner_group_id": "b870e8ec-a101-4e69-95dc-955c17d266d6",
  "name": "payments-vpc-runner-token",
  "expires_in_minutes": 30,
  "allowed_capabilities": ["coverage_analysis", "generate_tests", "mutation_testing"]
}
```

### RunnerRegisterRequest

```json
{
  "registration_token": "vrt_...",
  "runner_name": "payments-runner-01",
  "version": "0.1.0",
  "capabilities": ["coverage_analysis", "generate_tests", "mutation_testing"],
  "labels": {
    "region": "us-east-1",
    "environment": "prod",
    "sensitivity": "restricted"
  },
  "providers": {
    "git": ["github_enterprise"],
    "llm": ["azure_openai"]
  }
}
```

### RunnerHeartbeatRequest

```json
{
  "runner_id": "3c4ea26a-33c2-49b3-8f41-c64db060481e",
  "version": "0.1.0",
  "status": "idle",
  "capacity": {
    "max_concurrent_tasks": 4,
    "available_slots": 3
  },
  "capabilities": ["coverage_analysis", "generate_tests", "mutation_testing"],
  "current_tasks": ["d0e35b27-5c32-43d1-8660-19e983653d1d"]
}
```

### LeaseTaskRequest

```json
{
  "runner_id": "3c4ea26a-33c2-49b3-8f41-c64db060481e",
  "capabilities": ["generate_tests"],
  "max_tasks": 1
}
```

### CompleteTaskRequest

```json
{
  "runner_id": "3c4ea26a-33c2-49b3-8f41-c64db060481e",
  "lease_id": "lease-123",
  "status": "completed",
  "result": {
    "summary": "Generated tests for expired coupon behavior.",
    "mode": "dry_run",
    "coverage_delta": {
      "patch_line": 12.5
    },
    "artifacts": [
      {
        "kind": "dry_run_summary",
        "storage_path": "agent-artifacts/tenant/repo/task/result.json"
      }
    ],
    "opened_pull_request": null
  },
  "audit_event_hashes": ["hash-1", "hash-2"]
}
```

## Response Models

### AgentRunResponse

```json
{
  "data": {
    "id": "85d7a880-91c1-465d-885b-2d2e325abed8",
    "repository_id": "4d607f16-1af7-4d3b-ac38-06454cba463c",
    "pull_request_number": 42,
    "task_type": "generate_tests",
    "mode": "dry_run",
    "status": "queued",
    "risk_level": "high",
    "created_at": "2026-05-22T10:00:00Z"
  }
}
```

### RunnerRegistrationTokenResponse

```json
{
  "data": {
    "token": "vrt_...",
    "expires_at": "2026-05-22T10:30:00Z",
    "runner_group_id": "b870e8ec-a101-4e69-95dc-955c17d266d6"
  }
}
```

### RunnerRegisterResponse

```json
{
  "data": {
    "runner_id": "3c4ea26a-33c2-49b3-8f41-c64db060481e",
    "runner_token": "short-lived-runner-token",
    "token_expires_at": "2026-05-22T11:00:00Z",
    "poll_interval_seconds": 10
  }
}
```

### LeaseTaskResponse

```json
{
  "data": {
    "task_id": "d0e35b27-5c32-43d1-8660-19e983653d1d",
    "lease_id": "lease-123",
    "lease_expires_at": "2026-05-22T10:10:00Z",
    "task_type": "generate_tests",
    "mode": "dry_run",
    "repository": {
      "provider": "github_enterprise",
      "full_name": "acme/payments-api",
      "commit_sha": "head456"
    },
    "policy": {
      "source_upload_allowed": false,
      "open_pr_allowed": false,
      "allowed_write_paths": ["**/*test*", "**/__tests__/**"]
    },
    "llm": {
      "allowed_providers": ["azure_openai"],
      "model_route": "test_generation"
    }
  }
}
```

## Database Models

### `runner_groups`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `org_id` | uuid | FK to organizations |
| `name` | text | Group name |
| `routing_labels` | jsonb | Region/sensitivity/capability routing |
| `status` | text | `active`, `disabled` |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

### `runners`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `org_id` | uuid | FK to organizations |
| `runner_group_id` | uuid | FK to runner_groups |
| `name` | text | Runner name |
| `version` | text | Runner version |
| `status` | text | `active`, `idle`, `busy`, `draining`, `revoked`, `offline` |
| `capabilities` | text[] | Supported task types |
| `labels` | jsonb | Routing labels |
| `providers` | jsonb | Git/LLM provider metadata |
| `last_heartbeat_at` | timestamptz | Last heartbeat |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

### `runner_registration_tokens`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `runner_group_id` | uuid | FK to runner_groups |
| `token_hash` | text | Hashed registration token |
| `allowed_capabilities` | text[] | Capability restriction |
| `status` | text | `active`, `used`, `expired`, `revoked` |
| `expires_at` | timestamptz | Expiration |
| `created_by` | uuid | Supabase user ID |
| `created_at` | timestamptz | Created time |

### `agent_runs`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `repository_id` | uuid | FK to repositories |
| `pull_request_number` | integer | Nullable |
| `commit_sha` | text | Target commit |
| `task_type` | text | `explain_gap`, `generate_tests`, `mutation_testing`, `fix_flake` |
| `mode` | text | `advise`, `dry_run`, `open_pr` |
| `status` | text | `queued`, `running`, `completed`, `failed`, `canceled`, `policy_denied` |
| `risk_level` | text | `critical`, `high`, `medium`, `low` |
| `requested_by_type` | text | `user`, `policy`, `slash_command`, `gate` |
| `requested_by_id` | text | Actor ID |
| `summary` | text | Result summary |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

### `agent_tasks`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `agent_run_id` | uuid | FK to agent_runs |
| `runner_id` | uuid | Nullable until leased |
| `task_type` | text | Task type |
| `mode` | text | `advise`, `dry_run`, `open_pr` |
| `status` | text | `queued`, `leased`, `acknowledged`, `running`, `completed`, `failed`, `expired` |
| `lease_id` | text | Current lease |
| `lease_expires_at` | timestamptz | Lease expiration |
| `payload` | jsonb | Task descriptor |
| `result` | jsonb | Policy-allowed result |
| `attempt_count` | integer | Retry count |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

### `policy_decisions`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `agent_task_id` | uuid | FK to agent_tasks |
| `repository_id` | uuid | FK to repositories |
| `decision` | text | `allow`, `deny`, `force_dry_run`, `require_approval` |
| `matched_policy_ids` | uuid[] | Policies matched |
| `action` | text | Requested action |
| `resource` | jsonb | Path/component/repo target |
| `reason` | text | Explainable decision |
| `created_at` | timestamptz | Created time |

### `agent_artifacts`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `agent_run_id` | uuid | FK to agent_runs |
| `agent_task_id` | uuid | FK to agent_tasks |
| `kind` | text | `dry_run_summary`, `patch`, `log`, `mutation_report` |
| `storage_bucket` | text | Supabase Storage bucket |
| `storage_path` | text | Object path |
| `source_bearing` | boolean | True if artifact may contain source |
| `visibility` | text | `internal`, `customer`, `hidden_metadata_only` |
| `created_at` | timestamptz | Created time |

## Events Consumed

| Event | Action |
| --- | --- |
| `coverage.gates.evaluated` | Queue agent task if policy requires |
| `git.slash_command.received` | Queue requested agent action |
| `test.flaky.detected` | Queue flake remediation if enabled |
| `policy.updated` | Re-evaluate queued tasks if needed |

## Events Published

| Event | Trigger |
| --- | --- |
| `agent.run.created` | Agent run created |
| `agent.task.leased` | Runner leases task |
| `agent.task.policy_denied` | Policy blocks task |
| `agent.run.completed` | Agent run complete |
| `runner.heartbeat.missed` | Runner unhealthy |

## Open Questions

- Should runner task leasing use Postgres row locks, Redis leases, or Supabase Queues if available in the selected self-hosted version?
- Should source-bearing dry-run artifacts ever sync to SaaS in metadata-only mode?
- Which BYOLLM provider metadata is safe to store centrally for enterprise tenants?
