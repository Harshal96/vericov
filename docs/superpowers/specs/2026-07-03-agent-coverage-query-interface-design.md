# Agent Coverage Query Interface Design

Date: 2026-07-03
Status: Draft

## Summary

Vericov will expose a read-only coverage query API on the upload service and
ship a Model Context Protocol server, `vericov-mcp`, that wraps it. Coding
agents (Claude Code, Copilot, or anything MCP-capable) can then ask "what is
uncovered in the file I just changed", "what is the patch coverage for this
pull request", or "which gates failed" mid-session, against the same
PostgreSQL data the analysis pipeline already writes.

The query interface is deterministic and contains no LLM dependency. It reads
`coverage_reports`, `coverage_file_summaries`, `coverage_line_hits`,
`component_coverage_rollups`, `pull_request_coverage_diffs` (and its file and
line tables), `coverage_gap_findings`, and `gate_evaluations`. Nothing new is
computed at write time; nothing agentic enters the two-service runtime.

Patch-coverage queries depend on
`2026-07-03-cli-supplied-pr-diff-coverage-design.md`. The gap-manifest
endpoint defined in `2026-07-03-coverage-gap-manifest-design.md` is served
through this same API surface.

## Goals

- Let an agent retrieve uncovered lines for a specific file in one call.
- Let an agent retrieve patch coverage and uncovered added lines for a pull
  request in one call.
- Expose repository, component, file, gap, and gate data through stable
  versioned endpoints instead of direct database reads.
- Ship an MCP server that any MCP client can configure with a URL and an API
  key.
- Keep every response bounded, paginated, and safe to place in an LLM
  context window.
- Support read-only credentials so agent keys cannot upload.

## Non-Goals

- Migrating the web dashboard from direct PostgreSQL reads to this API. That
  is a desirable follow-up, and endpoint shapes should not preclude it, but
  it is out of scope here.
- Any LLM call, summarization, or natural-language answering inside Vericov
  services or the MCP server. Interpretation happens in the client agent.
- Write operations of any kind through the MCP server.
- A third deployed service. The API lives on the existing upload service;
  the MCP server is a client-side process, not part of the compose stack.
- Cross-repository queries. One API key maps to one repository.
- Historical trend endpoints beyond "latest report for a ref". Trend queries
  are a follow-up once retention policy is designed.

## Current State

- The upload service exposes `POST /api/v1/uploads`, `GET
  /api/v1/uploads/{upload_id}`, `GET /api/v1/uploads/{upload_id}/artifacts`,
  `GET /api/v1/uploads/{upload_id}/report`, and `POST
  /api/v1/uploads/auth/runner-token`, all authenticated with repository API
  keys via the `Authorization` header.
- All read paths are upload-scoped: a caller must know an upload UUID. An
  agent working in a checkout knows a commit SHA, a branch, or a pull
  request number — not an upload UUID.
- The Next.js dashboard bypasses the services entirely and queries
  PostgreSQL through `web/lib/db.ts`, coupling the UI to the storage schema.
- `repository_api_keys` has no scope concept; every active key can upload.
- Per-line data exists in `coverage_line_hits` with a unique key of
  `(coverage_report_id, file_path, line_number)`; uncovered executable lines
  are the rows with `hits = 0`.

## API Design

### Authentication And Scoping

All endpoints authenticate with the existing repository API key mechanism.
The repository is resolved from the key, so no repository UUID appears in
agent-facing paths.

`repository_api_keys` gains a `scopes text[]` column:

| Scope | Grants |
| --- | --- |
| `upload` | `POST /api/v1/uploads` and the runner-token exchange |
| `read` | Every endpoint in this design plus the existing GET endpoints |

A `NULL` or empty scope list means all scopes, which preserves every existing
key unchanged. Operators mint agent keys with `{read}` only. Scope
enforcement returns `403` with a structured error naming the missing scope.

### Ref Resolution

Read endpoints accept a `ref` query parameter identifying which coverage
report to serve:

- a full 40-character commit SHA → the latest `complete` report for that
  commit;
- anything else → treated as a branch name, the latest `complete` report for
  that branch;
- omitted → the latest `complete` report for the repository's
  `default_branch`.

Resolution failures return `404` with a structured error stating what was
looked up. Every response carries a `resolved` block (report id, upload id,
commit SHA, branch, `created_at`) so agents can detect staleness.

### Endpoints

All endpoints are GET, versioned under `/api/v1/coverage`, and wrapped in the
existing `ApiResponse` envelope.

`GET /api/v1/coverage/summary?ref=`

Repository totals for the resolved report: line, branch, function, and
statement counters with percentages, `gate_status`, `config_sha256`,
`warnings`, and the `resolved` block.

`GET /api/v1/coverage/components?ref=`

The nested component tree from `component_coverage_rollups`, shaped exactly
like the report API `components` field of the monorepo component design: key,
name, path, depth, owners, effective gates, four metric counters, direct and
descendant file counts, plus `gap_count`, `debt_count`, `risk_score_total`,
and `highest_active_risk_level`. Includes `unassigned` when present.

`GET /api/v1/coverage/files?ref=&path_prefix=&component=&sort=&limit=&cursor=`

A page of `coverage_file_summaries` rows: path, leaf component key, owners,
and the four metric counters. `path_prefix` filters by normalized path
prefix; `component` filters by leaf component key; `sort` is
`line_percentage_asc` (default, worst first) or `path`. `limit` defaults to
100 with a maximum of 500. `cursor` is an opaque keyset cursor.

`GET /api/v1/coverage/file?ref=&path=`

The primary agent tool. One file's summary plus its uncovered executable
lines, collapsed into inclusive ranges:

```json
{
  "path": "services/payments/src/Retry.java",
  "leaf_component_key": "payments-api",
  "owners": ["team-payments"],
  "metrics": { "line": { "covered": 41, "total": 58 }, "...": "..." },
  "uncovered_ranges": [
    { "start": 12, "end": 14 },
    { "start": 87, "end": 87 }
  ]
}
```

Ranges come from `coverage_line_hits` rows with `hits = 0`, merged when
consecutive. An unknown path returns `404` with a `did_you_mean` list of up
to 5 same-basename paths from the report, because path-prefix mismatches
between build output and repository layout are the most common agent error.

`GET /api/v1/coverage/pull-requests/{number}?include=files,lines`

Patch coverage for the latest report of that pull request number: the
`pull_request_coverage_diffs` row (status, base and head SHA, patch totals,
newly-missed and lost-coverage counts), optionally per-file entries, and
optionally per-file uncovered added lines as ranges built from
`pull_request_coverage_diff_lines` where `change_type = 'added'`,
`executable`, and head hits are zero or null. Returns `404` when the
repository has no diff-bearing report for that number, with a hint that
uploads must include a diff artifact.

`GET /api/v1/coverage/gaps?ref=&pull_request=&component=&min_risk_level=&status=&limit=&cursor=`

A page of `coverage_gap_findings` for the resolved report: file path, target
type, line range, symbol, reason code, explanation, confidence, risk score
and level, owners, component key, next action, and status. Default filter is
`status=active`. `min_risk_level` orders and filters by the existing
`critical > high > medium > low` scale; default sort is risk score
descending.

`GET /api/v1/coverage/gates?ref=`

All `gate_evaluations` rows for the resolved report: gate name, type, metric,
scope (repository or component with key and path), threshold, actual,
blocking flag, and status.

### Response Conventions

- Percentages are computed server-side with the existing `0/0 = 100%`
  semantics and returned alongside raw counters.
- All list endpoints use keyset cursors; cursors are opaque strings.
- Responses that were truncated by `limit` say so explicitly with
  `"truncated": true` and the next cursor, so an agent never mistakes a page
  for the whole answer.
- Errors use the existing structured validation-error envelope with a stable
  `code` (`ref_not_found`, `file_not_found`, `scope_missing`, and so on).

## MCP Server

A new Python package at `clis/mcp`, published as `vericov-mcp`, following the
layout conventions of `clis/coverage-upload`.

- Python 3.10+, official `mcp` SDK, stdio transport.
- Configuration: `VERICOV_API_URL` and `VERICOV_API_KEY` environment
  variables, with `--api-url`/`--api-key` overrides — the same variables the
  upload CLI documents.
- Strictly a thin client of the query API. It performs no database access,
  no git access, and no LLM calls, and it holds no state beyond the
  configuration.

Runnable via `uvx`, so agent configuration is one block:

```json
{
  "mcpServers": {
    "vericov": {
      "command": "uvx",
      "args": ["--from", "vericov-mcp", "vericov-mcp"],
      "env": {
        "VERICOV_API_URL": "http://localhost:8080",
        "VERICOV_API_KEY": "..."
      }
    }
  }
}
```

Tools map one-to-one onto endpoints:

| Tool | Endpoint |
| --- | --- |
| `get_coverage_summary` | `/api/v1/coverage/summary` |
| `get_component_coverage` | `/api/v1/coverage/components` |
| `list_file_coverage` | `/api/v1/coverage/files` |
| `get_file_coverage` | `/api/v1/coverage/file` |
| `get_patch_coverage` | `/api/v1/coverage/pull-requests/{number}` |
| `list_coverage_gaps` | `/api/v1/coverage/gaps` |
| `get_gate_status` | `/api/v1/coverage/gates` |
| `get_gap_manifest` | per the gap-manifest design |

Tool descriptions are written for agent consumption: each states what
question it answers, what `ref` defaults to, and that paths are
repository-relative. Tool results render as compact JSON; the server
truncates any single result above 50 KiB and appends an explicit truncation
notice with the parameters to narrow the query.

## Data Model

| Change | Detail |
| --- | --- |
| `repository_api_keys.scopes` | `text[]`, `NULL` means all scopes; values constrained to `upload` and `read` |
| Index | `coverage_reports (repository_id, branch, created_at DESC) WHERE status = 'complete'` for branch ref resolution |
| Index | `coverage_reports (repository_id, commit_sha, created_at DESC) WHERE status = 'complete'` for SHA ref resolution |
| Index | `coverage_gap_findings (coverage_report_id, status, risk_score DESC)` for gap listing |

Existing unique constraints already cover per-report file and line lookups.
No table gains rows from this design; it is read-only over what analysis
already persists.

## Error Handling

- Unknown `ref`, unknown file path, and unknown pull request number return
  `404` with stable codes and, where cheap, actionable hints
  (`did_you_mean`, "upload with a diff artifact").
- Invalid pagination parameters, unknown sort keys, and malformed filters
  return `400` with the offending parameter named.
- A key without the `read` scope receives `403 scope_missing`.
- Ref resolution racing an in-flight analysis returns the previous complete
  report; agents observe monotonic report history, never partial reports.

## Security And Resource Limits

- The query API shares the trust posture of the existing endpoints: no
  bundled gateway, private network or operator-provided proxy in front.
- Read-scope keys cannot upload, exchange runner tokens, or mutate anything;
  agent credentials should be minted read-only.
- `limit` is capped at 500 rows; `coverage_line_hits` reads are always
  scoped to one `(report, file)` pair, never a whole report.
- Path parameters go through the existing normalized-path validation before
  touching a query.
- Query parameters are bounded in length (paths 1,024 characters, cursors
  512 characters).
- Responses never include artifact bytes or storage paths.
- The MCP server sends the API key only to the configured base URL and
  refuses plain-HTTP URLs unless the host is a loopback address.

## Testing Strategy

### API Tests

- Ref resolution: SHA, branch, default branch, missing report, and the
  race-with-processing case.
- Scope enforcement across every endpoint for legacy, read-only, and
  upload-only keys.
- Uncovered-range collapsing against known line-hit fixtures, including
  single-line ranges and fully covered files (empty list, not an error).
- `did_you_mean` suggestions for basename matches.
- Pagination: stable ordering, cursor round-trips, and explicit truncation
  flags.
- Patch endpoint against fixtures from the diff coverage design, including
  `base_coverage_missing` and no-diff `404`.
- Gap and gate endpoints reflect exactly what analysis persisted.

### MCP Server Tests

- Each tool calls the expected endpoint with the expected parameters
  (mocked HTTP).
- Configuration precedence and the loopback-only plain-HTTP rule.
- Result truncation above the size threshold, with the truncation notice.
- Error responses map to MCP tool errors, not crashes.
- Coverage gated at 80% with pytest-cov, matching the upload CLI.

### End-To-End Test

Extend the smoke test: after the existing upload completes, query
`/api/v1/coverage/summary`, `/api/v1/coverage/file` for a known fixture
file, and `/api/v1/coverage/gates`, asserting values match the report
endpoint. Run one `vericov-mcp` tool call against the live stack over stdio.

## Documentation

- README: a new "For coding agents" section with the MCP configuration
  block and one worked example (agent edits a file, asks for uncovered
  lines, writes a test).
- CLI guide: minting read-only keys and the shared environment variables.
- SELF_HOSTING: scope column migration note; existing keys keep working.
- A new `clis/mcp/README.md` documenting every tool with example calls.
- `tests/test_public_repo.py`: additive assertions — the MCP package
  exists with pytest-cov gating, the compose file still defines exactly the
  two services, and the README documents the agent section.

## Rollout And Compatibility

Implementation order:

1. Scope column and key-scope enforcement (legacy keys unaffected).
2. Ref resolution and the summary, components, files, file, gates
   endpoints — useful before the diff design lands.
3. Gap listing endpoint.
4. Pull request patch endpoint, once diff coverage is active.
5. MCP server package, released like `vericov-coverage-upload`.
6. Smoke test and documentation.

Every change is additive. Existing upload-scoped endpoints keep their exact
behavior, and no client is required to adopt the new surface.

## Acceptance Criteria

- An agent with a read-only key can retrieve uncovered line ranges for one
  file in one round trip, in under 100 ms against a warm local stack.
- Summary, components, files, file, patch, gaps, and gates endpoints serve
  data identical to what the analysis pipeline persisted.
- Read-only keys cannot upload; legacy keys lose no capability.
- `vericov-mcp` runs via `uvx` with two environment variables and exposes
  every listed tool with bounded, truncation-aware results.
- All list responses are paginated and explicitly flag truncation.
- The compose stack still runs exactly two services.
- Focused unit, persistence, API, MCP, and smoke tests pass with at least
  80% coverage in affected modules.
