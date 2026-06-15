"""Reusable common Typer options."""

from typing import Optional

import typer


def config_option(value: Optional[str] = typer.Option(None, "--config", help="Path to .vericov.yml.")) -> Optional[str]:
    return value


def json_option(value: bool = typer.Option(False, "--json", help="Emit machine-readable JSON.")) -> bool:
    return value
