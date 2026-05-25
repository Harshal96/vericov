# Vericov Agentic Coverage Platform PRD

Status: Draft
Owner: Product / Engineering
Last updated: 2026-05-22

## 1. Executive Summary

Vericov is an agentic-first replacement for Codecov. It provides the expected coverage reporting, pull request checks, historical trends, and badges that teams already rely on, then extends the category with autonomous remediation: the product can reason about uncovered behavior, evaluate test quality, generate missing tests, run them in the customer's environment, and open reviewable pull requests.

The product should not be positioned as "another coverage dashboard." The product promise is:

> Vericov tells teams whether a change is adequately tested, explains the risk in plain English, and dispatches safe agents to close the highest-value gaps.

The initial product should support three customer shapes:

- Open source maintainers who need low-friction coverage, badges, and PR feedback.
- Agent-heavy engineering teams that need verification before merging AI-authored code.
- Enterprises that need private, auditable, self-hostable agent execution with code staying inside their perimeter.

The default business model is open-core SaaS: coverage upload and local/runner primitives should be transparent and portable, while the hosted platform provides history, dashboards, agent orchestration, organization policy, collaboration, and enterprise fleet management.

## 2. Problem Statement

Coverage tools answer "what percentage of code ran?" but modern engineering teams need a stronger answer: "is this pull request safe to merge?"

This gap is larger for teams adopting coding agents. Agents can produce large diffs quickly, but reviewers still need trustworthy evidence that the generated code is covered by meaningful tests. Traditional coverage tools miss several important signals:

- Coverage can be high while assertions are weak or irrelevant.
- Coverage drops can be low risk, while unchanged percentages can hide risky untested behavior.
- Flaky tests make coverage and CI outcomes hard to trust.
- Monorepos need package-level and ownership-aware rollups, not only repository-level percentages.
- Enterprise teams cannot let source code or generated patches leave their VPC.
- Developers do not want generic AI review noise; they want actionable test evidence.

Vericov should combine coverage data, test result data, source diffs, architectural context, mutation testing, and agentic repair workflows into one merge-confidence system.

## 3. Goals

- Provide complete core coverage functionality: multi-type coverage, PR diffs, gates, historical trends, and badges.
- Explain coverage gaps in developer language, not only as percentages.
- Identify weak tests, brittle tests, flaky tests, and coverage theater.
- Prioritize coverage work by code risk, architecture, ownership, and change context.
- Generate missing tests autonomously and open reviewable PRs.
- Support enterprise-grade privacy through a self-hostable runner that executes inside the customer's network.
- Support bring-your-own LLM backends for regulated and cost-sensitive customers.
- Work well for monorepos, multi-language repositories, and multi-CI pipelines.
- Expose APIs and integrations so coverage intelligence can become part of a customer's engineering system.

## 4. Non-Goals

- Vericov is not a general-purpose SAST platform in v1. Security findings are in scope only when they affect test coverage, risk scoring, policy, or merge confidence.
- Vericov is not a CI provider. It integrates with CI systems and can run tasks through a runner, but it should not replace CI orchestration.
- Vericov is not a generic AI coding assistant. Its agents are scoped to coverage, tests, flaky-test remediation, mutation testing, and PR verification.
- Vericov should not silently push changes to protected branches. Agent output should land as a reviewable branch or PR unless an explicit enterprise policy allows a narrower direct-commit workflow.
- Vericov should not require enterprises to send source code to SaaS. Source-bearing analysis must be runnable inside the customer perimeter.

## 5. Personas

### 5.1 Open Source Maintainer

Needs free or low-cost coverage reporting, simple setup, badges, PR comments, and reliable checks. Values transparency and easy migration from existing tools.

### 5.2 Startup Engineering Lead

Needs a fast way to keep coverage healthy while the team ships quickly. Wants agents to create missing tests and reduce review burden without adding process drag.

### 5.3 Agent-Heavy Team Lead

Uses Codex, Claude Code, Cursor, or internal coding agents. Needs a merge-time quality gate that can detect untested AI-generated behavior and ask another agent to fix it.

### 5.4 Platform Engineer

Owns CI, monorepo tooling, policy, and developer productivity. Needs stable APIs, configurable gates, package rollups, ownership integration, and low-noise notifications.

### 5.5 Security / Compliance Buyer

Needs tenant isolation, audit logs, policy enforcement, private execution, token rotation, retention controls, and proof that source code does not leave approved systems.

### 5.6 Enterprise Developer

Needs IDE feedback, plain-English explanations, and safe agent suggestions without learning a new coverage workflow.

## 6. Product Principles

- Evidence before automation: agents should act only after producing a clear diagnosis and expected improvement.
- Local code stays local when required: enterprise runner mode must support metadata-only SaaS operation.
- Reviewable autonomy: generated tests and fixes should be easy to inspect, reproduce, and reject.
- Low noise: Vericov should comment only when it has concrete evidence, a failing gate, a risk explanation, or a proposed fix.
- Risk over raw percentage: coverage metrics matter, but merge confidence should combine coverage, tests, diff risk, ownership, and history.
- Monorepo-native: package-level views and rollups should be first-class, not bolted on.
- Policy is explicit: customers should be able to define where agents may read, write, test, open PRs, or only dry-run.

## 7. Capability Overview

Vericov has four product pillars:

1. Core Coverage
   - Multi-type coverage tracking.
   - PR and commit diff views.
   - Coverage gates.
   - Historical trends.
   - Badge generation.

2. Agentic Coverage Intelligence
   - Autonomous coverage agent.
   - Coverage reasoning.
   - Test quality analysis.
   - Codebase-aware prioritization.
   - Coverage debt tracking.
   - Natural language interface.
   - Agent-assisted PR review.
   - Mutation testing integration.
   - Flaky test detection and remediation.

3. Enterprise / Self-Hosted Execution
   - Agent Runner SDK.
   - Outbound-only task protocol.
   - Bring Your Own LLM.
   - Coverage upload CLI.
   - Runner authentication and token rotation.
   - Tenant isolation.
   - Audit logs.
   - Policy engine.
   - Dry-run mode.
   - Runner fleet view.
   - Runner version management.
   - Bring your own Git.

4. Platform and Integrations
   - Monorepo support.
   - IDE plugins.
   - SBOM and compliance mapping.
   - Coverage API.
   - Self-hosted SaaS option.

## 8. Functional Requirements

### 8.1 Core Coverage

#### FR-COV-001: Multi-Type Coverage Tracking

Vericov must ingest and normalize line, branch, function, and statement coverage across major languages and coverage formats.

Supported coverage categories:

- Line coverage.
- Branch coverage.
- Function / method coverage.
- Statement coverage.

Initial supported report families:

- LCOV.
- Cobertura XML.
- JaCoCo XML.
- Go coverage profiles.
- gcov / llvm-cov outputs.
- Clover XML.
- Istanbul / nyc JSON and LCOV.
- coverage.py XML / JSON.
- Generic JSON coverage adapter for custom importers.

Requirements:

- Normalize files to repository-relative paths.
- Support multiple reports per commit.
- Support report flags for language, package, CI job, shard, or test suite.
- Support partial uploads and finalization once all expected shards arrive.
- Preserve raw report metadata for debugging ingestion problems.
- Identify unparseable, missing, stale, or mismatched reports with actionable errors.

#### FR-COV-002: PR and Commit Diff Views

Vericov must show exactly what coverage changed for each commit and pull request.

Requirements:

- Show base coverage, head coverage, and delta.
- Show patch coverage for changed lines.
- Show file-level and component-level deltas.
- Highlight newly uncovered lines and branches.
- Highlight lines that became covered.
- Explain missing coverage in PR comments and dashboard views.
- Support commit-to-commit, branch-to-branch, and release-to-release comparisons.

Acceptance criteria:

- A reviewer can open a PR and see which changed lines are untested.
- A reviewer can distinguish total project coverage drops from patch coverage gaps.
- A reviewer can see whether a coverage change belongs to a package, service, or owner group.

#### FR-COV-003: Coverage Gates

Vericov must fail CI checks when configured coverage requirements are not met.

Gate types:

- Project coverage threshold.
- Patch coverage threshold.
- Component coverage threshold.
- Flag-specific coverage threshold.
- Branch coverage threshold.
- Function coverage threshold.
- Statement coverage threshold.
- Maximum allowed coverage drop.
- Minimum mutation score for changed high-risk files.
- Required agent review for risky uncovered paths.

Requirements:

- Gates are configured in `vericov.yml` and optionally overridden at organization or repository level.
- Gates produce Git provider checks with clear pass/fail status.
- Gates can be blocking or advisory.
- Gates can support grace periods during migration.
- Gates can support path-specific exceptions and coverage debt waivers.
- Gate evaluation must be deterministic and reproducible.

#### FR-COV-004: Historical Trends

Vericov must track coverage over time across commits, branches, pull requests, releases, packages, and teams.

Requirements:

- Store historical coverage metrics for each commit and report flag.
- Visualize trends by branch, package, component, and release.
- Show regression events and recovery events.
- Support retention policies by plan and enterprise configuration.
- Support release markers and deployment markers.
- Support queries such as "coverage since v2.3.0" and "coverage trend for payments service."

#### FR-COV-005: Badge Generation

Vericov must generate embeddable badges for READMEs, docs, and dashboards.

Badge types:

- Repository coverage.
- Branch coverage.
- Package/component coverage.
- Patch coverage for default branch.
- Mutation score.
- Flaky-test health.
- Agent debt count.

Requirements:

- Badges should support public repositories without authentication.
- Private repository badges should require signed, revocable badge URLs.
- Badge style should support flat, shields-compatible, and JSON endpoint modes.

### 8.2 Agentic Features

#### FR-AGT-001: Autonomous Coverage Agent

Vericov must provide an autonomous agent that detects uncovered paths, writes missing tests, runs the relevant test command, and opens a PR for human review.

Workflow:

1. Detect changed code with missing or weak coverage.
2. Classify coverage gaps by risk and testability.
3. Select the minimal high-value test target.
4. Create or modify tests in a scoped branch.
5. Run the configured test command locally or in the runner environment.
6. Iterate on failures within policy limits.
7. Open a PR or attach a dry-run preview.
8. Include a concise explanation of what was covered and why.

Requirements:

- Agents must respect repository policy, CODEOWNERS, and Vericov policy rules.
- Agents must produce small, reviewable diffs.
- Agents must avoid unrelated refactors.
- Agents must log commands run, files read, files changed, and model/tool decisions.
- Agents must be able to operate in SaaS-hosted mode for public or explicitly opted-in repositories.
- Agents must be able to operate in self-hosted runner mode without source code leaving the enterprise environment.

#### FR-AGT-002: Coverage Reasoning

Vericov must explain why a line, branch, function, or statement is uncovered.

Explanation types:

- No test reaches this function.
- Test reaches function but not this branch.
- Code path requires an untested error condition.
- Code path is behind feature flag or environment gate.
- Code path depends on external service behavior.
- Generated or ignored file.
- Dead or unreachable code candidate.
- Coverage report path mismatch or instrumentation issue.

Risk levels:

- Critical: high-impact production path, security-sensitive path, payment/auth/data loss area, or changed behavior without tests.
- High: user-visible behavior, complex branching, recent incident area, or owner-critical service.
- Medium: business logic path with limited blast radius.
- Low: logging, telemetry, generated code, defensive fallback, or explicitly waived debt.

Requirements:

- Explanations should link to evidence: diff hunk, coverage report, test results, ownership, or policy.
- Explanations should avoid certainty when evidence is incomplete.
- Explanations should provide the next best action: test suggestion, waive as debt, mark generated, or inspect instrumentation.

#### FR-AGT-003: Test Quality Analysis

Vericov must detect tests that increase coverage without meaningful confidence.

Detection categories:

- Weak assertions: test runs code but only asserts truthy values, snapshots, or no assertions.
- Coverage theater: tests execute lines without checking outcomes or side effects.
- Over-mocking: mocks the behavior under test so the test cannot catch regressions.
- Brittle tests: tests rely on time, random order, network state, sleep-based waits, or implementation details.
- Duplicate tests: new test adds little behavioral value over existing tests.
- Unclear intent: test name and assertions do not describe the behavior being protected.

Requirements:

- Test quality comments must be concrete and evidence-backed.
- The product must separate weak-test warnings from hard coverage gate failures.
- Customers can configure whether weak-test findings are advisory or blocking.
- Agent-generated tests must pass the same quality analysis before PR creation.

#### FR-AGT-004: Codebase-Aware Prioritization

Vericov must understand repository architecture well enough to recommend where coverage improvements matter most.

Signals:

- Ownership metadata such as CODEOWNERS.
- Monorepo package graph.
- Service boundaries.
- Dependency graph.
- Runtime criticality labels.
- Incident history or deployment markers where available.
- Changed files and change complexity.
- Flaky-test history.
- Existing coverage debt.
- Mutation testing results.

Requirements:

- The dashboard should rank coverage gaps by risk-adjusted value, not just uncovered line count.
- PR comments should include only the most important gaps by default.
- The agent should choose high-impact, small-scope test additions first.
- Users should be able to override prioritization through policy and component configuration.

#### FR-AGT-005: Coverage Debt Tracking

Vericov must let teams consciously defer low-risk gaps like technical debt.

Requirements:

- Users can create coverage debt items for uncovered lines, files, functions, branches, or components.
- Debt items require owner, reason, risk level, expiration or review date, and optional linked issue.
- Debt can be created from PR comments, dashboard, API, or config.
- Expired debt reappears in gates and dashboards.
- Debt must be visible in trends and team rollups.
- Policy can block new debt in critical paths.

Acceptance criteria:

- A team can accept a low-risk uncovered branch temporarily without hiding it permanently.
- A manager can see debt aging, owners, and burn-down over time.

#### FR-AGT-006: Natural Language Interface

Vericov must support plain-English questions through chat surfaces.

Surfaces:

- Web dashboard chat.
- Slack bot.
- Microsoft Teams bot.
- CLI query mode later.

Example questions:

- "What changed in coverage on this PR?"
- "Why did patch coverage fail?"
- "What are the riskiest uncovered paths in payments?"
- "Which tests are flaky this week?"
- "Generate tests for the highest-risk uncovered branches."
- "What coverage debt expires this sprint?"
- "Explain mutation testing failures in plain English."

Requirements:

- The assistant must cite coverage/test evidence in answers.
- Destructive or external actions require confirmation or policy authorization.
- Enterprise mode must respect data residency and source-code boundaries.
- Chat answers should create links to PRs, files, reports, and agent runs.

#### FR-AGT-007: Agent-Assisted PR Review

Vericov must comment on pull requests with missing test suggestions and code snippets.

Requirements:

- Comments should be sparse, high signal, and tied to changed lines.
- Suggestions should include target test files, scenario names, and example assertions.
- Inline snippets should be short and illustrative, not full generated patches unless the user requests them.
- The product should support slash commands:
  - `/vericov explain`
  - `/vericov fix-tests`
  - `/vericov dry-run`
  - `/vericov mutation`
  - `/vericov mark-debt`
  - `/vericov rerun-risk`
  - `/vericov quarantine-flake`
- Users should be able to suppress, resolve, or convert comments into debt items.

#### FR-AGT-008: Mutation Testing Integration

Vericov must run targeted mutation testing on changed files and summarize results plainly.

Requirements:

- Support language-specific mutation engines through adapters.
- Initial adapters should prioritize JavaScript/TypeScript, Python, Java, and Go.
- Default mode should mutate changed high-risk files only.
- Results should report killed, survived, timed out, and invalid mutants.
- Survived mutants should be linked to missing test scenarios.
- Mutation thresholds can be used as advisory or blocking gates.
- Agent can generate tests to kill survived mutants when policy allows.

Plain-English summary example:

> Three changed conditions in `billing/discounts.ts` can be inverted without failing tests. The highest-risk survivor allows expired coupons to remain valid. Vericov recommends adding a boundary test for expired coupons and can open a test PR.

#### FR-AGT-009: Flaky Test Detection and Remediation

Vericov must detect inconsistent tests and propose fixes for common flaky patterns.

Signals:

- Same test alternates pass/fail across runs without code changes.
- Test fails only on specific CI shards, platforms, or times.
- Test duration variance exceeds configured bounds.
- Test correlates with network calls, sleeps, clock time, random order, shared state, or resource contention.

Requirements:

- Ingest test result history from CI.
- Group flaky failures by test identity, file, suite, and stack signature.
- Identify likely root cause category.
- Recommend fixes and optionally open agent PRs.
- Support quarantine workflows without hiding risk.
- Track flake rate trends and remediation outcomes.

### 8.3 Enterprise / Self-Hosted

#### FR-ENT-001: Agent Runner SDK

Vericov must provide an open-source, self-hostable agent runner that enterprises deploy inside their own VPC.

Responsibilities:

- Poll SaaS for tasks.
- Fetch code from customer Git provider.
- Run coverage, tests, mutation testing, and agent workflows locally.
- Invoke configured LLM backend.
- Enforce policy locally.
- Return allowed metadata, summaries, status, audit events, and optional patch references.

Requirements:

- Runner must be deployable as a container, binary, and Kubernetes workload.
- Runner must expose SDK hooks for custom task types, policy checks, LLM providers, and Git providers.
- Runner must operate with least-privilege Git credentials.
- Runner must support dry-run and no-network task modes.
- Runner must keep source code and source-bearing patches inside the customer perimeter unless explicitly configured otherwise.

#### FR-ENT-002: Outbound-Only Task Protocol

The runner must communicate with SaaS using outbound-only polling. No inbound ports should be required.

Protocol requirements:

- Runner registers to a tenant and runner group.
- Runner polls for tasks over TLS.
- SaaS returns task descriptors containing metadata, repository identity, commit SHA, policy references, and required capabilities.
- Runner validates policy and local permissions before executing.
- Runner streams or batches task events back to SaaS.
- Runner returns final task status and allowed outputs.
- Runner receives short-lived credentials per task cycle.

Task lifecycle:

- `queued`
- `leased`
- `acknowledged`
- `running`
- `waiting_for_policy`
- `waiting_for_ci`
- `completed`
- `failed`
- `canceled`
- `expired`

#### FR-ENT-003: Bring Your Own LLM

The runner must support pluggable LLM backends.

Required providers:

- Azure OpenAI.
- AWS Bedrock.
- Ollama.
- Self-hosted OpenAI-compatible endpoints.

Future providers:

- Google Vertex AI.
- Anthropic through enterprise proxy.
- Local model gateways.

Requirements:

- Provider configuration lives in the runner environment or enterprise control plane.
- SaaS should store provider type and capability metadata, not customer API secrets unless explicitly configured for hosted execution.
- Runner must support model routing by task type.
- Runner must log model name, provider, token usage where available, and policy outcome.
- Runner must support disabling code-bearing prompts from leaving the runner host.

#### FR-ENT-004: Coverage Upload CLI

Vericov must provide a coverage upload CLI. In enterprise metadata-only mode, this is the only component that phones home to SaaS from CI.

Commands:

- `vericov upload`
- `vericov validate-config`
- `vericov local-report`
- `vericov finalize`
- `vericov runner register`
- `vericov runner doctor`

Upload requirements:

- Ship coverage metadata, normalized coverage maps, test summaries, and commit metadata.
- Do not upload source code by default.
- Support source upload only as explicit opt-in for SaaS-hosted public/private modes.
- Support signed uploads and provenance metadata.
- Support retries, idempotency keys, and resumable large uploads.
- Support tokenless CI where provider identity can be verified.

#### FR-ENT-005: Runner Authentication and Token Rotation

Vericov must support secure runner registration and short-lived task credentials.

Requirements:

- Registration tokens are one-time or short-lived.
- Runner exchanges registration token for runner identity.
- Runner uses short-lived JWTs or mTLS-bound credentials for polling.
- Each task cycle receives scoped credentials.
- Credentials are rotated automatically.
- Revoked runners stop receiving tasks immediately.
- Audit log records registration, rotation, revocation, and failed authentication events.

#### FR-ENT-006: Tenant Isolation

Each enterprise tenant's task queues, results, metadata, policies, and runner identities must be isolated.

Requirements:

- Strong tenant identifiers on every persisted object.
- Authorization checks at every API boundary.
- Separate encryption keys per tenant where feasible.
- No cross-tenant task leasing.
- No cross-tenant analytics unless explicitly aggregated and anonymized.
- Automated tests for tenant isolation.

#### FR-ENT-007: Audit Logs

Vericov SaaS must log every task dispatched and received. The runner must log every file access and Git operation locally.

SaaS audit events:

- User login and admin action.
- Runner registration, update, revocation.
- Task creation, lease, status transition, completion.
- Policy update and policy decision.
- PR comment, status check, or branch creation request.
- Coverage upload and gate evaluation.
- API token creation, rotation, revocation.

Runner audit events:

- Task received.
- Repository cloned or fetched.
- File read.
- File written.
- Git branch created.
- Commit created.
- PR opened.
- Command executed.
- LLM request metadata.
- Policy allowed, denied, or required dry-run.

Requirements:

- Enterprise customers can export audit logs.
- Runner logs must be tamper-evident where supported.
- Sensitive values must be redacted.

#### FR-ENT-008: Policy Engine

Vericov must provide a policy engine configured in SaaS and enforced by the runner.

Example policies:

- Never auto-open PRs for payment files.
- Dry-run only for auth, billing, infra, or regulated data paths.
- Allow agent PRs only for tests, fixtures, docs, and coverage config.
- Require CODEOWNER approval before applying generated tests in critical services.
- Block source upload for private repos.
- Require BYOLLM provider for enterprise repos.
- Limit mutation testing runtime per PR.
- Quarantine flaky tests only with owner approval.

Requirements:

- Policies can be defined at organization, repository, component, path, and runner-group levels.
- Runner must enforce the most restrictive applicable policy.
- Policy decisions must be explainable and auditable.
- SaaS UI should preview policy effects before saving.

#### FR-ENT-009: Dry-Run Mode

Vericov must support dry-run mode where agents generate tests and previews without opening PRs.

Requirements:

- Dry-run output includes intended files, summary, commands, expected coverage gain, and risk reduction.
- In enterprise metadata-only mode, dashboard previews must avoid displaying source-bearing diffs unless the customer explicitly enables secure patch sync.
- Users can promote dry-run output into a PR when policy allows.
- Dry-run tasks must be labeled clearly in audit logs and UI.

#### FR-ENT-010: Runner Fleet View

Enterprise customers must be able to manage multiple runners from one UI.

Fleet view fields:

- Runner name.
- Runner group.
- Version.
- Status.
- Last heartbeat.
- Capacity.
- Supported capabilities.
- LLM provider.
- Git provider.
- Current tasks.
- Recent failures.
- Policy profile.

Requirements:

- Admins can pause, drain, revoke, and group runners.
- Admins can route tasks by repository, component, region, sensitivity, and capability.
- Fleet health should be visible in dashboards and API.

#### FR-ENT-011: Runner Version Management

Vericov must support runner update management while allowing enterprise control.

Requirements:

- SaaS shows latest available runner versions.
- Runner reports current version and capability manifest.
- Admins can configure update channels: manual, stable, preview.
- Admins can stage rollout by runner group.
- Runner can reject tasks requiring unsupported capabilities.
- Security advisories can flag vulnerable runner versions.

#### FR-ENT-012: Bring Your Own Git

Runner must support customer Git providers.

Required providers:

- GitHub Enterprise.
- GitLab self-hosted.
- Bitbucket Server / Data Center.

Requirements:

- Support provider-specific PR creation, comments, status checks, and code annotations.
- Support least-privilege tokens or app installations.
- Support custom certificate authorities and internal network endpoints.
- Support mapping Vericov users to Git provider identities.

### 8.4 Platform and Integrations

#### FR-PLT-001: Monorepo Support

Vericov must provide first-class monorepo coverage.

Requirements:

- Per-package coverage.
- Component rollups.
- Ownership rollups.
- Path-based gates.
- Package-specific badges.
- Package dependency graph.
- Partial CI and sparse checkout awareness.
- Large-report ingestion that does not time out on large monorepos.
- Config inheritance by package.

Acceptance criteria:

- A 500-package monorepo can upload sharded coverage and produce package-level checks.
- A PR touching one package does not fail because unrelated packages did not upload coverage.
- Platform engineers can see repository, domain, team, and package rollups.

#### FR-PLT-002: IDE Plugin

Vericov must provide IDE plugins for VS Code and JetBrains.

Features:

- Inline coverage display.
- Coverage gap explanations on hover.
- Agent suggestions on hover.
- Coverage debt creation.
- Link to PR coverage report.
- Run local Vericov analysis.
- Show flaky test warnings near test files.

Requirements:

- IDE plugin should work with local coverage files without SaaS login for basic display.
- Authenticated mode should show historical and team context.
- Enterprise mode must respect runner and source-boundary policies.

#### FR-PLT-003: SBOM and Compliance Mapping

Vericov must map coverage and test evidence to compliance controls for SOC2, ISO, and similar frameworks.

Requirements:

- Link services, packages, and controls to coverage evidence.
- Track test evidence for critical controls.
- Export control coverage summaries.
- Support auditor-friendly evidence reports.
- Support SBOM inputs to map dependencies/components to coverage ownership.
- Show gaps where critical components lack meaningful tests.

Notes:

- Vericov should not claim to certify compliance.
- Vericov should provide evidence artifacts that support customer compliance programs.

#### FR-PLT-004: Coverage API

Vericov must expose APIs for customers to build custom dashboards and integrations.

API resources:

- Organizations.
- Repositories.
- Commits.
- Pull requests.
- Reports.
- Files.
- Components.
- Flags.
- Gates.
- Badges.
- Trends.
- Test results.
- Flaky tests.
- Mutation summaries.
- Coverage debt.
- Agent runs.
- Runner fleet.
- Audit logs.
- Compliance mappings.

Requirements:

- REST API for common integrations.
- GraphQL or analytical query API for dashboards.
- Webhooks for report complete, gate failed, agent PR opened, debt expired, flake detected, and runner unhealthy.
- API keys scoped by organization, repository, resource, and action.
- Rate limits and audit logs for API use.

#### FR-PLT-005: Self-Hosted SaaS Option

Vericov must eventually support a full air-gapped deployment for highly sensitive enterprises.

Requirements:

- Deployable control plane, data plane, and runner.
- No external network dependency after installation.
- Offline license activation path.
- Private model provider support.
- Private package/image registry support.
- Backup and restore.
- Upgrade orchestration.
- Audit export.

Phasing:

- Phase 1: SaaS plus self-hosted runner.
- Phase 2: Customer-managed data plane.
- Phase 3: Fully self-hosted and air-gapped platform.

## 9. Technical Design

### 9.1 High-Level Architecture

Core services:

- Web app: dashboards, PR reports, runner fleet, policy, audit, chat.
- API gateway: authentication, authorization, rate limits, tenant isolation.
- Coverage ingestion service: upload validation, parsing, normalization, merge orchestration.
- Coverage storage service: normalized coverage maps, summary metrics, historical trend storage.
- Diff service: Git diff mapping, patch coverage, line remapping, base/head comparison.
- Gate engine: deterministic policy and threshold evaluation.
- Test intelligence service: test result ingestion, flake detection, test quality analysis.
- Mutation service: targeted mutation task planning and result aggregation.
- Agent orchestration service: task creation, leasing, status tracking, agent run records.
- Runner control plane: runner registration, heartbeat, capability discovery, task queues.
- Policy service: organization/repository/path rules and decision logging.
- Notification service: PR comments, checks, Slack, Teams, email, webhooks.
- Badge service: signed and public badge rendering.
- Audit service: immutable audit event collection and export.
- API service: public REST/GraphQL API.

Customer-side components:

- Coverage upload CLI.
- Agent Runner SDK.
- Git provider adapter.
- LLM provider adapter.
- Local audit logger.
- Local task executor.

### 9.2 Data Flow: Coverage Upload

1. CI runs tests and generates coverage files.
2. `vericov upload` validates config and report paths.
3. CLI sends report metadata, coverage maps, test summaries, commit SHA, branch, PR ID, flags, and CI metadata.
4. Ingestion service validates auth, repo access, idempotency, and report format.
5. Parser normalizes coverage into canonical file/path/line/branch/function/statement records.
6. Merge service combines reports by commit, flag, package, and component.
7. Diff service compares base and head coverage.
8. Gate engine evaluates thresholds and policies.
9. Notification service updates Git checks, PR comments, dashboard, badges, and webhooks.

### 9.3 Data Flow: Agent Auto-Fix

1. Gate or risk engine identifies uncovered high-risk behavior.
2. Agent orchestration service creates a task with repository, commit, PR, risk summary, policy references, and desired action.
3. SaaS-hosted worker or enterprise runner leases the task.
4. Executor checks out repository at the target commit.
5. Agent inspects changed code, tests, coverage reports, and config.
6. Agent proposes a minimal test plan.
7. Policy engine approves, denies, or switches to dry-run.
8. Agent writes tests in a branch or local workspace.
9. Runner executes configured tests and optional coverage/mutation checks.
10. Agent iterates until success, budget exhaustion, or policy stop.
11. Runner opens PR or uploads dry-run result.
12. SaaS records agent run, audit events, status, and allowed summaries.

### 9.4 Data Flow: Enterprise Runner

1. Admin registers runner with one-time token.
2. Runner exchanges token for identity and scoped polling credentials.
3. Runner heartbeats with version, capabilities, provider configuration, and capacity.
4. SaaS enqueues metadata-only task.
5. Runner polls, leases task, validates tenant/repo/policy.
6. Runner performs all source-bearing work locally.
7. Runner returns metadata, status, audit hashes, and allowed output.
8. SaaS displays results according to tenant privacy policy.

### 9.5 Canonical Data Model

The following entities should exist conceptually. Exact database schema can evolve during implementation.

- Tenant.
- Organization.
- User.
- Team.
- Repository.
- RepositoryProviderInstallation.
- Commit.
- Branch.
- PullRequest.
- CoverageReport.
- CoverageFile.
- CoverageMetric.
- CoverageSegment.
- ReportFlag.
- Component.
- Package.
- GatePolicy.
- GateEvaluation.
- TestRun.
- TestSuite.
- TestCase.
- FlakyTestFinding.
- MutationRun.
- MutationFinding.
- CoverageDebtItem.
- AgentRun.
- AgentTask.
- Runner.
- RunnerGroup.
- RunnerCapability.
- PolicyRule.
- PolicyDecision.
- AuditEvent.
- Badge.
- Notification.
- ComplianceControlMapping.

### 9.6 Configuration Model

Primary config file: `vericov.yml`.

Example:

```yaml
version: 1

coverage:
  project:
    target: 82
    max_drop: 1
  patch:
    target: 75
  metrics:
    line: true
    branch: true
    function: true
    statement: true

components:
  payments:
    paths:
      - services/payments/**
    owners:
      - team-payments
    gates:
      patch:
        target: 90
      branch:
        target: 85

monorepo:
  packages:
    - name: web
      path: apps/web
    - name: api
      path: services/api

agent_policy:
  default_mode: suggest
  allow_prs: true
  max_iterations: 3
  allowed_write_paths:
    - "**/*test*"
    - "**/__tests__/**"
  dry_run_paths:
    - services/payments/**
    - services/auth/**
  denied_paths:
    - infra/production/**

mutation:
  enabled: true
  changed_files_only: true
  min_score: 70
  max_runtime_minutes: 15

debt:
  require_owner: true
  max_age_days: 90
  block_critical_paths: true

runner:
  required_for_private_repos: true
  source_upload: false
  allowed_llm_providers:
    - azure_openai
    - bedrock
    - ollama

notifications:
  pr_comments: high_signal
  slack:
    enabled: true
    channel: "#eng-quality"
```

### 9.7 Public Interfaces

#### CLI

Required commands:

```bash
vericov upload --coverage coverage/lcov.info --test-results junit.xml --flag unit --component api
vericov validate-config
vericov local-report
vericov finalize --commit "$GIT_SHA"
vericov runner register --token "$VERICOV_RUNNER_REGISTRATION_TOKEN"
vericov runner doctor
```

Upload command requirements:

- Support multiple `--coverage` arguments.
- Support multiple `--test-results` arguments.
- Support `--flag`, `--component`, `--package`, `--commit`, `--branch`, `--pr`, and `--build-url`.
- Support environment auto-detection for major CI providers.
- Support JSON output for CI scripting.

#### PR Commands

Required commands:

- `/vericov explain`
- `/vericov fix-tests`
- `/vericov dry-run`
- `/vericov mutation`
- `/vericov mark-debt`
- `/vericov rerun-risk`
- `/vericov quarantine-flake`

Each command must produce a visible audit trail.

#### API

The API should expose:

- Report upload and finalization.
- Coverage summaries and detailed file views.
- Gate evaluations.
- PR comments and status check data.
- Agent run lifecycle.
- Runner registration and fleet management.
- Policy configuration.
- Audit export.
- Webhook subscriptions.

### 9.8 Security Architecture

Security requirements:

- Validate every upload and task at tenant, repository, commit, and permission boundaries.
- Use signed upload tokens or tokenless CI identity where possible.
- Use short-lived task tokens for runners.
- Never store customer LLM secrets in SaaS unless explicitly configured.
- Do not upload source code in enterprise metadata-only mode.
- Redact secrets from logs, prompts, command output, and audit events.
- Encrypt data at rest and in transit.
- Use per-tenant encryption keys where feasible.
- Apply strict RBAC to policies, runners, audit logs, and source-bearing features.
- Enforce policy in the runner before any file write, Git operation, LLM request, or PR creation.

Threats to account for:

- Malicious coverage upload spoofing another commit.
- Cross-tenant task leakage.
- Prompt injection through repository code or tests.
- Agent modifying production files outside policy.
- Runner token theft.
- Leaking source through SaaS summaries or chat responses.
- PR comment spam or noisy automation.
- Untrusted fork pull requests.

### 9.9 Privacy Modes

Vericov should support explicit privacy modes:

1. Public SaaS mode
   - Suitable for open source.
   - Source snippets may appear in public PR comments if already public.

2. Private SaaS mode
   - Coverage metadata and optional source snippets stored in SaaS.
   - Customer opts into source-bearing features.

3. Metadata-only SaaS mode
   - SaaS stores coverage maps, summaries, risk metadata, and audit events.
   - Source-bearing agent work runs in enterprise runner.
   - Dashboard does not display source diffs unless allowed.

4. Self-hosted / air-gapped mode
   - Control plane, data plane, and runners run in customer environment.
   - No required external network access.

### 9.10 Observability

Internal observability:

- Upload parse success/failure rate.
- Report merge latency.
- Gate evaluation latency.
- PR comment/check latency.
- Agent task queue latency.
- Runner lease failure rate.
- Runner heartbeat health.
- Agent success/failure/timeout rate.
- Generated PR acceptance rate.
- Flaky-test detection precision.
- Mutation task runtime.
- API error rate and latency.

Customer-facing observability:

- Runner fleet health.
- Task history.
- Gate history.
- Agent run history.
- Audit exports.
- Coverage debt burn-down.
- Flake trend.
- Mutation score trend.

## 10. User Experience

### 10.1 PR Experience

The PR surface should answer five questions quickly:

1. Did coverage pass?
2. What changed?
3. What is risky?
4. What can Vericov fix?
5. What requires human judgment?

PR comments should include:

- Summary coverage delta.
- Patch coverage.
- Failed gates.
- Top uncovered risky paths.
- Agent action buttons or slash commands.
- Link to full report.

Avoid:

- Long tables by default.
- Repeating unchanged file coverage.
- Generic AI review feedback.
- Commenting on every uncovered line.

### 10.2 Dashboard

Primary dashboard views:

- Repository overview.
- PR report.
- Commit report.
- Component/package report.
- Historical trends.
- Coverage debt.
- Test intelligence.
- Flaky tests.
- Mutation testing.
- Agent runs.
- Runner fleet.
- Policy.
- Audit logs.
- Compliance evidence.

### 10.3 Chat / Natural Language

The chat experience should behave like an evidence-backed analyst:

- Cite reports and commits.
- Provide concise answers.
- Offer safe next actions.
- Respect permissions and privacy mode.
- Escalate to dry-run when policy blocks direct action.

## 11. Rollout Plan

### Phase 0: Foundation

- Define coverage canonical model.
- Build CLI skeleton.
- Support LCOV, Cobertura, JaCoCo, Go, and coverage.py.
- Build upload API, auth, and report storage.
- Build basic dashboard and GitHub PR checks.

### Phase 1: Codecov Parity MVP

- Multi-report merge.
- PR diff coverage.
- Project and patch gates.
- Flags/components.
- Historical trends.
- Badges.
- GitHub App and GitHub Actions docs.
- Public repo onboarding.

### Phase 2: Agentic MVP

- Coverage reasoning.
- Test gap detection.
- Agent-assisted PR review.
- Dry-run test generation.
- Auto-open test PRs for TypeScript and Python.
- Test quality analysis v1.
- Basic coverage debt tracking.

### Phase 3: Test Intelligence

- JUnit/test result ingestion.
- Flaky-test detection.
- Flaky-test remediation agent.
- Targeted mutation testing for changed files.
- Risk scoring.
- Slack and Teams natural language interface.

### Phase 4: Enterprise Runner

- Open-source Agent Runner SDK.
- Outbound-only task protocol.
- Runner registration and token rotation.
- BYOLLM providers.
- GitHub Enterprise, GitLab self-hosted, Bitbucket Server.
- Runner fleet view.
- Policy engine.
- Audit exports.

### Phase 5: Platform Expansion

- VS Code and JetBrains plugins.
- SBOM and compliance mapping.
- Coverage API and webhooks.
- Advanced monorepo rollups.
- Runner version management.
- Customer-managed data plane.

### Phase 6: Air-Gapped Platform

- Full self-hosted SaaS deployment.
- Offline licensing.
- Private image/package registry support.
- Air-gapped upgrades.
- Enterprise backup and restore.

## 12. Success Metrics

Adoption:

- Repositories connected.
- Weekly active repositories.
- Upload success rate.
- PRs with Vericov checks.
- Open source badge adoption.

Coverage quality:

- Patch coverage pass rate.
- Coverage regression detection rate.
- Coverage debt created/resolved.
- Mutation score improvement.
- Weak-test finding resolution rate.

Agent value:

- Agent test PRs opened.
- Agent test PR acceptance rate.
- Average coverage improvement per agent PR.
- Average reviewer time saved.
- Agent failure/timeout rate.
- Percentage of agent runs requiring human correction.

Enterprise:

- Runner uptime.
- Runner task success rate.
- Runner version compliance.
- Policy-denied unsafe actions.
- Audit export usage.
- BYOLLM adoption.

Developer experience:

- PR comment dismissal rate.
- False-positive reports.
- Time from upload to check result.
- Time from failed gate to actionable explanation.
- Chat question success rate.

## 13. Acceptance Test Scenarios

### Scenario 1: Multi-Type Coverage Upload

Given a repository uploads line, branch, function, and statement reports from multiple CI jobs, Vericov merges the reports into one commit view and shows correct project, patch, file, component, and flag coverage.

### Scenario 2: PR Coverage Gate Failure

Given a pull request reduces patch coverage below the configured threshold, Vericov fails the Git provider check, comments with the uncovered changed lines, and links to the report.

### Scenario 3: Agent Generates Missing Tests

Given a risky changed branch is uncovered, Vericov classifies the gap, proposes a test, writes the test in an allowed path, runs the configured command, and opens a reviewable PR with an explanation.

### Scenario 4: Test Quality Warning

Given a test executes changed code but has no meaningful assertions, Vericov flags it as coverage theater and recommends a stronger assertion without treating it as ordinary uncovered code.

### Scenario 5: Coverage Debt

Given a team defers a low-risk uncovered fallback branch, Vericov creates a debt item with owner, reason, risk, and expiration date. When the expiration date passes, the item returns to active gate evaluation.

### Scenario 6: Enterprise Runner Privacy

Given an enterprise repository is configured for metadata-only SaaS mode, Vericov dispatches an agent task to the self-hosted runner, the runner analyzes source locally, and SaaS receives only allowed metadata and audit events.

### Scenario 7: Policy Blocks Sensitive Auto-Fix

Given a changed file matches `services/payments/**`, Vericov policy forces dry-run mode. The agent may generate a local preview, but it cannot open a PR unless a permitted user approves promotion.

### Scenario 8: BYOLLM Execution

Given a runner is configured with Azure OpenAI, Vericov routes agent tasks to that provider locally, logs provider metadata, and does not store customer API credentials in SaaS.

### Scenario 9: Flaky Test Remediation

Given a test alternates pass/fail across identical commits, Vericov identifies it as flaky, classifies the likely cause, recommends a fix, and optionally opens a remediation PR.

### Scenario 10: Monorepo Rollup

Given a monorepo PR touches one package, Vericov evaluates only relevant package gates while preserving repository and team rollups.

## 14. Open Questions

- Which language ecosystems should receive first-class agent test generation after TypeScript and Python?
- Should Vericov implement GraphQL in v1 or start with REST plus export endpoints?
- Which mutation testing engines should be bundled versus adapter-only?
- How much source context should private SaaS mode allow by default?
- Should compliance mapping depend on customer-provided control metadata or ship with default SOC2/ISO templates?
- What free-tier limits should apply to public repositories, private repositories, and agent minutes?

## 15. Competitive Context

Codecov sets the expected baseline for coverage reports, PR comments, status checks, flags/components, report merging, test analytics, bundle analysis, and integrations. Vericov must match the baseline enough to make migration credible, then win on agentic remediation, enterprise runner privacy, monorepo scale, and quality-of-tests reasoning.

Reference surfaces:

- Codecov product features: https://about.codecov.io/product/features/
- Codecov supported report formats: https://docs.codecov.com/docs/supported-report-formats
- Codecov report merging: https://docs.codecov.com/docs/merging-reports
- Codecov test analytics: https://docs.codecov.com/docs/test-analytics
- Codecov bundle analysis: https://docs.codecov.com/docs/javascript-bundle-analysis
- Qodo code review / PR agent direction: https://docs.qodo.ai/qodo-documentation/code-review
- SonarQube quality gate precedent: https://docs.sonarsource.com/sonarqube-cloud/standards/quality-gates

## 16. Implementation Handoff

Recommended next engineering lanes:

1. Build the coverage ingestion and canonical coverage model.
2. Build GitHub App integration, PR checks, and patch coverage.
3. Build `vericov.yml` config validation and gate engine.
4. Build basic dashboard and badge service.
5. Build agent task model and dry-run coverage reasoning.
6. Build self-hosted runner skeleton with outbound polling.
7. Add autonomous test-generation for one ecosystem at a time.

The first production milestone should be a credible Codecov migration path for GitHub repositories. The second milestone should prove the agentic wedge: Vericov identifies a real uncovered behavior, generates a useful test, runs it, and opens a clean PR that a human reviewer accepts.
