# Coverage Analysis Service Contract

Status: Draft for review
Runtime: Helidon 4 on Java 25+
Internal base path: `/internal/v1/coverage-analysis`
OpenAPI: `/openapi`

## Purpose

The Coverage Analysis Service is an internal worker-facing service. It parses uploaded reports, normalizes coverage, merges shards, computes commit and PR diffs, evaluates gates, stores summary metrics, and publishes report-ready events.

This service has no public business API in v1. Public report reads are served by the API / Control Plane Service.

The first implementation milestone is queue-driven: the service consumes `upload.received` events from Supabase Postgres via PGMQ, claims the matching `analysis_jobs` row, downloads coverage artifacts from Supabase Storage, merges shard coverage, stores a gzip-compressed normalized coverage map, persists project/file summaries and per-line hit maps, evaluates active project coverage gates, computes PR diff coverage when the upload belongs to a pull request, and archives or reschedules the queue message. The HTTP surface below remains the intended internal control API, but worker execution does not require another service to call an HTTP `complete` endpoint.

Initial parser support:

- LCOV line coverage from `DA`
- LCOV branch coverage from `BRDA`
- LCOV function coverage from `FN` and `FNDA`
- Cobertura XML line coverage from `class/lines/line`
- Cobertura XML branch coverage from `condition-coverage`
- Cobertura XML function coverage from `method` entries
- coverage.py XML through the Cobertura-compatible parser
- JaCoCo XML line and branch coverage from `sourcefile/line`
- JaCoCo XML function coverage from `class/method` counters
- Clover XML line coverage from `file/line`
- Clover XML branch coverage from `type="cond"` true/false counts
- Clover XML function coverage from `type="method"` lines
- Go cover profile line and statement coverage from block records
- gcov and llvm-cov gcov text line, branch, and function coverage

Statement coverage is represented independently when the source format exposes statement counts. LCOV, Cobertura, Clover, and gcov mirror statement coverage from executable line records; Go profiles use block statement counts.

Remaining parser families from the PRD are Istanbul/nyc JSON, coverage.py JSON, and the generic JSON adapter. Those require a stable JSON canonical import contract and are tracked separately from the XML/text parser expansion.

Initial gate evaluator support:

- `project_coverage` gates for `line`, `branch`, `function`, and `statement`
- Blocking threshold miss -> `failed`
- Non-blocking threshold miss -> `warning`
- Disabled gates and gate types requiring missing inputs are skipped by report processing

## PR Diff Coverage Flow

For pull request uploads, the worker asks Git Integration for the true provider diff between the stored PR base SHA and uploaded head SHA. It then compares that diff against the latest persisted base and head line-hit maps.

Diff coverage persists:

- patch line coverage for added executable lines in the provider diff
- newly missed lines, which are added executable lines with zero head hits
- lost coverage lines, which are unchanged diff-context lines that had base hits and now have zero head hits
- per-file diff rollups and optional per-line diagnostic rows for UI drill-down

If the matching base coverage report is not available, the PR diff record is stored with `base_coverage_missing` so the UI can distinguish missing historical data from a clean diff.

## Event Handling

| Queue | Purpose |
| --- | --- |
| `coverage_analysis_jobs` | Durable `upload.received` messages waiting for coverage analysis |
| `coverage_analysis_dead_letters` | Messages that are unsupported or exhausted retries |

### UploadReceivedEvent

```json
{
  "schema_version": 1,
  "event_type": "upload.received",
  "upload_id": "84cb6d3c-312f-43af-a3fc-5964b2d27626",
  "analysis_job_id": "fb0e1e5d-55d7-4f74-9303-7a93400d53a1",
  "tenant_id": "0f4f478a-3fc0-45c4-b274-43a0e18850cf",
  "repository_id": "4d607f16-1af7-4d3b-ac38-06454cba463c",
  "commit_sha": "abc123"
}
```

### Worker Outcomes

| Condition | Action |
| --- | --- |
| Job claim succeeds | Run coverage processor, mark job succeeded, archive PGMQ message |
| Job is already terminal | Archive PGMQ message without processing |
| Job is locked by another worker | Reschedule PGMQ message |
| Processor fails with attempts remaining | Record failed attempt and reschedule PGMQ message |
| Processor fails after max attempts | Record failed attempt, send dead-letter payload, archive original |
| Unsupported schema or event type | Send dead-letter payload, archive original |

## Internal Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/internal/v1/coverage-analysis/jobs/{job_id}/lease` | Lease an analysis job |
| `POST` | `/internal/v1/coverage-analysis/jobs/{job_id}/complete` | Complete an analysis job |
| `POST` | `/internal/v1/coverage-analysis/jobs/{job_id}/fail` | Mark analysis job failed |
| `POST` | `/internal/v1/coverage-analysis/repositories/{repository_id}/commits/{sha}/analyze` | Trigger commit analysis |
| `POST` | `/internal/v1/coverage-analysis/repositories/{repository_id}/pull-requests/{number}/analyze` | Trigger PR diff analysis |
| `POST` | `/internal/v1/coverage-analysis/gates/evaluate` | Evaluate gates for commit/PR |
| `GET` | `/internal/v1/coverage-analysis/reports/{report_id}` | Get internal report model |

## Request Models

### TriggerCommitAnalysisRequest

```json
{
  "repository_id": "4d607f16-1af7-4d3b-ac38-06454cba463c",
  "commit_sha": "abc123",
  "upload_ids": [
    "84cb6d3c-312f-43af-a3fc-5964b2d27626"
  ],
  "analysis_modes": ["coverage", "test_results", "gates"],
  "reason": "upload_received"
}
```

### TriggerPullRequestAnalysisRequest

```json
{
  "repository_id": "4d607f16-1af7-4d3b-ac38-06454cba463c",
  "pull_request_number": 42,
  "base_sha": "base123",
  "head_sha": "head456",
  "analysis_modes": ["diff", "gates", "risk"]
}
```

### EvaluateGatesRequest

```json
{
  "repository_id": "4d607f16-1af7-4d3b-ac38-06454cba463c",
  "commit_sha": "head456",
  "pull_request_number": 42,
  "config_version_id": "f604e2ca-86ff-4917-8b93-77024d8c7d34",
  "metrics": {
    "project": {
      "line": 84.2,
      "branch": 71.4,
      "function": 88.1,
      "statement": 83.9
    },
    "patch": {
      "line": 76.5,
      "branch": 62.0
    }
  }
}
```

### CompleteAnalysisJobRequest

```json
{
  "status": "completed",
  "coverage_report_id": "a2e66c26-0330-4682-841e-15c240cf92f0",
  "gate_evaluation_id": "f42336ec-1855-465a-99d8-bc76f60eec63",
  "warnings": []
}
```

## Response Models

### AnalysisJobLeaseResponse

```json
{
  "data": {
    "job_id": "fb0e1e5d-55d7-4f74-9303-7a93400d53a1",
    "lease_id": "lease-123",
    "lease_expires_at": "2026-05-22T10:05:00Z",
    "payload": {
      "repository_id": "4d607f16-1af7-4d3b-ac38-06454cba463c",
      "commit_sha": "abc123",
      "upload_ids": ["84cb6d3c-312f-43af-a3fc-5964b2d27626"]
    }
  }
}
```

### CoverageReportInternalResponse

```json
{
  "data": {
    "id": "a2e66c26-0330-4682-841e-15c240cf92f0",
    "repository_id": "4d607f16-1af7-4d3b-ac38-06454cba463c",
    "commit_sha": "head456",
    "coverage": {
      "line": {
        "covered": 8420,
        "total": 10000,
        "percentage": 84.2
      },
      "branch": {
        "covered": 714,
        "total": 1000,
        "percentage": 71.4
      }
    },
    "storage_path": "tenant/repo/head456/coverage-map.zst"
  }
}
```

### GateEvaluationResponse

```json
{
  "data": {
    "id": "f42336ec-1855-465a-99d8-bc76f60eec63",
    "status": "failed",
    "results": [
      {
        "gate_id": "patch-line",
        "status": "failed",
        "metric": "line",
        "scope": "patch",
        "actual": 76.5,
        "expected": 80
      }
    ]
  }
}
```

## Database Models

### `coverage_reports`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `repository_id` | uuid | FK to repositories |
| `commit_sha` | text | Git commit |
| `branch` | text | Branch |
| `pull_request_number` | integer | Nullable |
| `status` | text | `processing`, `complete`, `failed` |
| `line_covered` | integer | Covered line count |
| `line_total` | integer | Total line count |
| `branch_covered` | integer | Covered branch count |
| `branch_total` | integer | Total branch count |
| `function_covered` | integer | Covered function count |
| `function_total` | integer | Total function count |
| `statement_covered` | integer | Covered statement count |
| `statement_total` | integer | Total statement count |
| `normalized_storage_bucket` | text | Supabase Storage bucket for normalized coverage map |
| `normalized_storage_path` | text | Supabase Storage object for normalized coverage map |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

### `analysis_jobs`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `upload_id` | uuid | Unique FK to uploads |
| `tenant_id` | uuid | Tenant boundary |
| `repository_id` | uuid | FK to repositories |
| `commit_sha` | text | Git commit |
| `status` | text | `queued`, `running`, `succeeded`, `failed`, `cancelled` |
| `priority` | integer | Lower number means higher priority |
| `queue_name` | text | PGMQ queue, default `coverage_analysis_jobs` |
| `queue_message_id` | bigint | Last PGMQ message id |
| `attempts` | integer | Number of processing attempts |
| `max_attempts` | integer | Retry ceiling before dead-letter |
| `run_after` | timestamptz | Earliest retry time |
| `queued_at` | timestamptz | Last enqueue time |
| `locked_by` | text | Worker id that claimed the job |
| `locked_at` | timestamptz | Claim timestamp |
| `lease_expires_at` | timestamptz | Claim expiry |
| `started_at` | timestamptz | First processing start |
| `finished_at` | timestamptz | Terminal timestamp |
| `last_error` | text | Latest processor error |
| `created_at` | timestamptz | Created time |

### `analysis_job_attempts`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `analysis_job_id` | uuid | FK to analysis_jobs |
| `worker_id` | text | Worker that handled the attempt |
| `attempt_number` | integer | Copied from job attempt count |
| `status` | text | `succeeded`, `failed` |
| `started_at` | timestamptz | Attempt start |
| `finished_at` | timestamptz | Attempt finish |
| `error_code` | text | Nullable machine error |
| `error_message` | text | Nullable human-readable error |
| `created_at` | timestamptz | Created time |

### `coverage_file_summaries`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `coverage_report_id` | uuid | FK to coverage_reports |
| `repository_id` | uuid | FK to repositories |
| `commit_sha` | text | Git commit |
| `file_path` | text | Repository-relative path |
| `component_id` | uuid | Nullable component |
| `package_name` | text | Nullable package |
| `line_covered` | integer | Covered lines |
| `line_total` | integer | Total lines |
| `branch_covered` | integer | Covered branches |
| `branch_total` | integer | Total branches |
| `function_covered` | integer | Covered functions |
| `function_total` | integer | Total functions |
| `statement_covered` | integer | Covered statements |
| `statement_total` | integer | Total statements |

### `coverage_line_hits`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `coverage_report_id` | uuid | FK to coverage_reports |
| `repository_id` | uuid | FK to repositories |
| `commit_sha` | text | Git commit |
| `file_path` | text | Repository-relative path |
| `line_number` | integer | Executable source line |
| `hits` | bigint | Execution hit count for the line |
| `created_at` | timestamptz | Created time |

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
| `lost_coverage_line_count` | integer | Context lines that lost all hits |
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
| `org_id` | uuid | Organization boundary |
| `repository_id` | uuid | FK to repositories |
| `coverage_report_id` | uuid | Nullable FK to coverage report |
| `commit_sha` | text | Evaluated commit |
| `branch` | text | Evaluated branch |
| `pull_request_number` | integer | Nullable |
| `gate_name` | text | Gate config name at evaluation time |
| `gate_type` | text | Gate type at evaluation time |
| `metric` | text | Metric at evaluation time |
| `threshold` | numeric | Configured threshold |
| `actual` | numeric | Evaluated actual metric, nullable |
| `status` | text | `passed`, `failed`, or `warning` |
| `blocking` | boolean | Whether a threshold miss blocks |
| `details_json` | jsonb | Evaluation details |
| `evaluated_at` | timestamptz | Evaluation time |

### `test_runs`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `repository_id` | uuid | FK to repositories |
| `commit_sha` | text | Git commit |
| `upload_id` | uuid | Source upload |
| `suite_name` | text | Test suite |
| `status` | text | `passed`, `failed`, `skipped`, `error` |
| `total_count` | integer | Total tests |
| `passed_count` | integer | Passed tests |
| `failed_count` | integer | Failed tests |
| `skipped_count` | integer | Skipped tests |
| `duration_ms` | bigint | Runtime |
| `created_at` | timestamptz | Created time |

### `flaky_test_findings`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `repository_id` | uuid | FK to repositories |
| `test_identity` | text | Stable test identity |
| `file_path` | text | Test file |
| `flake_score` | numeric | 0-1 score |
| `likely_cause` | text | `time`, `network`, `order`, `shared_state`, `unknown` |
| `status` | text | `active`, `quarantined`, `resolved`, `ignored` |
| `first_seen_at` | timestamptz | First observed |
| `last_seen_at` | timestamptz | Most recent |

## Events Published

| Event | Trigger |
| --- | --- |
| `coverage.report.completed` | Report computed |
| `coverage.gates.evaluated` | Gates evaluated |
| `coverage.pr_report.ready` | PR diff report ready |
| `test.flaky.detected` | Flaky test finding created |
| `analysis.job.failed` | Analysis failure |

## Open Questions

- Should full line-hit maps stay in Postgres long term, or should older reports be compacted into compressed Storage objects after the UI no longer needs low-latency drill-down?
- Should flake detection live here initially or move to a dedicated Test Intelligence service once volume grows?
- Which mutation testing result fields should be part of the initial gate model?
