# Monorepo Component Coverage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add hierarchical monorepo components to `.vericov.yml`, preserve the exact configuration with each upload, calculate deterministic per-component coverage after exclusions, evaluate inherited component gates, and return the nested component tree from the report API.

**Architecture:** The Python CLI remains the YAML authority and emits a canonical `version`/`ignore`/`components` snapshot plus SHA-256. A shared Java `component-config` library independently validates and canonicalizes that snapshot for both backend services. Coverage Analysis filters ignored files first, assigns remaining files to one leaf or `unassigned`, builds immutable ancestor rollups, evaluates config-defined gates, and persists string component keys. The Upload Service reads those rows into a stable nested API projection.

**Tech Stack:** Python 3.12+, PyYAML, pytest, Java 25, Jakarta JSON, Helidon MP, PostgreSQL, Maven, JUnit 6, JaCoCo.

---

## Task 1: Shared Path Matching And Snapshot Contracts

**Files:**
- Modify: `libraries/coverage-ignore/src/main/java/dev/vericov/ignore/CoverageIgnoreRules.java`
- Create: `libraries/coverage-ignore/src/main/java/dev/vericov/ignore/CoveragePathPattern.java`
- Modify: `libraries/coverage-ignore/src/test/java/dev/vericov/ignore/CoverageIgnoreRulesContractTest.java`
- Modify: `clis/coverage-upload/src/vericov_coverage_upload/domain/coverage_ignore.py`
- Create: `test-contracts/component-path-matches.tsv`
- Create: `test-contracts/component-specificity.tsv`
- Create: `clis/coverage-upload/tests/test_component_path_pattern.py`

- [x] Add failing Java and Python contract tests for positive component matching, normalization, root anchoring, specificity, malformed ranges, negation rejection, absolute paths, and parent traversal.
- [x] Extract a positive `CoveragePathPattern` abstraction in both languages and make ordered ignore rules reuse the same matcher semantics.
- [x] Define specificity as literal segments and literal characters before the first unescaped wildcard.
- [x] Run `mvn -pl libraries/coverage-ignore test`.
- [x] Run `uv run pytest -q tests/test_coverage_ignore.py tests/test_component_path_pattern.py` from `clis/coverage-upload`.

## Task 2: CLI Component Model And Canonical Snapshot

**Files:**
- Create: `clis/coverage-upload/src/vericov_coverage_upload/domain/component_config.py`
- Modify: `clis/coverage-upload/src/vericov_coverage_upload/domain/config.py`
- Modify: `clis/coverage-upload/src/vericov_coverage_upload/infrastructure/config_loader.py`
- Modify: `clis/coverage-upload/src/vericov_coverage_upload/domain/upload_request.py`
- Modify: `clis/coverage-upload/src/vericov_coverage_upload/application/upload_workflow.py`
- Modify: `clis/coverage-upload/tests/test_config.py`
- Modify: `clis/coverage-upload/tests/test_upload.py`
- Modify: `clis/coverage-upload/tests/test_http.py`
- Create: `clis/coverage-upload/tests/test_component_config.py`
- Create: `test-contracts/component-config-snapshots.json`

- [x] Add failing tests for valid nested parents/leaves, defaults, owner and gate inheritance, invalid shapes, duplicate/reserved keys, duplicate patterns, unknown fields, and all resource limits.
- [x] Add immutable `ComponentDefinition`, `ComponentGates`, and `ConfigSnapshot` values.
- [x] Reject config files larger than 256 KiB before YAML parsing.
- [x] Parse optional top-level `components`; preserve declaration, path, owner, and ignore order.
- [x] Canonicalize with sorted object keys, compact separators, array order preserved, and explicit defaults.
- [x] Add top-level `components` and `config_sha256` to upload JSON and idempotency material.
- [x] Keep omitted-components behavior repository-only while continuing to send an ignore-only snapshot.
- [x] Run `uv run pytest -q tests/test_component_config.py tests/test_config.py tests/test_upload.py tests/test_http.py`.

## Task 3: Shared Java Component Configuration Library

**Files:**
- Modify: `pom.xml`
- Create: `libraries/component-config/pom.xml`
- Create: `libraries/component-config/src/main/java/dev/vericov/componentconfig/ComponentConfigException.java`
- Create: `libraries/component-config/src/main/java/dev/vericov/componentconfig/ComponentGates.java`
- Create: `libraries/component-config/src/main/java/dev/vericov/componentconfig/ComponentDefinition.java`
- Create: `libraries/component-config/src/main/java/dev/vericov/componentconfig/ComponentConfigSnapshot.java`
- Create: `libraries/component-config/src/main/java/dev/vericov/componentconfig/ComponentConfigJson.java`
- Create: `libraries/component-config/src/main/java/dev/vericov/componentconfig/ComponentAssignment.java`
- Create: `libraries/component-config/src/main/java/dev/vericov/componentconfig/ComponentResolver.java`
- Create: `libraries/component-config/src/test/java/dev/vericov/componentconfig/ComponentConfigSnapshotTest.java`
- Create: `libraries/component-config/src/test/java/dev/vericov/componentconfig/ComponentConfigContractTest.java`

- [x] Add failing tests that load CLI-produced snapshot fixtures and assert exact canonical JSON and SHA-256 parity.
- [x] Validate keys, shape, owners, gates, path patterns, duplicates, hierarchy limits, and canonical snapshot size.
- [x] Flatten immutable metadata for each node: parent key, key path, depth, sibling position, effective owners, and effective gates.
- [x] Resolve files by highest static specificity and fail with conflicting keys on a highest-specificity tie.
- [x] Add the module to the reactor and service dependencies.
- [x] Run `mvn -pl libraries/component-config -am test`.

## Task 4: Upload API Validation And Snapshot Persistence

**Files:**
- Modify: `services/upload/pom.xml`
- Modify: `services/upload/src/main/java/dev/vericov/upload/api/CreateUploadHttpRequest.java`
- Create: `services/upload/src/main/java/dev/vericov/upload/api/ComponentConfigHttpRequest.java`
- Modify: `services/upload/src/main/java/dev/vericov/upload/domain/CreateUploadCommand.java`
- Modify: `services/upload/src/main/java/dev/vericov/upload/application/QueuedUpload.java`
- Modify: `services/upload/src/main/java/dev/vericov/upload/application/UploadApplicationService.java`
- Modify: `services/upload/src/main/java/dev/vericov/upload/adapter/jdbc/JdbcUploadRepository.java`
- Modify: `services/upload/src/test/java/dev/vericov/upload/application/UploadApplicationServiceTest.java`
- Modify: `services/upload/src/test/java/dev/vericov/upload/api/UploadResourceTest.java`
- Modify: `services/upload/src/test/java/dev/vericov/upload/api/UploadResourceIntegrationTest.java`
- Modify: `services/upload/src/test/java/dev/vericov/upload/adapter/jdbc/JdbcUploadRepositoryTest.java`
- Modify: `infra/supabase/volumes/db/vericov.sql`

- [x] Add failing tests for missing, ignore-only, and full snapshots; supplied hash mismatch; invalid snapshots before authentication/storage; and exact JDBC bindings.
- [x] Convert HTTP component values to the shared immutable model.
- [x] Rebuild and validate the snapshot before any external side effect.
- [x] Accept transitional ignore-only requests by creating and hashing the normalized empty-components snapshot.
- [x] Persist `config_snapshot_json` and `config_sha256`; stop treating `ignore_rules` as a separate authority while retaining compatibility reads.
- [x] Reload the exact snapshot into `QueuedUpload` and include the hash in upload identity.
- [x] Run `mvn -pl services/upload -am test`.

## Task 5: Coverage Assignment And Hierarchical Rollups

**Files:**
- Modify: `services/coverage-analysis/pom.xml`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoverageAnalysisInput.java`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/adapter/jdbc/JdbcCoverageAnalysisInputRepository.java`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoverageFileSummary.java`
- Replace: `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoverageComponentRollup.java`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoverageReport.java`
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/ComponentCoverageCalculator.java`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/application/DefaultCoverageAnalysisProcessor.java`
- Create: `services/coverage-analysis/src/test/java/dev/vericov/analysis/coverage/ComponentCoverageCalculatorTest.java`
- Modify: `services/coverage-analysis/src/test/java/dev/vericov/analysis/application/DefaultCoverageAnalysisProcessorTest.java`
- Modify: `services/coverage-analysis/src/test/java/dev/vericov/analysis/adapter/jdbc/JdbcCoverageAnalysisInputRepositoryTest.java`

- [x] Add failing tests proving ignored files are removed before assignment and ambiguity checks.
- [x] Load, validate, and hash-check the stored snapshot; map corruption to `NonRetryableAnalysisException`.
- [x] Preserve repository-only behavior when components are empty.
- [x] Assign each included file to one leaf or `unassigned`; report ambiguity as non-retryable.
- [x] Build one rollup for every configured node, including zero-valued nodes, by aggregating leaf metrics upward without sibling leakage.
- [x] Add an `unassigned_files` warning only when unmatched included files exist.
- [x] Preserve repository total equals root rollups plus `unassigned` for every metric.
- [x] Run focused coverage-analysis tests.

## Task 6: Component Gates And Report Outcome

**Files:**
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/gates/GateEvaluation.java`
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/gates/ComponentGateEvaluator.java`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/application/DefaultCoverageAnalysisProcessor.java`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoverageReport.java`
- Create: `services/coverage-analysis/src/test/java/dev/vericov/analysis/gates/ComponentGateEvaluatorTest.java`
- Modify: `services/coverage-analysis/src/test/java/dev/vericov/analysis/application/DefaultCoverageAnalysisProcessorTest.java`

- [x] Add failing tests for inherited metrics, per-metric overrides, parent descendant totals, leaf totals, `0/0 = 100%`, and no gates on `unassigned`.
- [x] Emit one blocking `component_config` evaluation per effective gate with component key/path scope.
- [x] Combine repository and component evaluations into `failed`, `warning`, `passed`, or `not_evaluated`.
- [x] Keep analysis/upload completion successful when gate status is failed.
- [x] Disable legacy database `component_coverage` gates from the active repository gate list.
- [x] Run focused gate and processor tests.

## Task 7: String-Key Persistence And Report API

**Files:**
- Modify: `infra/supabase/volumes/db/vericov.sql`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/gaps/CoverageGapFinding.java`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/gaps/CoverageGapExtractor.java`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/adapter/jdbc/JdbcCoverageReportRepository.java`
- Modify: `services/coverage-analysis/src/test/java/dev/vericov/analysis/adapter/jdbc/JdbcCoverageReportRepositoryTest.java`
- Modify: `services/upload/src/main/java/dev/vericov/upload/application/CoverageReportDetails.java`
- Create: `services/upload/src/main/java/dev/vericov/upload/application/ComponentCoverageDetails.java`
- Create: `services/upload/src/main/java/dev/vericov/upload/application/CoverageWarningDetails.java`
- Modify: `services/upload/src/main/java/dev/vericov/upload/api/CoverageReportHttpResponse.java`
- Create: `services/upload/src/main/java/dev/vericov/upload/api/ComponentCoverageHttpResponse.java`
- Modify: `services/upload/src/main/java/dev/vericov/upload/adapter/jdbc/JdbcUploadRepository.java`
- Modify: `services/upload/src/test/java/dev/vericov/upload/adapter/jdbc/JdbcUploadRepositoryTest.java`
- Modify: `services/upload/src/test/java/dev/vericov/upload/api/UploadResourceTest.java`
- Modify: `services/upload/src/test/java/dev/vericov/upload/api/UploadResourceIntegrationTest.java`

- [x] Add failing JDBC tests for all new report, file, rollup, finding, and gate bindings.
- [x] Migrate UUID component columns to nullable string keys and rebuild component rollup rows around `(coverage_report_id, component_key)`.
- [x] Persist snapshot hash, gate status, warnings, hierarchy metadata, effective gates, counters, and file counts transactionally.
- [x] Query rollups/evaluations in stable path and sibling order and build an immutable nested tree.
- [x] Add `config_sha256`, `gate_status`, `warnings`, and `components` to the report response while retaining existing top-level fields.
- [x] Run upload and coverage-analysis persistence/API tests.

## Task 8: Documentation, Public Contracts, And End-To-End Verification

**Files:**
- Modify: `README.md`
- Modify: `clis/coverage-upload/README.md`
- Modify: `docs/SELF_HOSTING.md`
- Modify: `tests/test_public_repo.py`
- Create: `test-contracts/fixtures/monorepo-component-coverage/README.md`

- [x] Add failing public-contract checks for the canonical config example, exclusions-before-components wording, stable keys, hierarchy, gates, and future-upload-only behavior.
- [x] Document a complete `.vericov.yml` example and report outcome semantics.
- [x] Add a smoke fixture covering ignored, re-included, assigned, overlapping-specific, and unassigned files.
- [x] Run `uv run pytest -q tests` from the repository root.
- [x] Run `uv run pytest -q --cov=vericov_coverage_upload --cov-report=term-missing --cov-fail-under=80` from `clis/coverage-upload`.
- [x] Run `mvn --batch-mode verify`.

## Task 9: Review, Security, And Delivery

- [x] Review the complete diff for scope, immutability, input validation, migration safety, SQL parameterization, and accidental secret exposure.
- [x] Run focused tests after every review fix, then repeat all verification commands from Task 8.
- [x] Confirm Java modules and CLI remain above the repository's 80% coverage gates.
- [ ] Commit the implementation with conventional commit messages.
- [ ] Push `feat/monorepo-component-coverage`.
- [ ] Create a PR with design summary, migration notes, compatibility behavior, and exact test results.
