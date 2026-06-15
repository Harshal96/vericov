# Coverage File Exclusions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement repository-controlled source-file coverage exclusions from `.vericov.yml` through upload persistence and coverage analysis.

**Architecture:** The Python CLI and a small shared Java library implement equivalent ordered gitignore-style rule semantics, verified by the same root-level contract fixtures. The upload service validates and stores an immutable JSON snapshot on each upload; coverage analysis reloads that snapshot, validates it again, and filters normalized parsed files before merging.

**Tech Stack:** Python 3.9+, PyYAML, pytest, Java 25, Maven, JUnit, Helidon, PostgreSQL JSONB.

---

### Task 1: Shared Rule Contract And Python Matcher

**Files:**
- Create: `test-contracts/coverage-ignore-matches.tsv`
- Create: `test-contracts/coverage-ignore-invalid.tsv`
- Create: `clis/coverage-upload/src/vericov_coverage_upload/domain/coverage_ignore.py`
- Create: `clis/coverage-upload/tests/test_coverage_ignore.py`

- [x] Add shared match and invalid-rule cases covering normalization, anchoring, basename matching, directory rules, wildcards, ranges, globstar, ordered negation, case sensitivity, traversal, absolute Windows paths, malformed ranges, and invalid escapes.
- [x] Write Python tests that load every shared case and fail because the matcher does not exist.
- [x] Implement immutable compiled rules with ordered last-match-wins behavior.
- [x] Run `uv run pytest -q tests/test_coverage_ignore.py`.

### Task 2: Canonical CLI Config And Upload Snapshot

**Files:**
- Modify: `clis/coverage-upload/src/vericov_coverage_upload/domain/config.py`
- Modify: `clis/coverage-upload/src/vericov_coverage_upload/domain/upload_request.py`
- Modify: `clis/coverage-upload/src/vericov_coverage_upload/infrastructure/config_loader.py`
- Modify: `clis/coverage-upload/src/vericov_coverage_upload/application/upload_workflow.py`
- Modify: `clis/coverage-upload/src/vericov_coverage_upload/cli/commands/config.py`
- Modify: `clis/coverage-upload/src/vericov_coverage_upload/cli/commands/upload.py`
- Modify: `clis/coverage-upload/src/vericov_coverage_upload/cli/options/common.py`
- Modify: `clis/coverage-upload/tests/test_config.py`
- Modify: `clis/coverage-upload/tests/test_upload.py`
- Modify: `clis/coverage-upload/tests/test_http.py`
- Modify: `clis/coverage-upload/tests/test_cli.py`

- [x] Write failing tests for `.vericov.yml`-only discovery, legacy rename errors, explicit basename enforcement, ordered top-level `ignore`, malformed YAML, indexed rule errors, request JSON, and idempotency changes.
- [x] Add the immutable ignore tuple to config and request models.
- [x] Validate rules during YAML loading and preserve their exact order in upload JSON and idempotency material.
- [x] Run the focused CLI tests, then the full CLI suite with 80% coverage.

### Task 3: Shared Java Matcher Library

**Files:**
- Create: `libraries/coverage-ignore/pom.xml`
- Create: `libraries/coverage-ignore/src/main/java/dev/vericov/ignore/CoverageIgnoreRules.java`
- Create: `libraries/coverage-ignore/src/main/java/dev/vericov/ignore/InvalidCoverageIgnoreRuleException.java`
- Create: `libraries/coverage-ignore/src/test/java/dev/vericov/ignore/CoverageIgnoreRulesContractTest.java`
- Modify: `pom.xml`
- Modify: `services/upload/pom.xml`
- Modify: `services/coverage-analysis/pom.xml`

- [x] Write a JUnit contract test that loads the same root-level TSV fixtures.
- [x] Add the library module and dependencies from upload and analysis.
- [x] Implement immutable validation and matching equivalent to the Python matcher.
- [x] Run `mvn -pl libraries/coverage-ignore test`.

### Task 4: Upload API Validation And Immutable Persistence

**Files:**
- Modify: `services/upload/src/main/java/dev/vericov/upload/api/CreateUploadHttpRequest.java`
- Modify: `services/upload/src/main/java/dev/vericov/upload/domain/CreateUploadCommand.java`
- Modify: `services/upload/src/main/java/dev/vericov/upload/application/QueuedUpload.java`
- Modify: `services/upload/src/main/java/dev/vericov/upload/application/UploadApplicationService.java`
- Modify: `services/upload/src/main/java/dev/vericov/upload/adapter/jdbc/JdbcUploadRepository.java`
- Modify: `services/upload/src/test/java/dev/vericov/upload/api/UploadResourceIntegrationTest.java`
- Modify: `services/upload/src/test/java/dev/vericov/upload/application/UploadApplicationServiceTest.java`
- Modify: `services/upload/src/test/java/dev/vericov/upload/adapter/jdbc/JdbcUploadRepositoryTest.java`
- Modify: `infra/supabase/volumes/db/vericov.sql`

- [x] Write failing API, service, and JDBC tests for missing/populated/invalid snapshots and exact-order reload.
- [x] Add optional `ignore`, normalize null to an immutable empty list, and validate before authentication or storage side effects.
- [x] Store `ignore_rules` as JSONB and reload it into `QueuedUpload`.
- [x] Run `mvn -pl services/upload test`.

### Task 5: Analysis Input, Filtering, And Terminal Invalid Input

**Files:**
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoverageFileFilter.java`
- Create: `services/coverage-analysis/src/main/java/dev/vericov/analysis/domain/NonRetryableAnalysisException.java`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/coverage/CoverageAnalysisInput.java`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/adapter/jdbc/JdbcCoverageAnalysisInputRepository.java`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/application/DefaultCoverageAnalysisProcessor.java`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/application/UploadAnalysisEventHandler.java`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/application/port/AnalysisJobRepository.java`
- Modify: `services/coverage-analysis/src/main/java/dev/vericov/analysis/adapter/jdbc/JdbcAnalysisJobRepository.java`
- Modify: related unit, JDBC, processor, and handler tests.

- [x] Write failing tests for snapshot reload, post-parse/pre-merge filtering, re-inclusion, multiple artifacts, successful empty `0/0`, preserved test runs, downstream absence, and terminal invalid persisted rules.
- [x] Reload and independently validate the immutable snapshot.
- [x] Filter each parsed coverage result before merge; continue normal report processing even when all files are excluded.
- [x] Mark invalid persisted snapshots failed immediately and dead-letter without retry.
- [x] Run `mvn -pl services/coverage-analysis test`.

### Task 6: Documentation, Smoke Contract, And Verification

**Files:**
- Modify: `README.md`
- Modify: `clis/coverage-upload/README.md`
- Modify: `tests/test_public_repo.py`

- [x] Add failing public contract assertions for the canonical filename and documented ignore semantics.
- [x] Document source-file `ignore`, ordered negation, distinction from `upload.discover.exclude`, future-upload-only behavior, and empty `0/0` reports.
- [x] Run `python3 -m pytest -q tests`.
- [x] Run `uv run pytest -q --cov=vericov_coverage_upload --cov-report=term-missing --cov-fail-under=80` from `clis/coverage-upload`.
- [x] Run `mvn --batch-mode clean verify`, `git diff --check`, and a secret/security diff review.
- [x] Prepare the conventional commit and ready pull request summary with a test plan.
