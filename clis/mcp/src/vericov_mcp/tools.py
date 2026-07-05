"""Tool implementations: one function per MCP tool, independent of the MCP SDK.

Each function calls exactly one query-API endpoint through `CoverageApiClient`
and renders the result as truncation-aware JSON. Errors from the API (ref not
found, scope missing, and so on) are rendered as a structured error payload
rather than raised, so a misconfigured query surfaces as a clear tool result
instead of crashing the agent's turn.
"""

import json
from typing import Any, Dict, Optional

from vericov_mcp.client import CoverageApiClient, CoverageApiError
from vericov_mcp.rendering import render


def _call(client: CoverageApiClient, narrow_hint: str, operation) -> str:
    try:
        data = operation()
    except CoverageApiError as error:
        return json.dumps(
            {"ok": False, "error": {"code": error.code, "message": error.message}},
            sort_keys=True,
            separators=(",", ":"),
        )
    return render(data, narrow_hint)


def get_coverage_summary(client: CoverageApiClient, ref: Optional[str] = None) -> str:
    """Repository-wide coverage totals and gate status for a ref (commit SHA, branch, or the default branch)."""
    return _call(client, "Narrow by specifying a more specific ref.", lambda: client.get_summary(ref))


def get_component_coverage(client: CoverageApiClient, ref: Optional[str] = None) -> str:
    """The nested monorepo component coverage tree for a ref."""
    return _call(client, "Narrow by specifying a more specific ref.", lambda: client.get_components(ref))


def list_file_coverage(
    client: CoverageApiClient,
    ref: Optional[str] = None,
    path_prefix: Optional[str] = None,
    component: Optional[str] = None,
    sort: Optional[str] = None,
    limit: Optional[int] = None,
    cursor: Optional[str] = None,
) -> str:
    """Paged per-file coverage for a ref; worst-covered files first by default. Paths are repository-relative."""
    return _call(
        client,
        "Narrow with path_prefix, component, or a smaller limit.",
        lambda: client.list_files(ref, path_prefix, component, sort, limit, cursor),
    )


def get_file_coverage(client: CoverageApiClient, path: str, ref: Optional[str] = None) -> str:
    """One file's coverage summary plus its uncovered executable line ranges for a ref. path is repository-relative."""
    return _call(client, "Narrow to a single, more specific file path.", lambda: client.get_file(path, ref))


def get_patch_coverage(client: CoverageApiClient, number: int) -> str:
    """Patch coverage for a pull request's latest diff-bearing report: patch totals and uncovered added lines."""
    return _call(
        client,
        "Confirm the pull request number and that its upload included a diff artifact.",
        lambda: client.get_pull_request_patch(number),
    )


def list_coverage_gaps(
    client: CoverageApiClient,
    ref: Optional[str] = None,
    component: Optional[str] = None,
    min_risk_level: Optional[str] = None,
    status: Optional[str] = None,
    limit: Optional[int] = None,
    cursor: Optional[str] = None,
) -> str:
    """Paged coverage gap findings for a ref, ranked by risk; defaults to active gaps only."""
    return _call(
        client,
        "Narrow with component, min_risk_level, or a smaller limit.",
        lambda: client.list_gaps(ref, component, min_risk_level, status, limit, cursor),
    )


def get_gate_status(client: CoverageApiClient, ref: Optional[str] = None) -> str:
    """All gate evaluations (repository, component, and patch) for a ref."""
    return _call(client, "Narrow by specifying a more specific ref.", lambda: client.get_gates(ref))


def get_gap_manifest(
    client: CoverageApiClient,
    ref: Optional[str] = None,
    pull_request: Optional[int] = None,
    next_action: Optional[str] = None,
    min_risk_level: Optional[str] = None,
    limit: Optional[int] = None,
) -> str:
    """A ranked, deterministic manifest of actionable coverage gaps for a ref or pull request: files, uncovered
    line ranges, risk, owners, and next_action — everything needed to write a missing test without a second query."""
    return _call(
        client,
        "Narrow with next_action, min_risk_level, or a smaller limit.",
        lambda: client.get_gap_manifest(ref, pull_request, next_action, min_risk_level, limit),
    )
