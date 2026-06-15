from pathlib import Path

import pytest

from vericov_coverage_upload.domain.errors import VericovCliError
from vericov_coverage_upload.infrastructure.format_detection import detect_format


def test_detects_lcov(tmp_path: Path) -> None:
    report = tmp_path / "lcov.info"
    report.write_text("TN:\nSF:a.py\nDA:1,1\nend_of_record\n", encoding="utf-8")

    assert detect_format(report, "coverage") == ("lcov", "text/plain")


def test_detects_junit(tmp_path: Path) -> None:
    report = tmp_path / "junit.xml"
    report.write_text("<testsuite tests=\"1\"></testsuite>", encoding="utf-8")

    assert detect_format(report, "test_results") == ("junit", "application/xml")


def test_detects_jacoco(tmp_path: Path) -> None:
    report = tmp_path / "jacoco.xml"
    report.write_text("<report name=\"unit\"></report>", encoding="utf-8")

    assert detect_format(report, "coverage") == ("jacoco", "application/xml")


@pytest.mark.parametrize(
    ("name", "content", "expected"),
    [
        ("coverage.gcov", "Source:a.py\n    #####: 1: missed\n", ("gcov", "text/plain")),
        ("coverage.out", "mode: atomic\na.go:1.1,1.2 1 1\n", ("go_cover", "text/plain")),
        ("coverage.xml", "<coverage></coverage>", ("cobertura", "application/xml")),
        ("clover.xml", "<coverage></coverage>", ("clover", "application/xml")),
    ],
)
def test_detects_additional_coverage_formats(tmp_path: Path, name: str, content: str, expected) -> None:
    report = tmp_path / name
    report.write_text(content, encoding="utf-8")

    assert detect_format(report, "coverage") == expected


def test_detects_namespaced_junit_root(tmp_path: Path) -> None:
    report = tmp_path / "junit.xml"
    report.write_text('<testsuites xmlns="urn:test"></testsuites>', encoding="utf-8")

    assert detect_format(report, "test_results") == ("junit", "application/xml")


def test_rejects_unknown_or_malformed_format(tmp_path: Path) -> None:
    report = tmp_path / "coverage.xml"
    report.write_text("<not-closed>", encoding="utf-8")

    with pytest.raises(VericovCliError) as error:
        detect_format(report, "coverage")

    assert error.value.code == "unsupported_artifact_format"
