# Coverage Upload CLI Technical Design And Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an independently buildable Coverage Upload CLI package that provides `vericov upload`, config validation, deterministic file discovery, safe retries, and optional upload-status polling.

**Architecture:** Add a small Typer-based Python CLI under `clis/coverage-upload/`. This subfolder is its own Python project with its own `pyproject.toml`, tests, README, package namespace, and build lifecycle. The CLI packages coverage and test-result artifacts into the existing Upload / Ingestion Service JSON contract. The CLI owns local concerns: config loading, CI/git metadata discovery, artifact discovery, artifact size limits, Base64 encoding, idempotency-key generation, retry behavior, and user-facing output. The backend remains the durable ingestion boundary and should resolve repository identity from repo-scoped API keys when `repository_id` is omitted.

**Tech Stack:** Python 3.9+, uv for dependency management/build/test execution, setuptools build backend, Typer, PyYAML, urllib from the standard library for direct URL calls in v1, pytest for tests, existing Java 25 Helidon upload service for the optional backend contract adjustment.

---

## Product Position

The v1 upload experience should be better than incumbent coverage uploaders by being:

- **Low-friction:** a repo-scoped key plus `vericov upload` should be enough in common CI environments.
- **Deterministic:** the CLI always prints which config, metadata, and files it used.
- **Safe by default:** secrets never live in config files or logs, retries reuse the same idempotency key, and files outside the repo are rejected unless explicitly allowed.
- **CI-native:** errors are concise, exit codes are stable, `--json` is available, and `--wait` makes downstream processing state observable.
- **Honest about limits:** direct JSON uploads have clear client-side size limits until multipart or signed-storage upload exists.

This design intentionally avoids a large plugin system in v1. The first release should be boring, predictable, and easy to debug.

## Current State

- Root `pyproject.toml` reserves the `vericov` package but defines no console script, runtime dependencies, or test dependencies.
- Root `src/vericov/__init__.py` exposes version metadata only.
- There is no dedicated CLI workspace such as `clis/coverage-upload/`.
- `README.md` says the Python client and CLI are future work.
- `services/upload` already exposes `POST /api/v1/uploads`, `GET /api/v1/uploads/{upload_id}`, and artifact listing.
- The upload API currently requires `repository_id` in the request body before authentication.
- `docs/backend/services/03-upload-ingestion-service.md` already describes direct JSON uploads, Base64 artifacts, idempotent retries, and polling.

## Completion Definition

- `cd clis/coverage-upload && uv run vericov --version` runs the independently managed `vericov` console script.
- `vericov --version` prints the package version.
- `vericov config validate` validates `vericov.yml` or `.vericov.yml` with useful line-level or field-level errors where available.
- `vericov upload` discovers metadata and files, validates inputs, submits a JSON upload request, and prints upload status details.
- `vericov upload --wait` polls until `completed`, `failed`, or timeout.
- `vericov upload --dry-run` shows the resolved upload plan without sending artifact bytes.
- Repo-scoped uploads can omit `repository_id`; the upload service resolves the repository from the authenticated API key.
- The CLI validates artifact paths, formats, sizes, duplicate names, and config typos before uploading.
- Transient HTTP and network failures retry with the same `Idempotency-Key`.
- Tests cover config precedence, validation, metadata discovery, file discovery, artifact packaging, retry behavior, status polling, CLI exit codes, and backend omitted-repository behavior.
- README and upload service docs show the minimal CI path and advanced options.

## Scope Boundary

In scope:

- Independent Python CLI package structure under `clis/coverage-upload/`.
- One-shot direct JSON uploads to the existing upload API.
- Repo-scoped API key authentication through `Authorization: Bearer <key>`.
- Explicit artifact paths, config-driven artifact paths, and curated default discovery.
- Coverage artifacts and JUnit-style test results.
- Upload idempotency and retry.
- Optional polling through existing upload status endpoint.
- Small backend change to allow `repository_id` omission for repo-scoped keys.

Out of scope for v1:

- Multipart uploads.
- Direct signed-storage uploads.
- Tokenless OIDC uploads.
- Creating repository API keys.
- Resolving repository IDs from the Vericov control plane by git remote.
- Per-artifact flags or per-artifact components. The current backend model stores flags/component/package at upload level.
- Local coverage parsing or validation of coverage report semantics.
- GitHub Action wrapper.
- Windows service integration. The CLI itself must still run on Windows.
- A shared generated API client dependency. V1 uses direct upload/status URLs through a small replaceable HTTP gateway.

---

## User Experience

### Happy Path

```bash
export VERICOV_API_KEY=vc_live_...
vericov upload
```

Expected behavior:

1. Load config if present.
2. Detect CI/git metadata.
3. Discover coverage artifacts if no explicit artifacts are configured.
4. Validate the upload plan.
5. Submit `POST /api/v1/uploads`.
6. Print upload id, status, and poll URL.

Example output:

```text
Vericov upload accepted
  upload_id: 84cb6d3c-312f-43af-a3fc-5964b2d27626
  status: queued
  poll_url: https://api.vericov.dev/api/v1/uploads/84cb6d3c-312f-43af-a3fc-5964b2d27626
  artifacts: 2 files, 184.2 KiB
```

### CI-Gating Path

```bash
vericov upload --wait --timeout 5m
```

Expected behavior:

- Return `0` only when upload processing reaches `completed`.
- Return a stable nonzero exit code on upload failure, processing failure, validation failure, or timeout.
- Print intermediate status only when status changes, unless `--verbose` is set.

### Explicit Files

```bash
vericov upload \
  --coverage coverage/lcov.info \
  --coverage services/api/target/site/jacoco/jacoco.xml \
  --test-results build/test-results/test/TEST-results.xml \
  --flag unit \
  --component api \
  --package services/api
```

Explicit file flags disable default discovery unless `--discover` is also supplied. This avoids accidentally uploading stale or unrelated reports.

### Config Validation

```bash
vericov config validate
vericov config validate --config .github/vericov.yml
```

Validation should check:

- Known top-level keys only.
- Value types.
- UUID format if `repository_id` is present.
- Artifact globs are strings.
- Format names are supported when explicitly supplied.
- Size limits are positive integers.
- Secrets are not present in config.

### Dry Run

```bash
vericov upload --dry-run
```

Dry run should print the resolved upload plan without artifact `content_base64` and without the API key:

```text
Vericov upload dry run
  config: vericov.yml
  api_url: https://api.vericov.dev
  repository_id: inferred from API key
  commit_sha: abc123
  branch: main
  idempotency_key: vericov-upload-v1-8e4f...
  artifacts:
    - coverage/lcov.info (coverage, lcov, 44.1 KiB)
    - junit.xml (test_results, junit, 12.8 KiB)
```

---

## Configuration Design

### File Names

Support both names:

1. `vericov.yml` is canonical.
2. `.vericov.yml` is accepted for users who expect hidden tool config files.

Default lookup order:

```text
--config <path>
vericov.yml
.vericov.yml
```

If both `vericov.yml` and `.vericov.yml` exist in the project root, fail with:

```text
Found both vericov.yml and .vericov.yml. Choose one or pass --config.
```

Do not search `.github/`, `dev/`, parent directories, or the home directory in v1. That keeps config provenance obvious in CI logs.

### Secret Handling

The API key must not be accepted from config. Supported sources:

1. `--api-key`
2. `VERICOV_API_KEY`

If a config file contains `api_key`, `token`, `upload_token`, or similarly named keys, validation must fail with a message that secrets belong in environment variables or CI secret storage.

### Config Precedence

For non-secret values:

```text
CLI flags
environment variables
config file
auto-detected CI/git metadata
defaults
```

The CLI should expose provenance in `--verbose` output:

```text
branch: main (source: GITHUB_REF_NAME)
commit_sha: abc123 (source: GITHUB_SHA)
api_url: https://api.vericov.dev (source: default)
```

### Versioned Schema

Use a small versioned schema:

```yaml
version: 1

api:
  url: https://api.vericov.dev

upload:
  repository_id: 4d607f16-1af7-4d3b-ac38-06454cba463c
  flags:
    - unit
    - linux
  component: api
  package: services/api

  coverage:
    - coverage/lcov.info
    - services/api/target/site/jacoco/jacoco.xml

  test_results:
    - junit.xml

  discover:
    enabled: true
    roots:
      - .
    include:
      - coverage/**/*.xml
      - coverage/**/*.info
    exclude:
      - .git/**
      - node_modules/**
      - vendor/**
      - dist/**
      - build/tmp/**

  wait: false
  timeout_seconds: 300
  max_artifact_bytes: 26214400
  max_total_bytes: 52428800
```

### Allowed Keys

Top level:

- `version`
- `api`
- `upload`

`api`:

- `url`

`upload`:

- `repository_id`
- `commit_sha`
- `branch`
- `pull_request_number`
- `ci_provider`
- `ci_build_id`
- `ci_build_url`
- `flags`
- `component`
- `package`
- `coverage`
- `test_results`
- `discover`
- `wait`
- `timeout_seconds`
- `max_artifact_bytes`
- `max_total_bytes`

`upload.discover`:

- `enabled`
- `roots`
- `include`
- `exclude`

Unknown keys fail validation. A typo in CI config should not silently alter upload behavior.

---

## CLI Contract

### Commands

```text
vericov --version
vericov config validate [--config PATH] [--json]
vericov upload [OPTIONS]
```

### Upload Options

Required by value source, not necessarily by flag:

- `--api-key`: defaults from `VERICOV_API_KEY`.
- `--api-url`: defaults from `VERICOV_API_URL`, config, then `https://api.vericov.dev`.
- `--commit-sha`: defaults from CI env or git.
- `--branch`: defaults from CI env or git.

Optional:

- `--repository-id UUID`: optional for repo-scoped keys.
- `--coverage PATH_OR_GLOB`: repeatable.
- `--test-results PATH_OR_GLOB`: repeatable.
- `--flag VALUE`: repeatable.
- `--component VALUE`
- `--package VALUE`
- `--pull-request-number INT`
- `--ci-provider VALUE`
- `--ci-build-id VALUE`
- `--ci-build-url VALUE`
- `--config PATH`
- `--discover`: enable default discovery even if explicit files are supplied.
- `--no-discover`: disable default discovery.
- `--dry-run`
- `--wait`
- `--timeout DURATION`: accepts `30s`, `5m`, `1h`, or raw seconds.
- `--poll-interval DURATION`: default `2s`, capped internally.
- `--max-artifact-size SIZE`: accepts `25MiB`, `100MB`, or raw bytes.
- `--max-total-size SIZE`
- `--json`
- `--verbose`

Do not add short aliases in v1 except `--version`. Long options make CI definitions easier to read.

### Exit Codes

Use stable exit codes:

| Code | Meaning |
| --- | --- |
| `0` | Upload accepted, or completed when `--wait` is used |
| `1` | CLI usage or config validation error |
| `2` | No uploadable artifacts found |
| `3` | Local artifact validation failed |
| `4` | Authentication or authorization failed |
| `5` | Non-retryable upload API error |
| `6` | Retry budget exhausted |
| `7` | Upload processing failed while waiting |
| `8` | Wait timeout |
| `9` | Unexpected CLI error |

Map HTTP errors:

- `401` and `403` -> `4`
- `400`, `404`, `409`, `413`, `422` -> `5`
- retry-exhausted network or `429`/`5xx` -> `6`

### JSON Output

`--json` should emit a single JSON object on stdout and human-readable diagnostics on stderr only.

Success:

```json
{
  "ok": true,
  "upload_id": "84cb6d3c-312f-43af-a3fc-5964b2d27626",
  "status": "queued",
  "poll_url": "https://api.vericov.dev/api/v1/uploads/84cb6d3c-312f-43af-a3fc-5964b2d27626",
  "analysis_job_id": "fb0e1e5d-55d7-4f74-9303-7a93400d53a1",
  "artifacts": [
    {
      "path": "coverage/lcov.info",
      "name": "coverage__lcov.info",
      "kind": "coverage",
      "format": "lcov",
      "size_bytes": 45122
    }
  ]
}
```

Failure:

```json
{
  "ok": false,
  "error": {
    "code": "artifact_too_large",
    "message": "coverage/jacoco.xml is 31.4 MiB, above the 25 MiB v1 limit."
  }
}
```

---

## Metadata Discovery

Metadata discovery must be deterministic and testable. Implement each provider as a pure function from `Mapping[str, str]` to a frozen metadata object.

### Priority

1. CLI flags.
2. Environment variables.
3. Config file.
4. CI provider environment.
5. Git commands.

### Supported CI Environments In v1

GitHub Actions:

- `GITHUB_SHA` -> `commit_sha`
- `GITHUB_HEAD_REF` or `GITHUB_REF_NAME` -> `branch`
- `GITHUB_RUN_ID` -> `ci_build_id`
- `GITHUB_SERVER_URL`, `GITHUB_REPOSITORY`, `GITHUB_RUN_ID` -> `ci_build_url`
- `GITHUB_EVENT_NAME=pull_request` and event path parsing -> `pull_request_number`
- `ci_provider=github_actions`

GitLab CI:

- `CI_COMMIT_SHA`
- `CI_COMMIT_REF_NAME`
- `CI_PIPELINE_ID`
- `CI_PIPELINE_URL`
- `CI_MERGE_REQUEST_IID`
- `ci_provider=gitlab_ci`

CircleCI:

- `CIRCLE_SHA1`
- `CIRCLE_BRANCH`
- `CIRCLE_BUILD_NUM`
- `CIRCLE_BUILD_URL`
- `CIRCLE_PULL_REQUEST`
- `ci_provider=circleci`

Buildkite:

- `BUILDKITE_COMMIT`
- `BUILDKITE_BRANCH`
- `BUILDKITE_BUILD_ID`
- `BUILDKITE_BUILD_URL`
- `BUILDKITE_PULL_REQUEST`
- `ci_provider=buildkite`

Generic CI fallback:

- `CI_COMMIT_SHA`
- `COMMIT_SHA`
- `GIT_COMMIT`
- `BRANCH_NAME`
- `CI_BRANCH`
- `BUILD_ID`
- `BUILD_URL`

Git fallback:

- `git rev-parse HEAD`
- `git branch --show-current`

The CLI should not run git if values are already available from flags, env, or config.

### Repository Identity

`repository_id` is optional for repo-scoped API keys.

Rules:

- If `--repository-id` or config `upload.repository_id` is present, validate it is a UUID and send it.
- If missing, omit `repository_id` from the JSON payload.
- The server resolves omitted `repository_id` from the authenticated `RepositoryApiKeyPrincipal`.
- If the credential type later supports organization-wide/global tokens, those credentials must require `repository_id` or an explicit repository slug.

The CLI may detect a git remote slug for diagnostics, but it must not use the slug as authorization. Server-side credential scope is authoritative.

---

## Artifact Discovery

### Discovery Modes

There are three modes:

1. **Explicit mode:** `--coverage`, `--test-results`, or config artifact lists are present. Default discovery is disabled unless `--discover` is supplied or `upload.discover.enabled: true`.
2. **Default discovery mode:** no explicit artifacts are supplied. The CLI searches curated default patterns.
3. **Disabled mode:** `--no-discover` or `upload.discover.enabled: false`. If no explicit artifacts are present, fail with exit code `2`.

### Default Roots

Default root is the project root:

- Prefer `git rev-parse --show-toplevel` when inside a git worktree.
- Otherwise use current working directory.

Do not traverse parent directories.

### Default Coverage Patterns

Use deterministic glob matching with `pathlib.Path.rglob` and `fnmatch`.

Coverage includes:

- `coverage/lcov.info`
- `coverage/**/*.info`
- `coverage/**/*.lcov`
- `coverage.xml`
- `coverage/**/*.xml`
- `**/jacoco.xml`
- `**/jacocoTestReport.xml`
- `**/site/jacoco/*.xml`
- `**/cobertura-coverage.xml`
- `**/clover.xml`
- `**/coverage.out`
- `**/cover.out`
- `**/*.gcov`

Test results include:

- `junit.xml`
- `test-results/**/*.xml`
- `build/test-results/**/*.xml`
- `target/surefire-reports/*.xml`
- `target/failsafe-reports/*.xml`

### Default Excludes

Exclude:

- `.git/**`
- `.hg/**`
- `.svn/**`
- `.tox/**`
- `.nox/**`
- `.venv/**`
- `venv/**`
- `node_modules/**`
- `vendor/**`
- `dist/**`
- `build/tmp/**`
- `tmp/**`
- `.pytest_cache/**`
- `.mypy_cache/**`
- `.ruff_cache/**`
- `__pycache__/**`

### Symlinks And Outside-Repo Files

Default behavior:

- Do not follow directory symlinks.
- Resolve each candidate path.
- Reject files outside the project root.

Future option:

- `--allow-outside-repo` can be added later if monorepo or generated-artifact workflows need it.

Do not add the option in v1 unless a real workflow needs it.

### Dedupe And Sorting

- Resolve each artifact path to an absolute path.
- Dedupe by resolved path.
- Sort by path relative to project root.
- Fail if more than `100` artifacts are selected unless `--max-artifacts` is added in a later release.

### Artifact Names

The upload backend requires artifact names to be file names, not paths. The CLI should derive stable safe names from project-relative paths:

```text
coverage/lcov.info -> coverage__lcov.info
services/api/coverage/lcov.info -> services__api__coverage__lcov.info
```

Rules:

- Replace `/` and `\` with `__`.
- Replace unsafe characters outside `[A-Za-z0-9._-]` with `-`.
- Limit to 180 characters before extension.
- If a collision remains, append `-<sha256-prefix>`.
- Never send `..`, `/`, or `\` in artifact names.

This avoids collisions when multiple packages produce `coverage.xml`.

---

## Format Detection

The CLI should infer `kind`, `format`, and `content_type`.

### Coverage Formats

| Format | Detection |
| --- | --- |
| `lcov` | `.info`, `.lcov`, or file containing `SF:` and `DA:` records |
| `cobertura` | XML root `coverage` with Cobertura-like attributes or `cobertura` in name |
| `jacoco` | XML root `report` with JaCoCo counters or `jacoco` in name |
| `clover` | XML root `coverage` with Clover-style `project`/`metrics` structure or `clover` in name |
| `go_cover` | first non-empty line starts with `mode:` and later lines match Go cover profile shape |
| `gcov` | `.gcov` extension or content with `Source:` and line-count records |

### Test Result Formats

| Format | Detection |
| --- | --- |
| `junit` | XML root `testsuite` or `testsuites`, or path matches common JUnit report directories |

### Detection Rules

- Prefer explicit format if future config supports object entries.
- Use file content sniffing for XML files instead of trusting the name only.
- Read only a bounded prefix for sniffing, for example 64 KiB.
- Do not parse external XML entities in the CLI. Use `xml.etree.ElementTree.iterparse` or a simple bounded root-tag read. Full XML security remains backend parser responsibility.
- Fail unknown coverage formats with actionable text.

Example:

```text
Could not infer coverage format for coverage/report.dat.
Pass an explicit supported format after object-style config is added, or rename/export as lcov, cobertura, jacoco, clover, go_cover, or gcov.
```

For v1, CLI flags are path-only. If explicit format overrides are needed immediately, add:

```text
--coverage-file PATH:FORMAT
--test-results-file PATH:FORMAT
```

Prefer deferring this unless inference proves insufficient.

---

## Size Limits

Until multipart or signed-storage upload exists, direct JSON upload must have client-side limits:

- Default max per artifact: `25 MiB` raw bytes.
- Default max total: `50 MiB` raw bytes.
- Warn above `25 MiB` total raw bytes because Base64 and JSON increase request size.

Base64 expands raw bytes by roughly 4/3, so `50 MiB` raw becomes about `66.7 MiB` before JSON overhead.

Validation must happen before reading all files into memory. Use file stat sizes first.

Recommended failure:

```text
coverage/jacoco.xml is 31.4 MiB, above the 25 MiB v1 limit.
Use a smaller report, split uploads by component, or wait for multipart uploads.
```

Recommended warning:

```text
Selected artifacts total 34.8 MiB raw. Direct JSON upload will send roughly 46.4 MiB.
```

The backend should also enforce a request size limit through Kong and service validation. The CLI limit is a user-experience guard, not the only protection.

---

## Upload Request Construction

### Endpoint

```text
POST {api_url}/api/v1/uploads
```

Headers:

```text
Authorization: Bearer <VERICOV_API_KEY>
Idempotency-Key: <stable-key>
Content-Type: application/json
Accept: application/json
User-Agent: vericov-coverage-upload/<version>
```

### Body

Use the existing upload service shape:

```json
{
  "commit_sha": "abc123",
  "branch": "main",
  "pull_request_number": 42,
  "ci_provider": "github_actions",
  "ci_build_id": "987654321",
  "ci_build_url": "https://github.com/acme/payments-api/actions/runs/987654321",
  "flags": ["unit", "linux"],
  "component": "api",
  "package": "services/api",
  "artifacts": [
    {
      "name": "coverage__lcov.info",
      "kind": "coverage",
      "format": "lcov",
      "content_type": "text/plain",
      "content_base64": "VE46ClNGOnNyYy9NYWluLmphdmEK"
    }
  ]
}
```

Include `repository_id` only when it is known from flags or config.

Do not include null values in the JSON payload. This keeps request diffs clean and reduces ambiguity.

### Memory Profile

V1 can construct the full JSON request in memory because total raw bytes are capped at `50 MiB`.

Implementation should still:

- Read files once for digest and content.
- Encode bytes with `base64.b64encode`.
- Avoid holding duplicate copies where practical.

Future multipart work should replace this path behind the same upload gateway abstraction.

### API Client Dependency Seam

V1 should call upload service URLs directly:

- `POST {api_url}/api/v1/uploads`
- `GET {resolved_poll_url}`

The direct URL code belongs only in `infrastructure/http/direct_url_upload_gateway.py`. The application workflow depends on a protocol, not on URL construction:

```python
class UploadGateway(Protocol):
    def create_upload(self, request: UploadRequest, auth: UploadAuth) -> UploadAccepted:
        ...

    def get_upload_status(self, poll_url: str, auth: UploadAuth) -> UploadStatus:
        ...
```

When a reusable API client exists, add it as a dependency of `clis/coverage-upload/` and implement:

```text
infrastructure/http/api_client_upload_gateway.py
```

The Typer commands, application workflows, config model, artifact discovery, idempotency, retry policy, and output renderers should not need to change. This keeps v1 simple while leaving a clean path to a generated or shared Vericov API client.

---

## Idempotency

The CLI must generate a stable idempotency key for retries.

Inputs:

- Schema version string: `vericov-upload-v1`
- Repository identity if provided, otherwise detected git remote slug if available, otherwise empty.
- Commit SHA.
- Branch.
- Pull request number.
- CI provider.
- CI build ID.
- Flags.
- Component.
- Package.
- Artifact names.
- Artifact SHA-256 digests.

Algorithm:

1. Build canonical JSON with sorted keys and compact separators.
2. SHA-256 hash the JSON.
3. Use header value:

```text
vericov-upload-v1-<first-48-hex-chars>
```

Do not include the API key in the idempotency material.

Rationale:

- Same upload attempt with same artifact bytes returns the same upload id.
- Changed artifacts produce a new idempotency key.
- Retries after transient failure are safe.
- The header is short and log-friendly.

Allow an explicit override:

```text
--idempotency-key VALUE
VERICOV_IDEMPOTENCY_KEY=VALUE
```

This is useful when CI systems already provide a stable upload attempt key. Validate max length, printable ASCII, and no whitespace.

---

## Retry Design

Retry only when the request is safe to retry with the same idempotency key.

Retry on:

- Network timeout.
- Connection reset.
- DNS/transient socket errors.
- HTTP `408`.
- HTTP `425`.
- HTTP `429`.
- HTTP `500`.
- HTTP `502`.
- HTTP `503`.
- HTTP `504`.

Do not retry on:

- HTTP `400`.
- HTTP `401`.
- HTTP `403`.
- HTTP `404`.
- HTTP `409`.
- HTTP `413`.
- HTTP `422`.
- Any response with a validation/security error code.

Defaults:

- `4` total attempts.
- Initial backoff `0.5s`.
- Multiplier `2`.
- Max backoff `8s`.
- Jitter `0-250ms`.
- Respect `Retry-After` for `429` and `503`, capped at `30s`.
- Request timeout `30s`.

Retry logging:

```text
Upload attempt 2/4 after 503 service_unavailable; retrying in 1.2s with same idempotency key.
```

Never print the API key.

---

## Wait And Polling

`--wait` should poll the returned `poll_url`.

### Statuses

Treat as non-terminal:

- `accepted`
- `queued`
- `processing`

Treat as success:

- `completed`

Treat as failure:

- `failed`

Unknown status:

- Continue polling until timeout, but print a warning once.
- If timeout occurs, exit `8`.

### Polling Defaults

- Timeout: `300s`.
- Initial interval: `2s`.
- Max interval: `10s`.
- Jitter: `0-250ms`.
- Print only on status changes by default.

### Poll URL Resolution

The backend returns a relative poll URL. The CLI should resolve it against `api_url`:

```text
api_url=https://api.vericov.dev
poll_url=/api/v1/uploads/...
resolved=https://api.vericov.dev/api/v1/uploads/...
```

If the server returns an absolute URL, use it only if the host matches `api_url` unless `--allow-cross-host-poll-url` is introduced later. Do not add that option in v1.

---

## Backend Contract Adjustment

The CLI can ship without this if every user passes `repository_id`, but the better v1 experience requires a small upload service change.

### Current Behavior

`UploadApplicationService.validate()` rejects requests with missing `repository_id` before authentication.

### Desired Behavior

For repo-scoped API keys:

1. Validate auth header, idempotency key, commit SHA, branch, and artifacts.
2. Authenticate the API key.
3. Resolve repository id:
   - if request has `repository_id`, require it equals `principal.repositoryId()`;
   - if request omits `repository_id`, use `principal.repositoryId()`.
4. Authorize scope and branch.
5. Save the upload with the resolved repository id.
6. Return the resolved repository id in the response.

### Implementation Shape

Prefer immutable records:

- Keep `CreateUploadCommand.repositoryId()` nullable or change it to `Optional<UUID>`.
- Add a small resolved command/value object if needed:

```java
record ResolvedCreateUploadCommand(
        CreateUploadCommand original,
        UUID repositoryId) {
}
```

or create a new `CreateUploadCommand` copy with the resolved id after authentication.

Do not mutate the incoming command.

### Backend Tests

Add tests:

- Missing `repository_id` is accepted for a repo-scoped key.
- Response includes the principal repository id.
- Explicit mismatched `repository_id` returns `403`.
- Missing `repository_id` still requires `uploads:create`.
- Missing `repository_id` with a future/global credential returns `400` or `403` depending on credential type once that exists.

---

## Security Requirements

### Secrets

- API key only from `--api-key` or `VERICOV_API_KEY`.
- Do not allow secrets in YAML.
- Do not print secrets in human output, JSON output, dry run, tracebacks, or HTTP errors.
- If verbose output prints headers, redact authorization:

```text
Authorization: Bearer vc_live_...redacted
```

### File Safety

- Reject files outside project root.
- Reject directories.
- Reject empty files.
- Reject unsupported formats.
- Reject files over max size.
- Reject total size over max size.
- Do not follow directory symlinks.
- Use safe artifact names with no path separators.

### Input Validation

- Validate UUID fields.
- Validate branch and commit are non-empty.
- Validate flags/components/packages are bounded length strings.
- Validate URLs parse as HTTP or HTTPS.
- Prefer HTTPS API URLs. Allow HTTP only for localhost, `127.0.0.1`, or `::1`.
- Validate idempotency key characters and length.

### HTTP Safety

- Use TLS verification by default.
- Do not add `--insecure-skip-tls-verify` in v1.
- Add local development support by allowing `http://localhost:*`.
- Set explicit request timeout.
- Parse error envelopes without exposing raw response bodies beyond a safe length.

### Supply Chain

- Use Typer for CLI parsing, command composition, validation help text, and future command scaling.
- Use standard library `urllib.request` for direct URL calls in v1 instead of adding `requests`.
- Keep the HTTP layer behind a small gateway/protocol so a generated or handwritten Vericov API client can replace direct URLs later without changing command or workflow code.
- Add only `Typer` and `PyYAML` as runtime dependencies.
- Pin lower bounds, not exact versions, in package metadata.
- Keep test dependencies under optional extras.

---

## Python Package Design

### Independent CLI Workspace

Create the CLI as its own buildable project:

```text
clis/
  coverage-upload/
    pyproject.toml
    README.md
    src/
      vericov_coverage_upload/
        __init__.py
        cli/
        application/
        domain/
        infrastructure/
        presentation/
    tests/
```

`coverage-upload` is the folder name because it describes the job this CLI does. Future independent CLIs should get sibling folders such as `clis/repository-admin/`, `clis/agent-runner/`, or `clis/report-export/` rather than sharing one root Python project by default.

Rules:

- `clis/coverage-upload/` has its own `pyproject.toml` and can be built, tested, and installed from that subfolder.
- Local development commands work from the subfolder:

```bash
cd clis/coverage-upload
uv sync --dev
uv run pytest
uv build
uv run vericov upload --help
```

- The import package is `vericov_coverage_upload`, not `vericov`, so it does not collide with the reserved root package.
- The distribution name is `vericov-coverage-upload`.
- The console script remains `vericov` so users get `vericov upload`.
- The CLI package must not import the root `src/vericov` package.
- Shared reusable API-client code can become a separate dependency later, for example `vericov-api-client`, but v1 keeps direct upload/status URL calls inside this CLI package.
- Use `uv` for all dependency, lockfile, test, build, and local command execution workflows in this folder.

### File Structure

Create:

```text
clis/coverage-upload/
  pyproject.toml
  README.md
  src/vericov_coverage_upload/
    __init__.py
    cli/
      __init__.py
      app.py
      main.py
      context.py
      commands/
        __init__.py
        config.py
        upload.py
      options/
        __init__.py
        common.py
        upload.py
    application/
      __init__.py
      config_validation.py
      upload_workflow.py
      wait_for_upload.py
    domain/
      __init__.py
      artifacts.py
      config.py
      errors.py
      metadata.py
      upload_request.py
      upload_response.py
    infrastructure/
      __init__.py
      ci_metadata.py
      config_loader.py
      file_discovery.py
      format_detection.py
      git_metadata.py
      http/
        __init__.py
        direct_url_upload_gateway.py
        retry.py
        urls.py
    presentation/
      __init__.py
      human.py
      json.py
  tests/
    test_cli.py
    test_config.py
    test_discovery.py
    test_formats.py
    test_metadata.py
    test_artifacts.py
    test_http.py
    test_upload.py
```

Responsibilities:

- `cli/`: Typer app construction, command registration, option declarations, and exit handling only.
- `cli/commands/`: one module per top-level command or command group. Command modules call application services and should stay thin.
- `cli/options/`: reusable Typer option definitions so new commands do not copy/paste shared flags.
- `application/`: use-case orchestration such as validate config, build upload plan, submit upload, and wait for processing.
- `domain/`: immutable dataclasses, value validation, idempotency material, and typed errors.
- `infrastructure/`: filesystem, git, CI environment, YAML, format sniffing, and HTTP implementations.
- `infrastructure/http/direct_url_upload_gateway.py`: direct URL implementation for `POST /api/v1/uploads` and `GET poll_url`.
- `presentation/`: human and JSON output rendering.

The future API-client dependency should slot in by adding another implementation of the same upload gateway protocol, for example `ApiClientUploadGateway`, without changing `cli/commands/upload.py` or `application/upload_workflow.py`.

### Command Scalability

Treat the CLI structure as if 100 more commands may be added:

- Every command or command family gets its own module under `cli/commands/`.
- A command module owns only Typer wiring and delegates work to `application/`.
- Shared options live under `cli/options/`, grouped by concern.
- `cli/app.py` creates the root Typer app and registers command modules.
- No command module imports another command module.
- Cross-command behavior, such as config loading, JSON output, and error mapping, lives in `cli/context.py`, `presentation/`, or `application/`.
- New commands should require adding a command module and one app-registration line, not editing a large command switch.

Typer app registration should look conceptually like:

```python
app = typer.Typer(name="vericov", no_args_is_help=True)
config.register(app)
upload.register(app)
```

If a command family grows, promote it to a sub-Typer:

```python
upload_app = typer.Typer(help="Upload coverage and test artifacts.")
app.add_typer(upload_app, name="upload")
```

Do not put business logic in Typer callbacks. Callback functions translate CLI inputs into application request dataclasses and render application results.

Create tests:

- `clis/coverage-upload/tests/test_cli.py`
- `clis/coverage-upload/tests/test_config.py`
- `clis/coverage-upload/tests/test_discovery.py`
- `clis/coverage-upload/tests/test_formats.py`
- `clis/coverage-upload/tests/test_metadata.py`
- `clis/coverage-upload/tests/test_artifacts.py`
- `clis/coverage-upload/tests/test_http.py`
- `clis/coverage-upload/tests/test_upload.py`

Modify:

- `clis/coverage-upload/pyproject.toml`
- root `README.md`
- `clis/coverage-upload/README.md`
- `docs/backend/services/03-upload-ingestion-service.md`
- Java upload service files for optional repository id resolution.

### Data Model

Use frozen dataclasses:

```python
@dataclass(frozen=True)
class UploadConfig:
    api_url: str
    repository_id: str | None
    flags: tuple[str, ...]
    component: str | None
    package: str | None
    coverage: tuple[str, ...]
    test_results: tuple[str, ...]
    discover: DiscoveryConfig
    wait: bool
    timeout_seconds: int
    max_artifact_bytes: int
    max_total_bytes: int
```

Python 3.9 does not support `str | None`, so actual code should use `Optional[str]` and `Tuple[str, ...]`.

Prefer returning new dataclass instances over mutating existing ones.

### Dependency Updates

`clis/coverage-upload/pyproject.toml`:

```toml
[build-system]
requires = ["setuptools>=77"]
build-backend = "setuptools.build_meta"

[project]
name = "vericov-coverage-upload"
version = "0.1.0"
description = "Vericov coverage upload CLI."
requires-python = ">=3.9"
dependencies = [
  "typer>=0.12",
  "PyYAML>=6.0.1"
]

[project.scripts]
vericov = "vericov_coverage_upload.cli.main:main"

[dependency-groups]
dev = [
  "build>=1.2",
  "pytest>=8"
]

[tool.setuptools.packages.find]
where = ["src"]
```

No `requests` dependency in v1.

---

## Implementation Tasks

### Task 1: Add Python CLI Skeleton

**Files:**
- Create: `clis/coverage-upload/pyproject.toml`
- Create: `clis/coverage-upload/src/vericov_coverage_upload/__init__.py`
- Create: `clis/coverage-upload/src/vericov_coverage_upload/cli/app.py`
- Create: `clis/coverage-upload/src/vericov_coverage_upload/cli/main.py`
- Create: `clis/coverage-upload/src/vericov_coverage_upload/cli/context.py`
- Create: `clis/coverage-upload/src/vericov_coverage_upload/cli/commands/config.py`
- Create: `clis/coverage-upload/src/vericov_coverage_upload/cli/commands/upload.py`
- Create: `clis/coverage-upload/src/vericov_coverage_upload/cli/options/common.py`
- Create: `clis/coverage-upload/src/vericov_coverage_upload/cli/options/upload.py`
- Create: `clis/coverage-upload/src/vericov_coverage_upload/domain/errors.py`
- Create: `clis/coverage-upload/src/vericov_coverage_upload/presentation/human.py`
- Create: `clis/coverage-upload/src/vericov_coverage_upload/presentation/json.py`
- Create: `clis/coverage-upload/tests/test_cli.py`

- [ ] Add a failing test that `vericov --version` exits `0` and prints `__version__`.
- [ ] Add a failing test that unknown commands exit with usage error code `1`.
- [ ] Use `typer.testing.CliRunner` for command-boundary tests.
- [ ] Add `[project.scripts] vericov = "vericov_coverage_upload.cli.main:main"`.
- [ ] Implement a Typer root app in `cli/app.py`.
- [ ] Implement `main()` in `cli/main.py` as the console-script wrapper around the Typer app.
- [ ] Implement top-level `--version`, `config validate`, and `upload` subcommand placeholders.
- [ ] Add typed `VericovCliError` with `code`, `message`, and `exit_code`.
- [ ] Add output helpers that can write human text or JSON.
- [ ] Verify with `cd clis/coverage-upload && uv run pytest tests/test_cli.py`.

Exit criteria:

- Editable install exposes `vericov`.
- CLI skeleton has no upload behavior yet.
- No secrets or network calls are involved.

### Task 2: Implement Config Loading And Validation

**Files:**
- Create: `clis/coverage-upload/src/vericov_coverage_upload/application/config_validation.py`
- Create: `clis/coverage-upload/src/vericov_coverage_upload/domain/config.py`
- Create: `clis/coverage-upload/src/vericov_coverage_upload/infrastructure/config_loader.py`
- Modify: `clis/coverage-upload/src/vericov_coverage_upload/cli/commands/config.py`
- Create: `clis/coverage-upload/tests/test_config.py`

- [ ] Write tests for default lookup: no file, `vericov.yml`, `.vericov.yml`, both files error, explicit `--config`.
- [ ] Write tests for unknown keys failing.
- [ ] Write tests that `api_key`, `token`, and `upload_token` in config fail.
- [ ] Write tests for config precedence against environment and CLI overrides.
- [ ] Write tests for UUID, URL, size, duration, and list validation.
- [ ] Implement `load_config(path, cwd)` with `yaml.safe_load`.
- [ ] Implement schema validation with explicit allowed keys and clear field paths.
- [ ] Implement `vericov config validate`.
- [ ] Verify with `cd clis/coverage-upload && uv run pytest tests/test_config.py`.

Exit criteria:

- Config errors are actionable.
- Secrets are rejected from YAML.
- `vericov config validate --json` emits machine-readable errors.

### Task 3: Implement CI And Git Metadata Discovery

**Files:**
- Create: `clis/coverage-upload/src/vericov_coverage_upload/domain/metadata.py`
- Create: `clis/coverage-upload/src/vericov_coverage_upload/infrastructure/ci_metadata.py`
- Create: `clis/coverage-upload/src/vericov_coverage_upload/infrastructure/git_metadata.py`
- Create: `clis/coverage-upload/tests/test_metadata.py`

- [ ] Write tests for GitHub Actions metadata.
- [ ] Write tests for GitLab CI metadata.
- [ ] Write tests for CircleCI metadata.
- [ ] Write tests for Buildkite metadata.
- [ ] Write tests for generic CI fallback.
- [ ] Write tests that git is not invoked when CI values are complete.
- [ ] Write tests for git fallback using an injectable command runner.
- [ ] Implement provider-specific pure functions over env mappings.
- [ ] Implement git fallback with timeout and safe failure.
- [ ] Verify with `cd clis/coverage-upload && uv run pytest tests/test_metadata.py`.

Exit criteria:

- Metadata discovery is deterministic.
- Missing commit or branch fails before upload with a clear message.

### Task 4: Implement Artifact Discovery And Naming

**Files:**
- Create: `clis/coverage-upload/src/vericov_coverage_upload/domain/artifacts.py`
- Create: `clis/coverage-upload/src/vericov_coverage_upload/infrastructure/file_discovery.py`
- Create: `clis/coverage-upload/tests/test_discovery.py`
- Create: `clis/coverage-upload/tests/test_artifacts.py`

- [ ] Write tests for explicit mode disabling discovery.
- [ ] Write tests for default discovery when no explicit files exist.
- [ ] Write tests for config discovery roots/include/exclude.
- [ ] Write tests for deterministic sorting and dedupe.
- [ ] Write tests rejecting outside-root files.
- [ ] Write tests rejecting empty files and directories.
- [ ] Write tests for safe artifact name generation and collision handling.
- [ ] Write tests for per-artifact and total size limits.
- [ ] Implement project root detection.
- [ ] Implement glob expansion without following directory symlinks.
- [ ] Implement size validation before content reads.
- [ ] Implement artifact naming and digesting.
- [ ] Verify with `cd clis/coverage-upload && uv run pytest tests/test_discovery.py tests/test_artifacts.py`.

Exit criteria:

- The selected file list is reproducible.
- Unsafe paths never reach the upload request.

### Task 5: Implement Format Detection

**Files:**
- Create: `clis/coverage-upload/src/vericov_coverage_upload/infrastructure/format_detection.py`
- Modify: `clis/coverage-upload/src/vericov_coverage_upload/domain/artifacts.py`
- Create: `clis/coverage-upload/tests/test_formats.py`

- [ ] Write tests for LCOV detection.
- [ ] Write tests for Cobertura XML detection.
- [ ] Write tests for JaCoCo XML detection.
- [ ] Write tests for Clover XML detection.
- [ ] Write tests for Go cover profile detection.
- [ ] Write tests for gcov detection.
- [ ] Write tests for JUnit XML test-result detection.
- [ ] Write tests for unsupported files failing with actionable messages.
- [ ] Implement bounded content sniffing.
- [ ] Implement XML root detection without resolving external entities.
- [ ] Wire detected kind/format/content type into artifact payload.
- [ ] Verify with `cd clis/coverage-upload && uv run pytest tests/test_formats.py`.

Exit criteria:

- Common report formats are classified correctly.
- Unknown reports fail locally before upload.

### Task 6: Implement Upload Request And Retry Client

**Files:**
- Create: `clis/coverage-upload/src/vericov_coverage_upload/domain/upload_request.py`
- Create: `clis/coverage-upload/src/vericov_coverage_upload/domain/upload_response.py`
- Create: `clis/coverage-upload/src/vericov_coverage_upload/application/upload_workflow.py`
- Create: `clis/coverage-upload/src/vericov_coverage_upload/infrastructure/http/direct_url_upload_gateway.py`
- Create: `clis/coverage-upload/src/vericov_coverage_upload/infrastructure/http/retry.py`
- Create: `clis/coverage-upload/src/vericov_coverage_upload/infrastructure/http/urls.py`
- Modify: `clis/coverage-upload/src/vericov_coverage_upload/cli/commands/upload.py`
- Create: `clis/coverage-upload/tests/test_http.py`
- Create: `clis/coverage-upload/tests/test_upload.py`

- [ ] Write tests that upload request omits `repository_id` when absent.
- [ ] Write tests that authorization and idempotency headers are set.
- [ ] Write tests that idempotency key is stable for identical artifacts.
- [ ] Write tests that changed artifact bytes change the idempotency key.
- [ ] Write tests for retryable network errors.
- [ ] Write tests for retryable HTTP status codes.
- [ ] Write tests that `400`, `401`, `403`, `413`, and `422` are not retried.
- [ ] Write tests for `Retry-After` handling.
- [ ] Implement payload construction and null omission.
- [ ] Implement `urllib.request` JSON POST with timeout in `direct_url_upload_gateway.py`.
- [ ] Keep gateway methods narrow enough that a future API client dependency can replace the direct URL implementation.
- [ ] Implement retry policy with injected sleeper/random for tests.
- [ ] Implement response envelope parsing.
- [ ] Verify with `cd clis/coverage-upload && uv run pytest tests/test_http.py tests/test_upload.py`.

Exit criteria:

- Uploads are safe to retry.
- Server errors map to stable CLI errors.
- API key never appears in test snapshots or logs.

### Task 7: Implement `--wait` Polling

**Files:**
- Modify: `clis/coverage-upload/src/vericov_coverage_upload/infrastructure/http/direct_url_upload_gateway.py`
- Create: `clis/coverage-upload/src/vericov_coverage_upload/application/wait_for_upload.py`
- Modify: `clis/coverage-upload/src/vericov_coverage_upload/application/upload_workflow.py`
- Modify: `clis/coverage-upload/src/vericov_coverage_upload/cli/commands/upload.py`
- Modify: `clis/coverage-upload/tests/test_http.py`
- Modify: `clis/coverage-upload/tests/test_upload.py`

- [ ] Write tests resolving relative poll URLs against `api_url`.
- [ ] Write tests rejecting cross-host absolute poll URLs.
- [ ] Write tests for queued -> processing -> completed.
- [ ] Write tests for failed status exit code `7`.
- [ ] Write tests for timeout exit code `8`.
- [ ] Write tests for unknown status warning.
- [ ] Implement status polling with backoff and timeout.
- [ ] Implement human and JSON final output.
- [ ] Verify with `cd clis/coverage-upload && uv run pytest tests/test_http.py tests/test_upload.py`.

Exit criteria:

- `--wait` is reliable enough for CI gating on upload processing.
- Non-wait uploads remain fast by default.

### Task 8: Adjust Backend Repository Resolution

**Files:**
- Modify: `services/upload/src/main/java/dev/vericov/upload/api/CreateUploadHttpRequest.java`
- Modify: `services/upload/src/main/java/dev/vericov/upload/domain/CreateUploadCommand.java`
- Modify: `services/upload/src/main/java/dev/vericov/upload/application/UploadApplicationService.java`
- Modify: `services/upload/src/test/java/dev/vericov/upload/application/UploadApplicationServiceTest.java`
- Modify: `services/upload/src/test/java/dev/vericov/upload/api/UploadResourceTest.java`
- Modify: `services/upload/src/test/resources/features/upload/upload-ingestion.feature`
- Modify: `services/upload/src/test/java/dev/vericov/upload/bdd/steps/UploadSteps.java`

- [ ] Write failing application test for omitted `repository_id` accepted through repo-scoped key.
- [ ] Write failing API resource test for omitted `repository_id`.
- [ ] Write failing test for explicit mismatched `repository_id` returning `403`.
- [ ] Change validation order to authenticate before repository resolution.
- [ ] Resolve missing repository id from `RepositoryApiKeyPrincipal`.
- [ ] Preserve explicit mismatch as forbidden.
- [ ] Keep response `repository_id` populated.
- [ ] Update BDD scenario or add a new scenario for CLI-style upload without repository id.
- [ ] Verify with `mvn -pl services/upload test`.

Exit criteria:

- Existing repository-id uploads still pass.
- Missing repository-id uploads work for repo-scoped keys.
- Authorization remains server-side and branch-scoped.

### Task 9: Add End-To-End CLI Behavior Tests

**Files:**
- Modify: `clis/coverage-upload/tests/test_cli.py`
- Modify: `clis/coverage-upload/tests/test_upload.py`

- [ ] Add test for `vericov upload --dry-run` with discovered files.
- [ ] Add test for `vericov upload --json` success object.
- [ ] Add test for no artifacts exit code `2`.
- [ ] Add test for auth failure exit code `4`.
- [ ] Add test for retry exhaustion exit code `6`.
- [ ] Add test that validation errors do not print tracebacks.
- [ ] Add test that `--verbose` prints value provenance.
- [ ] Verify all Python tests with `cd clis/coverage-upload && uv run pytest`.

Exit criteria:

- CLI behavior is covered at the command boundary.
- Human and JSON outputs are stable.

### Task 10: Documentation And Examples

**Files:**
- Modify: `README.md`
- Modify: `clis/coverage-upload/README.md`
- Modify: `docs/backend/services/03-upload-ingestion-service.md`
- Optional create: `docs/cli/upload.md` if README becomes too large.

- [ ] Document installation.
- [ ] Document minimal CI usage.
- [ ] Document `vericov.yml`.
- [ ] Document environment variables.
- [ ] Document file discovery behavior.
- [ ] Document size limits.
- [ ] Document `--wait`, `--dry-run`, and `--json`.
- [ ] Document exit codes.
- [ ] Document that API keys must live in CI secrets, not config files.
- [ ] Update backend docs to state `repository_id` may be omitted for repo-scoped API keys.

Exit criteria:

- A new user can upload coverage from CI using only README instructions.
- Backend contract docs match implementation.

### Task 11: Full Verification

**Commands:**

```bash
cd clis/coverage-upload
uv lock
uv run pytest
uv build
uv run vericov --version
uv run vericov config validate --help
uv run vericov upload --help
cd ../..
mvn -pl services/upload test
```

The CLI package should keep development tools in the uv dependency group:

```toml
[dependency-groups]
dev = [
  "build>=1.2",
  "pytest>=8"
]
```

Exit criteria:

- Python tests pass.
- Upload service tests pass.
- Package builds.
- Installed wheel exposes `vericov`.

---

## Release And Rollout

### Versioning

The existing root package version is `0.0.3`. The independent CLI distribution should start at `0.1.0` because it introduces the first real upload CLI surface.

### Backward Compatibility

No Python public API exists yet, so compatibility risk is low.

Backend compatibility:

- Existing clients that send `repository_id` continue working.
- New clients can omit it when using repo-scoped keys.
- The upload response remains unchanged.

### Observability

The CLI should include:

- User-Agent with version.
- Idempotency key prefix for server-side log correlation.
- CI provider/build metadata in request.

The backend should log:

- Upload id.
- Repository id.
- Idempotency key hash or prefix.
- Artifact count and total bytes.
- Never log API key or artifact content.

### Future Work

- GitHub Action wrapper around the Python CLI.
- Tokenless OIDC upload.
- Multipart or signed-storage uploads.
- Control-plane repository resolution by git remote slug.
- Per-package/component batch mode.
- `vericov upload --wait gates` once gate evaluation status is exposed.
- Local parser preflight to catch invalid coverage reports before upload.
- Signed CLI release artifacts and provenance attestations.

---

## Risks And Mitigations

| Risk | Mitigation |
| --- | --- |
| Users accidentally upload stale reports | Print selected artifacts by default; explicit mode disables discovery unless requested |
| Large JSON uploads fail slowly | Enforce local per-artifact and total raw byte limits |
| Config typos silently change behavior | Fail on unknown keys |
| API keys leak in logs | Reject secrets in config; redact headers; avoid verbose raw request dumps |
| Repo identity ambiguity | Server resolves repo-scoped keys; explicit mismatch returns `403` |
| Retry creates duplicate uploads | Stable idempotency key and same key reused for all attempts |
| Discovery is too broad | Curated patterns, default excludes, deterministic dry run |
| XML sniffing opens parser risk | Bounded reads; root sniff only; no entity resolution |
| Python dependency footprint grows | Use stdlib HTTP; only add Typer and PyYAML runtime dependencies |

---

## Design Summary

The first production-grade Vericov CLI should make the simplest CI command powerful:

```bash
VERICOV_API_KEY=vc_live_... vericov upload --wait
```

The important implementation choices are:

- `vericov.yml` is canonical, `.vericov.yml` is accepted, and having both is an error.
- Repo-scoped API keys remove the need to pass `repository_id` in the happy path.
- Direct JSON upload is capped at `25 MiB` per artifact and `50 MiB` total raw bytes.
- `--wait` ships in v1, but default upload returns after the server accepts the upload.
- File discovery is deterministic and visible, not magical.
- Retries are safe because every attempt uses the same idempotency key.
