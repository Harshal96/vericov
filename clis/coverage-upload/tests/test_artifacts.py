from pathlib import Path

import pytest

from vericov_coverage_upload.domain.artifacts import (
    ArtifactCandidate,
    build_artifacts,
    safe_artifact_name,
    validate_candidate_files,
)
from vericov_coverage_upload.domain.errors import VericovCliError


def test_safe_artifact_name_flattens_path() -> None:
    assert safe_artifact_name(Path("services/api/coverage.xml"), {}) == "services__api__coverage.xml"


def test_validate_rejects_outside_root(tmp_path: Path) -> None:
    outside = tmp_path.parent / "outside-lcov.info"
    outside.write_text("TN:\nSF:a.py\nDA:1,1\nend_of_record\n", encoding="utf-8")

    with pytest.raises(VericovCliError) as error:
        validate_candidate_files(
            [ArtifactCandidate(outside, "coverage")],
            tmp_path,
            max_artifact_bytes=1024,
            max_total_bytes=2048,
        )

    assert error.value.code == "artifact_outside_root"


def test_build_artifacts_adds_digest_and_request_payload(tmp_path: Path) -> None:
    report = tmp_path / "coverage" / "lcov.info"
    report.parent.mkdir()
    report.write_text("TN:\nSF:a.py\nDA:1,1\nend_of_record\n", encoding="utf-8")
    candidate = ArtifactCandidate(report, "coverage")
    valid = validate_candidate_files([candidate], tmp_path, 1024, 2048)

    artifacts = build_artifacts(valid, tmp_path, {report.resolve(): ("lcov", "text/plain")})

    assert artifacts[0].format == "lcov"
    assert artifacts[0].to_request()["content_base64"]
