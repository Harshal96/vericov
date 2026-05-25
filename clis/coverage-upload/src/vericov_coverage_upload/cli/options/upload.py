"""Reusable upload Typer option defaults."""

from typing import List, Optional

import typer


CoverageOption = typer.Option(None, "--coverage", help="Coverage report path or glob. Repeatable.")
TestResultsOption = typer.Option(None, "--test-results", help="Test result path or glob. Repeatable.")
FlagOption = typer.Option(None, "--flag", help="Upload flag. Repeatable.")
ApiKeyOption = typer.Option(None, "--api-key", envvar="VERICOV_API_KEY", help="Vericov repo API key.")
ApiUrlOption = typer.Option(None, "--api-url", envvar="VERICOV_API_URL", help="Vericov API base URL.")
RepositoryIdOption = typer.Option(None, "--repository-id", envvar="VERICOV_REPOSITORY_ID", help="Repository UUID.")
