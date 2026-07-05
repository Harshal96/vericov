# CLI-Supplied Pull Request Diff Coverage Design

Date: 2026-07-03
Status: Draft

## Summary

Vericov will compute patch coverage in the public two-service runtime by making
the upload CLI the diff source. At upload time the CLI resolves the merge-base
between the pull request head and its target branch, produces a unified diff,
and ships both with the upload: the merge-base as a new `base_sha` request
field and the diff as a new `diff` artifact kind.

Coverage Analysis gains an artifact-backed implementation of the existing
`PullRequestDiffClient` port. It replaces the current
`PrDiffCoverageProcessor.noop()` binding, which activates the dormant
`DiffCoverageCalculator`, the `pull_request_coverage_diffs` persistence path,
the already-implemented `patch_coverage` gate in `GateEvaluator`, and
diff-aware coverage gap findings.

No Git provider integration is reintroduced. The repository checkout in CI is
the only source of Git truth, which keeps the public runtime at two services
communicating only through PostgreSQL and PGMQ.

This design is the prerequisite for
`2026-07-03-agent-coverage-query-interface-design.md` (patch queries),
`2026-07-03-coverage-gap-manifest-design.md` (patch-gap manifest entries), and
`2026-07-03-risk-weighted-coverage-design.md` (churn signals).

## Goals

- Compute patch coverage against an explicit, per-upload merge-base.
- Keep patch coverage correct when the target branch advances under a
  long-lived pull request.
- Ship the diff from CI without any service calling a Git provider.
- Activate the existing `patch_coverage` gate type for repositories that
  configure it.
- Preserve enough raw input (diff artifact plus merge-base) to recompute
  historical patch coverage if the algorithm changes.
- Preserve current behavior for uploads that carry no diff.

## Non-Goals

- Reintroducing the git-integration service or any provider API client.
- Fetching base-branch source or diffs during analysis.
- Component-scoped patch gates. Repository-level patch gates only, matching
  the deferral in the monorepo component coverage design.
- Declaring patch gates in `.vericov.yml`. Repository gates remain in
  `repository_gate_configurations`.
- Posting pull request comments or statuses to a Git provider.
- Backfilling patch coverage for historical uploads.
- Cross-upload shard aggregation. One upload still produces one report.

## Current State

The diff coverage pipeline exists end to end but has no input:

- `PullRequestDiffClient` is a port with no public implementation; the
  previous `InternalGitDiffHttpClient` was removed with the git-integration
  service, and `tests/test_public_repo.py` asserts it stays removed.
- `AnalysisComponents` binds `PrDiffCoverageProcessor.noop()`, so
  `DefaultPrDiffCoverageProcessor`, `DiffCoverageCalculator`, and
  `JdbcPrDiffCoverageRepository` never run.
- `GateEvaluator` already evaluates the `patch_coverage` gate type when a
  diff coverage report is present.
- The schema already has `pull_request_coverage_diffs` (with `base_sha`,
  `head_sha`, and a `status` of `complete`, `base_coverage_missing`, or
  `unavailable`), `pull_request_coverage_diff_files`, and
  `pull_request_coverage_diff_lines`.
- `CoverageGapExtractor` already emits diff-specific findings such as
  `new_uncovered_changed_line` and `base_coverage_missing` when diff coverage
  exists.
- The upload request carries `pull_request_number` but no base commit, and
  `upload_artifacts.kind` allows only `coverage`, `test_results`, and
  `metadata`.
- The CLI has `--pull-request-number` but no diff or base-ref options.

Everything below wires input into this existing machinery.

## Merge-Base Contract

Patch coverage is always computed against the merge-base of the upload's head
commit and the pull request's target branch, resolved in the CI checkout at
upload time:

```bash
git merge-base <base_ref> <head_sha>
```

The resolved SHA is pinned to the upload. When the target branch advances
after the upload, the stored patch coverage remains a true statement about
the diff that was measured. A new head commit produces a new upload with a
freshly resolved merge-base, which is the correct behavior for long-lived
pull requests.

Base-ref resolution precedence in the CLI:

1. `--base-sha <sha>` — explicit merge-base, used verbatim, no git required.
2. `--base-ref <ref>` — resolved with `git merge-base`.
3. CI environment detection — `GITHUB_BASE_REF` (GitHub Actions),
   `CI_MERGE_REQUEST_TARGET_BRANCH_NAME` (GitLab CI), or
   `BITBUCKET_PR_DESTINATION_BRANCH` (Bitbucket Pipelines), resolved with
   `git merge-base` against the corresponding remote-tracking ref.

Shallow clones are the common failure mode. When `git merge-base` fails, the
CLI reports the exact git error and instructs the user to deepen the fetch
(for example `fetch-depth: 0` on GitHub Actions). Explicit flags fail the
upload command; automatic detection downgrades to a visible warning and
uploads without a diff.

## Diff Contract

The CLI produces the diff itself; users do not hand-craft diffs in the common
path:

```bash
git -c core.quotePath=false diff --no-color --no-ext-diff \
    --find-renames --unified=0 <merge_base_sha> <head_sha>
```

- `--unified=0` keeps the artifact small; the calculator consumes added and
  deleted lines and does not require context lines.
- `--find-renames` preserves `old_file_path` for renamed files, matching the
  existing `pull_request_coverage_diff_files.old_file_path` column.
- Binary file changes are recorded as file entries with no line rows.

`--diff <file>` accepts a pre-generated unified diff for CI setups without a
full checkout at upload time. The file must be a valid unified diff; the CLI
validates it parses before upload and fails otherwise.

`--no-diff` disables diff handling entirely even when a pull request number
is present.

Automatic behavior: when `--pull-request-number` is set, no explicit diff
flags are given, and the working directory is a git checkout, the CLI
resolves the merge-base and generates the diff automatically.

## Upload API Changes

`CreateUploadHttpRequest` gains one field:

| Field | Required | Meaning |
| --- | --- | --- |
| `base_sha` | No | Merge-base commit for pull request uploads |

`base_sha` follows the same shape rule as `commit_sha` (trimmed length 1–128).
Supplying `base_sha` without `pull_request_number` is a validation error.
Supplying a `diff` artifact without `base_sha` is a validation error.

`upload_artifacts.kind` gains a `diff` value. A diff artifact has:

- `kind`: `diff`
- `format`: `git_unified_diff`
- at most one per upload (validation error otherwise)

The Upload Service validates the diff artifact before persistence:

1. size within limits (see Security And Resource Limits);
2. content parses as a unified diff;
3. every path inside the diff passes the existing normalized-path rules (no
   absolute paths, no parent traversal).

Validation failures return the existing structured validation-error envelope
and create no upload, artifact, job, or queue side effects — the same
contract as component snapshot validation.

The diff artifact and `base_sha` participate in upload idempotency exactly as
other artifacts do: the artifact set is part of the upload identity, and the
existing `(repository_id, idempotency_key)` uniqueness is unchanged.

## Analysis Changes

A new `UploadArtifactPullRequestDiffClient` implements
`PullRequestDiffClient`:

1. Load the `diff` artifact bytes from artifact storage.
2. Parse the unified diff into the existing `PullRequestDiff` model: the
   upload's `base_sha`, the upload's `commit_sha` as head SHA, and per-file
   added/deleted line mappings with rename tracking.
3. Return the parsed diff to `DefaultPrDiffCoverageProcessor`, which is
   unchanged: it looks up base coverage with
   `reports.findLatestByCommit(repositoryId, baseSha)`, computes diff
   coverage, and persists it.

`AnalysisComponents` replaces `PrDiffCoverageProcessor.noop()` with the real
processor wired to the artifact-backed client. Processing rules:

- No pull request number: no diff processing (unchanged).
- Pull request number but no diff artifact: no diff processing. This
  preserves the exact behavior of every existing client.
- Diff artifact present: diff coverage runs. Because the Upload Service
  validated the artifact, a parse failure during analysis indicates
  corruption and is non-retryable, the same class as a snapshot hash
  mismatch.
- Base coverage missing (no complete report exists for the merge-base
  commit): the existing `base_coverage_missing` status flows through, patch
  totals are computed from head coverage only, and `newly_missed` /
  `lost_coverage` line classification is skipped. The existing
  `base_coverage_missing` gap finding surfaces the situation.

For the `base_coverage_missing` path to be rare, repositories should upload
coverage for default-branch builds (push or post-merge), not only pull
requests. This becomes a documented operating requirement.

### Patch Gate

No gate changes are required. Repositories activate the gate by inserting an
active `repository_gate_configurations` row with `gate_type =
'patch_coverage'`; `GateEvaluator` already evaluates it against the diff
coverage report and persists a `gate_evaluations` row with `metric` scope
`patch`. A failed blocking patch gate sets `coverage_reports.gate_status` to
`failed` through the existing status aggregation.

## Report API Changes

The upload report response gains a `patch` object, present only when diff
coverage was computed:

- `status`: `complete`, `base_coverage_missing`, or `unavailable`;
- `base_sha` and `head_sha`;
- `line_covered`, `line_total`, and percentage;
- `newly_missed_line_count` and `lost_coverage_line_count`;
- `files`: per-file entries with path, old path, change status, and the same
  counters.

Line-level detail is not returned by the report endpoint; it is served by the
query interface design.

## CLI Changes

New `vericov upload` options:

| Option | Meaning |
| --- | --- |
| `--base-ref <ref>` | Resolve merge-base against this ref |
| `--base-sha <sha>` | Use this merge-base verbatim |
| `--diff <file>` | Upload a pre-generated unified diff |
| `--no-diff` | Never attach a diff |

Dry-run output includes the resolved merge-base and diff artifact summary.
`--wait` output includes patch totals and patch gate outcomes when present.
JSON output mirrors the report response `patch` object.

## Error Handling

CLI failures before upload:

- explicit `--base-ref`/`--base-sha`/`--diff` that cannot be resolved,
  parsed, or validated;
- `--diff` without a resolvable base SHA;
- diff options combined with `--no-diff`;
- diff exceeding the size limits.

CLI warnings (upload proceeds without a diff):

- automatic detection found a pull request number but no usable git checkout;
- automatic merge-base resolution failed (with the git error and the
  deepen-fetch hint).

Upload Service rejections (structured validation error, no side effects):

- more than one `diff` artifact;
- unparseable or oversized diff content;
- invalid paths inside the diff;
- `base_sha` without `pull_request_number`, or `diff` without `base_sha`.

Coverage Analysis non-retryable failures:

- missing artifact bytes for a persisted `diff` artifact;
- diff content that no longer parses (corruption);
- diff head/base inconsistent with the upload's `commit_sha`/`base_sha`.

Infrastructure failures retain normal retry behavior.

## Security And Resource Limits

The diff is untrusted input in both the CLI and the services.

- The CLI invokes git as an argument-vector subprocess, never through a
  shell, with `--no-ext-diff` to prevent external diff driver execution.
- Raw diff artifacts are limited to 10 MiB.
- A diff may touch at most 10,000 files and 500,000 changed lines; both
  bounds are enforced during parsing in the Upload Service and Coverage
  Analysis with identical limits.
- Paths inside the diff go through the same normalization and rejection rules
  as coverage report paths.
- Diff content is never logged; validation errors reference file counts and
  byte sizes, not content.
- Parser state is bounded per line; no regular expressions over unbounded
  user input.

These limits are part of the public validation contract and must match in
the CLI and both services.

## Testing Strategy

### Shared Diff Fixture Contract

Add contract fixtures under `test-contracts/` pairing raw unified diffs with
expected parsed structures, exercised by equivalent Python (CLI) and Java
(services) tests:

- added, deleted, and mixed hunks with `--unified=0`;
- renames with and without content changes;
- binary files, mode-only changes, and empty-file creation/deletion;
- paths requiring normalization and paths that must be rejected;
- oversized and truncated inputs.

### CLI Tests

- Base-ref precedence: explicit SHA, explicit ref, each CI environment.
- Merge-base resolution failure in shallow clones produces the documented
  error and hint.
- Automatic mode attaches a diff exactly when a PR number and git checkout
  are present.
- `--no-diff`, `--diff`, and conflict combinations.
- Generated diffs parse under the contract fixtures.
- Dry-run and JSON output include merge-base and diff summaries.

### Upload Tests

- Accept a valid diff artifact with `base_sha` and persist both.
- Reject each validation failure with no side effects.
- Idempotency identity changes when the diff artifact changes.
- Older clients without diff fields behave exactly as before.

### Coverage Analysis Tests

- The artifact-backed client reproduces the contract fixtures.
- End-to-end diff coverage with base coverage present: patch totals,
  `newly_missed`, and `lost_coverage` classification.
- `base_coverage_missing` path: totals from head only, finding emitted.
- Missing artifact bytes and corrupted content are non-retryable.
- No diff artifact preserves the pre-existing no-op behavior.
- Patch gate evaluation fires with a configured `patch_coverage` gate and
  contributes to `gate_status`.

### End-To-End Test

Extend `scripts/smoke-test.sh` with a pull request upload: a base upload on
the default branch, then a PR upload carrying a diff artifact whose added
lines are partially covered. The completed report must expose the `patch`
object with the expected covered/total and a failed patch gate when one is
configured.

## Documentation

- README architecture section: patch coverage is computed from a CI-supplied
  diff; no Git provider access is required or performed.
- CLI guide: the new options, CI environment detection, the shallow-clone
  requirement, and the default-branch upload requirement for base coverage.
- SELF_HOSTING: enabling the `patch_coverage` gate.
- `tests/test_public_repo.py`: add assertions that the compose file still
  contains no git-integration service while the CLI documents the diff
  options — the diff source is the CI checkout by design.

## Rollout And Compatibility

Implementation order:

1. Shared diff contract fixtures.
2. CLI merge-base resolution, diff generation, and new options.
3. Upload API `base_sha` field, `diff` artifact kind, and validation.
4. Artifact-backed diff client and the `AnalysisComponents` rebinding.
5. Report API `patch` object.
6. Smoke test and documentation.

Older clients never send diffs and observe no behavioral change. The schema
change is additive (`base_sha` column, extended artifact-kind check). Because
the diff artifact and merge-base are retained per upload, patch coverage is
recomputable from stored inputs if the algorithm changes later.

## Acceptance Criteria

- A PR upload from a CI checkout carries a resolved merge-base and a
  generated diff without provider credentials.
- Patch coverage is computed against the pinned merge-base and stored in the
  existing diff tables with `complete` status when base coverage exists.
- A moving target branch does not change already-computed patch coverage.
- `base_coverage_missing` degrades gracefully and visibly.
- A configured `patch_coverage` gate evaluates and contributes to
  `gate_status`.
- Uploads without diffs behave exactly as today.
- All diff input limits are enforced identically in the CLI and services.
- Focused unit, contract, persistence, and smoke tests pass with at least
  80% coverage in affected modules.
