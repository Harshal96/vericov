# Coverage Agent Runner Handoff L2 Design

Date: 2026-05-25
Status: Proposed
Owner: Agent / Runner Control Plane service, Coverage Analysis service

## Current State

The Agent / Runner Control Plane docs define `explain_gap` and `generate_tests` task concepts, but the implemented coverage stack does not produce ranked gap targets or create agent tasks. Coverage reasoning is metadata-only in this design set, and source-bearing analysis must not happen in SaaS unless repository privacy policy explicitly allows it. There is no handoff contract from coverage findings to runner tasks.

## Goals

- Define how ranked coverage gaps become runner tasks.
- Keep SaaS metadata-only by default for private repositories.
- Provide enough context for a runner to reproduce the target without leaking source text.
- Support two task types: `explain_gap` and `generate_tests`.
- Make task creation policy-aware, auditable, and deterministic.
- Let future PR comments and slash commands reuse the same handoff API.

## Non-Goals

- Do not implement the runner protocol in this L2.
- Do not generate tests inside Coverage Analysis.
- Do not send source snippets, raw diff text, or patches from SaaS to runner tasks.
- Do not auto-open PRs unless policy and user action explicitly allow it.

## Service Ownership

- Coverage Analysis persists ranked findings and recommends next action.
- Organization service authorizes users and exposes gap selection APIs.
- Agent / Runner Control Plane owns task creation, leasing, status, policy decision recording, and task history.
- Runner executes source-bearing work in the customer environment and returns policy-allowed summaries, artifacts, and audit hashes.
- Git Integration opens branches/PRs only when Agent Control Plane or runner requests provider actions through approved service APIs.

## Handoff Inputs

Agent tasks reference existing records:

- `repository_id`
- `coverage_report_id`
- `coverage_gap_finding_ids`
- `pull_request_number`
- `commit_sha`
- `base_sha` and `head_sha` when PR-specific
- `component_id`
- `owners`
- `risk_score` and `risk_level`
- `reason_code`
- `next_action`
- `evidence_json`
- `policy_context_version`

The runner receives paths and line numbers, not source text.

## Task Types

`explain_gap`:

- Runner checks out the repository at the target commit.
- Runner inspects source, nearby tests, config, and coverage artifacts permitted by policy.
- Runner returns a refined explanation, confidence, suggested test scenario, and whether generation is feasible.
- SaaS stores the returned summary on the finding or as an agent run artifact according to privacy mode.

`generate_tests`:

- Runner starts from one or more high-ranked findings.
- Runner creates a minimal test plan and writes tests only in allowed paths.
- Runner runs configured test/coverage commands.
- Runner returns status, command summaries, coverage delta, artifact references, audit hashes, and optional branch/PR metadata.

## Data Model

Runner handoff reuses the Agent / Runner Control Plane task and run tables from the draft service contract. The source reference is a coverage-gap resource object that stores `coverage_report_id`, `coverage_gap_finding_ids`, commit/PR identifiers, risk metadata, and policy context version. Finding rows may later store nullable `latest_agent_run_id` or enrichment artifact references, but the canonical task lifecycle remains in Agent Control Plane.

## APIs and Events

Agent Control Plane task creation endpoint:

- `POST /internal/v1/agents/tasks`

Request:

```json
{
  "tenant_id": "uuid",
  "org_id": "uuid",
  "repository_id": "uuid",
  "task_type": "generate_tests",
  "mode": "dry_run",
  "source": {
    "type": "coverage_gap",
    "coverage_report_id": "uuid",
    "coverage_gap_finding_ids": ["uuid"],
    "pull_request_number": 42,
    "commit_sha": "head456"
  },
  "target": {
    "file_path": "services/payments/discounts.ts",
    "line_start": 88,
    "line_end": 94,
    "risk_level": "high",
    "component_id": "uuid",
    "owners": ["team-payments"]
  },
  "evidence": {
    "reason_code": "new_uncovered_changed_line",
    "risk_score": 72.5,
    "context_version": "ctx-2026-05-25T10:00:00Z"
  },
  "requested_by": {
    "type": "system",
    "id": "coverage-analysis"
  }
}
```

Public user-facing request can be added later:

- `POST /api/v1/orgs/{org_id}/repositories/{repository_id}/coverage-gaps/{gap_id}/agent-runs`

This public endpoint should authorize the user, validate policy, and call the internal task endpoint.

Events:

- `agent.task.created`: emitted when a finding handoff creates a task.
- `agent.policy_decision.recorded`: emitted before task leasing.
- `coverage.gap.agent_enrichment_ready`: emitted when an `explain_gap` result can update a finding.
- `coverage.gap.test_generation_completed`: emitted when a `generate_tests` result is available.

## Policy Decisions

Before task creation:

- Check repository privacy mode.
- Check agent policy for `allow_prs`, `default_mode`, `allowed_write_paths`, `dry_run_paths`, `denied_paths`, max iterations, and required runner labels.
- Check CODEOWNERS/component owner restrictions.
- Check debt status; active debt-suppressed findings should not auto-create generation tasks unless explicitly requested.
- Choose mode:
  - `suggest` for source-aware explanation only.
  - `dry_run` for generated patch kept inside runner artifacts.
  - `open_pr` only when policy and requester allow it.

Record the policy decision on the agent task before leasing.

## Processing Flow

1. Coverage Analysis persists ranked active findings with next actions.
2. A user, PR command, scheduled policy, or future automation requests an agent run for a finding or `fix-first` set.
3. Organization service authorizes the requester and fetches current finding details.
4. Agent Control Plane evaluates policy and creates a task with metadata-only evidence.
5. Runner leases the task and checks out source locally.
6. Runner performs source-aware explanation or test generation.
7. Runner completes task with allowed metadata and artifact references.
8. SaaS updates agent run status, finding enrichment, PR comments/checks, and audit events.

## Privacy and Security

- Task payloads must not contain source text, raw diff text, secrets, test output with secrets, or generated patch content unless policy allows upload.
- Runner must redact command output before returning summaries.
- SaaS stores artifact references, not source-bearing artifacts, for metadata-only mode.
- All external actions, including branch creation and PR opening, require explicit policy authorization and auditable requester identity.
- The runner should verify the target commit SHA before acting to prevent stale or confused deputy tasks.

## Tests

- Unit tests for task payload construction from one finding and a `fix-first` set.
- Policy tests for privacy mode, denied paths, dry-run-only paths, active debt, and owner restrictions.
- API tests for authorization and invalid finding status.
- Agent Control Plane contract tests for accepted `explain_gap` and `generate_tests` payloads.
- Runner protocol tests later proving source text is not present in leased metadata-only tasks.
- End-to-end dry-run scenario from ranked gap to completed task summary.

## Rollout Order

1. Add internal task payload contract and policy decision shape.
2. Add Organization public endpoint for user-requested gap agent runs.
3. Add automatic task creation only for explicit slash-command/user action.
4. Add `fix-first` multi-gap batching.
5. Add PR-opening mode after dry-run summaries are proven safe.
6. Add source-aware explanation enrichment back onto findings.

## Open Follow-up Questions

- Should `fix-first` batch multiple nearby findings in one task, or enforce one finding per task for simpler audit?
- Which requester roles may run `generate_tests` vs `explain_gap`?
- In metadata-only mode, should generated patches stay entirely in runner storage, or can encrypted artifacts be uploaded for later approval?
