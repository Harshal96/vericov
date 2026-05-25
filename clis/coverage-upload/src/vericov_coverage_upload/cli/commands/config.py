"""Config command group."""

from pathlib import Path
from typing import Optional

import typer

from vericov_coverage_upload.application.config_validation import validate_config
from vericov_coverage_upload.cli.context import handle_error, handle_unexpected
from vericov_coverage_upload.domain.errors import VericovCliError
from vericov_coverage_upload.presentation import json as json_output


config_app = typer.Typer(help="Validate and inspect Vericov config.")


def register(root: typer.Typer) -> None:
    root.add_typer(config_app, name="config")


@config_app.command("validate")
def validate(
    config: Optional[str] = typer.Option(None, "--config", help="Path to vericov.yml."),
    as_json: bool = typer.Option(False, "--json", help="Emit machine-readable JSON."),
) -> None:
    try:
        resolved = validate_config(Path.cwd(), config)
    except VericovCliError as error:
        handle_error(error, as_json)
    except Exception as error:
        handle_unexpected(error, as_json)

    if as_json:
        typer.echo(json_output.dump({"ok": True, "config": resolved.path}))
    else:
        typer.echo(f"Config valid: {resolved.path or 'no config file found'}")
