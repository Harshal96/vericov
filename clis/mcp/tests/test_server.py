import asyncio

from vericov_mcp.client import CoverageApiClient
from vericov_mcp.config import ServerConfig
from vericov_mcp.server import build_server, main

EXPECTED_TOOL_NAMES = {
    "get_coverage_summary",
    "get_component_coverage",
    "list_file_coverage",
    "get_file_coverage",
    "get_patch_coverage",
    "list_coverage_gaps",
    "get_gate_status",
    "get_gap_manifest",
}


def _client() -> CoverageApiClient:
    return CoverageApiClient(ServerConfig(api_url="http://localhost:8080", api_key="vc_repo_test"))


def test_server_registers_every_tool_from_the_design() -> None:
    server = build_server(_client())

    registered = asyncio.run(server.list_tools())
    names = {tool.name for tool in registered}

    assert names == EXPECTED_TOOL_NAMES


def test_every_tool_has_a_docstring_description() -> None:
    server = build_server(_client())

    registered = asyncio.run(server.list_tools())

    for tool in registered:
        assert tool.description, f"{tool.name} must document what question it answers"


def test_main_fails_fast_without_configuration(capsys, monkeypatch) -> None:
    monkeypatch.delenv("VERICOV_API_URL", raising=False)
    monkeypatch.delenv("VERICOV_API_KEY", raising=False)

    exit_code = main([])

    assert exit_code == 1
    assert "VERICOV_API_URL" in capsys.readouterr().err


def test_main_prints_version_and_exits(capsys) -> None:
    exit_code = main(["--version"])

    assert exit_code == 0
    assert capsys.readouterr().out.strip()
