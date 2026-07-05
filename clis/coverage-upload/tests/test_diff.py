from pathlib import Path

import pytest

from vericov_coverage_upload.infrastructure.diff import (
    DiffResolutionError,
    detect_ci_base_ref,
    generate_unified_diff,
    parses_as_unified_diff,
    resolve_merge_base,
)


def test_detects_ci_base_ref_precedence() -> None:
    assert detect_ci_base_ref({"GITHUB_BASE_REF": "main"}) == "origin/main"
    assert detect_ci_base_ref({"CI_MERGE_REQUEST_TARGET_BRANCH_NAME": "develop"}) == "origin/develop"
    assert detect_ci_base_ref({"BITBUCKET_PR_DESTINATION_BRANCH": "release"}) == "origin/release"
    assert detect_ci_base_ref({}) is None


def test_resolve_merge_base_returns_trimmed_sha(tmp_path: Path) -> None:
    def runner(command, cwd):
        assert command == ("git", "merge-base", "origin/main", "headsha")
        return "abc123\n"

    assert resolve_merge_base(tmp_path, "origin/main", "headsha", runner) == "abc123"


def test_resolve_merge_base_wraps_failures_with_a_deepen_fetch_hint(tmp_path: Path) -> None:
    def failing_runner(command, cwd):
        raise RuntimeError("fatal: no merge base")

    with pytest.raises(DiffResolutionError) as error:
        resolve_merge_base(tmp_path, "origin/main", "headsha", failing_runner)

    assert "deepen the fetch" in str(error.value)


def test_resolve_merge_base_rejects_empty_output(tmp_path: Path) -> None:
    def empty_runner(command, cwd):
        return "\n"

    with pytest.raises(DiffResolutionError):
        resolve_merge_base(tmp_path, "origin/main", "headsha", empty_runner)


def test_generate_unified_diff_invokes_expected_git_arguments(tmp_path: Path) -> None:
    captured = {}

    def runner(command, cwd):
        captured["command"] = command
        return "diff --git a/x b/x\n"

    content = generate_unified_diff(tmp_path, "base-sha", "head-sha", runner)

    assert captured["command"] == (
        "git",
        "-c",
        "core.quotePath=false",
        "diff",
        "--no-color",
        "--no-ext-diff",
        "--find-renames",
        "--unified=0",
        "base-sha",
        "head-sha",
    )
    assert content == b"diff --git a/x b/x\n"


def test_generate_unified_diff_wraps_failures(tmp_path: Path) -> None:
    def failing_runner(command, cwd):
        raise RuntimeError("git error")

    with pytest.raises(DiffResolutionError):
        generate_unified_diff(tmp_path, "base", "head", failing_runner)


def test_parses_as_unified_diff() -> None:
    assert parses_as_unified_diff(b"diff --git a/x b/x\n")
    assert not parses_as_unified_diff(b"not a diff")
