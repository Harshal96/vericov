# Coverage Risk Scoring and Prioritization L2 Design

Date: 2026-05-25
Status: Proposed
Owner: Coverage Analysis service, Organization service

## Current State

The docs and PRD describe risk-aware coverage prioritization, but implementation has no `risk_score`, `risk_level`, CODEOWNERS mapping, package graph, component rollups, or ranked "fix these first" endpoint. Existing gate evaluation only handles active `project_coverage` gates, and PR diff coverage calculates coverage deltas without ranking gaps.

## Goals

- Assign every coverage gap finding a deterministic numeric risk score and risk level.
- Rank gaps by risk-adjusted value, not raw uncovered line count.
- Make scoring explainable through explicit factor contributions.
- Support policy and component overrides without requiring code changes.
- Provide a ranked endpoint for dashboards, PR comments, and later agent task selection.
- Keep v1 scoring metadata-only and reproducible.

## Non-Goals

- Do not claim security vulnerability severity; this is coverage risk, not SAST.
- Do not use an LLM for default scoring.
- Do not require incident/deployment/mutation/test-quality inputs in the first implementation.
- Do not hide low-risk findings; rank and filter them.

## Service Ownership

- Coverage Analysis computes and persists score, level, and factor contributions on `coverage_gap_findings`.
- Organization service owns policy/config inputs and public ranked reads.
- Component context provides criticality, owners, and package metadata.
- Agent Runner consumes ranked gaps when creating `explain_gap` and `generate_tests` tasks.

## Data Model

Risk scoring does not require a standalone risk table in v1. It extends `coverage_gap_findings` with required `risk_score`, `risk_level`, and score evidence inside `evidence_json`. Component-level aggregates are stored in `component_coverage_rollups` with `risk_score_total`, highest active risk level, active finding count, and debt-suppressed finding count.

If score configuration is persisted separately from repository config, use `repository_policies` with `policy_type = coverage`, `target_type` of repository/component/path, and a `config_json.risk` object. This keeps risk policy in the existing policy surface instead of creating a parallel rules engine.

## Score Model

Use a 0 to 100 score. Clamp final score to the range and store one decimal place.

Initial factors:

| Factor | Range | Source | Notes |
| --- | --- | --- | --- |
| Change exposure | 0-25 | PR diff | Added/lost coverage lines score higher than existing uncovered lines |
| Component criticality | 0-20 | components/policy | `critical` 20, `high` 14, `medium` 8, `low` 3 |
| Coverage severity | 0-15 | line hits/file summary | Zero-hit changed line, zero-covered file, branch/function gap |
| Ownership signal | 0-10 | owner context | Owned code scores higher than unowned; unowned adds triage risk |
| Blast radius | 0-10 | package graph metadata | Shared library or many dependents scores higher |
| Historical trend | 0-10 | coverage reports | Recent regression or repeated gap increases score |
| Debt state | -20 to 15 | coverage debt | Active debt reduces score; expired debt increases score |
| Policy override | -50 to 50 | policies/config | Explicit path/component boost or dampening |

Risk levels:

- `critical`: score >= 85
- `high`: score >= 65 and < 85
- `medium`: score >= 35 and < 65
- `low`: score < 35

Default next action by level:

- `critical` or `high`: `add_test` unless policy requires `run_source_explain`.
- `medium`: `add_test` for changed code, otherwise `create_debt` or `run_source_explain`.
- `low`: `create_debt`, `mark_generated`, or `inspect_instrumentation` based on reason code.

## Factor Evidence

Persist factor contributions in `coverage_gap_findings.evidence_json`:

```json
{
  "score": {
    "schema_version": 1,
    "total": 72.5,
    "level": "high",
    "factors": [
      {"name": "change_exposure", "value": 25, "reason": "new_uncovered_changed_line"},
      {"name": "component_criticality", "value": 14, "reason": "component_high"},
      {"name": "debt_state", "value": 0, "reason": "no_matching_debt"}
    ]
  }
}
```

This payload is part of explainability. UI and PR comments can show the top two factors without recomputing.

## Policy Overrides

Repository policy/config can set:

```yaml
risk:
  path_overrides:
    - pattern: "services/payments/**"
      criticality: critical
      score_boost: 15
    - pattern: "docs/**"
      score_dampening: 20
  component_overrides:
    - component: "auth"
      criticality: critical
  rank_comments:
    max_items: 5
    min_level: high
```

Validation rules:

- Score boost/dampening must be between 0 and 50.
- Path patterns must be repository-relative.
- Component overrides must reference active components.
- Policy overrides are evidence, not hidden behavior.

## APIs and Events

Public Control Plane endpoint:

- `GET /api/v1/orgs/{org_id}/repositories/{repository_id}/coverage-gaps`

Ranking defaults:

1. Unsuppressed active findings before debt-suppressed findings.
2. Higher risk score.
3. PR changed files before unchanged files.
4. Higher component criticality.
5. Older first seen date for tie-breaking.
6. File path and line number for stable output.

Additional endpoint for focused agent selection:

- `GET /api/v1/orgs/{org_id}/repositories/{repository_id}/coverage-gaps/fix-first`

`fix-first` returns a short list of non-overlapping targets that are likely to be small, high-impact test additions. It excludes findings suppressed by active debt, findings blocked by policy, and findings requiring source inspection unless caller requests `include_source_required=true`.

Events:

- `coverage.risk.scored`: emitted after findings receive risk scores for a report.
- `coverage.risk.policy_changed`: emitted when risk policy changes and historical findings may need reprocessing.

## Processing Flow

1. Gap extraction creates source-free findings with reason code and evidence.
2. Risk scorer loads score config from the context snapshot.
3. It evaluates factors independently and records each contribution.
4. Final score and level are persisted on the finding row.
5. Component rollups aggregate `risk_score_total`, highest risk level, active finding count, and debt-suppressed count.
6. Gate evaluation can use risk findings for `agent_review_required` or future `risk` metric gates.
7. Organization API returns ranked findings without recalculating risk.

## Privacy and Security

- Scoring inputs must be metadata only in SaaS mode.
- Policy overrides should not expose private path patterns outside repository members.
- Do not infer sensitive business meaning from path names beyond configured policy/component metadata.
- Keep factor names and scoring versions stable for auditability.

## Tests

- Unit tests for score factor calculations and clamping.
- Boundary tests for risk levels at 35, 65, and 85.
- Policy validation tests for boosts, dampening, invalid components, and path pattern safety.
- Ranking tests for active vs debt-suppressed, equal scores, and deterministic tie-breaks.
- Integration test proving the same report/context snapshot recomputes identical scores.
- API tests for `min_risk`, `owner`, `component_id`, `status`, and `fix-first` filtering.

## Rollout Order

1. Add score factor domain and deterministic scoring service.
2. Persist `risk_score`, `risk_level`, and score evidence on findings.
3. Add ranked Control Plane reads.
4. Add component rollup risk aggregates.
5. Add policy override validation and config merge.
6. Feed high-risk findings into gate details and agent handoff.

## Open Follow-up Questions

- Should unowned code increase risk by default, or should it reduce owner-specific prioritization?
- Which package graph signal is reliable enough for v1: direct dependents, package type, or manually configured blast radius?
- Should teams be able to customize risk level thresholds per repository, or only factor weights?
