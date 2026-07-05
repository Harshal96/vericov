import json
from unittest.mock import MagicMock

import pytest

from vericov_mcp import tools
from vericov_mcp.client import CoverageApiError


@pytest.fixture
def client() -> MagicMock:
    return MagicMock()


def test_get_coverage_summary_calls_client_and_renders_result(client: MagicMock) -> None:
    client.get_summary.return_value = {"gate_status": "passed"}

    result = tools.get_coverage_summary(client, ref="main")

    client.get_summary.assert_called_once_with("main")
    assert json.loads(result) == {"gate_status": "passed"}


def test_get_component_coverage_delegates_to_client(client: MagicMock) -> None:
    client.get_components.return_value = {"components": []}

    tools.get_component_coverage(client, ref="main")

    client.get_components.assert_called_once_with("main")


def test_list_file_coverage_passes_all_filters(client: MagicMock) -> None:
    client.list_files.return_value = {"files": []}

    tools.list_file_coverage(client, ref="main", path_prefix="src/", component="api", sort="path", limit=10, cursor="c")

    client.list_files.assert_called_once_with("main", "src/", "api", "path", 10, "c")


def test_get_file_coverage_delegates_with_path(client: MagicMock) -> None:
    client.get_file.return_value = {"path": "src/Main.java", "uncovered_ranges": []}

    tools.get_file_coverage(client, path="src/Main.java", ref="main")

    client.get_file.assert_called_once_with("src/Main.java", "main")


def test_get_patch_coverage_delegates_with_number(client: MagicMock) -> None:
    client.get_pull_request_patch.return_value = {"status": "complete"}

    tools.get_patch_coverage(client, number=481)

    client.get_pull_request_patch.assert_called_once_with(481)


def test_list_coverage_gaps_passes_all_filters(client: MagicMock) -> None:
    client.list_gaps.return_value = {"gaps": []}

    tools.list_coverage_gaps(
        client, ref="main", component="api", min_risk_level="high", status="active", limit=5, cursor="c"
    )

    client.list_gaps.assert_called_once_with("main", "api", "high", "active", 5, "c")


def test_get_gate_status_delegates_to_client(client: MagicMock) -> None:
    client.get_gates.return_value = {"gates": []}

    tools.get_gate_status(client, ref="main")

    client.get_gates.assert_called_once_with("main")


def test_get_gap_manifest_passes_all_filters(client: MagicMock) -> None:
    client.get_gap_manifest.return_value = {"entries": []}

    tools.get_gap_manifest(client, ref="main", pull_request=481, next_action="add_test", min_risk_level="high", limit=10)

    client.get_gap_manifest.assert_called_once_with("main", 481, "add_test", "high", 10)


def test_api_errors_render_as_structured_error_payload_instead_of_raising(client: MagicMock) -> None:
    client.get_summary.side_effect = CoverageApiError("ref_not_found", "No report for ref", 404)

    result = tools.get_coverage_summary(client, ref="missing")

    parsed = json.loads(result)
    assert parsed["ok"] is False
    assert parsed["error"]["code"] == "ref_not_found"


def test_file_not_found_error_renders_without_raising(client: MagicMock) -> None:
    client.get_file.side_effect = CoverageApiError("file_not_found", "No file found (did you mean: src/Main.java)")

    result = tools.get_file_coverage(client, path="Main.java")

    parsed = json.loads(result)
    assert parsed["ok"] is False
    assert "did you mean" in parsed["error"]["message"]
