from pathlib import Path

from typer.testing import CliRunner

from vericov_coverage_upload import __version__
from vericov_coverage_upload.cli.app import app

runner = CliRunner()


def test_version() -> None:
    result = runner.invoke(app, ["--version"])

    assert result.exit_code == 0
    assert __version__ in result.stdout


def test_config_validate_without_config(tmp_path: Path, monkeypatch) -> None:
    monkeypatch.chdir(tmp_path)

    result = runner.invoke(app, ["config", "validate"])

    assert result.exit_code == 0
    assert "Config valid" in result.stdout


def test_upload_dry_run(tmp_path: Path, monkeypatch) -> None:
    report = tmp_path / "coverage" / "lcov.info"
    report.parent.mkdir()
    report.write_text("TN:\nSF:a.py\nDA:1,1\nend_of_record\n", encoding="utf-8")
    monkeypatch.chdir(tmp_path)

    result = runner.invoke(
        app,
        [
            "upload",
            "--coverage",
            "coverage/lcov.info",
            "--commit-sha",
            "abc123",
            "--branch",
            "main",
            "--dry-run",
        ],
        env={"VERICOV_API_KEY": "vc_live_test"},
    )

    assert result.exit_code == 0
    assert "Vericov upload dry run" in result.stdout
    assert "coverage__lcov.info" in result.stdout
