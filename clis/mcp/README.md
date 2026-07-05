# vericov-mcp

A [Model Context Protocol](https://modelcontextprotocol.io) server that gives
coding agents read-only access to Vericov's coverage data: uncovered lines,
patch coverage, component rollups, gap findings, and gate status. It is a
thin HTTP client of the upload service's `/api/v1/coverage/*` query API —
`vericov-mcp` performs no database access, no git access, and no LLM calls.
It cannot upload, exchange runner tokens, or mutate any state.

## Configuration

| Variable / flag | Meaning |
| --- | --- |
| `VERICOV_API_URL` / `--api-url` | Base URL of the upload service, for example `http://localhost:8080`. |
| `VERICOV_API_KEY` / `--api-key` | A repository API key with the `uploads:read` scope. Mint a read-only key; never reuse an upload key here. |

Plain `http://` is only accepted for loopback hosts (`localhost`,
`127.0.0.1`); any other host must use `https://`, so the API key is never
sent in cleartext over a network.

## Running

Via `uvx` (recommended — no local install):

```bash
uvx --from vericov-mcp vericov-mcp
```

Agent configuration block (Claude Code, or any MCP-capable client):

```json
{
  "mcpServers": {
    "vericov": {
      "command": "uvx",
      "args": ["--from", "vericov-mcp", "vericov-mcp"],
      "env": {
        "VERICOV_API_URL": "http://localhost:8080",
        "VERICOV_API_KEY": "vc_repo_..."
      }
    }
  }
}
```

## Tools

Every tool takes a `ref` (a commit SHA, a branch name, or omitted for the
repository's default branch) unless noted, and every path is
repository-relative. Results are compact JSON; any single result above 50 KiB
is replaced with a truncation notice naming how to narrow the query.

| Tool | Answers | Endpoint |
| --- | --- | --- |
| `get_coverage_summary(ref?)` | What's the overall coverage and gate status for this ref? | `GET /api/v1/coverage/summary` |
| `get_component_coverage(ref?)` | What's the coverage per monorepo component? | `GET /api/v1/coverage/components` |
| `list_file_coverage(ref?, path_prefix?, component?, sort?, limit?, cursor?)` | Which files have the worst coverage? | `GET /api/v1/coverage/files` |
| `get_file_coverage(path, ref?)` | What lines are uncovered in this specific file? | `GET /api/v1/coverage/file` |
| `get_patch_coverage(number)` | What's the patch coverage for this pull request, and which added lines are uncovered? | `GET /api/v1/coverage/pull-requests/{number}` |
| `list_coverage_gaps(ref?, component?, min_risk_level?, status?, limit?, cursor?)` | Which coverage gaps matter most right now? | `GET /api/v1/coverage/gaps` |
| `get_gate_status(ref?)` | Which gates passed or failed, and by how much? | `GET /api/v1/coverage/gates` |
| `get_gap_manifest(ref?, pull_request?, next_action?, min_risk_level?, limit?)` | What's a ranked, ready-to-act-on list of coverage gaps, with everything needed to write a test? | `GET /api/v1/coverage/gap-manifest` |

### Example: uncovered lines in a file just edited

```
> get_file_coverage(path="services/payments/src/Retry.java")
{
  "path": "services/payments/src/Retry.java",
  "leaf_component_key": "payments-api",
  "owners": ["team-payments"],
  "metrics": {"line": {"covered": 41, "total": 58}, "...": "..."},
  "uncovered_ranges": [{"start": 12, "end": 14}, {"start": 87, "end": 87}]
}
```

An unknown path returns a `file_not_found` error with `did_you_mean`
suggestions for same-basename files in the resolved report.

## Development

```bash
uv venv .venv
uv pip install --python .venv/bin/python -e . --group dev
.venv/bin/python -m pytest -q --cov=vericov_mcp --cov-report=term-missing --cov-fail-under=80
```

## What Can Break If You Are Careless

- Never add write tools. This package is read-only by design; the upload
  service enforces it independently via the `uploads:read` scope, but the
  client should not even offer the temptation.
- Don't call any LLM or perform any local inference here — interpretation of
  results belongs entirely to the calling agent.
- Keep the loopback-only plain-HTTP check in `config.py`; it is the only
  thing standing between an agent's API key and a cleartext network hop.
