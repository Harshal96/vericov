# Coverage Gap Extraction and Reasoning L2 Design

Date: 2026-05-25
Status: Implemented
Owner: Coverage Analysis service

## Current State

Coverage Analysis stores report totals, file summaries, line hits, and PR diff coverage rows. It can identify newly missed and lost coverage lines in PR diffs, but those diagnostics are not promoted into durable coverage gap findings. There is no service that classifies why a line or range is uncovered, no explanation model, no evidence links, no next best action, and no public ranked gap read API.

## Goals

- Persist actionable coverage gap findings derived from coverage maps and PR diff rows.
- Classify gaps with deterministic reason codes before any source-bearing or LLM reasoning.
- Link each explanation to evidence IDs and metadata that already exist in Vericov.
- Provide a clear next best action for developers and future agent workflows.
- Keep the model useful in metadata-only SaaS mode where source text is unavailable.
- Support later branch/function/mutation/test-quality explanations without changing the top-level API shape.

## Non-Goals

- Do not infer semantic source behavior in SaaS without an explicit source-bearing runner task.
- Do not persist diff line text or source snippets in gap findings.
- Do not replace raw coverage reports, PR diff rows, or gate evaluations.
- Do not generate tests directly from this subsystem; it only prepares evidence and targets.

## Service Ownership

- Coverage Analysis owns extraction, deterministic classification, persistence, and report-time gap status.
- Organization service owns public reads, user permissions, filtering, and dashboard presentation.
- Agent Runner owns source-aware `explain_gap` and `generate_tests` enrichment when policy allows source-bearing work.
- Git Integration continues to provide diff metadata without deciding whether a gap matters.

## Data Model

Add `vericov.coverage_gap_findings`:

| Column | Type | Notes |
| --- | --- | --- |
| `id` | uuid | Primary key |
| `tenant_id` | uuid | Tenant boundary |
| `org_id` | uuid | Organization boundary |
| `repository_id` | uuid | FK to repositories |
| `coverage_report_id` | uuid | FK to coverage reports |
| `pr_diff_id` | uuid | Nullable FK to PR diff |
| `component_id` | uuid | Nullable FK to components |
| `commit_sha` | text | Head/report commit |
| `pull_request_number` | integer | Nullable |
| `file_path` | text | Repository-relative path |
| `target_type` | text | `line`, `range`, `file`, `function`, `branch`, `component` |
| `line_start` | integer | Nullable for non-line targets |
| `line_end` | integer | Nullable for non-line targets |
| `symbol_name` | text | Nullable function/method/branch label |
| `reason_code` | text | Deterministic classification |
| `explanation` | text | Human-readable source-free explanation |
| `confidence` | text | `high`, `medium`, `low` |
| `risk_score` | numeric | Risk score from risk subsystem |
| `risk_level` | text | `critical`, `high`, `medium`, `low` |
| `owners` | text[] | Resolved owners |
| `next_action` | text | `add_test`, `create_debt`, `mark_generated`, `inspect_instrumentation`, `run_source_explain` |
| `status` | text | `active`, `debt_suppressed`, `resolved`, `obsolete` |
| `evidence_json` | jsonb | IDs, counters, context version, policy/debt references |
| `created_at` | timestamptz | Created time |
| `updated_at` | timestamptz | Updated time |

Finding identity should be stable enough for reprocessing:

```text
repository_id + coverage_report_id + file_path + target_type + line_start + line_end + reason_code
```

Across reports, UI should correlate findings by:

```text
repository_id + file_path + target_type + line_start + line_end + symbol_name
```

## Reason Codes

Initial deterministic codes:

- `new_uncovered_changed_line`: added executable PR line has zero head hits.
- `lost_existing_coverage`: context line had base hits and now has zero head hits.
- `uncovered_executable_line`: executable line in the report has zero hits outside PR-specific classification.
- `file_has_no_executable_coverage`: file summary has executable lines but zero covered lines.
- `base_coverage_missing`: PR diff exists but base report is unavailable.
- `path_not_in_report`: provider diff path has no matching coverage line hits or file summary.
- `possible_path_mismatch`: same basename or normalized path hints suggest instrumentation path mismatch.
- `generated_or_ignored_candidate`: path matches generated/ignored policy but lacks explicit ignore/debt.
- `debt_suppressed`: active unexpired debt matched this gap.
- `expired_debt_reappeared`: matching debt exists but no longer suppresses the gap.

Confidence defaults:

- `high`: direct line-hit and diff evidence.
- `medium`: inferred from file summary or path mismatch hints.
- `low`: generated/ignored/dead-code candidates without source inspection.

## Explanation Shape

Every finding gets a short explanation assembled from reason code, metric, path, owner, component, and evidence. Examples:

- "Added executable line 88 is uncovered in the head report."
- "Line 95 was covered in the base report but has zero hits in the head report."
- "This changed file has provider diff lines but no matching coverage records; inspect path normalization or instrumentation."
- "Matching active debt suppresses this gap until 2026-08-31."

Explanations must avoid certainty when evidence is incomplete. Use "may" or "possible" for path mismatch, generated code, or unreachable candidates.

## Evidence JSON

`evidence_json` should include stable references, not source text:

```json
{
  "schema_version": 1,
  "coverage_report_id": "uuid",
  "pr_diff_id": "uuid",
  "diff_line_id": "uuid",
  "base_sha": "base123",
  "head_sha": "head456",
  "head_hits": 0,
  "base_hits": 4,
  "component_id": "uuid",
  "context_version": "ctx-2026-05-25T10:00:00Z",
  "policy_ids": ["uuid"],
  "debt_item_ids": ["uuid"]
}
```

## APIs and Events

Public Control Plane endpoints:

- `GET /api/v1/orgs/{org_id}/repositories/{repository_id}/coverage-gaps`
- `GET /api/v1/orgs/{org_id}/repositories/{repository_id}/coverage-gaps/{gap_id}`

Filters:

- `commit_sha`
- `pull_request_number`
- `component_id`
- `owner`
- `risk_level`
- `status`
- `reason_code`
- `include_debt=true|false`
- `limit=1..500`

Response items include finding identity, path target, reason code, explanation, confidence, risk score, risk level, owners, component, next action, status, evidence links, and timestamps.

Events:

- `coverage.gaps.extracted`: emitted after findings are persisted for a report.
- `coverage.gaps.changed`: emitted when reprocessing changes finding status or risk.
- `coverage.gaps.source_enriched`: emitted later when a runner returns source-aware explanation output.

## Processing Flow

1. Coverage Analysis saves a complete report and PR diff coverage when available.
2. `GapExtractor` reads report line hits, file summaries, PR diff files/lines, component context, policies, and active/expired debt.
3. It creates candidate findings from PR newly missed lines, lost coverage lines, uncovered report lines, zero-covered files, and path mismatch signals.
4. `GapReasoner` assigns reason codes, source-free explanation, confidence, evidence JSON, and next action.
5. `DebtMatcher` marks matching active debt as `debt_suppressed` and matching expired debt as evidence only.
6. `RiskScorer` assigns score and level.
7. Findings are persisted after report projections and before gate details are finalized.
8. Organization service reads findings for PR reports, dashboards, and later agent-run creation.

## Privacy and Security

- Persist paths, line numbers, hit counts, IDs, and policy/debt metadata only.
- Source-bearing enrichment must create an Agent Runner task and respect repository privacy mode.
- Validate public filters so users cannot infer repository data outside their org/repository membership.
- Keep evidence JSON bounded and schema-versioned to prevent unreviewable payload growth.

## Tests

- Unit tests for each initial reason code.
- Tests for grouping adjacent uncovered lines into range findings when lines share reason, owner, component, and next action.
- Tests for confidence assignment and source-free explanation wording.
- JDBC tests for finding persistence, status updates, and evidence JSON schema constraints.
- Coverage Analysis integration test for a PR with newly missed, lost coverage, path mismatch, active debt, and expired debt.
- Organization API tests for filters, authorization, pagination, and hidden source text.

## Rollout Order

1. Add finding domain model and reason-code catalog.
2. Persist PR diff-derived findings only.
3. Add uncovered whole-report line/file findings.
4. Add path mismatch and generated/ignored candidates.
5. Add public read APIs.
6. Add source-aware runner enrichment in a separate L2/L3 implementation.

## Open Follow-up Questions

- Should adjacent lines always be grouped into ranges, or should PR inline annotations preserve one finding per line?
- Which reason codes should be considered blocking evidence for `agent_review_required` gates in the first gate integration?
- Should obsolete findings be retained forever for trend analysis or compacted with old report retention?
