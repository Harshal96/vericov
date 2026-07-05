# Coverage Gap Manifest And Test-Closure Loop Design

Date: 2026-07-03
Status: Draft

## Summary

Vericov will close the loop from "coverage gap reported" to "test written"
without putting an LLM inside the product. The design has three parts:

1. A **gap manifest**: a versioned, deterministic, machine-readable document
   describing every actionable coverage gap for a report or pull request,
   assembled at read time from data the analysis pipeline already persists
   (`coverage_gap_findings`, diff coverage lines, component rollups, and gate
   evaluations).
2. A **`vericov gaps` CLI command** that fetches and renders the manifest in
   CI or a terminal.
3. A **reference test-closure workflow** under `examples/agentic-test-closure/`
   showing how CI feeds the manifest to a user-operated coding agent (Claude
   Code with the user's own API key) that writes the missing tests and pushes
   a follow-up commit for human review.

Vericov produces the evidence; the agent, its credentials, and its execution
environment belong entirely to the user. This keeps the public runtime at two
deterministic services while making Vericov the coverage backend that
agent-driven SDLCs can act on, not just read.

The manifest is served through the query API defined in
`2026-07-03-agent-coverage-query-interface-design.md`. Patch-scoped entries
require `2026-07-03-cli-supplied-pr-diff-coverage-design.md`. Ranking uses
the risk scores from `2026-07-03-risk-weighted-coverage-design.md` but
functions with the current scorer output unchanged.

## Goals

- Give any agent one document containing everything needed to write missing
  tests: files, line ranges, reasons, risk ranking, owners, and gate context.
- Make the manifest deterministic and reproducible from persisted data — the
  same report always yields the same manifest.
- Make gate failures actionable in CI with a single CLI invocation.
- Provide a credible, copyable reference workflow for automated test closure
  with explicit guardrails.
- Keep every LLM call, credential, and code-writing action outside Vericov.

## Non-Goals

- An agent-runner service, `vericov.agent_*` tables, or any bundled agent
  execution. `tests/test_public_repo.py` continues to assert their absence.
- Generating test code, test skeletons, or prompts inside Vericov services.
- Calling any LLM from the services, CLI, or MCP server.
- Automatically merging agent-produced commits. The reference workflow always
  ends in a human-reviewable commit or pull request.
- Tracking agent runs, attempts, or outcomes in the database.
- Suggesting which test file should host a new test. Test placement is
  repository-convention knowledge that belongs to the agent in the checkout.
- Cross-report gap identity or a gap lifecycle beyond what
  `coverage_gap_findings.status` already models.

## Current State

- `CoverageGapExtractor` runs inside `DefaultCoverageAnalysisProcessor` on
  every analysis and persists `coverage_gap_findings` rows with reason
  codes, explanations, confidence, risk score and level (via
  `CoverageRiskScorer`), owners, component keys, a `next_action`
  (`add_test`, `create_debt`, `mark_generated`, `inspect_instrumentation`,
  `run_source_explain`), and an `evidence_json` object.
- Findings are report-scoped: each analysis produces a fresh finding set for
  its report; the unique key is `(repository_id, coverage_report_id,
  file_path, target_type, line_start, line_end, reason_code)`.
- `component_coverage_rollups` aggregates `gap_count`, `debt_count`,
  `risk_score_total`, and `highest_active_risk_level`.
- Nothing consumes findings today: no endpoint serves them, the CLI cannot
  fetch them, and gate failures surface only as report `gate_status`.
- Diff-aware findings (`new_uncovered_changed_line`, `base_coverage_missing`)
  only appear once diff coverage is active.

The manifest is therefore a read-time projection. No analysis-side changes
are required.

## Manifest Contract

### Scope Selection

A manifest is built for exactly one coverage report, selected the same way as
every query endpoint: `ref` (commit SHA, branch, or default branch) or
`pull_request={number}` (the latest diff-bearing report for that number).
When built for a pull request, entries are additionally classified by whether
they fall inside the patch.

### Document Shape

```json
{
  "manifest_version": 1,
  "generated_at": "2026-07-03T18:02:11Z",
  "repository": {
    "full_name": "acme/monorepo",
    "default_branch": "main"
  },
  "report": {
    "report_id": "…",
    "upload_id": "…",
    "commit_sha": "…",
    "branch": "feature/retry-logic",
    "pull_request_number": 481,
    "gate_status": "failed",
    "config_sha256": "…"
  },
  "patch": {
    "status": "complete",
    "base_sha": "…",
    "head_sha": "…",
    "line_covered": 34,
    "line_total": 61
  },
  "failed_gates": [
    {
      "gate_name": "patch-coverage",
      "gate_type": "patch_coverage",
      "metric": "line",
      "scope_type": "repository",
      "scope_key": null,
      "threshold": 80.0,
      "actual": 55.7,
      "blocking": true
    }
  ],
  "entries": [
    {
      "finding_id": "…",
      "rank": 1,
      "file_path": "services/payments/src/Retry.java",
      "target_type": "range",
      "line_start": 84,
      "line_end": 97,
      "symbol_name": null,
      "in_patch": true,
      "reason_code": "new_uncovered_changed_line",
      "explanation": "…",
      "confidence": "high",
      "risk": { "score": 78.0, "level": "high", "factors": [ "…" ] },
      "component_key": "payments-api",
      "owners": ["team-payments"],
      "next_action": "add_test",
      "uncovered_ranges": [ { "start": 84, "end": 91 }, { "start": 95, "end": 97 } ]
    }
  ],
  "truncated": false
}
```

Rules:

- `entries` contains findings with `status = 'active'`, ordered by risk score
  descending, then file path, then line start — a total, deterministic order.
  `rank` is that position.
- `next_action` filtering defaults to `add_test`; other actions are included
  only when requested, because they are operator work, not agent work.
- `uncovered_ranges` is resolved per entry: from
  `pull_request_coverage_diff_lines` for patch entries, from
  `coverage_line_hits` for report-wide entries — so an agent needs no second
  query to know which lines a new test must execute.
- `risk.factors` echoes the factor breakdown persisted in `evidence_json`,
  making every rank explainable.
- The manifest carries no source code, no artifact content, and no storage
  references.
- Field additions are backward compatible within `manifest_version: 1`; any
  removal or meaning change increments the version.

### Serving

`GET /api/v1/coverage/gap-manifest?ref=|pull_request=&next_action=&min_risk_level=&limit=`

Served by the upload service under the query-API conventions (repository
API key, `read` scope, `ApiResponse` envelope). `limit` defaults to 100
entries, maximum 500; a truncated manifest sets `truncated: true` and states
the applied filters. The MCP server exposes it as `get_gap_manifest`.

## CLI Command

A new `vericov gaps` subcommand in `clis/coverage-upload`, sharing the
existing configuration surface (`--api-url`, `--api-key`, `--config`,
environment variables):

| Option | Meaning |
| --- | --- |
| `--ref <ref>` | Manifest for a commit SHA or branch |
| `--pull-request <n>` | Manifest for a pull request |
| `--min-risk-level <level>` | Filter entries below this level |
| `--next-action <action>` | Filter by action, default `add_test` |
| `--json` | Emit the raw manifest document |
| `--fail-on-entries` | Exit 1 when any entry remains after filters |

Human-readable output is a ranked table (rank, risk, file, lines, reason,
owners) preceded by the gate summary. `--json` output is the manifest
verbatim, suitable for piping into an agent. `--fail-on-entries` lets CI
enforce "no unaddressed high-risk gaps" without a gate configuration.

## Reference Test-Closure Workflow

A documented, copyable example under `examples/agentic-test-closure/`:

- `README.md` — what it does, what it costs, what the guardrails are, and
  explicitly that the workflow runs the user's agent with the user's
  `ANTHROPIC_API_KEY`; Vericov never sees the credential.
- `github-workflow.yml` — a GitHub Actions workflow for the pull request
  flow:

1. The repository's normal test job uploads coverage with `vericov upload
   --wait` (diff attached per the diff design).
2. When the reported `gate_status` is `failed`, a follow-up job fetches the
   manifest: `vericov gaps --pull-request $PR --json --min-risk-level medium`.
3. The job invokes Claude Code non-interactively with the manifest and a
   pinned instruction file (`CLOSURE_PROMPT.md`, versioned in the example):
   write tests that execute the uncovered ranges, modify test files only,
   run the test suite, and stop.
4. The job re-runs coverage, re-uploads, and pushes a
   `test: close coverage gaps` commit to the pull request branch only if the
   suite passes and patch coverage improved.
5. A human reviews the commit like any other; the workflow never merges.

Documented guardrails, stated as requirements in the example README:

- The agent step runs with a diff guard: if the produced diff touches
  non-test paths, the job fails without pushing.
- One closure attempt per head commit — the pushed commit triggers a fresh
  upload, and the workflow does not re-enter on its own commit (commit
  message sentinel check).
- A hard timeout and iteration cap on the agent step.
- No force pushes; no pushes to protected branches.
- The verification signal is Vericov's own next report: the pushed commit's
  upload either clears the gate or the loop stops for human attention.

The example is CI documentation, not shipped code: it is excluded from
release packaging and carries no coverage gate, but the workflow YAML is
linted in CI to stay syntactically valid.

## Data Model

No new tables and no analysis-side changes. One addition:

| Change | Detail |
| --- | --- |
| Index | `coverage_gap_findings (coverage_report_id, next_action, status, risk_score DESC)` for manifest assembly |

The manifest is otherwise served entirely from existing rows. If the query
interface design's gap-listing index already exists, this index supersedes it;
they should be reconciled at implementation time rather than duplicated.

## Error Handling

- Unknown `ref` or pull request: `404` with the same codes as the query API.
- A manifest for a report with no active findings is a success with an empty
  `entries` list and the gate summary — "nothing to do" is a valid, useful
  answer.
- A pull-request manifest where diff status is `base_coverage_missing`
  includes the patch block with that status so agents can explain why
  patch-scoped data is partial.
- `vericov gaps` exits 0 on an empty manifest (unless `--fail-on-entries`),
  1 on entries with `--fail-on-entries`, and 2 on transport or validation
  errors, so CI can distinguish outcome classes.

## Security And Resource Limits

- The manifest exposes file paths, line numbers, reasons, and owner strings —
  the same sensitivity class as existing report responses. It never includes
  source, diffs, or artifact content.
- Served only to `read`-scoped (or legacy) repository keys.
- Entry count capped at 500 per response; `uncovered_ranges` capped at 100
  ranges per entry with an explicit per-entry truncation flag.
- The reference workflow README states the security posture plainly: the
  agent runs with repository write access in the user's CI; users must pin
  action versions, restrict the workflow to same-repo pull requests (no
  `pull_request_target` on forks), and scope the CI token to contents-write
  only.

## Testing Strategy

### Manifest Assembly Tests

- Deterministic ordering: identical persisted data yields byte-identical
  manifests (modulo `generated_at`).
- Ranking across risk-score ties falls back to path and line ordering.
- Patch classification: entries inside and outside the diff, and reports
  with no diff at all.
- `uncovered_ranges` resolution from both line-hit and diff-line sources,
  including range collapsing and the per-entry cap.
- Filter combinations: `next_action`, `min_risk_level`, `limit` with
  truncation flags.
- Empty manifests and `base_coverage_missing` manifests.

### CLI Tests

- Ref and pull-request selection, filter passing, and table rendering.
- `--json` emits the manifest unmodified.
- Exit-code contract for all three outcome classes.

### End-To-End Test

Extend the smoke test: after the PR upload with a partially covered diff
(from the diff design's smoke scenario), fetch the manifest via the CLI and
assert it contains the expected `add_test` entry with the expected uncovered
range and a `failed` patch gate in `failed_gates`.

### Workflow Example Validation

CI lints `examples/agentic-test-closure/github-workflow.yml` for YAML
validity and asserts the README documents every listed guardrail (string
assertions, in the style of `tests/test_public_repo.py`).

## Documentation

- README: a "Closing gaps automatically" section pointing at the example and
  stating the boundary — Vericov produces the manifest, your agent and your
  key write the tests.
- CLI guide: the `vericov gaps` command with CI snippets.
- `tests/test_public_repo.py`: additive assertions that the example exists
  and that the compose file and schema still contain no agent-runner service
  and no `vericov.agent_` tables — the closure loop must not erode that
  boundary.

## Rollout And Compatibility

Implementation order:

1. Manifest assembly and endpoint (works today with report-wide findings,
   before the diff design lands).
2. `vericov gaps` CLI command.
3. MCP `get_gap_manifest` tool (with the query interface design).
4. Patch-scoped entries once diff coverage is active.
5. Reference workflow example, validated against a real repository.
6. Smoke test and documentation.

Everything is additive and read-time; no existing behavior changes. The
manifest version field gives downstream consumers a stable contract as
entries grow richer (for example, when the risk design adds factors).

## Acceptance Criteria

- One HTTP call or one CLI invocation returns a ranked, deterministic,
  versioned manifest for a ref or pull request.
- Every entry contains file, ranges, reason, risk with factors, owners,
  component, and action — sufficient for an agent to act without a second
  query.
- Empty and degraded (missing base coverage) manifests are well-formed
  successes.
- `vericov gaps` supports human, JSON, and CI-enforcement modes with the
  documented exit codes.
- The reference workflow closes a seeded coverage gap in a test repository:
  gate fails, agent writes a test, the follow-up upload passes the gate, and
  a human-reviewable commit is the only side effect.
- No LLM call, agent execution, or agent credential exists inside Vericov
  services, CLI, or MCP server; `tests/test_public_repo.py` still passes
  with its agent-absence assertions intact.
- Focused unit, API, CLI, and smoke tests pass with at least 80% coverage in
  affected modules.
