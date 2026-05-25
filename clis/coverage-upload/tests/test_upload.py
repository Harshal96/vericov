from pathlib import Path

from vericov_coverage_upload.application.upload_workflow import UploadOverrides, build_upload_plan
from vericov_coverage_upload.domain.config import UploadConfig
from vericov_coverage_upload.domain.upload_request import UploadRequest


def test_upload_request_omits_repository_id_when_absent(tmp_path: Path, monkeypatch) -> None:
    report = tmp_path / "coverage" / "lcov.info"
    report.parent.mkdir()
    report.write_text("TN:\nSF:a.py\nDA:1,1\nend_of_record\n", encoding="utf-8")
    monkeypatch.setattr("vericov_coverage_upload.application.upload_workflow.git_root", lambda cwd: tmp_path)
    monkeypatch.setattr("vericov_coverage_upload.application.upload_workflow.resolve_git_metadata", lambda cwd: None)

    plan = build_upload_plan(
        tmp_path,
        None,
        UploadConfig(),
        UploadOverrides(coverage=("coverage/lcov.info",), commit_sha="abc123", branch="main"),
        {"VERICOV_API_KEY": "vc_live_test"},
    )

    payload = plan.request.to_json()
    assert "repository_id" not in payload
    assert payload["commit_sha"] == "abc123"


def test_idempotency_key_changes_with_artifact_bytes(tmp_path: Path, monkeypatch) -> None:
    report = tmp_path / "coverage" / "lcov.info"
    report.parent.mkdir()
    report.write_text("TN:\nSF:a.py\nDA:1,1\nend_of_record\n", encoding="utf-8")
    monkeypatch.setattr("vericov_coverage_upload.application.upload_workflow.git_root", lambda cwd: tmp_path)
    monkeypatch.setattr("vericov_coverage_upload.application.upload_workflow.resolve_git_metadata", lambda cwd: None)

    first = build_upload_plan(
        tmp_path,
        None,
        UploadConfig(),
        UploadOverrides(coverage=("coverage/lcov.info",), commit_sha="abc123", branch="main"),
        {"VERICOV_API_KEY": "vc_live_test"},
    ).request.resolved_idempotency_key()

    report.write_text("TN:\nSF:a.py\nDA:1,0\nend_of_record\n", encoding="utf-8")
    second = build_upload_plan(
        tmp_path,
        None,
        UploadConfig(),
        UploadOverrides(coverage=("coverage/lcov.info",), commit_sha="abc123", branch="main"),
        {"VERICOV_API_KEY": "vc_live_test"},
    ).request.resolved_idempotency_key()

    assert first != second
