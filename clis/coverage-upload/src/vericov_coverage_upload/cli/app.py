"""Typer application assembly."""

import typer

from vericov_coverage_upload import __version__
from vericov_coverage_upload.cli.commands import config, gaps, upload


def build_app() -> typer.Typer:
    app = typer.Typer(
        name="vericov",
        no_args_is_help=True,
        invoke_without_command=True,
        help="Vericov developer tools.",
    )

    @app.callback()
    def root(
        version: bool = typer.Option(
            False,
            "--version",
            help="Show the CLI version and exit.",
            is_eager=True,
        ),
    ) -> None:
        if version:
            typer.echo(__version__)
            raise typer.Exit()

    config.register(app)
    upload.register(app)
    gaps.register(app)
    return app


app = build_app()
