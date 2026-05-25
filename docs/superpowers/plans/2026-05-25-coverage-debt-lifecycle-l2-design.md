# Coverage Debt Lifecycle L2 Design

Date: 2026-05-25
Status: Proposed
Owner: Organization service

## Current State

The Control Plane docs mention `coverage_debt_items` and public coverage debt endpoints, but the actual Supabase SQL has no table, the Organization service has no debt models, and Coverage Analysis does not apply debt to findings or gates. There is no expiry flow, no audit trail for debt decisions, and no way to distinguish intentionally deferred low-risk gaps from accidental uncovered code.

## Goals

- Add a durable, tenant-scoped coverage debt model.
- Let authorized users create, update, resolve, revoke, and list debt items.
- Support line, range, file, function, branch, and component debt targets.
- Require owner, reason, risk level, and expiration or review date.
- Link debt to source findings, reports, commits, PRs, and optional external issues.
- Make debt lifecycle visible for dashboards, trends, and gate evaluation.

## Non-Goals

- Do not let debt permanently hide findings.
- Do not allow debt to rewrite raw coverage metrics.
- Do not implement PR slash commands in this L2; they can call the same APIs later.
- Do not require issue tracker integration in v1; `linked_issue_url` is a string field.

## Service Ownership

- Organization service owns debt CRUD, validation, authorization, and public APIs.
- Coverage Analysis owns debt matching during report processing and gate evaluation.
- Git Integration may later create debt from slash commands, but only through Control Plane APIs.
- Audit/event systems consume debt events emitted by Control Plane.

## Data Model

Add `vericov.coverage_debt_items`:

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `org_id` | uuid | Organization boundary |
| `repository_id` | uuid | FK to repositories |
| `component_id` | uuid | Nullable FK to components |
| `source_gap_id` | uuid | Nullable FK to coverage gap finding |
| `source_report_id` | uuid | Nullable FK to coverage report |
| `source_commit_sha` | text | Commit where debt was created |
| `pull_request_number` | integer | Nullable source PR |
| `target_type` | text | `line`, `range`, `file`, `function`, `branch`, `component` |
| `file_path` | text | Nullable only for component debt |
| `line_start` | integer | Required for line/range debt |
| `line_end` | integer | Required for range debt |
| `symbol_name` | text | Optional function/branch label |
| `risk_level` | text | `critical`, `high`, `medium`, `low` |
| `reason` | text | Required human explanation |
| `owner` | text | Required owner key |
| `status` | text | `active`, `resolved`, `expired`, `revoked` |
| `expires_at` | timestamptz | Required review deadline |
| `resolved_at` | timestamptz | Nullable |
| `resolved_by_user_id` | uuid | Nullable |
| `revoked_at` | timestamptz | Nullable |
| `revoked_by_user_id` | uuid | Nullable |
| `linked_issue_url` | text | Nullable |
| `metadata_json` | jsonb | Extra context such as policy references |
| `created_by_user_id` | uuid | Supabase user ID |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

Add `vericov.coverage_debt_events`:

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `org_id` | uuid | Organization boundary |
| `repository_id` | uuid | FK to repositories |
| `debt_item_id` | uuid | FK to coverage debt |
| `event_type` | text | `created`, `updated`, `resolved`, `expired`, `revoked`, `matched_gap` |
| `actor_user_id` | uuid | Nullable for system expiry/matching |
| `payload_json` | jsonb | Change summary and evidence |
| `created_at` | timestamptz | Event time |

## Validation Rules

- `reason` must be 10 to 2000 trimmed characters.
- `owner` must be a non-empty owner key and should match resolved owners when a source gap exists.
- `expires_at` is required and must be in the future at creation.
- `critical` debt is rejected unless repository policy explicitly allows critical-path debt.
- `line_start` and `line_end` must be positive and ordered for line/range targets.
- `file_path` must be repository-relative for file, line, range, function, and branch debt.
- Component debt must reference an active component.
- Resolved and revoked debt never suppresses findings.

## APIs and Events

Public Control Plane endpoints:

- `GET /api/v1/orgs/{org_id}/repositories/{repository_id}/coverage-debt`
- `POST /api/v1/orgs/{org_id}/repositories/{repository_id}/coverage-debt`
- `GET /api/v1/orgs/{org_id}/repositories/{repository_id}/coverage-debt/{debt_id}`
- `PATCH /api/v1/orgs/{org_id}/repositories/{repository_id}/coverage-debt/{debt_id}`
- `POST /api/v1/orgs/{org_id}/repositories/{repository_id}/coverage-debt/{debt_id}/resolve`
- `POST /api/v1/orgs/{org_id}/repositories/{repository_id}/coverage-debt/{debt_id}/revoke`

List filters:

- `status`
- `owner`
- `risk_level`
- `component_id`
- `expires_before`
- `include_expired=true|false`
- `source_gap_id`
- `limit=1..500`

Create request:

```json
{
  "source_gap_id": "uuid",
  "target_type": "range",
  "file_path": "services/payments/discounts.ts",
  "line_start": 88,
  "line_end": 94,
  "risk_level": "low",
  "reason": "Defensive fallback is accepted until the integration test suite is added.",
  "owner": "team-payments",
  "expires_at": "2026-08-31T00:00:00Z",
  "linked_issue_url": "https://tracker.example/PROJ-123"
}
```

Events:

- `coverage_debt.created`
- `coverage_debt.updated`
- `coverage_debt.resolved`
- `coverage_debt.revoked`
- `coverage_debt.expired`
- `coverage_debt.matched_gap`

## Expiry Flow

Debt expiration is evaluated in two places:

1. Read-time status normalization in Organization service: active debt with `expires_at <= now` is returned as expired in list/detail responses and can be persisted by a scheduled job later.
2. Analysis-time matching in Coverage Analysis: expired debt is ignored for suppression and included as `expired_debt_reappeared` evidence.

The first implementation can update expired statuses opportunistically during debt list/detail and during Coverage Analysis context load. A scheduled daily job can be added later for event emission consistency.

## Processing Flow

1. User creates debt from a gap, dashboard, API, or later PR command.
2. Organization service validates authorization, target shape, owner, risk level, and policy restrictions.
3. Debt row and `coverage_debt.created` event are stored transactionally.
4. Coverage Analysis loads active and recently expired debt in the repository context snapshot.
5. Matching active debt suppresses matching findings and is recorded in finding/gate evidence.
6. Matching expired debt does not suppress and is recorded as reappearance evidence.
7. User resolves debt when tests are added or revokes it when the deferral is invalid.

## Privacy and Security

- Debt reasons may contain sensitive project context; restrict reads to repository members.
- Reject absolute paths, parent traversal, and external file references.
- Do not allow anonymous badge/token access to reveal debt counts unless a future badge setting explicitly enables it.
- Store linked issue URLs as plain text but never fetch them from the API path.

## Tests

- Organization service unit tests for create/update/resolve/revoke validation.
- Authorization tests for admin/developer/viewer behavior once role policy is selected.
- JDBC tests for status constraints, FK scope, and event creation.
- Expiry tests for active to expired read normalization.
- Coverage Analysis tests for active debt suppression and expired debt reappearance.
- API tests for list filters and response shape.

## Rollout Order

1. Add schema, repository port, domain models, and API request/response models.
2. Implement create/list/detail/update/resolve/revoke APIs.
3. Emit debt lifecycle events.
4. Add debt to internal coverage context response.
5. Add debt matching in Coverage Analysis.
6. Add dashboard/debt rollup reads.

## Open Follow-up Questions

- Which roles may create debt: admin only, developer and above, or owner-configurable?
- Should debt owner be restricted to resolved CODEOWNERS/component owners?
- Should active debt require approval when risk is `high`, even if `critical` is blocked?
