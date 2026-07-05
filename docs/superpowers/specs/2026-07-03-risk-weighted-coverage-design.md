# Risk-Weighted Coverage Design

Date: 2026-07-03
Status: Draft

## Summary

Vericov already scores every coverage gap finding through
`CoverageRiskScorer`, but two of its most important signals are inert in the
public runtime: component criticality falls back to a `medium` default
because nothing supplies it, and the `historical_trend` factor is hardcoded
to zero with reason `no_trend_input`.

This design makes the score signal-rich while staying fully deterministic:

1. **Component criticality** becomes a repository-owned declaration in
   `.vericov.yml`, inherited through the component hierarchy like owners and
   gates, carried in the immutable configuration snapshot.
2. **Change frequency (churn)** becomes a new factor computed from the diff
   history Vericov already persists — how often a file appeared in pull
   request diffs over a trailing window.
3. **Historical trend** gets real input: the file's line-coverage trajectory
   across recent default-branch reports.

Every factor input is recorded in the finding's `evidence_json`, so every
score is explainable and auditable after the fact. The weighted scores flow
into the surfaces that already exist: `coverage_gap_findings`, the
`risk_score_total` and `highest_active_risk_level` rollup columns, and the
ranking of the gap manifest.

An uncovered getter and an uncovered payment-retry branch stop being worth
the same attention.

Churn depends on `2026-07-03-cli-supplied-pr-diff-coverage-design.md` for
its data source. Scores surface through
`2026-07-03-agent-coverage-query-interface-design.md` and order
`2026-07-03-coverage-gap-manifest-design.md`.

## Goals

- Let repositories declare which components matter most, in the same file
  that declares ownership and gates.
- Weight gaps in frequently changed files above gaps in dormant files, using
  only data Vericov already stores.
- Replace the stubbed trend factor with real per-file coverage trajectory.
- Keep scoring deterministic given the database state at analysis time, and
  make every score explainable from persisted evidence.
- Keep analysis-time cost bounded: a fixed number of additional queries per
  analysis, not per finding.

## Non-Goals

- LLM-judged risk, severity, or test-quality assessment.
- Mining external bug trackers, incident systems, or commit-message history.
- Parsing source code for cyclomatic or cognitive complexity. The analysis
  service sees coverage artifacts and diffs, never source, and that boundary
  stays.
- User-configurable factor weights or level thresholds. The factor model and
  the `critical ≥ 85 / high ≥ 65 / medium ≥ 35` levels stay fixed in this
  release.
- Repository- or component-level trend analytics and anomaly detection.
  This design scores individual findings; trend dashboards are a follow-up
  once retention policy exists.
- Rescoring historical reports.

## Current State

- `CoverageRiskScorer` runs on every analysis inside `CoverageGapExtractor`
  and sums eight factor contributions: `change_exposure`,
  `component_criticality`, `coverage_severity`, `ownership_signal`,
  `blast_radius`, `historical_trend`, `debt_state`, and `policy_override`,
  normalized to 0–100 with fixed level thresholds.
- `component_criticality` already maps `critical`/`high`/`medium`/`low`
  strings to contributions of 20/14/8/3, but the candidate's criticality
  comes from `RepositoryContext`, which is empty in the public runtime, so
  every component scores as `medium`.
- `historical_trend` always contributes 0 with reason `no_trend_input`.
- Findings persist `risk_score`, `risk_level`, and `evidence_json`;
  `component_coverage_rollups` aggregates `gap_count`, `risk_score_total`,
  and `highest_active_risk_level`.
- The monorepo component design established the `.vericov.yml` snapshot
  pipeline: CLI validation, canonical JSON, `config_sha256`, upload-time
  revalidation, and analysis-time verification. Criticality rides that
  pipeline unchanged.
- `pull_request_coverage_diff_files` (once the diff design lands) records
  which files each PR upload touched — a change-frequency record that needs
  no Git access.

## Component Criticality

### Configuration Contract

Components in `.vericov.yml` gain one optional field:

```yaml
components:
  - key: commerce
    criticality: high
    components:
      - key: payments
        criticality: critical
        components:
          - key: payments-api
            paths:
              - services/payments/api/**
```

| Rule | Behavior |
| --- | --- |
| Values | `critical`, `high`, `medium`, `low` |
| Omitted | Inherits the nearest ancestor's effective criticality |
| No ancestor value | Effective criticality is `medium` (the scorer's existing default) |
| `unassigned` | Always `medium`; cannot be configured |
| Unknown value | Validation error naming the field path |

Inheritance is nearest-ancestor override, identical to owners. The field is
part of the normalized immutable snapshot: it appears in canonical JSON,
changes `config_sha256`, and therefore creates a distinct upload identity —
consistent with every other snapshot field. The CLI and Upload Service
validate it with the shared rules; Coverage Analysis reads it from the
verified snapshot.

### Flow Into Scoring

During component assignment, each file's leaf component resolves an
effective criticality from the snapshot. Gap candidates for that file carry
it into the existing `componentCriticality` input of the scorer — no scorer
changes needed for this signal. Files in `unassigned`, and repositories
without components, keep the `medium` default.

### Surfacing

`component_coverage_rollups` gains an `effective_criticality` column so the
component tree responses in the report and query APIs can display it
alongside owners and gates.

## Churn Factor

A new `change_frequency` factor replaces nothing; it is added to the sum.

- **Input:** the number of distinct pull request diffs that touched the
  file's path in the trailing 90 days, counted from
  `pull_request_coverage_diff_files` joined to its parent diff rows for the
  repository. Renames count under the path they carried at the time.
- **Contribution:**

| Distinct diffs in 90 days | Contribution | Reason |
| --- | --- | --- |
| 0 (or no diff history) | 0 | `no_churn_history` |
| 1–2 | 3 | `occasional_change` |
| 3–9 | 8 | `frequent_change` |
| 10+ | 12 | `hotspot` |

Rationale: a gap in a file changed ten times a quarter is far more likely to
be executed, extended, and broken than one in a file untouched for a year.
The cap of 12 keeps churn influential but below `change_exposure` (max 25),
so "uncovered and just changed in this PR" still dominates "uncovered and
often changed."

Repositories that never upload diffs simply score 0 with
`no_churn_history` — the factor degrades to today's behavior.

## Historical Trend Factor

The stubbed `historical_trend` factor gets real input.

- **Input:** the file's line-coverage percentage across the most recent 10
  `complete` reports on the repository's `default_branch` (from
  `coverage_file_summaries` joined to `coverage_reports`), oldest to newest,
  requiring at least 3 data points.
- **Signal:** the difference in percentage points between the file's median
  coverage over the older half of the window and its value in the newest
  default-branch report. A median baseline resists single-report noise.
- **Contribution:**

| Decline (percentage points) | Contribution | Reason |
| --- | --- | --- |
| < 2 or insufficient data | 0 | `no_trend_input` / `stable_coverage` |
| 2–5 | 4 | `declining_file_coverage` |
| 5–15 | 7 | `declining_file_coverage` |
| > 15 | 10 | `eroding_file_coverage` |

Improving coverage contributes 0; the factor only penalizes erosion. New
files without history score 0 with `no_trend_input`, preserving the current
reason string.

## Determinism And Auditability

History-dependent factors make the score a function of the database at
analysis time: re-analyzing the same artifacts a month later may yield a
different churn count. That is intended — risk is a property of the
repository's present, not of the artifact — but it must be auditable.

Every finding's `evidence_json` gains a `risk_inputs` object recording what
each history factor saw:

```json
{
  "risk_inputs": {
    "change_frequency": { "window_days": 90, "distinct_diffs": 11 },
    "historical_trend": {
      "window_reports": 10,
      "data_points": 8,
      "baseline_median_pct": 84.2,
      "latest_pct": 71.0
    },
    "component_criticality": { "effective": "critical", "source": "config" }
  }
}
```

Given `risk_inputs`, every persisted score is exactly reproducible from the
scorer's fixed factor table. The factor breakdown already flows into the gap
manifest's `risk.factors`.

## Analysis-Time Cost

Two additional bounded queries per analysis, never per finding:

1. **Churn:** one aggregate over the 90-day window, grouped by file path,
   restricted to the candidate file set (`WHERE file_path = ANY(...)`).
2. **Trend:** one query for the candidate file set across the report IDs of
   the last 10 default-branch reports (resolved by a cheap primary lookup).

Candidate file sets are already bounded by report size limits. Supporting
indexes:

| Index | Purpose |
| --- | --- |
| `pull_request_coverage_diff_files (repository_id, file_path, created_at)` | Churn window scan |
| `coverage_file_summaries (repository_id, file_path, coverage_report_id)` | Trend lookup across recent reports |

If either query fails transiently, the analysis attempt fails and retries
normally; history factors are part of analysis, not best-effort decoration.

## Data Model

| Change | Detail |
| --- | --- |
| Snapshot | `criticality` field in normalized component JSON (canonicalization, hash, validation) |
| `component_coverage_rollups.effective_criticality` | `text` nullable, constrained to the four levels |
| Indexes | The two history indexes above |

`coverage_gap_findings` is unchanged: scores, levels, and `evidence_json`
already have homes.

## Error Handling

- Invalid `criticality` values fail CLI validation and Upload Service
  revalidation with field paths, like every other snapshot error; analysis
  treats an invalid persisted value as snapshot corruption (non-retryable).
- Missing history (no diffs, few reports, new files) is not an error; each
  factor contributes 0 with its explicit reason string.
- A repository whose `default_branch` has no reports yields `no_trend_input`
  for all files.

## Security And Resource Limits

- `criticality` is untrusted snapshot input, bounded by the existing
  snapshot limits (size, component count) and a closed value set.
- History queries are parameterized, scoped to one repository, and bounded
  by window and candidate set; no user-controlled pattern ever reaches them.
- `evidence_json` additions are fixed-shape numbers and enum strings —
  no free text beyond existing reason codes — keeping finding rows small.

## Testing Strategy

### Configuration Tests (CLI and Upload)

- Criticality parsing, inheritance, override, and the `medium` default.
- Rejection of unknown values with field paths.
- Canonical JSON stability and `config_sha256` change when criticality
  changes — and idempotency identity with it.
- Contract fixtures in `test-contracts/component-config-snapshots.json`
  extended with criticality cases, exercised by both Python and Java.

### Scorer Tests

- Each churn and trend band maps to its exact contribution and reason.
- Boundary values: 2 diffs vs 3; 1.9 vs 2.0 vs 5.0 percentage points;
  exactly 3 data points vs 2.
- Improving trend contributes 0.
- Score normalization still caps at 100 with all factors maximal.
- `risk_inputs` reproduces every persisted score.

### Analysis Integration Tests

- Effective criticality flows from snapshot to leaf files to findings to
  `effective_criticality` on rollups.
- Churn counts from seeded diff history across the window edge (91-day-old
  diffs excluded).
- Trend from seeded default-branch report sequences, including
  non-default-branch reports being ignored.
- Exactly two history queries per analysis (query-count assertion).
- Repositories with no history reproduce today's scores exactly.

### End-To-End Test

Extend the smoke test's component fixture with a `criticality: critical`
component; assert the resulting finding's risk level exceeds the identical
gap in a default-criticality component, and that `risk_inputs` is present.

## Documentation

- README and CLI guide: the `criticality` field with the inheritance rules,
  in the existing component documentation blocks.
- CLI guide: a short "How risk scores are computed" section listing the
  factors, their maxima, and the two history windows — scores agents and
  humans will act on should not be a black box.
- SELF_HOSTING: note that richer scores accrue as diff and default-branch
  upload history accumulates.
- `tests/test_public_repo.py`: additive assertion that the CLI guide
  documents `criticality` alongside `owners` and `gates`.

## Rollout And Compatibility

Implementation order:

1. Criticality through the snapshot pipeline (CLI, contracts, upload,
   analysis, rollup column) — independent of the diff design.
2. Trend factor (needs only existing report history).
3. Churn factor, once diff uploads populate history.
4. Documentation and smoke test.

Older snapshots without criticality and repositories without history score
exactly as today: the factors default to their current contributions.
Historical findings are not rescored. Manifest consumers see only ranking
changes, which `manifest_version: 1` permits.

## Acceptance Criteria

- `.vericov.yml` components declare criticality with validated,
  inheritance-consistent semantics carried in the immutable snapshot.
- A gap in a `critical` component outscores an identical gap in a `medium`
  one by the documented margin.
- Churn and trend contribute per the fixed bands, from persisted data only,
  with reasons and inputs recorded in `evidence_json`.
- Every persisted score is reproducible from its `risk_inputs`.
- Analysis issues exactly two additional bounded queries regardless of
  finding count.
- Repositories without configuration or history observe unchanged scores.
- Focused unit, contract, integration, and smoke tests pass with at least
  80% coverage in affected modules.
