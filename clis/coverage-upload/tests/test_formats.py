from pathlib import Path

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
