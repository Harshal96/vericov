"""Vericov MCP server entrypoint.

Runs over stdio, per the Model Context Protocol convention for locally
launched agent tools. Configuration comes from `VERICOV_API_URL` /
`VERICOV_API_KEY` (or `--api-url` / `--api-key`), matching the upload CLI.
"""

import argparse
import os
import sys
from typing import Optional, Sequence

from mcp.server.fastmcp import FastMCP

from vericov_mcp import __version__, tools
from vericov_mcp.client import CoverageApiClient
from vericov_mcp.config import ConfigurationError, resolve_config


def build_server(client: CoverageApiClient) -> FastMCP:
    mcp = FastMCP(name="vericov")

    @mcp.tool()
    def get_coverage_summary(ref: Optional[str] = None) -> str:
        """Repository-wide coverage totals and gate status for a ref (commit SHA, branch, or the default branch)."""
        return tools.get_coverage_summary(client, ref)

    @mcp.tool()
    def get_component_coverage(ref: Optional[str] = None) -> str:
        """The nested monorepo component coverage tree for a ref."""
        return tools.get_component_coverage(client, ref)

    @mcp.tool()
    def list_file_coverage(
        ref: Optional[str] = None,
        path_prefix: Optional[str] = None,
        component: Optional[str] = None,
        sort: Optional[str] = None,
        limit: Optional[int] = None,
        cursor: Optional[str] = None,
    ) -> str:
        """Paged per-file coverage for a ref; worst-covered files first by default. Paths are repository-relative."""
        return tools.list_file_coverage(client, ref, path_prefix, component, sort, limit, cursor)

    @mcp.tool()
    def get_file_coverage(path: str, ref: Optional[str] = None) -> str:
        """One file's coverage summary plus its uncovered executable line ranges. path is repository-relative."""
        return tools.get_file_coverage(client, path, ref)

    @mcp.tool()
    def get_patch_coverage(number: int) -> str:
        """Patch coverage for a pull request's latest diff-bearing report: patch totals and uncovered added lines."""
        return tools.get_patch_coverage(client, number)

    @mcp.tool()
    def list_coverage_gaps(
        ref: Optional[str] = None,
        component: Optional[str] = None,
        min_risk_level: Optional[str] = None,
        status: Optional[str] = None,
        limit: Optional[int] = None,
        cursor: Optional[str] = None,
    ) -> str:
        """Paged coverage gap findings for a ref, ranked by risk; defaults to active gaps only."""
        return tools.list_coverage_gaps(client, ref, component, min_risk_level, status, limit, cursor)

    @mcp.tool()
    def get_gate_status(ref: Optional[str] = None) -> str:
        """All gate evaluations (repository, component, and patch) for a ref."""
        return tools.get_gate_status(client, ref)

    @mcp.tool()
    def get_gap_manifest(
        ref: Optional[str] = None,
        pull_request: Optional[int] = None,
        next_action: Optional[str] = None,
        min_risk_level: Optional[str] = None,
        limit: Optional[int] = None,
    ) -> str:
        """A ranked, deterministic manifest of actionable coverage gaps for a ref or pull request: files, uncovered
        line ranges, risk, owners, and next_action — everything needed to write a missing test without a second query."""
        return tools.get_gap_manifest(client, ref, pull_request, next_action, min_risk_level, limit)

    return mcp


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(prog="vericov-mcp", description="Vericov coverage query MCP server.")
    parser.add_argument("--api-url", default=None, help="Vericov upload service base URL.")
    parser.add_argument("--api-key", default=None, help="Vericov repository API key (read scope).")
    parser.add_argument("--version", action="store_true", help="Print the version and exit.")
    args = parser.parse_args(argv)

    if args.version:
        print(__version__)
        return 0

    try:
        config = resolve_config(os.environ, args.api_url, args.api_key)
    except ConfigurationError as error:
        print(f"vericov-mcp: {error}", file=sys.stderr)
        return 1

    client = CoverageApiClient(config)
    server = build_server(client)
    server.run(transport="stdio")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
