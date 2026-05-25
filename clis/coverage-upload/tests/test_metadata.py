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
