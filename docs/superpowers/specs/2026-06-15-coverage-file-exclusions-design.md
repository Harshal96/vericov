# Coverage File Exclusions Design

## Summary

Vericov will support repository-controlled coverage exclusions through a single
canonical configuration file: `.vericov.yml`.

Repositories can define an ordered top-level `ignore` list using gitignore-style
patterns. The upload CLI validates the file and sends an immutable snapshot of
the ignore rules with each upload. Coverage Analysis filters normalized file
paths before merging reports, ensuring excluded files do not affect any
downstream coverage result.

## Goals

- Let repositories exclude generated, vendored, or otherwise irrelevant files
  and directories from coverage analysis.
- Use familiar gitignore-style matching, including ordered negation rules.
- Make each analysis reproducible from the configuration submitted with its
  upload.
- Remove excluded files consistently from all coverage-derived behavior.
- Preserve compatibility with older clients that do not send ignore rules.

## Non-Goals

- Fetching `.vericov.yml` from a Git provider during analysis.
- Managing ignore rules through the control-plane repository-config API.
- Reprocessing historical reports when `.vericov.yml` changes.
- Supporting multiple repository configuration filenames.
- Excluding coverage artifact files from CLI discovery. Existing
  `upload.discover.exclude` remains responsible for artifact discovery.

## Configuration Contract

`.vericov.yml` is the only automatically discovered configuration filename.

```yaml
version: 1

ignore:
  - generated/**
  - vendor/**
  - "!vendor/maintained/**"

upload:
  coverage:
    - coverage/lcov.info
```

The top-level `ignore` value is optional. If omitted, Vericov excludes no source
files.

The existing `vericov.yml` filename is no longer accepted. If it exists, the CLI
fails with an actionable error instructing the user to rename it to
`.vericov.yml`. If both filenames exist, the CLI reports the same conflict and
rename requirement rather than selecting one.

An explicitly supplied config path remains supported, but the file must be named
`.vericov.yml`. This keeps the project contract fixed even when callers use
`--config`.

## Ignore Semantics

Ignore rules operate on normalized repository-relative source paths:

- Backslashes are normalized to `/`.
- Leading `./` segments are removed.
- Matching is case-sensitive.
- Rules are evaluated in declaration order.
- A later matching rule overrides an earlier matching rule.
- A leading `!` re-includes a previously excluded path.
- A leading `/` anchors a rule at the repository root.
- A trailing `/` represents a directory pattern and applies to its descendants.
- Patterns without `/` match a file or directory name at any depth.
- `*`, `?`, character ranges, and `**` follow gitignore-style glob behavior.

Rules must be non-empty strings. A bare `!`, malformed character range, invalid
escape, absolute filesystem path, or parent traversal segment is rejected.
Patterns describe repository paths only and cannot access the filesystem.

The implementation should use one shared semantic test suite across the CLI and
backend matcher implementations. The upload payload remains the authority for
analysis, so the backend independently validates all received rules.

## Architecture

### Upload CLI

The Python CLI remains the owner of `.vericov.yml` discovery and YAML parsing.
Its immutable config model gains a tuple of top-level ignore rules.

During upload planning, the CLI:

1. Discovers and validates `.vericov.yml`.
2. Preserves ignore rule order exactly.
3. Adds the rules to the upload request.
4. Includes the rules in idempotency-key material.

Changing ignore rules therefore creates a distinct upload even when the coverage
artifacts and commit metadata are otherwise unchanged.

### Upload Service

The upload API accepts an optional ordered `ignore` array. Missing or null values
mean an empty list.

The service validates the rules at the HTTP boundary and persists an immutable
snapshot with the upload row. The snapshot belongs to the upload, not to mutable
repository configuration.

The existing queue event does not need to duplicate the rules. Coverage Analysis
loads them with the rest of the persisted upload input.

### Coverage Analysis

Coverage Analysis loads the ordered ignore rules as part of
`CoverageAnalysisInput`.

Each coverage artifact is parsed normally so all supported formats retain their
existing parser behavior. After parsing and path normalization, a dedicated
coverage-file filter removes ignored `ParsedCoverageFile` values. Filtering
occurs before `CoverageReportMerger`.

This boundary ensures excluded files never enter:

- report totals or file summaries;
- line-hit maps or normalized coverage storage;
- pull-request diff coverage;
- coverage-gap extraction and risk scoring;
- component, package, or owner resolution and rollups;
- gate evaluation or downstream report reads.

If every parsed file is excluded, analysis succeeds and persists a normal empty
coverage report with zero covered and zero total metrics. Test-result artifacts
in the same upload continue to be processed normally.

## Data Model And API

The upload request adds:

```json
{
  "ignore": [
    "generated/**",
    "vendor/**",
    "!vendor/maintained/**"
  ]
}
```

The persisted `uploads` record gains an ordered JSON array or equivalent
immutable representation for the ignore snapshot. A JSON array is preferred
over a SQL text array because it gives the versioned configuration snapshot room
to evolve without changing ordering semantics.

`CoverageAnalysisInput` gains an immutable list of ignore rules loaded from the
upload record.

No public report schema needs to expose ignored filenames. The upload snapshot
is retained for reproducibility and diagnostics, while report data contains only
included files.

## Error Handling

The CLI fails before upload for:

- legacy or conflicting config filenames;
- malformed YAML;
- an unsupported config version;
- unknown configuration keys;
- a non-list `ignore` value;
- empty, non-string, or invalid ignore rules.

CLI errors identify the relevant config path and rule index without printing
secrets or file contents.

The upload service repeats structural and rule validation. Invalid snapshots
return the existing structured validation-error response and are not persisted
or queued.

Coverage Analysis treats invalid persisted rules as a non-retryable analysis
failure because retrying cannot repair immutable input. This protects workers
from malformed data inserted outside the public upload API.

Older clients may omit `ignore`; the upload service stores an empty list and
analysis behaves exactly as it does before this feature.

## Testing Strategy

### CLI Unit Tests

- `.vericov.yml` is discovered as the sole canonical config.
- `vericov.yml` and dual-file states produce actionable rename errors.
- Explicit config paths with another filename are rejected.
- Valid ordered ignore rules are parsed without reordering.
- Invalid rule values and syntax produce field-specific errors.
- Ignore changes alter the resolved idempotency key.
- Missing ignore config produces an empty tuple.

### Matcher Contract Tests

Equivalent cases run against the Python and Java implementations:

- ordinary file and directory patterns;
- root anchoring;
- basename matching at any depth;
- `*`, `?`, character ranges, and `**`;
- ordered overrides and `!` re-inclusion;
- path normalization and case sensitivity;
- malformed patterns and traversal attempts.

### Upload Tests

- HTTP requests accept missing and populated ignore arrays.
- Invalid rules are rejected before authentication-independent side effects.
- Repository resolution preserves the snapshot.
- JDBC persistence and reload preserve exact rule order.
- Duplicate uploads use idempotency material that includes ignore rules.

### Coverage Analysis Tests

- Filtering happens after parsing and before merging.
- Excluded files do not affect any coverage metric or line hit.
- Re-included files are restored according to rule order.
- Multiple artifacts apply the same snapshot consistently.
- All files excluded produces a successful `0/0` report.
- Test results still persist when all coverage files are excluded.
- Excluded files do not reach normalized storage, diff coverage, gap extraction,
  component rollups, or gates.
- Invalid persisted rules cause a non-retryable failure.

### End-To-End Test

A smoke fixture uploads a report containing included and excluded files with a
tracked `.vericov.yml`. The completed report must contain only included files,
and its totals must equal the included-file metrics.

## Documentation

Update the root README and CLI guide to:

- name `.vericov.yml` as the only supported filename;
- distinguish source-file `ignore` from `upload.discover.exclude`;
- document matching order and negation;
- show generated-directory and selective re-inclusion examples;
- state that changes affect future uploads only;
- explain the successful empty-report behavior.

## Rollout And Compatibility

This is a forward-compatible upload API addition. Deploy backend support before
publishing the CLI version that sends ignore snapshots.

Existing backend rows and older clients behave as though `ignore` is empty.
There is no historical backfill and no automatic report recomputation.

The filename change is intentionally strict: users with `vericov.yml` receive a
clear rename error instead of a deprecation period.

## Acceptance Criteria

- `.vericov.yml` is the sole accepted project config filename.
- Top-level ordered `ignore` rules support gitignore semantics and negation.
- The CLI sends and the backend persists an immutable rule snapshot per upload.
- The snapshot participates in upload idempotency.
- Excluded files are absent from all persisted and derived coverage behavior.
- Excluding every file creates a successful empty `0/0` report.
- Older clients that omit ignore rules continue to work unchanged.
- Focused unit, integration, contract, and smoke tests pass.
