# Debt-Aware Gate Evaluation L2 Design

Date: 2026-05-25
Status: Proposed
Owner: Coverage Analysis service

## Current State

Gate evaluation currently supports active `project_coverage` gates for line, branch, function, and statement metrics. It does not evaluate patch, component, coverage drop, or risk gates. It does not know about coverage debt, waivers, debt expiry, or suppressed findings. `gate_evaluations.details_json` contains simple coverage counters but no debt-adjusted view or evidence links.

## Goals

- Integrate coverage debt into gate evaluation without hiding raw coverage.
- Record which gaps were suppressed by active debt and which expired debt reappeared.
- Support deterministic replay through stored details and context version.
- Keep v1 compatible with existing `gate_evaluations` read APIs.
- Prepare for patch, component, and risk gates once gap findings and component rollups exist.

## Non-Goals

- Do not change raw `coverage_reports` totals.
- Do not make all debt automatically gate-suppressing; suppression must be allowed by policy/gate config.
- Do not implement mutation or test-quality gates in this L2.
- Do not create or approve debt during gate evaluation.

## Service Ownership

- Coverage Analysis owns gate evaluation and writes enriched `gate_evaluations`.
- Organization service owns gate configuration, debt CRUD, and public reads.
- Coverage debt lifecycle owns whether debt is active, expired, resolved, or revoked.
- Risk scoring owns finding risk, which gate evaluation can consume as an input.

## Data Model

Debt-aware gate evaluation reuses existing `repository_gate_configurations` and `gate_evaluations` rather than adding a new table. Gate behavior is configured through `repository_gate_configurations.config_json.debt`. Evaluation evidence is stored in `gate_evaluations.details_json` with schema version 2, raw metrics, effective status, suppressed finding IDs, suppressed debt IDs, expired debt IDs, and top failing finding IDs.

## Gate Configuration

Existing gate rows should continue to work. Add optional `config_json` keys:

```json
{
  "debt": {
    "mode": "none",
    "allow_risk_levels": ["low", "medium"],
    "max_suppressed_findings": 10,
    "fail_on_expired_debt": true
  },
  "scope": {
    "component_id": "uuid",
    "path_pattern": "services/payments/**"
  }
}
```

Debt modes:

- `none`: debt is evidence only; it does not affect gate status.
- `suppress_findings`: active matching debt can suppress gap/risk/agent-review failures.
- `adjust_metric`: active matching debt can adjust evaluated metric totals for gate comparison, while raw metric remains visible.

Default mode is `none` for existing gates. New risk/agent-review gates may default to `suppress_findings` only if repository policy enables it.

## Evaluation Semantics

Raw metrics:

- Always calculate and store the metric that would apply without debt.
- Raw status is included in details for auditability.

Debt-adjusted metrics:

- Only calculated when gate `debt.mode = adjust_metric`.
- Exclude executable lines/ranges covered by active debt from denominator for the gate comparison.
- Never exclude debt from report totals or dashboard raw coverage.

Finding suppression:

- Only applies when gate `debt.mode = suppress_findings`.
- Active debt suppresses matching active findings if risk level is allowed and debt is unexpired.
- Expired, resolved, and revoked debt never suppresses.
- Suppression is recorded with debt IDs and finding IDs.

Expired debt:

- If `fail_on_expired_debt = true`, matching expired debt contributes to gate failure or warning according to `blocking`.
- If false, expired debt is evidence only, but findings reappear unsuppressed.

## Details JSON

Extend `gate_evaluations.details_json`:

```json
{
  "schema_version": 2,
  "scope": "patch",
  "coverage_report_id": "uuid",
  "context_version": "ctx-2026-05-25T10:00:00Z",
  "raw": {
    "covered": 8,
    "total": 10,
    "percentage": 80.0,
    "threshold": 85.0,
    "status": "failed"
  },
  "debt": {
    "mode": "suppress_findings",
    "suppressed_finding_ids": ["uuid"],
    "suppressed_debt_item_ids": ["uuid"],
    "expired_debt_item_ids": ["uuid"],
    "reappeared_finding_ids": ["uuid"]
  },
  "effective": {
    "actual": 80.0,
    "status": "warning",
    "reason": "raw_metric_failed_but_only_debt_suppressed_findings_remain"
  },
  "top_findings": ["uuid"]
}
```

For project coverage gates that do not use debt, keep the existing shape plus `schema_version`, `raw`, and `effective` while preserving backward-compatible fields such as `covered`, `total`, and `percentage`.

## APIs and Events

No new public gate endpoint is required in v1. Existing gate configuration and gate evaluation reads continue to serve debt-aware data through `config_json` and `details_json`.

Events:

- `coverage.gates.evaluated`: include raw and effective gate counts.
- `coverage.debt_suppression.applied`: optional internal event when active debt changes a gate outcome.
- `coverage.debt_expired.reappeared`: optional internal event when expired debt contributes to failure/warning evidence.

## Processing Flow

1. Coverage Analysis loads active gate configurations and repository context.
2. It computes raw metrics from report, PR diff, component rollups, and findings.
3. It matches active and expired debt against findings and scoped metrics.
4. For each gate, it evaluates raw status.
5. It applies debt mode if configured.
6. It writes final `status`, `actual`, and enriched `details_json`.
7. It emits `coverage.gates.evaluated` with both raw and effective summary counts.

## Gate Types

Initial debt-aware support order:

1. `agent_review_required` with `metric = risk`: fail when high-risk unsuppressed gaps exist.
2. `patch_coverage`: use PR diff patch coverage and finding evidence.
3. `component_coverage`: use component rollups and scoped debt.
4. `project_coverage`: keep raw default; allow `adjust_metric` only when policy explicitly permits.
5. `coverage_drop`: compare base/head raw metrics first, then include debt evidence.

## Privacy and Security

- Gate details may expose file paths and debt IDs; keep reads repository-authenticated.
- Do not embed debt reasons in gate event payloads unless the public read API already authorizes the caller.
- Preserve deterministic details so compliance users can explain why a gate passed despite raw uncovered code.
- Reject gate config that enables debt suppression on critical paths unless policy allows it.

## Tests

- Unit tests for debt modes: none, suppress findings, adjust metric.
- Tests that expired/resolved/revoked debt never suppresses.
- Tests that raw status remains failed even when effective status is warning/passed.
- Tests for blocking vs advisory behavior when expired debt reappears.
- Serialization tests for details JSON schema version 2.
- End-to-end Coverage Analysis test for PR patch coverage with one active debt item and one expired debt item.
- Organization API tests proving old gate read responses still include existing top-level fields.

## Rollout Order

1. Add debt matching inputs to gate evaluator without changing existing status behavior.
2. Enrich details JSON for current project gates.
3. Implement `agent_review_required` risk gate using active unsuppressed findings.
4. Implement patch coverage gate with debt evidence.
5. Implement component coverage gate after component rollups are available.
6. Add `adjust_metric` only after suppression semantics are validated with customers.

## Open Follow-up Questions

- Should debt-adjusted metrics ever be displayed as a headline percentage, or only as gate-specific effective evidence?
- Should an expired low-risk debt item fail a blocking gate immediately or first produce a warning window?
- Should repository policies be able to cap total active debt count per owner/component?
