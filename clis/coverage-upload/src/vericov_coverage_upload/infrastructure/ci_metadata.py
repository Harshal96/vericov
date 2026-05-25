"""CI environment metadata resolution."""

from typing import Mapping, Optional

from vericov_coverage_upload.domain.metadata import UploadMetadata


def resolve_ci_metadata(env: Mapping[str, str]) -> Optional[UploadMetadata]:
    if "GITHUB_SHA" in env:
        return UploadMetadata(
            commit_sha=env["GITHUB_SHA"],
            branch=env.get("GITHUB_HEAD_REF") or env.get("GITHUB_REF_NAME", ""),
            pull_request_number=_event_pull_request_number(env),
            ci_provider="github_actions",
            ci_build_id=env.get("GITHUB_RUN_ID"),
            ci_build_url=_github_run_url(env),
        )
    if "CI_COMMIT_SHA" in env:
        return UploadMetadata(
            commit_sha=env["CI_COMMIT_SHA"],
            branch=env.get("CI_COMMIT_REF_NAME", ""),
            pull_request_number=_int_or_none(env.get("CI_MERGE_REQUEST_IID")),
            ci_provider="gitlab_ci",
            ci_build_id=env.get("CI_PIPELINE_ID"),
            ci_build_url=env.get("CI_PIPELINE_URL"),
        )
    if "CIRCLE_SHA1" in env:
        return UploadMetadata(
            commit_sha=env["CIRCLE_SHA1"],
            branch=env.get("CIRCLE_BRANCH", ""),
            pull_request_number=_last_path_int(env.get("CIRCLE_PULL_REQUEST")),
            ci_provider="circleci",
            ci_build_id=env.get("CIRCLE_BUILD_NUM"),
            ci_build_url=env.get("CIRCLE_BUILD_URL"),
        )
    if "BUILDKITE_COMMIT" in env:
        return UploadMetadata(
            commit_sha=env["BUILDKITE_COMMIT"],
            branch=env.get("BUILDKITE_BRANCH", ""),
            pull_request_number=_int_or_none(env.get("BUILDKITE_PULL_REQUEST")),
            ci_provider="buildkite",
            ci_build_id=env.get("BUILDKITE_BUILD_ID"),
            ci_build_url=env.get("BUILDKITE_BUILD_URL"),
        )
    commit = env.get("CI_COMMIT_SHA") or env.get("COMMIT_SHA") or env.get("GIT_COMMIT")
    branch = env.get("BRANCH_NAME") or env.get("CI_BRANCH")
    if commit and branch:
        return UploadMetadata(
            commit_sha=commit,
            branch=branch,
            ci_provider="generic_ci",
            ci_build_id=env.get("BUILD_ID"),
            ci_build_url=env.get("BUILD_URL"),
        )
    return None


def _github_run_url(env: Mapping[str, str]) -> Optional[str]:
    server = env.get("GITHUB_SERVER_URL")
    repo = env.get("GITHUB_REPOSITORY")
    run_id = env.get("GITHUB_RUN_ID")
    if server and repo and run_id:
        return f"{server}/{repo}/actions/runs/{run_id}"
    return None


def _event_pull_request_number(env: Mapping[str, str]) -> Optional[int]:
    if env.get("GITHUB_EVENT_NAME") != "pull_request":
        return None
    return _last_path_int(env.get("GITHUB_REF"))


def _last_path_int(value: Optional[str]) -> Optional[int]:
    if not value:
        return None
    return _int_or_none(value.rstrip("/").split("/")[-1])


def _int_or_none(value: Optional[str]) -> Optional[int]:
    if not value or value == "false":
        return None
    try:
        return int(value)
    except ValueError:
        return None
