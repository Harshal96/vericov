"""Upload workflow orchestration."""

import os
from dataclasses import dataclass
from pathlib import Path
from typing import Mapping, Optional, Sequence, Tuple

from vericov_coverage_upload.domain.artifacts import UploadArtifact, build_artifacts, validate_candidate_files
from vericov_coverage_upload.domain.config import UploadConfig
from vericov_coverage_upload.domain.errors import ExitCode, VericovCliError
from vericov_coverage_upload.domain.metadata import UploadMetadata
from vericov_coverage_upload.domain.upload_request import UploadAuth, UploadRequest
from vericov_coverage_upload.domain.upload_response import UploadAccepted
from vericov_coverage_upload.infrastructure.ci_metadata import resolve_ci_metadata
from vericov_coverage_upload.infrastructure.config_loader import merge_environment
from vericov_coverage_upload.infrastructure.file_discovery import collect_candidates
from vericov_coverage_upload.infrastructure.format_detection import detect_format
from vericov_coverage_upload.infrastructure.git_metadata import git_root, resolve_git_metadata
from vericov_coverage_upload.infrastructure.http.direct_url_upload_gateway import UploadGateway


@dataclass(frozen=True)
class UploadOverrides:
    api_key: Optional[str] = None
    api_url: Optional[str] = None
    repository_id: Optional[str] = None
    commit_sha: Optional[str] = None
    branch: Optional[str] = None
    pull_request_number: Optional[int] = None
    coverage: Tuple[str, ...] = ()
    test_results: Tuple[str, ...] = ()
    flags: Tuple[str, ...] = ()
    component: Optional[str] = None
    package: Optional[str] = None
    wait: Optional[bool] = None
    timeout_seconds: Optional[int] = None
    max_artifact_bytes: Optional[int] = None
    max_total_bytes: Optional[int] = None
    discover: Optional[bool] = None
    idempotency_key: Optional[str] = None


@dataclass(frozen=True)
class UploadPlan:
    project_root: Path
    config_path: Optional[str]
    auth: UploadAuth
    request: UploadRequest

    @property
    def artifacts(self) -> Tuple[UploadArtifact, ...]:
        return self.request.artifacts


def build_upload_plan(
    cwd: Path,
    config_path: Optional[str],
    config: UploadConfig,
    overrides: UploadOverrides,
    env: Mapping[str, str] = os.environ,
) -> UploadPlan:
    merged = merge_environment(config, env)
    resolved = _apply_overrides(merged, overrides)
    api_key = overrides.api_key or env.get("VERICOV_API_KEY")
    if not api_key:
        raise VericovCliError("missing_api_key", "VERICOV_API_KEY or --api-key is required.", ExitCode.AUTH)

    project_root = git_root(cwd)
    metadata = _resolve_metadata(cwd, resolved, overrides, env)
    candidates = collect_candidates(
        project_root,
        resolved.coverage,
        resolved.test_results,
        resolved.discover,
        overrides.discover,
    )
    valid_candidates = validate_candidate_files(
        candidates,
        project_root,
        resolved.max_artifact_bytes,
        resolved.max_total_bytes,
    )
    detected = {candidate.path.resolve(): detect_format(candidate.path, candidate.kind) for candidate in valid_candidates}
    artifacts = build_artifacts(valid_candidates, project_root, detected)
    request = UploadRequest(
        api_url=resolved.api_url,
        repository_id=resolved.repository_id,
        metadata=metadata,
        flags=resolved.flags,
        component=resolved.component,
        package=resolved.package,
        artifacts=artifacts,
        idempotency_key=overrides.idempotency_key or env.get("VERICOV_IDEMPOTENCY_KEY"),
    )
    return UploadPlan(project_root=project_root, config_path=config_path, auth=UploadAuth(api_key), request=request)


def submit_upload(plan: UploadPlan, gateway: UploadGateway) -> UploadAccepted:
    return gateway.create_upload(plan.request, plan.auth)


def _resolve_metadata(
    cwd: Path,
    config: UploadConfig,
    overrides: UploadOverrides,
    env: Mapping[str, str],
) -> UploadMetadata:
    discovered = resolve_ci_metadata(env) or resolve_git_metadata(cwd)
    commit = overrides.commit_sha or config.commit_sha or (discovered.commit_sha if discovered else None)
    branch = overrides.branch or config.branch or (discovered.branch if discovered else None)
    if not commit:
        raise VericovCliError("missing_commit_sha", "commit_sha is required and could not be detected.", ExitCode.USAGE)
    if not branch:
        raise VericovCliError("missing_branch", "branch is required and could not be detected.", ExitCode.USAGE)
    return UploadMetadata(
        commit_sha=commit,
        branch=branch,
        pull_request_number=(
            overrides.pull_request_number
            if overrides.pull_request_number is not None
            else config.pull_request_number or (discovered.pull_request_number if discovered else None)
        ),
        ci_provider=config.ci_provider or (discovered.ci_provider if discovered else None),
        ci_build_id=config.ci_build_id or (discovered.ci_build_id if discovered else None),
        ci_build_url=config.ci_build_url or (discovered.ci_build_url if discovered else None),
    )


def _apply_overrides(config: UploadConfig, overrides: UploadOverrides) -> UploadConfig:
    return UploadConfig(
        api_url=overrides.api_url or config.api_url,
        repository_id=overrides.repository_id or config.repository_id,
        commit_sha=overrides.commit_sha or config.commit_sha,
        branch=overrides.branch or config.branch,
        pull_request_number=(
            overrides.pull_request_number if overrides.pull_request_number is not None else config.pull_request_number
        ),
        ci_provider=config.ci_provider,
        ci_build_id=config.ci_build_id,
        ci_build_url=config.ci_build_url,
        flags=overrides.flags or config.flags,
        component=overrides.component or config.component,
        package=overrides.package or config.package,
        coverage=overrides.coverage or config.coverage,
        test_results=overrides.test_results or config.test_results,
        discover=config.discover,
        wait=overrides.wait if overrides.wait is not None else config.wait,
        timeout_seconds=overrides.timeout_seconds or config.timeout_seconds,
        max_artifact_bytes=overrides.max_artifact_bytes or config.max_artifact_bytes,
        max_total_bytes=overrides.max_total_bytes or config.max_total_bytes,
    )
