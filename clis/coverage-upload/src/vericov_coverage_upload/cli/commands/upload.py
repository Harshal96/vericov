"""Upload command."""

from pathlib import Path
from typing import List, Optional

import typer

from vericov_coverage_upload.application.upload_workflow import UploadOverrides, build_upload_plan, submit_upload
from vericov_coverage_upload.application.wait_for_upload import wait_for_upload
from vericov_coverage_upload.cli.context import handle_error, handle_unexpected
from vericov_coverage_upload.domain.errors import VericovCliError
from vericov_coverage_upload.infrastructure.config_loader import load_config, parse_duration, parse_size
from vericov_coverage_upload.infrastructure.http.direct_url_upload_gateway import DirectUrlUploadGateway
from vericov_coverage_upload.presentation import human, json as json_output


def register(root: typer.Typer) -> None:
    root.command("upload")(upload)


def upload(
    config: Optional[str] = typer.Option(None, "--config", help="Path to .vericov.yml."),
    api_key: Optional[str] = typer.Option(None, "--api-key", envvar="VERICOV_API_KEY", help="Vericov repo API key."),
    api_url: Optional[str] = typer.Option(None, "--api-url", envvar="VERICOV_API_URL", help="Vericov API base URL."),
    repository_id: Optional[str] = typer.Option(None, "--repository-id", envvar="VERICOV_REPOSITORY_ID", help="Repository UUID."),
    commit_sha: Optional[str] = typer.Option(None, "--commit-sha", help="Git commit SHA."),
    branch: Optional[str] = typer.Option(None, "--branch", help="Git branch name."),
    pull_request_number: Optional[int] = typer.Option(None, "--pull-request-number", help="Pull request number."),
    coverage: Optional[List[str]] = typer.Option(None, "--coverage", help="Coverage report path or glob. Repeatable."),
    test_results: Optional[List[str]] = typer.Option(None, "--test-results", help="Test result path or glob. Repeatable."),
    flag: Optional[List[str]] = typer.Option(None, "--flag", help="Upload flag. Repeatable."),
    component: Optional[str] = typer.Option(None, "--component", help="Logical component name."),
    package: Optional[str] = typer.Option(None, "--package", help="Package path or name."),
    discover: Optional[bool] = typer.Option(None, "--discover/--no-discover", help="Control default artifact discovery."),
    dry_run: bool = typer.Option(False, "--dry-run", help="Print upload plan without sending data."),
    wait: bool = typer.Option(False, "--wait", help="Wait for upload processing to complete."),
    timeout: str = typer.Option("300s", "--timeout", help="Wait timeout, such as 30s, 5m, or 1h."),
    max_artifact_size: Optional[str] = typer.Option(None, "--max-artifact-size", help="Per-artifact size limit."),
    max_total_size: Optional[str] = typer.Option(None, "--max-total-size", help="Total raw artifact size limit."),
    idempotency_key: Optional[str] = typer.Option(None, "--idempotency-key", envvar="VERICOV_IDEMPOTENCY_KEY", help="Override upload idempotency key."),
    as_json: bool = typer.Option(False, "--json", help="Emit machine-readable JSON."),
) -> None:
    try:
        resolved = load_config(Path.cwd(), config)
        overrides = UploadOverrides(
            api_key=api_key,
            api_url=api_url,
            repository_id=repository_id,
            commit_sha=commit_sha,
            branch=branch,
            pull_request_number=pull_request_number,
            coverage=tuple(coverage or ()),
            test_results=tuple(test_results or ()),
            flags=tuple(flag or ()),
            component=component,
            package=package,
            wait=wait,
            timeout_seconds=parse_duration(timeout),
            max_artifact_bytes=parse_size(max_artifact_size) if max_artifact_size else None,
            max_total_bytes=parse_size(max_total_size) if max_total_size else None,
            discover=discover,
            idempotency_key=idempotency_key,
        )
        plan = build_upload_plan(Path.cwd(), resolved.path, resolved.upload, overrides)
        if dry_run:
            if as_json:
                typer.echo(
                    json_output.dry_run_payload(
                        plan.config_path,
                        plan.request.repository_id,
                        plan.request.resolved_idempotency_key(),
                        plan.artifacts,
                    )
                )
            else:
                typer.echo(
                    human.dry_run_text(
                        plan.config_path,
                        plan.request.repository_id,
                        plan.request.resolved_idempotency_key(),
                        plan.artifacts,
                    )
                )
            return

        gateway = DirectUrlUploadGateway()
        accepted = submit_upload(plan, gateway)
        if wait or resolved.upload.wait:
            status = wait_for_upload(gateway, accepted, plan.request.api_url, plan.auth, overrides.timeout_seconds or resolved.upload.timeout_seconds)
            typer.echo(json_output.completed_payload(status) if as_json else human.completed_text(status))
            return
        total = sum(artifact.size_bytes for artifact in plan.artifacts)
        typer.echo(
            json_output.accepted_payload(accepted, plan.artifacts)
            if as_json
            else human.accepted_text(accepted, len(plan.artifacts), total)
        )
    except VericovCliError as error:
        handle_error(error, as_json)
    except Exception as error:
        handle_unexpected(error, as_json)
