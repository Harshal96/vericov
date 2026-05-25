"""Shared CLI error handling."""

import sys

import typer

from vericov_coverage_upload.domain.errors import ExitCode, VericovCliError
from vericov_coverage_upload.presentation import json as json_output


def handle_error(error: VericovCliError, as_json: bool = False) -> None:
    if as_json:
        typer.echo(json_output.error_payload(error))
    else:
        typer.echo(f"Error [{error.code}]: {error.message}", err=True)
    raise typer.Exit(error.exit_code)


def handle_unexpected(error: Exception, as_json: bool = False) -> None:
    cli_error = VericovCliError("unexpected_error", str(error), ExitCode.UNEXPECTED)
    handle_error(cli_error, as_json)
