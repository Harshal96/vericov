import io
import json
from pathlib import Path
from urllib.error import HTTPError

from typer.testing import CliRunner

from vericov_coverage_upload.cli.app import app
from vericov_coverage_upload.domain.upload_request import UploadAuth
from vericov_coverage_upload.infrastructure.http.coverage_query_gateway import CoverageQueryGateway
from vericov_coverage_upload.presentation import gaps as gaps_output

runner = CliRunner()


class FakeResponse:
    def __init__(self, payload: dict) -> None:
        self.payload = payload

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, traceback) -> None:
        return None

    def read(self) -> bytes:
        return json.dumps(self.payload).encode("utf-8")


def sample_manifest(entries=None, truncated=False, gate_status="failed") -> dict:
    return {
        "manifest_version": 1,
        "generated_at": "2026-07-03T18:02:11Z",
        "repository": {"full_name": "acme/api", "default_branch": "main"},
        "report": {
            "report_id": "r1",
            "upload_id": "u1",
            "commit_sha": "abc123",
            "branch": "main",
            "pull_request_number": None,
            "gate_status": gate_status,
            "config_sha256": "sha",
        },
        "patch": None,
        "failed_gates": [
            {
                "gate_name": "patch-coverage",
                "gate_type": "patch_coverage",
                "metric": "line",
                "scope_type": "repository",
                "scope_key": None,
                "threshold": 80.0,
                "actual": 55.7,
                "blocking": True,
            }
        ]
        if gate_status == "failed"
        else [],
        "entries": entries if entries is not None else [],
        "truncated": truncated,
    }


def sample_entry(rank=1, path="src/Retry.java", risk_level="high", score=78.0) -> dict:
    return {
        "finding_id": "f1",
        "rank": rank,
        "file_path": path,
        "target_type": "range",
        "line_start": 84,
        "line_end": 97,
        "symbol_name": None,
        "in_patch": True,
        "reason_code": "new_uncovered_changed_line",
        "explanation": "explanation",
        "confidence": "high",
        "risk": {"score": score, "level": risk_level, "factors": ["change_exposure: reason (+25)"]},
        "component_key": "payments-api",
        "owners": ["team-payments"],
        "next_action": "add_test",
        "uncovered_ranges": [{"start": 84, "end": 91}, {"start": 95, "end": 97}],
    }


# --- Gateway tests ---


def test_gateway_sends_authenticated_request_with_all_filters(monkeypatch) -> None:
    captured = {}

    def fake_urlopen(request, timeout):
        captured["request"] = request
        return FakeResponse({"data": sample_manifest()})

    monkeypatch.setattr("vericov_coverage_upload.infrastructure.http.coverage_query_gateway.urlopen", fake_urlopen)
    gateway = CoverageQueryGateway()

    manifest = gateway.get_gap_manifest(
        "http://localhost:8080",
        UploadAuth("vc_repo_test"),
        ref="main",
        pull_request=481,
        next_action="add_test",
        min_risk_level="medium",
        limit=50,
    )

    assert manifest["manifest_version"] == 1
    request = captured["request"]
    assert "api/v1/coverage/gap-manifest" in request.full_url
    assert "pull_request=481" in request.full_url
    assert request.get_header("Authorization") == "Bearer vc_repo_test"


def test_gateway_omits_none_filters(monkeypatch) -> None:
    captured = {}

    def fake_urlopen(request, timeout):
        captured["request"] = request
        return FakeResponse({"data": sample_manifest()})

    monkeypatch.setattr("vericov_coverage_upload.infrastructure.http.coverage_query_gateway.urlopen", fake_urlopen)
    gateway = CoverageQueryGateway()

    gateway.get_gap_manifest("http://localhost:8080", UploadAuth("vc_repo_test"))

    assert "?" not in captured["request"].full_url


def test_gateway_maps_404_to_cli_error(monkeypatch) -> None:
    def fake_urlopen(request, timeout):
        raise HTTPError(
            "http://localhost:8080/api/v1/coverage/gap-manifest",
            404,
            "error",
            {},
            io.BytesIO(json.dumps({"error": {"code": "ref_not_found", "message": "No report"}}).encode("utf-8")),
        )

    monkeypatch.setattr("vericov_coverage_upload.infrastructure.http.coverage_query_gateway.urlopen", fake_urlopen)
    gateway = CoverageQueryGateway()

    import pytest
    from vericov_coverage_upload.domain.errors import VericovCliError

    with pytest.raises(VericovCliError) as excinfo:
        gateway.get_gap_manifest("http://localhost:8080", UploadAuth("vc_repo_test"))

    assert excinfo.value.code == "ref_not_found"


# --- Rendering tests ---


def test_manifest_json_is_raw_passthrough() -> None:
    manifest = sample_manifest(entries=[sample_entry()])

    rendered = gaps_output.manifest_json(manifest)

    assert json.loads(rendered) == manifest


def test_manifest_text_includes_gate_summary_and_table() -> None:
    manifest = sample_manifest(entries=[sample_entry()])

    rendered = gaps_output.manifest_text(manifest)

    assert "gate_status=failed" in rendered
    assert "patch-coverage" in rendered
    assert "src/Retry.java" in rendered
    assert "team-payments" in rendered


def test_manifest_text_reports_nothing_to_do_when_empty() -> None:
    manifest = sample_manifest(entries=[], gate_status="passed")

    rendered = gaps_output.manifest_text(manifest)

    assert "No coverage gaps match the current filters." in rendered


def test_manifest_text_notes_truncation() -> None:
    manifest = sample_manifest(entries=[sample_entry()], truncated=True)

    rendered = gaps_output.manifest_text(manifest)

    assert "Showing 1 of more entries" in rendered


# --- CLI command tests ---


def test_gaps_command_exits_zero_on_empty_manifest(monkeypatch) -> None:
    monkeypatch.setattr(
        "vericov_coverage_upload.cli.commands.gaps.CoverageQueryGateway.get_gap_manifest",
        lambda self, *args, **kwargs: sample_manifest(entries=[], gate_status="passed"),
    )

    result = runner.invoke(app, ["gaps"], env={"VERICOV_API_KEY": "vc_repo_test", "VERICOV_API_URL": "http://localhost:8080"})

    assert result.exit_code == 0
    assert "No coverage gaps" in result.stdout


def test_gaps_command_exits_zero_with_entries_when_fail_on_entries_not_set(monkeypatch) -> None:
    monkeypatch.setattr(
        "vericov_coverage_upload.cli.commands.gaps.CoverageQueryGateway.get_gap_manifest",
        lambda self, *args, **kwargs: sample_manifest(entries=[sample_entry()]),
    )

    result = runner.invoke(app, ["gaps"], env={"VERICOV_API_KEY": "vc_repo_test", "VERICOV_API_URL": "http://localhost:8080"})

    assert result.exit_code == 0


def test_gaps_command_exits_one_with_entries_when_fail_on_entries_set(monkeypatch) -> None:
    monkeypatch.setattr(
        "vericov_coverage_upload.cli.commands.gaps.CoverageQueryGateway.get_gap_manifest",
        lambda self, *args, **kwargs: sample_manifest(entries=[sample_entry()]),
    )

    result = runner.invoke(
        app,
        ["gaps", "--fail-on-entries"],
        env={"VERICOV_API_KEY": "vc_repo_test", "VERICOV_API_URL": "http://localhost:8080"},
    )

    assert result.exit_code == 1


def test_gaps_command_exits_two_on_missing_api_key(monkeypatch) -> None:
    result = runner.invoke(app, ["gaps"], env={"VERICOV_API_KEY": "", "VERICOV_API_URL": "http://localhost:8080"})

    assert result.exit_code == 2


def test_gaps_command_exits_two_on_transport_error(monkeypatch) -> None:
    def raise_error(self, *args, **kwargs):
        from vericov_coverage_upload.domain.errors import VericovCliError

        raise VericovCliError("ref_not_found", "No report for ref")

    monkeypatch.setattr(
        "vericov_coverage_upload.cli.commands.gaps.CoverageQueryGateway.get_gap_manifest", raise_error
    )

    result = runner.invoke(app, ["gaps"], env={"VERICOV_API_KEY": "vc_repo_test", "VERICOV_API_URL": "http://localhost:8080"})

    assert result.exit_code == 2
    assert "ref_not_found" in result.output


def test_gaps_command_json_output_is_raw_manifest(monkeypatch) -> None:
    manifest = sample_manifest(entries=[sample_entry()])
    monkeypatch.setattr(
        "vericov_coverage_upload.cli.commands.gaps.CoverageQueryGateway.get_gap_manifest",
        lambda self, *args, **kwargs: manifest,
    )

    result = runner.invoke(
        app, ["gaps", "--json"], env={"VERICOV_API_KEY": "vc_repo_test", "VERICOV_API_URL": "http://localhost:8080"}
    )

    assert result.exit_code == 0
    assert json.loads(result.stdout) == manifest


def test_gaps_command_passes_ref_and_pull_request_filters(monkeypatch, tmp_path) -> None:
    captured = {}

    def fake_get_gap_manifest(self, api_url, auth, **kwargs):
        captured.update(kwargs)
        captured["api_url"] = api_url
        return sample_manifest()

    monkeypatch.setattr(
        "vericov_coverage_upload.cli.commands.gaps.CoverageQueryGateway.get_gap_manifest", fake_get_gap_manifest
    )
    monkeypatch.chdir(tmp_path)

    result = runner.invoke(
        app,
        ["gaps", "--pull-request", "481", "--min-risk-level", "medium", "--limit", "25"],
        env={"VERICOV_API_KEY": "vc_repo_test", "VERICOV_API_URL": "http://localhost:8080"},
    )

    assert result.exit_code == 0
    assert captured["pull_request"] == 481
    assert captured["min_risk_level"] == "medium"
    assert captured["limit"] == 25
    assert captured["next_action"] == "add_test"
