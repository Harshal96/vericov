import io
import json
from urllib.error import HTTPError, URLError

import pytest

from vericov_mcp.client import CoverageApiClient, CoverageApiError
from vericov_mcp.config import ServerConfig


class FakeResponse:
    def __init__(self, payload: dict) -> None:
        self.payload = payload

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, traceback) -> None:
        return None

    def read(self) -> bytes:
        return json.dumps(self.payload).encode("utf-8")


def config() -> ServerConfig:
    return ServerConfig(api_url="http://localhost:8080", api_key="vc_repo_test")


def http_error(status: int, body: dict) -> HTTPError:
    return HTTPError(
        "http://localhost:8080/api/v1/coverage/summary",
        status,
        "error",
        {},
        io.BytesIO(json.dumps(body).encode("utf-8")),
    )


def test_get_summary_sends_authenticated_request_with_ref(monkeypatch) -> None:
    captured = {}

    def fake_urlopen(request, timeout):
        captured["request"] = request
        return FakeResponse({"data": {"gate_status": "passed"}})

    monkeypatch.setattr("vericov_mcp.client.urlopen", fake_urlopen)
    client = CoverageApiClient(config())

    result = client.get_summary(ref="main")

    assert result == {"gate_status": "passed"}
    request = captured["request"]
    assert request.full_url == "http://localhost:8080/api/v1/coverage/summary?ref=main"
    assert request.get_header("Authorization") == "Bearer vc_repo_test"
    assert request.get_header("User-agent").startswith("vericov-mcp/")


def test_get_file_omits_none_query_parameters(monkeypatch) -> None:
    captured = {}

    def fake_urlopen(request, timeout):
        captured["request"] = request
        return FakeResponse({"data": {}})

    monkeypatch.setattr("vericov_mcp.client.urlopen", fake_urlopen)
    client = CoverageApiClient(config())

    client.get_file(path="src/Main.java")

    assert "ref" not in captured["request"].full_url


def test_get_pull_request_patch_builds_path_parameter(monkeypatch) -> None:
    captured = {}

    def fake_urlopen(request, timeout):
        captured["request"] = request
        return FakeResponse({"data": {"status": "complete"}})

    monkeypatch.setattr("vericov_mcp.client.urlopen", fake_urlopen)
    client = CoverageApiClient(config())

    result = client.get_pull_request_patch(481)

    assert result == {"status": "complete"}
    assert captured["request"].full_url.endswith("/api/v1/coverage/pull-requests/481")


def test_http_error_maps_to_coverage_api_error(monkeypatch) -> None:
    def fake_urlopen(request, timeout):
        raise http_error(404, {"error": {"code": "ref_not_found", "message": "No report for ref"}})

    monkeypatch.setattr("vericov_mcp.client.urlopen", fake_urlopen)
    client = CoverageApiClient(config())

    with pytest.raises(CoverageApiError) as excinfo:
        client.get_summary(ref="missing")

    assert excinfo.value.code == "ref_not_found"
    assert excinfo.value.status == 404


def test_http_error_with_did_you_mean_details_appends_suggestions(monkeypatch) -> None:
    def fake_urlopen(request, timeout):
        raise http_error(
            404,
            {
                "error": {
                    "code": "file_not_found",
                    "message": "No file found",
                    "details": [{"field": "path", "code": "did_you_mean", "message": "src/Main.java"}],
                }
            },
        )

    monkeypatch.setattr("vericov_mcp.client.urlopen", fake_urlopen)
    client = CoverageApiClient(config())

    with pytest.raises(CoverageApiError) as excinfo:
        client.get_file(path="Main.java")

    assert "src/Main.java" in excinfo.value.message


def test_malformed_error_body_falls_back_to_status_code(monkeypatch) -> None:
    def fake_urlopen(request, timeout):
        raise HTTPError("http://localhost:8080/x", 500, "error", {}, io.BytesIO(b"not json"))

    monkeypatch.setattr("vericov_mcp.client.urlopen", fake_urlopen)
    client = CoverageApiClient(config())

    with pytest.raises(CoverageApiError) as excinfo:
        client.get_summary()

    assert excinfo.value.code == "http_500"


def test_connection_failure_raises_coverage_api_error(monkeypatch) -> None:
    def fake_urlopen(request, timeout):
        raise URLError("Connection refused")

    monkeypatch.setattr("vericov_mcp.client.urlopen", fake_urlopen)
    client = CoverageApiClient(config())

    with pytest.raises(CoverageApiError) as excinfo:
        client.get_summary()

    assert excinfo.value.code == "connection_failed"


def test_get_gap_manifest_passes_all_filters(monkeypatch) -> None:
    captured = {}

    def fake_urlopen(request, timeout):
        captured["url"] = request.full_url
        return FakeResponse({"data": {"manifest_version": 1}})

    monkeypatch.setattr("vericov_mcp.client.urlopen", fake_urlopen)
    client = CoverageApiClient(config())

    result = client.get_gap_manifest(pull_request=481, next_action="add_test", min_risk_level="medium", limit=25)

    assert result == {"manifest_version": 1}
    assert "pull_request=481" in captured["url"]
    assert "min_risk_level=medium" in captured["url"]


def test_list_files_and_gaps_pass_through_all_filters(monkeypatch) -> None:
    captured = {}

    def fake_urlopen(request, timeout):
        captured["url"] = request.full_url
        return FakeResponse({"data": {}})

    monkeypatch.setattr("vericov_mcp.client.urlopen", fake_urlopen)
    client = CoverageApiClient(config())

    client.list_files(ref="main", path_prefix="src/", component="api", sort="path", limit=50, cursor="abc")
    assert "path_prefix=src%2F" in captured["url"]
    assert "component=api" in captured["url"]

    client.list_gaps(ref="main", component="api", min_risk_level="high", status="active", limit=10, cursor="xyz")
    assert "min_risk_level=high" in captured["url"]
