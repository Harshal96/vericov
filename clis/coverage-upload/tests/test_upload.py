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
    assert payload["ignore"] == []
    assert payload["components"] == []
    assert len(payload["config_sha256"]) == 64


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


def test_upload_request_preserves_ignore_order_and_changes_idempotency(
    tmp_path: Path,
    monkeypatch,
) -> None:
    report = tmp_path / "coverage" / "lcov.info"
    report.parent.mkdir()
    report.write_text("TN:\nSF:a.py\nDA:1,1\nend_of_record\n", encoding="utf-8")
    monkeypatch.setattr("vericov_coverage_upload.application.upload_workflow.git_root", lambda cwd: tmp_path)
    monkeypatch.setattr("vericov_coverage_upload.application.upload_workflow.resolve_git_metadata", lambda cwd: None)

    first_plan = build_upload_plan(
        tmp_path,
        str(tmp_path / ".vericov.yml"),
        UploadConfig(ignore=("vendor/**", "!vendor/maintained/**")),
        UploadOverrides(coverage=("coverage/lcov.info",), commit_sha="abc123", branch="main"),
        {"VERICOV_API_KEY": "vc_live_test"},
    )
    second_plan = build_upload_plan(
        tmp_path,
        str(tmp_path / ".vericov.yml"),
        UploadConfig(ignore=("generated/**",)),
        UploadOverrides(coverage=("coverage/lcov.info",), commit_sha="abc123", branch="main"),
        {"VERICOV_API_KEY": "vc_live_test"},
    )

    assert first_plan.request.to_json()["ignore"] == [
        "vendor/**",
        "!vendor/maintained/**",
    ]
    assert (
        first_plan.request.resolved_idempotency_key()
        != second_plan.request.resolved_idempotency_key()
    )


def test_upload_request_includes_canonical_components_and_hash(tmp_path: Path, monkeypatch) -> None:
    from vericov_coverage_upload.domain.component_config import parse_components

    report = tmp_path / "coverage" / "lcov.info"
    report.parent.mkdir()
    report.write_text("TN:\nSF:a.py\nDA:1,1\nend_of_record\n", encoding="utf-8")
    monkeypatch.setattr("vericov_coverage_upload.application.upload_workflow.git_root", lambda cwd: tmp_path)
    monkeypatch.setattr("vericov_coverage_upload.application.upload_workflow.resolve_git_metadata", lambda cwd: None)

    plan = build_upload_plan(
        tmp_path,
        str(tmp_path / ".vericov.yml"),
        UploadConfig(components=parse_components([{"key": "api", "paths": ["src/**"]}])),
        UploadOverrides(coverage=("coverage/lcov.info",), commit_sha="abc123", branch="main"),
        {"VERICOV_API_KEY": "vc_live_test"},
    )

    payload = plan.request.to_json()
    assert payload["components"][0]["key"] == "api"
    assert payload["components"][0]["owners"] is None
    assert payload["config_sha256"] == plan.request.config_snapshot.sha256


def _base_setup(tmp_path: Path, monkeypatch, pull_request_number=None) -> None:
    report = tmp_path / "coverage" / "lcov.info"
    report.parent.mkdir()
    report.write_text("TN:\nSF:a.py\nDA:1,1\nend_of_record\n", encoding="utf-8")
    monkeypatch.setattr("vericov_coverage_upload.application.upload_workflow.git_root", lambda cwd: tmp_path)
    monkeypatch.setattr("vericov_coverage_upload.application.upload_workflow.resolve_git_metadata", lambda cwd: None)


def test_explicit_base_sha_generates_diff_artifact(tmp_path: Path, monkeypatch) -> None:
    _base_setup(tmp_path, monkeypatch)
    monkeypatch.setattr(
        "vericov_coverage_upload.application.upload_workflow.generate_unified_diff",
        lambda cwd, base, head, runner=None: b"diff --git a/x b/x\n",
    )

    plan = build_upload_plan(
        tmp_path,
        None,
        UploadConfig(),
        UploadOverrides(
            coverage=("coverage/lcov.info",),
            commit_sha="headsha",
            branch="main",
            pull_request_number=7,
            base_sha="basesha",
        ),
        {"VERICOV_API_KEY": "vc_live_test"},
    )

    assert plan.request.base_sha == "basesha"
    diff_artifacts = [artifact for artifact in plan.artifacts if artifact.kind == "diff"]
    assert len(diff_artifacts) == 1
    assert diff_artifacts[0].format == "git_unified_diff"
    payload = plan.request.to_json()
    assert payload["base_sha"] == "basesha"


def test_no_diff_flag_skips_diff_even_with_pull_request_number(tmp_path: Path, monkeypatch) -> None:
    _base_setup(tmp_path, monkeypatch)

    plan = build_upload_plan(
        tmp_path,
        None,
        UploadConfig(),
        UploadOverrides(
            coverage=("coverage/lcov.info",),
            commit_sha="headsha",
            branch="main",
            pull_request_number=7,
            no_diff=True,
        ),
        {"VERICOV_API_KEY": "vc_live_test"},
    )

    assert plan.request.base_sha is None
    assert not [artifact for artifact in plan.artifacts if artifact.kind == "diff"]


def test_no_diff_conflicts_with_base_sha(tmp_path: Path, monkeypatch) -> None:
    import pytest

    from vericov_coverage_upload.domain.errors import VericovCliError

    _base_setup(tmp_path, monkeypatch)

    with pytest.raises(VericovCliError):
        build_upload_plan(
            tmp_path,
            None,
            UploadConfig(),
            UploadOverrides(
                coverage=("coverage/lcov.info",),
                commit_sha="headsha",
                branch="main",
                pull_request_number=7,
                base_sha="basesha",
                no_diff=True,
            ),
            {"VERICOV_API_KEY": "vc_live_test"},
        )


def test_pull_request_without_ci_base_ref_or_explicit_flags_skips_diff(tmp_path: Path, monkeypatch) -> None:
    _base_setup(tmp_path, monkeypatch)

    plan = build_upload_plan(
        tmp_path,
        None,
        UploadConfig(),
        UploadOverrides(
            coverage=("coverage/lcov.info",),
            commit_sha="headsha",
            branch="main",
            pull_request_number=7,
        ),
        {"VERICOV_API_KEY": "vc_live_test"},
    )

    assert plan.request.base_sha is None
    assert not [artifact for artifact in plan.artifacts if artifact.kind == "diff"]


def test_ci_auto_detected_base_ref_resolution_failure_degrades_to_warning(tmp_path: Path, monkeypatch, capsys) -> None:
    _base_setup(tmp_path, monkeypatch)

    def failing_resolve(cwd, base_ref, head_sha, runner=None):
        from vericov_coverage_upload.infrastructure.diff import DiffResolutionError

        raise DiffResolutionError("could not resolve", hint="deepen the fetch")

    monkeypatch.setattr(
        "vericov_coverage_upload.application.upload_workflow.resolve_merge_base",
        failing_resolve,
    )

    plan = build_upload_plan(
        tmp_path,
        None,
        UploadConfig(),
        UploadOverrides(
            coverage=("coverage/lcov.info",),
            commit_sha="headsha",
            branch="main",
            pull_request_number=7,
        ),
        {"VERICOV_API_KEY": "vc_live_test", "GITHUB_BASE_REF": "main"},
    )

    assert plan.request.base_sha is None
    assert "warning" in capsys.readouterr().err


def test_explicit_base_ref_resolution_failure_is_fatal(tmp_path: Path, monkeypatch) -> None:
    import pytest

    from vericov_coverage_upload.domain.errors import VericovCliError

    _base_setup(tmp_path, monkeypatch)

    def failing_resolve(cwd, base_ref, head_sha, runner=None):
        from vericov_coverage_upload.infrastructure.diff import DiffResolutionError

        raise DiffResolutionError("could not resolve")

    monkeypatch.setattr(
        "vericov_coverage_upload.application.upload_workflow.resolve_merge_base",
        failing_resolve,
    )

    with pytest.raises(VericovCliError):
        build_upload_plan(
            tmp_path,
            None,
            UploadConfig(),
            UploadOverrides(
                coverage=("coverage/lcov.info",),
                commit_sha="headsha",
                branch="main",
                pull_request_number=7,
                base_ref="main",
            ),
            {"VERICOV_API_KEY": "vc_live_test"},
        )


def test_diff_file_option_requires_resolvable_base_sha(tmp_path: Path, monkeypatch) -> None:
    import pytest

    from vericov_coverage_upload.domain.errors import VericovCliError

    _base_setup(tmp_path, monkeypatch)
    diff_file = tmp_path / "pre-generated.diff"
    diff_file.write_text("diff --git a/x b/x\n", encoding="utf-8")

    with pytest.raises(VericovCliError):
        build_upload_plan(
            tmp_path,
            None,
            UploadConfig(),
            UploadOverrides(
                coverage=("coverage/lcov.info",),
                commit_sha="headsha",
                branch="main",
                pull_request_number=7,
                diff_path=str(diff_file),
            ),
            {"VERICOV_API_KEY": "vc_live_test"},
        )


def test_diff_file_option_attaches_artifact_with_explicit_base_sha(tmp_path: Path, monkeypatch) -> None:
    _base_setup(tmp_path, monkeypatch)
    diff_file = tmp_path / "pre-generated.diff"
    diff_file.write_text("diff --git a/x b/x\n", encoding="utf-8")

    plan = build_upload_plan(
        tmp_path,
        None,
        UploadConfig(),
        UploadOverrides(
            coverage=("coverage/lcov.info",),
            commit_sha="headsha",
            branch="main",
            pull_request_number=7,
            base_sha="basesha",
            diff_path=str(diff_file),
        ),
        {"VERICOV_API_KEY": "vc_live_test"},
    )

    assert plan.request.base_sha == "basesha"
    diff_artifacts = [artifact for artifact in plan.artifacts if artifact.kind == "diff"]
    assert len(diff_artifacts) == 1


def test_diff_file_option_rejects_content_that_is_not_a_unified_diff(tmp_path: Path, monkeypatch) -> None:
    import pytest

    from vericov_coverage_upload.domain.errors import VericovCliError

    _base_setup(tmp_path, monkeypatch)
    diff_file = tmp_path / "pre-generated.diff"
    diff_file.write_text("not a diff", encoding="utf-8")

    with pytest.raises(VericovCliError):
        build_upload_plan(
            tmp_path,
            None,
            UploadConfig(),
            UploadOverrides(
                coverage=("coverage/lcov.info",),
                commit_sha="headsha",
                branch="main",
                pull_request_number=7,
                base_sha="basesha",
                diff_path=str(diff_file),
            ),
            {"VERICOV_API_KEY": "vc_live_test"},
        )
