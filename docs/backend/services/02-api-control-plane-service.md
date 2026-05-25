# Organization Service and API / Control Plane Contract

Status: Organization, repository registration, policy defaults, repository config, repository policies, gate configuration, coverage badges, report reads, coverage trends, gate evaluation reads, and dashboard reads implemented; wider control-plane surface draft
Runtime: Helidon 4 on Java 25+
Public base path: `/api/v1`
Internal base path: `/internal/v1/control-plane`
OpenAPI: `/openapi`

## Purpose

The Organization Service owns the first implemented part of Vericov's core product model: tenants, organizations, users, memberships, repository registration, org policy defaults, repository config overrides, repository policies, gate configuration, coverage badge reads/settings, commit and PR coverage report reads, coverage trends, gate evaluation history, and dashboard read APIs. The wider API / Control Plane surface for components, coverage debt, API keys, and repository-scoped agent APIs remains draft.

It is the first read/write API for the web app and public API clients. It validates Supabase Auth JWTs for user identity and uses Supabase Postgres for Vericov organization membership, invitations, and role authorization. Local header-based auth is available only when the service is explicitly started with `VERICOV_DEV_AUTH_BYPASS=true`.

## Public Endpoints

### Auth

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/auth/me` | Return the Supabase-authenticated user and visible organizations |

### Organizations

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/orgs` | List organizations visible to current user |
| `POST` | `/api/v1/orgs` | Create organization |
| `GET` | `/api/v1/orgs/{org_id}` | Get organization |
| `PATCH` | `/api/v1/orgs/{org_id}` | Update organization |
| `GET` | `/api/v1/orgs/{org_id}/memberships` | List members |
| `POST` | `/api/v1/orgs/{org_id}/memberships` | Invite/add member |
| `PATCH` | `/api/v1/orgs/{org_id}/memberships/{membership_id}` | Update role |
| `GET` | `/api/v1/orgs/{org_id}/invitations` | List invitations |
| `POST` | `/api/v1/orgs/{org_id}/invitations` | Invite a member by email |
| `POST` | `/api/v1/orgs/{org_id}/invitations/{invitation_id}/accept` | Accept an invitation |
| `GET` | `/api/v1/orgs/{org_id}/policy-defaults` | Get org-level policy/config defaults |
| `PUT` | `/api/v1/orgs/{org_id}/policy-defaults` | Upsert org-level policy/config defaults |
| `GET` | `/api/v1/orgs/{org_id}/dashboard` | Organization coverage dashboard summary |

## Internal Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/internal/v1/authz/check` | Return an allow/deny decision for an org action |

### Repositories

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/orgs/{org_id}/repositories` | List repositories |
| `POST` | `/api/v1/orgs/{org_id}/repositories` | Register repository |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}` | Get repository |
| `PATCH` | `/api/v1/orgs/{org_id}/repositories/{repository_id}` | Update repository settings |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/config` | Get effective Vericov config |
| `PUT` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/config` | Store UI-managed config override |
| `POST` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/config/validate` | Validate config |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/api-keys` | List repository API keys |
| `POST` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/api-keys` | Create repository API key |
| `POST` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/api-keys/{api_key_id}/revoke` | Revoke repository API key |

### Components and Policies

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/components` | List components/packages |
| `POST` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/components` | Create component |
| `PATCH` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/components/{component_id}` | Update component |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/policies` | List policies |
| `POST` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/policies` | Create policy |
| `PATCH` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/policies/{policy_id}` | Update policy |
| `POST` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/policies/{policy_id}/evaluate-preview` | Preview policy decision |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/gates` | List gate configuration |
| `PUT` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/gates` | Replace gate configuration |
| `POST` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/gates/validate` | Validate gate configuration |

### Reports, Gates, Trends, Debt, Badges

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/commits/{sha}/report` | Commit coverage report |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/commits/{sha}/line-hits` | Per-file line hit map for a commit |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/pull-requests/{number}/report` | PR coverage report |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/trends` | Coverage trends |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/gate-evaluations` | Gate history |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/dashboard` | Repository coverage dashboard |
| `GET` | `/api/v1/orgs/{org_id}/repositories/dashboard` | Repository dashboard summary list for an organization |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/coverage-debt` | List coverage debt |
| `POST` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/coverage-debt` | Create debt item |
| `PATCH` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/coverage-debt/{debt_id}` | Update debt item |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/badge-settings` | Get coverage badge settings |
| `PUT` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/badge-settings` | Upsert coverage badge settings |
| `POST` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/badge-settings/rotate-token` | Rotate coverage badge token |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/badges/coverage.svg` | SVG badge; accepts optional `style=flat\|flat-square\|plastic\|for-the-badge` |
| `GET` | `/api/v1/orgs/{org_id}/repositories/{repository_id}/badges/coverage.json` | JSON badge |

## Request Models

### CreateOrganizationRequest

```json
{
  "name": "Acme Engineering",
  "slug": "acme",
  "plan": "team"
}
```

### RegisterRepositoryRequest

```json
{
  "provider": "github",
  "provider_repository_id": "123456789",
  "full_name": "acme/payments-api",
  "default_branch": "main",
  "visibility": "private"
}
```

### UpsertRepositoryConfigRequest

```json
{
  "config": {
    "coverage": {
      "project": {
        "target": 82,
        "max_drop": 1
      },
      "patch": {
        "target": 75
      }
    },
    "agent_policy": {
      "allow_prs": true,
      "default_mode": "suggest"
    }
  },
  "schema_version": 1
}
```

### UpsertPolicyDefaultsRequest

```json
{
  "defaults": {
    "coverage": {
      "project": 82,
      "patch": 75
    }
  },
  "schema_version": 1
}
```

### CreatePolicyRequest

```json
{
  "name": "Coverage floor",
  "description": "Minimum repository coverage",
  "policy_type": "coverage",
  "target_type": "repository",
  "target_selector": null,
  "config": {
    "minimum": 82
  },
  "status": "active",
  "priority": 10
}
```

### UpsertRepositoryGatesRequest

```json
[
  {
    "name": "Project line coverage",
    "gate_type": "project_coverage",
    "metric": "line",
    "threshold": 82,
    "max_drop": null,
    "blocking": true,
    "config": {},
    "status": "active"
  }
]
```

### UpsertRepositoryBadgeSettingsRequest

```json
{
  "enabled": true,
  "branch": "main",
  "metric": "line",
  "label": "coverage",
  "thresholds": {
    "brightgreen": 90,
    "green": 80,
    "yellow": 60
  }
}
```

### RotateRepositoryBadgeTokenResponse

The raw badge token is returned only by the rotate-token endpoint.

```json
{
  "data": {
    "org_id": "8247a65f-9d3d-4d61-8cc9-1f2692c6da9e",
    "repository_id": "4d607f16-1af7-4d3b-ac38-06454cba463c",
    "token": "vc_badge_...",
    "token_prefix": "vc_badge_...",
    "rotated_at": "2026-05-23T10:00:00Z"
  }
}
```

### CoverageBadgeJsonResponse

The SVG badge endpoint accepts the same `token`, `branch`, and `metric` query parameters as the JSON endpoint, plus an SVG-only `style` query parameter. Supported styles are `flat` (default), `flat-square`, `plastic`, and `for-the-badge`. Invalid styles return `validation_error`.

```json
{
  "data": {
    "org_id": "8247a65f-9d3d-4d61-8cc9-1f2692c6da9e",
    "repository_id": "4d607f16-1af7-4d3b-ac38-06454cba463c",
    "label": "coverage",
    "message": "82.5%",
    "color": "green",
    "metric": "line",
    "branch": "main",
    "commit_sha": "abc123",
    "coverage_percent": 82.5,
    "report_created_at": "2026-05-23T10:00:00Z"
  }
}
```

### EffectiveRepositoryConfigResponse

```json
{
  "data": {
    "org_id": "8247a65f-9d3d-4d61-8cc9-1f2692c6da9e",
    "repository_id": "4d607f16-1af7-4d3b-ac38-06454cba463c",
    "org_defaults": {
      "coverage": {
        "project": 82
      }
    },
    "repository_config": {
      "coverage": {
        "patch": 75
      }
    },
    "policies": [],
    "gates": []
  }
}
```

### CreateRepositoryApiKeyRequest

```json
{
  "name": "github-actions-main",
  "scopes": ["uploads:create", "uploads:read"],
  "allowed_branches": ["main", "release/*"],
  "expires_at": "2027-05-22T00:00:00Z"
}
```

### CreateCoverageDebtRequest

```json
{
  "repository_id": "4d607f16-1af7-4d3b-ac38-06454cba463c",
  "commit_sha": "abc123",
  "file_path": "services/payments/discounts.ts",
  "line_start": 88,
  "line_end": 94,
  "risk_level": "low",
  "reason": "Defensive fallback covered by integration tests next sprint",
  "owner": "team-payments",
  "expires_at": "2026-08-31T00:00:00Z"
}
```

## Response Models

### OrganizationResponse

```json
{
  "data": {
    "id": "8247a65f-9d3d-4d61-8cc9-1f2692c6da9e",
    "name": "Acme Engineering",
    "slug": "acme",
    "plan": "team",
    "created_at": "2026-05-22T10:00:00Z"
  }
}
```

### RepositoryResponse

```json
{
  "data": {
    "id": "4d607f16-1af7-4d3b-ac38-06454cba463c",
    "org_id": "8247a65f-9d3d-4d61-8cc9-1f2692c6da9e",
    "provider": "github",
    "full_name": "acme/payments-api",
    "default_branch": "main",
    "visibility": "private",
    "status": "active"
  }
}
```

### CommitCoverageReportResponse

```json
{
  "data": {
    "id": "7d2c927b-4e38-47ef-9baf-3f41c7b6e24d",
    "tenant_id": "2c4b883a-9187-4a9f-9d0f-7f41c19ed5d9",
    "org_id": "8247a65f-9d3d-4d61-8cc9-1f2692c6da9e",
    "repository_id": "4d607f16-1af7-4d3b-ac38-06454cba463c",
    "upload_id": "06ab0718-08e9-4202-8915-c35319afd336",
    "commit_sha": "abc123",
    "branch": "main",
    "pull_request_number": 42,
    "line": {
      "covered": 33,
      "total": 40,
      "percent": 82.5
    },
    "branch_coverage": {
      "covered": 8,
      "total": 10,
      "percent": 80.0
    },
    "function": {
      "covered": 5,
      "total": 5,
      "percent": 100.0
    },
    "statement": {
      "covered": 20,
      "total": 25,
      "percent": 80.0
    },
    "files": [
      {
        "file_path": "src/App.java",
        "line": {
          "covered": 10,
          "total": 12,
          "percent": 83.33
        }
      }
    ],
    "created_at": "2026-05-23T10:00:00Z",
    "updated_at": "2026-05-23T10:00:00Z"
  }
}
```

`GET /commits/{sha}/report` accepts `include_files=true|false` and `limit=1..500` for file summaries.

### PullRequestCoverageReportResponse

```json
{
  "data": {
    "pull_request_number": 42,
    "head_sha": "abc123",
    "report": {
      "commit_sha": "abc123",
      "branch": "main",
      "pull_request_number": 42,
      "line": {
        "covered": 33,
        "total": 40,
        "percent": 82.5
      },
      "files": []
    },
    "diff": {
      "base_sha": "abc122",
      "head_sha": "abc123",
      "status": "complete",
      "patch_line": {
        "covered": 1,
        "total": 2,
        "percent": 50
      },
      "newly_missed_line_count": 1,
      "lost_coverage_line_count": 1,
      "files": [
        {
          "file_path": "src/App.java",
          "old_file_path": null,
          "change_status": "modified",
          "patch_line": {
            "covered": 1,
            "total": 2,
            "percent": 50
          },
          "newly_missed_line_count": 1,
          "lost_coverage_line_count": 1,
          "lines": [
            {
              "base_line_number": null,
              "head_line_number": 14,
              "change_type": "added",
              "executable": true,
              "base_hits": null,
              "head_hits": 0,
              "newly_missed": true,
              "lost_coverage": false
            }
          ]
        }
      ]
    }
  }
}
```

`GET /pull-requests/{number}/report` returns the latest report for the PR number and accepts `include_files=true|false`, `include_diff_lines=true|false`, and `limit=1..500`. Diff file summaries are returned when available; per-line diff diagnostics are included only when `include_diff_lines=true`.

### CoverageLineHitMapResponse

```json
{
  "data": {
    "repository_id": "4d607f16-1af7-4d3b-ac38-06454cba463c",
    "coverage_report_id": "7d2c927b-4e38-47ef-9baf-3f41c7b6e24d",
    "commit_sha": "abc123",
    "files": {
      "src/App.java": {
        "12": 4,
        "14": 0,
        "20": 0
      }
    }
  }
}
```

`GET /commits/{sha}/line-hits` requires a repository-relative `file_path` query parameter and returns executable line hit counts for the latest complete report at that commit.

### CoverageTrendResponse

```json
{
  "data": {
    "org_id": "8247a65f-9d3d-4d61-8cc9-1f2692c6da9e",
    "repository_id": "4d607f16-1af7-4d3b-ac38-06454cba463c",
    "branch": "main",
    "metric": "line",
    "points": [
      {
        "report_id": "7d2c927b-4e38-47ef-9baf-3f41c7b6e24d",
        "commit_sha": "abc123",
        "branch": "main",
        "metric": "line",
        "percent": 82.5,
        "created_at": "2026-05-23T10:00:00Z"
      }
    ]
  }
}
```

`GET /trends` accepts optional `branch`, `metric`, and `limit=1..500`. The default metric is `line`.

### GateEvaluationResponse

```json
{
  "data": [
    {
      "id": "f2c8efb6-1c3a-478f-9a3f-7477e11e0e4b",
      "repository_id": "4d607f16-1af7-4d3b-ac38-06454cba463c",
      "coverage_report_id": "7d2c927b-4e38-47ef-9baf-3f41c7b6e24d",
      "commit_sha": "abc123",
      "branch": "main",
      "pull_request_number": 42,
      "gate_name": "Project coverage",
      "gate_type": "project_coverage",
      "metric": "line",
      "threshold": 85,
      "actual": 82.5,
      "status": "failed",
      "blocking": true,
      "details": {
        "summary": "below threshold"
      },
      "evaluated_at": "2026-05-23T10:00:00Z"
    }
  ]
}
```

`GET /gate-evaluations` accepts optional `branch`, `status` (`passed`, `failed`, or `warning`), and `limit=1..500`.

### RepositoryDashboardResponse

```json
{
  "data": {
    "org_id": "8247a65f-9d3d-4d61-8cc9-1f2692c6da9e",
    "repository_id": "4d607f16-1af7-4d3b-ac38-06454cba463c",
    "branch": "main",
    "latest_commit_sha": "abc123",
    "latest_line_coverage": {
      "covered": 33,
      "total": 40,
      "percent": 82.5
    },
    "latest_branch_coverage": {
      "covered": 8,
      "total": 10,
      "percent": 80.0
    },
    "latest_function_coverage": {
      "covered": 5,
      "total": 5,
      "percent": 100.0
    },
    "latest_statement_coverage": {
      "covered": 20,
      "total": 25,
      "percent": 80.0
    },
    "failing_gate_count": 1,
    "latest_report_at": "2026-05-23T10:00:00Z"
  }
}
```

### OrganizationDashboardResponse

```json
{
  "data": {
    "org_id": "8247a65f-9d3d-4d61-8cc9-1f2692c6da9e",
    "branch": "main",
    "repository_count": 1,
    "average_line_coverage": 82.5,
    "failing_gate_count": 1,
    "repositories": [
      {
        "repository_id": "4d607f16-1af7-4d3b-ac38-06454cba463c",
        "full_name": "acme/payments-api",
        "branch": "main",
        "latest_commit_sha": "abc123",
        "latest_line_coverage": 82.5,
        "failing_gate_count": 1,
        "latest_report_at": "2026-05-23T10:00:00Z"
      }
    ]
  }
}
```

### CreateRepositoryApiKeyResponse

The raw API key is returned only once.

```json
{
  "data": {
    "id": "16e9b68f-a55f-4d8a-9f39-f0e9f6d6de12",
    "name": "github-actions-main",
    "api_key": "vc_live_...",
    "scopes": ["uploads:create", "uploads:read"],
    "allowed_branches": ["main", "release/*"],
    "expires_at": "2027-05-22T00:00:00Z"
  }
}
```

### CoverageDebtResponse

```json
{
  "data": {
    "id": "dd1a95cc-f267-40f7-9b7e-0f4645f6bfa1",
    "repository_id": "4d607f16-1af7-4d3b-ac38-06454cba463c",
    "file_path": "services/payments/discounts.ts",
    "line_start": 88,
    "line_end": 94,
    "risk_level": "low",
    "status": "active",
    "owner": "team-payments",
    "expires_at": "2026-08-31T00:00:00Z"
  }
}
```

## Database Models

### `organizations`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `name` | text | Display name |
| `slug` | text | Unique per platform |
| `plan` | text | `free`, `team`, `enterprise` |
| `status` | text | `active`, `suspended`, `deleted` |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

### `memberships`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `org_id` | uuid | FK to organizations |
| `supabase_user_id` | uuid | FK/reference to `auth.users.id` |
| `role` | text | `owner`, `admin`, `developer`, `viewer`, `auditor` |
| `status` | text | `active`, `invited`, `disabled` |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

### `repositories`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `org_id` | uuid | FK to organizations |
| `provider` | text | `github`, `gitlab`, `bitbucket` |
| `provider_repository_id` | text | Provider repo ID |
| `full_name` | text | `owner/name` or provider equivalent |
| `default_branch` | text | Default branch |
| `visibility` | text | `public`, `private`, `internal` |
| `privacy_mode` | text | `public_saas`, `private_saas`, `metadata_only`, `self_hosted` |
| `status` | text | `active`, `disabled`, `archived` |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

### `repository_configs`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `org_id` | uuid | FK to organizations |
| `repository_id` | uuid | FK to repositories |
| `source` | text | `ui_override`, `repo_file`, `computed` |
| `config_json` | jsonb | UI or repo-provided config |
| `schema_version` | integer | Config schema version |
| `validation_status` | text | `valid`, `invalid`, `warning` |
| `validation_errors` | jsonb | Validation messages |
| `updated_by_user_id` | uuid | Supabase user ID |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

### `organization_policy_defaults`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `org_id` | uuid | Unique FK to organizations |
| `defaults_json` | jsonb | Org-level defaults merged into effective config |
| `schema_version` | integer | Config schema version |
| `updated_by_user_id` | uuid | Supabase user ID |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

### `repository_api_keys`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `repository_id` | uuid | FK to repositories |
| `name` | text | Human-readable key name |
| `key_prefix` | text | Non-secret key prefix for display |
| `key_hash` | text | HMAC-SHA256 API key secret hash |
| `scopes` | text[] | `uploads:create`, `uploads:read` |
| `branch_allow_patterns` | text[] | Optional branch restrictions |
| `revoked_at` | timestamptz | Revocation timestamp |
| `revoked_by_user_id` | uuid | Supabase user ID that revoked the key |
| `last_used_at` | timestamptz | Last successful auth |
| `expires_at` | timestamptz | Optional expiration |
| `created_by_user_id` | uuid | Supabase user ID |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

### `repository_ci_trusts`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `repository_id` | uuid | FK to repositories |
| `provider` | text | `github_actions` in v1 |
| `issuer` | text | `https://token.actions.githubusercontent.com` |
| `audience` | text | Expected OIDC audience |
| `subject_pattern` | text | GitHub OIDC `sub` pattern |
| `scopes` | text[] | Upload scopes granted by the trust |
| `branch_allow_patterns` | text[] | Branch restrictions |
| `expires_at` | timestamptz | Optional expiration |
| `revoked_at` | timestamptz | Revocation timestamp |
| `last_used_at` | timestamptz | Last successful auth |

### `components`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `repository_id` | uuid | FK to repositories |
| `name` | text | Component/package name |
| `path_patterns` | text[] | Owned paths |
| `owners` | text[] | Team/user owner keys |
| `metadata` | jsonb | Package graph, labels |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

### `repository_policies`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `org_id` | uuid | FK to organizations |
| `repository_id` | uuid | FK to repositories |
| `name` | text | Policy name |
| `description` | text | Optional policy description |
| `policy_type` | text | `coverage`, `mutation`, `agent_review`, `waiver` |
| `target_type` | text | `repository`, `component`, `path` |
| `target_selector` | text | Component or path selector when needed |
| `config_json` | jsonb | Policy-specific settings |
| `status` | text | `active`, `disabled` |
| `priority` | integer | Lower numbers evaluate first |
| `created_by_user_id` | uuid | Supabase user ID |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

### `repository_gate_configurations`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `org_id` | uuid | FK to organizations |
| `repository_id` | uuid | FK to repositories |
| `name` | text | Gate name |
| `gate_type` | text | `project_coverage`, `patch_coverage`, `coverage_drop`, `component_coverage`, `mutation_score`, `agent_review_required` |
| `metric` | text | `line`, `branch`, `function`, `statement`, `mutation`, `risk` |
| `threshold` | numeric | Required except agent review gates |
| `max_drop` | numeric | Only for coverage-drop gates |
| `blocking` | boolean | Whether failure blocks |
| `config_json` | jsonb | Gate-specific settings |
| `status` | text | `active`, `disabled` |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

### `repository_badge_settings`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `org_id` | uuid | FK to organizations |
| `repository_id` | uuid | FK to repositories |
| `enabled` | boolean | Whether token-based badge access is enabled |
| `branch` | text | Branch used when no query override is provided |
| `metric` | text | `line`, `branch`, `function`, `statement` |
| `label` | text | Badge label |
| `thresholds_json` | jsonb | Color thresholds |
| `token_hash` | text | SHA-256 hash of the badge token |
| `token_prefix` | text | Non-secret display prefix |
| `created_by_user_id` | uuid | Supabase user ID |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |
| `revoked_at` | timestamptz | Present when token is revoked |

### `badge_cache`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `org_id` | uuid | FK to organizations |
| `repository_id` | uuid | FK to repositories |
| `badge_type` | text | Currently `coverage` |
| `cache_scope` | text | `token` or `authenticated` |
| `branch` | text | Badge branch |
| `metric` | text | `line`, `branch`, `function`, `statement` |
| `label` / `message` / `color` | text | Resolved badge display fields |
| `commit_sha` | text | Nullable covered commit SHA |
| `coverage_percent` | numeric | Nullable resolved percentage |
| `source_report_id` | uuid | Nullable FK to `coverage_reports` |
| `report_created_at` | timestamptz | Nullable source report time |
| `settings_updated_at` | timestamptz | Badge settings version used for cache validation |
| `cached_at` | timestamptz | Cache write time |
| `expires_at` | timestamptz | Cache expiry time |

### `coverage_reports`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `repository_id` | uuid | FK to repositories |
| `upload_id` | uuid | Unique FK to uploads |
| `commit_sha` | text | Covered commit SHA |
| `branch` | text | Covered branch |
| `pull_request_number` | integer | Nullable PR number |
| `status` | text | `processing`, `complete`, `failed` |
| `line_covered` / `line_total` | integer | Line coverage counters |
| `branch_covered` / `branch_total` | integer | Branch coverage counters |
| `function_covered` / `function_total` | integer | Function coverage counters |
| `statement_covered` / `statement_total` | integer | Statement coverage counters |
| `normalized_storage_bucket` | text | Optional normalized artifact bucket |
| `normalized_storage_path` | text | Optional normalized artifact path |
| `created_at` | timestamptz | Report creation time |
| `updated_at` | timestamptz | Report update time |

### `coverage_file_summaries`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `coverage_report_id` | uuid | FK to coverage reports |
| `repository_id` | uuid | FK to repositories |
| `commit_sha` | text | Covered commit SHA |
| `file_path` | text | Repository-relative path |
| `line_covered` / `line_total` | integer | Line coverage counters |
| `branch_covered` / `branch_total` | integer | Branch coverage counters |
| `function_covered` / `function_total` | integer | Function coverage counters |
| `statement_covered` / `statement_total` | integer | Statement coverage counters |
| `created_at` | timestamptz | Summary creation time |

### `coverage_line_hits`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `coverage_report_id` | uuid | FK to coverage reports |
| `repository_id` | uuid | FK to repositories |
| `commit_sha` | text | Covered commit SHA |
| `file_path` | text | Repository-relative path |
| `line_number` | integer | Executable line number |
| `hits` | bigint | Execution hit count |
| `created_at` | timestamptz | Summary creation time |

### `pull_request_coverage_diffs`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `repository_id` | uuid | FK to repositories |
| `coverage_report_id` | uuid | Unique FK to head coverage report |
| `pull_request_number` | integer | PR number |
| `provider_key` | text | Git provider key |
| `base_sha` | text | Base commit |
| `head_sha` | text | Head commit |
| `status` | text | `complete`, `base_coverage_missing`, `unavailable` |
| `patch_line_covered` / `patch_line_total` | integer | Added executable line coverage |
| `newly_missed_line_count` | integer | Added executable lines with zero hits |
| `lost_coverage_line_count` | integer | Context executable lines that lost all hits |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

### `pull_request_coverage_diff_files`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `pr_diff_id` | uuid | FK to pull_request_coverage_diffs |
| `repository_id` | uuid | FK to repositories |
| `file_path` | text | Head-side repository path |
| `old_file_path` | text | Base-side path for renames |
| `change_status` | text | Provider file status |
| `patch_line_covered` / `patch_line_total` | integer | File patch line coverage |
| `newly_missed_line_count` | integer | File newly missed count |
| `lost_coverage_line_count` | integer | File lost coverage count |
| `created_at` | timestamptz | Created time |

### `pull_request_coverage_diff_lines`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `pr_diff_id` | uuid | FK to pull_request_coverage_diffs |
| `repository_id` | uuid | FK to repositories |
| `file_path` | text | Head-side repository path |
| `old_file_path` | text | Base-side path for renames |
| `base_line_number` | integer | Nullable base-side line |
| `head_line_number` | integer | Nullable head-side line |
| `change_type` | text | `added`, `deleted`, `context` |
| `executable` | boolean | Whether the line participates in coverage |
| `base_hits` | bigint | Nullable base hit count |
| `head_hits` | bigint | Nullable head hit count |
| `newly_missed` | boolean | Added executable line with zero head hits |
| `lost_coverage` | boolean | Context executable line whose hits dropped to zero |
| `created_at` | timestamptz | Created time |

### `gate_evaluations`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `org_id` | uuid | FK to organizations |
| `repository_id` | uuid | FK to repositories |
| `coverage_report_id` | uuid | Nullable FK to coverage reports |
| `commit_sha` | text | Evaluated commit SHA |
| `branch` | text | Evaluated branch |
| `pull_request_number` | integer | Nullable PR number |
| `gate_name` | text | Gate display name |
| `gate_type` | text | Gate type from configuration |
| `metric` | text | Evaluated metric |
| `threshold` | numeric | Expected threshold when applicable |
| `actual` | numeric | Actual measured value |
| `status` | text | `passed`, `failed`, `warning` |
| `blocking` | boolean | Whether the gate blocks merges |
| `details_json` | jsonb | Gate-specific diagnostic payload |
| `evaluated_at` | timestamptz | Evaluation time |

### `coverage_debt_items`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `repository_id` | uuid | FK to repositories |
| `commit_sha` | text | Debt source commit |
| `file_path` | text | Repository-relative path |
| `line_start` | integer | Nullable for file/function debt |
| `line_end` | integer | Nullable for file/function debt |
| `risk_level` | text | `critical`, `high`, `medium`, `low` |
| `reason` | text | Human explanation |
| `owner` | text | Team/user owner |
| `status` | text | `active`, `resolved`, `expired`, `revoked` |
| `expires_at` | timestamptz | Review deadline |
| `created_by` | uuid | Supabase user ID |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

## Internal Events Published

| Event | Trigger |
| --- | --- |
| `repository.registered` | Repository registration |
| `repository.config.updated` | Config change |
| `policy.updated` | Policy change |
| `repository.gates.updated` | Gate configuration change |
| `coverage.report.created` | Coverage report ingestion |
| `coverage.gates.evaluated` | Gate evaluation storage |
| `repository.badge_settings.updated` | Badge settings change |
| `repository.badge_token.rotated` | Badge token rotation |
| `coverage_debt.created` | Debt creation |
| `coverage_debt.expired` | Debt expiration |

## Open Questions

- Should UI-managed config be allowed to override repo `vericov.yml`, or only supplement it?
- Should memberships mirror Git provider teams automatically in v1?
- Should badges eventually move behind CDN edge rendering once traffic patterns are known?
