from pathlib import Path

from vericov_coverage_upload.infrastructure.ci_metadata import resolve_ci_metadata
from vericov_coverage_upload.infrastructure.git_metadata import resolve_git_metadata


def test_resolves_github_actions_metadata() -> None:
    metadata = resolve_ci_metadata(
        {
            "GITHUB_SHA": "abc123",
            "GITHUB_REF_NAME": "main",
            "GITHUB_RUN_ID": "42",
            "GITHUB_SERVER_URL": "https://github.com",
            "GITHUB_REPOSITORY": "acme/api",
        }
    )

    assert metadata
    assert metadata.commit_sha == "abc123"
    assert metadata.branch == "main"
    assert metadata.ci_provider == "github_actions"
    assert metadata.ci_build_url == "https://github.com/acme/api/actions/runs/42"


def test_resolves_git_fallback(tmp_path: Path) -> None:
    def runner(command, cwd):
        if command[-1] == "HEAD":
            return "abc123\n"
        return "main\n"

    metadata = resolve_git_metadata(tmp_path, runner)

    assert metadata
    assert metadata.commit_sha == "abc123"
    assert metadata.branch == "main"


def test_resolves_github_pull_request_number() -> None:
    metadata = resolve_ci_metadata(
        {
            "GITHUB_SHA": "abc123",
            "GITHUB_HEAD_REF": "feature",
            "GITHUB_EVENT_NAME": "pull_request",
            "GITHUB_REF": "refs/pull/27",
        }
    )

    assert metadata
    assert metadata.branch == "feature"
    assert metadata.pull_request_number == 27


def test_resolves_supported_ci_providers() -> None:
    gitlab = resolve_ci_metadata(
        {
            "CI_COMMIT_SHA": "gitlab-sha",
            "CI_COMMIT_REF_NAME": "main",
            "CI_MERGE_REQUEST_IID": "12",
        }
    )
    circle = resolve_ci_metadata(
        {
            "CIRCLE_SHA1": "circle-sha",
            "CIRCLE_BRANCH": "feature",
            "CIRCLE_PULL_REQUEST": "https://github.com/acme/repo/pull/13",
        }
    )
    buildkite = resolve_ci_metadata(
        {
            "BUILDKITE_COMMIT": "buildkite-sha",
            "BUILDKITE_BRANCH": "release",
            "BUILDKITE_PULL_REQUEST": "false",
        }
    )
    generic = resolve_ci_metadata({"COMMIT_SHA": "generic-sha", "CI_BRANCH": "develop"})

    assert gitlab and (gitlab.ci_provider, gitlab.pull_request_number) == ("gitlab_ci", 12)
    assert circle and (circle.ci_provider, circle.pull_request_number) == ("circleci", 13)
    assert buildkite and (buildkite.ci_provider, buildkite.pull_request_number) == ("buildkite", None)
    assert generic and generic.ci_provider == "generic_ci"


def test_invalid_or_missing_ci_metadata_returns_none() -> None:
    assert resolve_ci_metadata({"COMMIT_SHA": "sha-only"}) is None
    metadata = resolve_ci_metadata(
        {
            "CIRCLE_SHA1": "circle-sha",
            "CIRCLE_BRANCH": "main",
            "CIRCLE_PULL_REQUEST": "not-a-number",
        }
    )
    assert metadata and metadata.pull_request_number is None


def test_git_metadata_returns_none_when_runner_fails_or_values_missing(tmp_path: Path) -> None:
    def failing_runner(command, cwd):
        raise RuntimeError("git unavailable")

    def empty_branch_runner(command, cwd):
        return "abc123\n" if command[-1] == "HEAD" else "\n"

    assert resolve_git_metadata(tmp_path, failing_runner) is None
    assert resolve_git_metadata(tmp_path, empty_branch_runner) is None
