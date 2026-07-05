"""`vericov gaps`: fetch and render the coverage gap manifest.

Exit code contract (distinct from the shared CLI exit-code scheme used by
`upload`, per the gap-manifest design): 0 on an empty manifest (or any
manifest when --fail-on-entries is not set), 1 when --fail-on-entries is set
and entries remain after filtering, 2 on any transport or validation error.
"""

import os
from pathlib import Path
from typing import Optional

import typer

from vericov_coverage_upload.domain.errors import VericovCliError
from vericov_coverage_upload.domain.upload_request import UploadAuth
from vericov_coverage_upload.infrastructure.config_loader import load_config, merge_environment
from vericov_coverage_upload.infrastructure.http.coverage_query_gateway import CoverageQueryGateway
from vericov_coverage_upload.presentation import gaps as gaps_output

TRANSPORT_ERROR_EXIT_CODE = 2
ENTRIES_REMAIN_EXIT_CODE = 1


def register(root: typer.Typer) -> None:
    root.command("gaps")(gaps)


def gaps(
    config: Optional[str] = typer.Option(None, "--config", help="Path to .vericov.yml."),
    api_key: Optional[str] = typer.Option(None, "--api-key", envvar="VERICOV_API_KEY", help="Vericov repo API key."),
    api_url: Optional[str] = typer.Option(None, "--api-url", envvar="VERICOV_API_URL", help="Vericov API base URL."),
    ref: Optional[str] = typer.Option(None, "--ref", help="Commit SHA or branch. Defaults to the default branch."),
    pull_request: Optional[int] = typer.Option(None, "--pull-request", help="Manifest for a pull request number."),
    min_risk_level: Optional[str] = typer.Option(None, "--min-risk-level", help="Filter entries below this risk level."),
    next_action: str = typer.Option("add_test", "--next-action", help="Filter by next_action."),
    limit: Optional[int] = typer.Option(None, "--limit", help="Maximum entries to return (server default 100)."),
    as_json: bool = typer.Option(False, "--json", help="Emit the raw manifest document."),
    fail_on_entries: bool = typer.Option(
        False, "--fail-on-entries", help="Exit 1 when any entry remains after filters."
    ),
) -> None:
    try:
        manifest = _fetch_manifest(
            config, api_key, api_url, ref, pull_request, next_action, min_risk_level, limit
        )
    except VericovCliError as error:
        typer.echo(f"Error [{error.code}]: {error.message}", err=True)
        raise typer.Exit(TRANSPORT_ERROR_EXIT_CODE) from None
    except Exception as error:  # noqa: BLE001 - surfaced with the command's exit-code contract
        typer.echo(f"Error [unexpected_error]: {error}", err=True)
        raise typer.Exit(TRANSPORT_ERROR_EXIT_CODE) from None

    typer.echo(gaps_output.manifest_json(manifest) if as_json else gaps_output.manifest_text(manifest))

    entries = manifest.get("entries") or []
    if fail_on_entries and entries:
        raise typer.Exit(ENTRIES_REMAIN_EXIT_CODE)
    raise typer.Exit(0)


def _fetch_manifest(
    config: Optional[str],
    api_key: Optional[str],
    api_url: Optional[str],
    ref: Optional[str],
    pull_request: Optional[int],
    next_action: str,
    min_risk_level: Optional[str],
    limit: Optional[int],
) -> dict:
    resolved = load_config(Path.cwd(), config)
    merged = merge_environment(resolved.upload, os.environ)
    resolved_api_url = api_url or merged.api_url
    resolved_api_key = api_key or os.environ.get("VERICOV_API_KEY")
    if not resolved_api_key:
        raise VericovCliError("missing_api_key", "VERICOV_API_KEY or --api-key is required.")

    gateway = CoverageQueryGateway()
    return gateway.get_gap_manifest(
        resolved_api_url,
        UploadAuth(resolved_api_key),
        ref=ref,
        pull_request=pull_request,
        next_action=next_action,
        min_risk_level=min_risk_level,
        limit=limit,
    )
