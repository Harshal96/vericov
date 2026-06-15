import json
from pathlib import Path
from types import SimpleNamespace

from typer.testing import CliRunner

from vericov_coverage_upload import __version__
from vericov_coverage_upload.cli.app import app
from vericov_coverage_upload.domain.artifacts import UploadArtifact
from vericov_coverage_upload.domain.upload_request import UploadAuth
from vericov_coverage_upload.domain.upload_response import UploadAccepted, UploadStatus

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


def test_upload_reports_missing_api_key_as_json(tmp_path: Path, monkeypatch) -> None:
    monkeypatch.chdir(tmp_path)

    result = runner.invoke(app, ["upload", "--json"], env={"VERICOV_API_KEY": ""})

    assert result.exit_code == 4
    assert json.loads(result.stdout) == {
        "error": {
            "code": "missing_api_key",
            "message": "VERICOV_API_KEY or --api-key is required.",
        },
        "ok": False,
    }


def test_config_validate_reports_error_as_json(tmp_path: Path, monkeypatch) -> None:
    (tmp_path / ".vericov.yml").write_text("version: 2\n", encoding="utf-8")
    monkeypatch.chdir(tmp_path)

    result = runner.invoke(app, ["config", "validate", "--json"])

    assert result.exit_code == 1
    assert json.loads(result.stdout)["error"]["code"] == "unsupported_version"


def test_config_validate_reports_legacy_filename_error(tmp_path: Path, monkeypatch) -> None:
    (tmp_path / "vericov.yml").write_text("version: 1\n", encoding="utf-8")
    monkeypatch.chdir(tmp_path)

    result = runner.invoke(app, ["config", "validate"])

    assert result.exit_code == 1
    assert "rename" in result.stderr.lower()
    assert ".vericov.yml" in result.stderr


def test_upload_prints_accepted_response(monkeypatch, tmp_path: Path) -> None:
    artifact = UploadArtifact(
        path=tmp_path / "coverage.info",
        name="coverage.info",
        kind="coverage",
        format="lcov",
        content_type="text/plain",
        size_bytes=7,
        sha256="digest",
    )
    plan = SimpleNamespace(
        request=SimpleNamespace(api_url="https://api.vericov.dev"),
        auth=UploadAuth("vc_live_test"),
        artifacts=(artifact,),
    )
    accepted = UploadAccepted(
        upload_id="upload-1",
        status="accepted",
        poll_url="/api/v1/uploads/upload-1",
        repository_id=None,
        commit_sha="abc123",
        analysis_job_id="job-1",
    )
    monkeypatch.setattr(
        "vericov_coverage_upload.cli.commands.upload.load_config",
        lambda cwd, config: SimpleNamespace(
            path=None,
            upload=SimpleNamespace(wait=False),
        ),
    )
    monkeypatch.setattr(
        "vericov_coverage_upload.cli.commands.upload.build_upload_plan",
        lambda *args: plan,
    )
    monkeypatch.setattr(
        "vericov_coverage_upload.cli.commands.upload.submit_upload",
        lambda upload_plan, gateway: accepted,
    )

    result = runner.invoke(app, ["upload"])

    assert result.exit_code == 0
    assert "Vericov upload accepted" in result.stdout
    assert "upload_id: upload-1" in result.stdout
    assert "artifacts: 1 files, 7 bytes" in result.stdout


def test_upload_wait_prints_completed_json(monkeypatch) -> None:
    plan = SimpleNamespace(
        request=SimpleNamespace(api_url="https://api.vericov.dev"),
        auth=UploadAuth("vc_live_test"),
        artifacts=(),
    )
    accepted = UploadAccepted(
        upload_id="upload-1",
        status="accepted",
        poll_url="/api/v1/uploads/upload-1",
        repository_id=None,
        commit_sha="abc123",
        analysis_job_id=None,
    )
    completed = UploadStatus("upload-1", "completed", "job-1")
    config = SimpleNamespace(wait=True, timeout_seconds=300)
    monkeypatch.setattr(
        "vericov_coverage_upload.cli.commands.upload.load_config",
        lambda cwd, path: SimpleNamespace(path=None, upload=config),
    )
    monkeypatch.setattr(
        "vericov_coverage_upload.cli.commands.upload.build_upload_plan",
        lambda *args: plan,
    )
    monkeypatch.setattr(
        "vericov_coverage_upload.cli.commands.upload.submit_upload",
        lambda upload_plan, gateway: accepted,
    )
    monkeypatch.setattr(
        "vericov_coverage_upload.cli.commands.upload.wait_for_upload",
        lambda *args: completed,
    )

    result = runner.invoke(app, ["upload", "--json"])

    assert result.exit_code == 0
    assert json.loads(result.stdout) == {
        "ok": True,
        "status": "completed",
        "upload_id": "upload-1",
    }


def test_upload_reports_unexpected_error(monkeypatch) -> None:
    monkeypatch.setattr(
        "vericov_coverage_upload.cli.commands.upload.load_config",
        lambda cwd, config: (_ for _ in ()).throw(RuntimeError("boom")),
    )

    result = runner.invoke(app, ["upload"])

    assert result.exit_code == 9
    assert "Error [unexpected_error]: boom" in result.stderr
