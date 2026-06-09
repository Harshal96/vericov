from pathlib import Path

import pytest

from vericov_coverage_upload.domain.config import DiscoveryConfig
from vericov_coverage_upload.domain.errors import VericovCliError
from vericov_coverage_upload.infrastructure.file_discovery import collect_candidates


def test_explicit_artifact_disables_default_discovery(tmp_path: Path) -> None:
    explicit = tmp_path / "custom.info"
    explicit.write_text("TN:\nSF:a.py\nDA:1,1\n", encoding="utf-8")
    discovered = tmp_path / "coverage" / "lcov.info"
    discovered.parent.mkdir()
    discovered.write_text("TN:\nSF:b.py\nDA:1,1\n", encoding="utf-8")

    candidates = collect_candidates(
        tmp_path,
        ("custom.info",),
        (),
        DiscoveryConfig(),
    )

    assert [candidate.path for candidate in candidates] == [explicit.resolve()]


def test_discovery_override_includes_coverage_and_test_results(tmp_path: Path) -> None:
    coverage = tmp_path / "reports" / "coverage.xml"
    coverage.parent.mkdir()
    coverage.write_text("<coverage/>", encoding="utf-8")
    junit = tmp_path / "test-results" / "junit.xml"
    junit.parent.mkdir()
    junit.write_text("<testsuite/>", encoding="utf-8")

    candidates = collect_candidates(
        tmp_path,
        (),
        (),
        DiscoveryConfig(roots=("reports",), include=("coverage.xml",)),
        discover_override=True,
    )

    assert [(candidate.path.name, candidate.kind) for candidate in candidates] == [
        ("coverage.xml", "coverage"),
    ]

    root_coverage = tmp_path / "coverage.xml"
    root_coverage.write_text("<coverage/>", encoding="utf-8")
    all_candidates = collect_candidates(
        tmp_path,
        (),
        (),
        DiscoveryConfig(),
        discover_override=True,
    )
    assert {(candidate.path.name, candidate.kind) for candidate in all_candidates} == {
        ("coverage.xml", "coverage"),
        ("junit.xml", "test_results"),
    }


def test_discovery_respects_default_excludes(tmp_path: Path) -> None:
    excluded = tmp_path / "node_modules" / "package" / "coverage" / "lcov.info"
    excluded.parent.mkdir(parents=True)
    excluded.write_text("TN:\nSF:a.py\nDA:1,1\n", encoding="utf-8")

    with pytest.raises(VericovCliError) as error:
        collect_candidates(tmp_path, (), (), DiscoveryConfig())

    assert error.value.code == "no_artifacts"
