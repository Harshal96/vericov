# Monorepo Component Coverage Design

Date: 2026-06-15
Status: Approved

## Summary

Vericov will support hierarchical component coverage for monorepositories through
the repository-owned `.vericov.yml` file.

Repositories may define nested components, leaf path patterns, inherited owners,
and inherited coverage gates. The upload CLI sends an immutable configuration
snapshot with each upload. Coverage Analysis applies top-level coverage
exclusions first, assigns every remaining file to at most one leaf component,
rolls coverage through the component hierarchy, evaluates component gates, and
persists the result for report reads.

This design extends
`docs/superpowers/specs/2026-06-15-coverage-file-exclusions-design.md`. Its
path normalization, gitignore matching, immutable upload snapshot, and
non-retryable validation decisions remain authoritative.

## Goals

- Let teams define stable component boundaries in a shared monorepository.
- Calculate coverage independently for each leaf component.
- Aggregate leaf coverage through an arbitrary component hierarchy.
- Apply repository coverage exclusions before any component behavior.
- Support inherited owners and coverage thresholds.
- Preserve deterministic, reproducible results for every upload.
- Expose component metrics and gate outcomes through the report API.
- Preserve repository-only behavior when no component configuration exists.

## Non-Goals

- Managing components through a separate control-plane service or database
  catalog.
- Fetching `.vericov.yml` from a Git provider during analysis.
- Assigning one file to multiple leaf components.
- Allowing parent components to own files directly.
- Defining component-specific exclusion rules. Source exclusions remain in the
  top-level `ignore` list.
- Automatically generating components from workspaces, manifests, CODEOWNERS,
  or build graphs.
- Adding component patch-coverage gates in the first release.
- Reprocessing or backfilling historical reports.
- Building a component-management user interface.

## Current State

The public runtime contains the upload and coverage-analysis services backed by
PostgreSQL. The earlier component catalog and control-plane context source were
removed from the public product.

Some component-aware analysis types and database columns remain:

- `CoverageFileSummary` has a nullable UUID-shaped component field.
- `CoverageComponentRollup` uses a UUID component ID and one owner string.
- `coverage_file_summaries`, `component_coverage_rollups`, and
  `coverage_gap_findings` retain nullable or unconstrained UUID component
  columns.
- Coverage Analysis receives an empty repository context in the public runtime,
  so those component paths are not populated from repository configuration.
- The upload report endpoint returns repository totals only.

The implementation should replace this inactive UUID model with stable
repository-defined string keys. It should not reintroduce a component catalog.

## Configuration Contract

`.vericov.yml` remains the sole canonical project configuration filename.
Components are an optional top-level list.

```yaml
version: 1

ignore:
  - generated/**
  - vendor/**
  - "!vendor/maintained/**"

components:
  - key: commerce
    name: Commerce
    owners:
      - team-commerce
    gates:
      line: 80
    components:
      - key: payments
        name: Payments
        owners:
          - team-payments
        gates:
          line: 90
        components:
          - key: payments-api
            name: Payments API
            paths:
              - services/payments/api/**
```

If `components` is omitted, Vericov produces repository-only reports. If the
field is present, it must contain at least one component.

### Component Shape

Every component supports:

| Field | Required | Meaning |
| --- | --- | --- |
| `key` | Yes | Stable identity unique across the entire file |
| `name` | No | Display name; defaults to `key` |
| `owners` | No | Non-empty owner list replacing inherited owners |
| `gates` | No | Metric thresholds inherited by descendants |
| `paths` | Leaf only | One or more source path patterns |
| `components` | Parent only | One or more child components |

A component is either:

- a parent with non-empty `components` and no `paths`; or
- a leaf with non-empty `paths` and no `components`.

Parents are aggregation and policy nodes only. A node with both fields, neither
field, or an empty field is invalid.

Component keys:

- must match `[a-z0-9][a-z0-9._-]{0,119}`;
- must be globally unique within the snapshot;
- must remain stable when a display name or path changes; and
- cannot equal the reserved synthetic key `unassigned`.

Unknown fields are rejected.

### Owners

An omitted `owners` field inherits the nearest ancestor's effective owner list.
A supplied field must contain at least one unique, non-empty string and replaces,
rather than merges with, inherited owners.

A root component without owners has an empty effective owner list. Reports may
display this as unowned, but the persisted value remains an empty list.

### Gates

Supported component gate metrics are:

- `line`
- `branch`
- `function`
- `statement`

Each value is an inclusive percentage threshold from `0` through `100`.
Component gates are blocking in this first release.

Inheritance is per metric. A descendant receives every ancestor metric it does
not redefine, while the nearest definition wins for an overridden metric.

For example, a parent may define `line: 80` and `branch: 70`, while a descendant
overrides only `line: 90`; its effective gates are `line: 90` and `branch: 70`.

### Component Path Patterns

Component `paths` use the same normalized path model and gitignore-style matcher
as top-level `ignore` rules, with these restrictions:

- A component path cannot begin with `!`. Exclusions and re-inclusions belong
  only in the top-level `ignore` list.
- Absolute filesystem paths and parent traversal are invalid.
- Matching is case-sensitive.
- A leaf's path list is a union; declaration order does not create overrides.
- Duplicate normalized patterns within one leaf are rejected.
- Duplicate normalized patterns assigned to different leaves are rejected
  during structural validation.

## Exclusion And Assignment Semantics

Coverage processing follows this order:

1. Parse each coverage artifact.
2. Normalize every repository-relative source path.
3. Apply ordered top-level `ignore` rules, including `!` re-inclusion.
4. Remove ignored files before report merging.
5. Merge the remaining parsed coverage files.
6. Resolve each merged file to the most-specific matching leaf component.
7. Assign unmatched included files to the synthetic `unassigned` rollup.
8. Calculate leaf and ancestor rollups.
9. Evaluate repository and component gates.
10. Persist the report, rollups, evaluations, and snapshot hash transactionally.

Ignored files never reach:

- repository totals;
- file summaries or line-hit maps;
- normalized coverage storage;
- component matching or ambiguity checks;
- the `unassigned` rollup;
- component rollups or gates;
- diff coverage, gap extraction, or risk scoring.

A re-included file proceeds through normal component assignment.

### Most-Specific Match

For every component pattern that matches a file, Vericov calculates a static
specificity tuple:

1. the number of complete literal path segments before the first unescaped
   wildcard token (`*`, `?`, or `[`); then
2. the number of literal characters before that wildcard token.

An optional leading `/` used for root anchoring is not counted. Higher tuple
values are more specific. A leaf's specificity is the highest tuple among its
matching patterns.

The leaf with the highest specificity wins. If two or more leaves have the same
highest specificity for an actual included report file, analysis fails
non-retryably and identifies the file plus the conflicting component keys.
Declaration order and component key order never break a tie.

This permits useful overlaps such as `services/**` and
`services/payments/**` while rejecting ambiguous ownership.

### Unassigned Files

When components are configured, every included file that matches no leaf is
assigned to a synthetic root-level rollup:

- key: `unassigned`
- name: `Unassigned`
- no owners
- no inherited component gates

The report includes an `unassigned_files` warning with the affected file count.
The warning does not fail a gate by itself.

## Immutable Configuration Snapshot

The CLI owns YAML discovery and parsing. It converts the relevant configuration
to a normalized immutable snapshot containing:

```json
{
  "version": 1,
  "ignore": [],
  "components": []
}
```

The normalized component representation makes defaults explicit while
preserving inheritance:

- `name` is resolved to a string;
- `owners` is either `null` for inheritance or a non-empty array;
- `gates` is an object;
- `paths` is an array;
- `components` is an array.

Object keys are serialized in lexical order with UTF-8 encoding and no
insignificant whitespace. Array order is preserved. SHA-256 of those canonical
JSON bytes is `config_sha256`.

The upload request carries top-level `ignore`, `components`, and
`config_sha256` values. The Upload Service:

1. reconstructs and validates the normalized snapshot;
2. recomputes its SHA-256;
3. rejects a supplied hash mismatch;
4. stores the snapshot and computed hash with the upload.

The canonical snapshot participates in upload idempotency. Changing ignore
rules, component hierarchy, owners, gates, names, or paths creates a distinct
upload identity.

For transition compatibility, the backend may accept `ignore` from an
exclusions-capable client that does not yet send `components` or
`config_sha256`. It computes and stores the hash when any configuration snapshot
is present. Clients that send neither retain the existing null-snapshot,
repository-only behavior.

Coverage Analysis independently validates the persisted snapshot and verifies
its stored hash before processing it.

## Rollup Model

Every included file contributes:

- once to its assigned leaf;
- once to each ancestor of that leaf; and
- once to repository totals through normal report merging.

A file never contributes to a sibling or a second leaf. Parent metrics therefore
represent all descendant leaves, while leaf metrics represent only directly
assigned files.

All configured components are present in a report, including components with
zero matching executable files. A `0/0` metric has the existing Vericov
percentage semantics of `100%`, including during gate evaluation.

The following invariant must hold independently for each metric:

```text
sum(root component rollups) + unassigned rollup = repository total
```

The synthetic `unassigned` node is omitted when no included files are
unassigned.

## Data Model

### Upload Snapshot

Add to `uploads`:

| Column | Type | Meaning |
| --- | --- | --- |
| `config_snapshot_json` | `jsonb` nullable | Immutable normalized version, ignore, and component tree |
| `config_sha256` | `text` nullable | Lowercase 64-character snapshot hash |

If the exclusions feature lands first with a temporary ignore-only persistence
column, migrate it into this combined snapshot before the next public release.
Do not keep two authorities for `.vericov.yml`.

### Coverage Reports

Add to `coverage_reports`:

| Column | Type | Meaning |
| --- | --- | --- |
| `config_sha256` | `text` nullable | Snapshot used by this report |
| `gate_status` | `text` | `passed`, `failed`, `warning`, or `not_evaluated` |

Upload processing still completes successfully when a gate fails. The upload
status remains `processed`; `gate_status` communicates policy outcome.

### File Summaries

Replace the unused UUID-shaped `coverage_file_summaries.component_id` with:

| Column | Type | Meaning |
| --- | --- | --- |
| `leaf_component_key` | `text` nullable | Assigned configured leaf or `unassigned` |

Ignored files produce no file-summary rows.

### Component Rollups

Replace the UUID-and-owner-shaped component rollup with one row per configured
component per report:

| Column | Type | Meaning |
| --- | --- | --- |
| `component_key` | `text` | Stable configured key or `unassigned` |
| `parent_component_key` | `text` nullable | Parent key within the same snapshot |
| `component_path` | `text[]` | Root-to-node key path |
| `depth` | `integer` | Root depth is zero |
| `position` | `integer` | Declaration order among siblings |
| `name` | `text` | Resolved display name |
| `owners` | `text[]` | Effective inherited owners |
| `effective_gates_json` | `jsonb` | Effective metric thresholds |
| coverage counters | `integer` | Line, branch, function, and statement totals |
| `direct_file_count` | `integer` | Files assigned directly to this node |
| `descendant_file_count` | `integer` | Files represented by the rollup |

The unique key is `(coverage_report_id, component_key)`.

Existing gap and risk records that identify a component should replace their
UUID-shaped `component_id` with nullable `component_key`. This keeps all
coverage-derived component evidence aligned to the same repository-defined
identity.

### Gate Evaluations

Reuse `gate_evaluations` for repository and component gates. Add explicit scope
fields:

| Column | Type | Meaning |
| --- | --- | --- |
| `source` | `text` | `repository` or `component_config` |
| `scope_type` | `text` | `repository` or `component` |
| `scope_key` | `text` nullable | Component key for component evaluations |
| `scope_path` | `text[]` | Component hierarchy path |

Component gate identity is `(coverage_report_id, scope_key, metric)`.
Evaluation details retain actual counters, percentage, threshold, and snapshot
hash for diagnostics.

Because component rollups are derived data and the public runtime has no active
UUID component authority, the pre-1.0 migration may discard old component
rollup rows and null or remove old UUID projections. Repository report totals
remain intact. No historical component tree is synthesized.

## Gate Evaluation

Repository gates continue to evaluate repository totals after ignored files are
removed.

`.vericov.yml` is the only authority for component gates. Legacy
`repository_gate_configurations` rows with UUID-shaped `component_coverage`
scope are disabled during migration and are not combined with config-defined
component gates. Repository, patch, and other non-component database gates
retain their existing behavior.

For component gates:

- a parent gate evaluates the parent's complete descendant rollup;
- a leaf gate evaluates only files assigned to that leaf;
- inherited metrics are evaluated as though declared on the descendant;
- `unassigned` has no component gates;
- a `0/0` metric evaluates as `100%`;
- every configured gate produces one persisted evaluation.

Any failed blocking repository or component gate sets
`coverage_reports.gate_status` to `failed`. Otherwise, a warning evaluation sets
it to `warning`; evaluated reports with no failures or warnings are `passed`.
Reports without configured gates use `not_evaluated`.

The CLI and API must not confuse gate failure with analysis failure. A completed
report with `gate_status: failed` remains readable and reproducible.

## Report API

The upload report response adds:

- `config_sha256`;
- `gate_status`;
- `warnings`; and
- `components`.

`components` is a nested list of root nodes. Each node contains:

- key, name, hierarchy path, owners, and depth;
- line, branch, function, and statement metrics;
- effective gate thresholds and their evaluations;
- direct and descendant file counts; and
- nested children.

The `unassigned` node appears as a root only when needed. Ignored filenames are
never exposed because they do not enter report data.

Repository totals remain the primary top-level metrics, preserving compatibility
for existing API consumers.

## Error Handling

The CLI fails before upload for:

- malformed YAML or unsupported config version;
- a non-list or empty `components` field;
- duplicate, reserved, or invalid component keys;
- parent/leaf shape violations;
- invalid owner lists or gate thresholds;
- invalid component patterns;
- identical normalized patterns assigned to different leaves;
- any exclusion error defined by the exclusions design.

Errors identify the `.vericov.yml` field path and list index where possible.

The Upload Service repeats structural validation before persistence. Invalid
requests return the existing structured validation-error envelope and create no
upload, artifact, job, or queue side effects.

Coverage Analysis treats these as non-retryable:

- invalid persisted snapshot structure;
- snapshot hash mismatch;
- equal-specificity assignment ambiguity for an included file.

Infrastructure and transient storage failures retain normal retry behavior.

If every source file is ignored, analysis succeeds with a repository `0/0`
report and a configured zero-valued component hierarchy. Test-result artifacts
continue to process normally.

## Security And Resource Limits

All configuration is untrusted input.

- Use safe YAML loading in the CLI and JSON parsing in backend services.
- Reject absolute paths, parent traversal, malformed glob syntax, empty values,
  and unknown fields.
- Use the bounded shared glob matcher rather than evaluating user-provided
  regular expressions.
- Limit both the raw `.vericov.yml` input and canonical snapshot to 256 KiB.
- Limit hierarchy depth to 20.
- Limit the snapshot to 1,000 components.
- Limit each leaf to 100 path patterns.
- Limit individual patterns to 1,024 characters.
- Limit owner entries to 200 characters.
- Avoid logging full snapshots or repository path inventories on validation
  failure.

These limits are part of the public validation contract and must match in the
CLI and Upload Service.

## Testing Strategy

### Shared Matcher Contract

Extend the exclusion matcher fixtures so equivalent Python and Java tests cover:

- normalization before ignore and component matching;
- exclusion followed by selective re-inclusion;
- anchored, basename, directory, wildcard, range, and `**` patterns;
- component specificity ranking;
- valid parent/child overlaps;
- equal-specificity ambiguity;
- ignored files that would otherwise be ambiguous;
- malformed patterns and traversal attempts.

### CLI Tests

- Parse valid nested components into immutable values.
- Reject every invalid parent/leaf shape.
- Validate keys, owners, gates, limits, and duplicate patterns.
- Resolve names while preserving owner and gate inheritance markers.
- Produce stable canonical JSON and SHA-256.
- Change idempotency when any relevant snapshot value changes.
- Preserve ignore and component declaration order.
- Retain repository-only behavior when `components` is omitted.

### Upload Tests

- Accept missing, ignore-only, and full component snapshots.
- Reject invalid snapshots before side effects.
- Reject a supplied hash mismatch.
- Persist and reload the exact normalized JSON and hash.
- Include the snapshot in duplicate-upload identity.
- Preserve older client behavior.

### Coverage Analysis Tests

- Filter ignored files before merging and assignment.
- Resolve each included file to one deterministic leaf.
- Put unmatched files in `unassigned`.
- Exclude ignored files from ambiguity detection and unassigned warnings.
- Aggregate multiple hierarchy depths without sibling leakage.
- Preserve the repository/root-rollup invariant for every metric.
- Persist configured components with `0/0` metrics.
- Evaluate inherited and overridden gates.
- Persist component scope on gap and risk evidence.
- Mark ambiguity and snapshot corruption as non-retryable.

### Persistence And API Tests

- Replace UUID-shaped component bindings with string keys.
- Persist one rollup row per report and component key.
- Persist gate scope and hierarchy paths.
- Return a stable nested component tree in declaration order.
- Return actionable gate failures with key, path, metric, actual, and required
  values.
- Keep repository-only report responses backward compatible.

### End-To-End Test

A smoke fixture contains:

- an ignored generated file;
- a file selectively re-included by `!`;
- files assigned to sibling leaves;
- an included unmatched file;
- a parent gate inherited by descendants; and
- a leaf gate override.

The completed report must exclude ignored data, include the expected component
tree and `unassigned` warning, satisfy the rollup invariant, and expose the
correct overall gate status.

## Documentation

Update the root README and CLI guide to:

- describe `.vericov.yml` as the sole config file;
- show `ignore` and `components` together;
- explain that exclusions run before component matching;
- explain leaf-only paths and parent rollups;
- document owner and gate inheritance;
- document `unassigned` warnings and ambiguity errors;
- explain that gate failure does not mean analysis failure; and
- state that configuration changes affect future uploads only.

## Rollout And Compatibility

Implementation order:

1. Complete the shared exclusion matcher and immutable ignore snapshot.
2. Extend the CLI config model and contract fixtures with components.
3. Add backend schema and upload API support for the combined snapshot.
4. Load and verify the snapshot in Coverage Analysis.
5. Add leaf assignment, hierarchy rollups, and component gate evaluation.
6. Replace UUID component projections with stable keys.
7. Extend report reads and API responses.
8. Publish the component-capable CLI after backend deployment.

Older clients that omit the snapshot continue to produce repository-only
reports. Ignore-only transitional clients continue to receive exclusion behavior
without component rollups.

There is no historical backfill. Reports created before this feature retain
their existing repository totals and return no component tree.

## Acceptance Criteria

- `.vericov.yml` supports a validated nested component hierarchy.
- Only leaves define paths; parents provide rollup and inherited policy.
- Top-level ignore rules filter files before component matching.
- Each included file belongs to exactly one leaf or `unassigned`.
- More-specific paths win and equal-specificity ties fail deterministically.
- Every leaf and ancestor receives correct coverage metrics.
- Owners and gate thresholds inherit with nearest-node override semantics.
- Component gate outcomes contribute to report gate status without failing
  analysis processing.
- Uploads and reports retain a verified immutable snapshot hash.
- The report API returns repository totals plus a nested component tree.
- Older clients and repositories without components retain repository-only
  behavior.
- Focused unit, integration, contract, persistence, and smoke tests pass with
  at least 80% coverage in affected modules.
