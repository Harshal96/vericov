"""Human-readable output rendering."""

from typing import Iterable, Optional

from vericov_coverage_upload.domain.artifacts import UploadArtifact, format_bytes
from vericov_coverage_upload.domain.upload_response import UploadAccepted, UploadStatus


def dry_run_text(
    config_path: Optional[str],
    repository_id: Optional[str],
    idempotency_key: str,
    artifacts: Iterable[UploadArtifact],
    base_sha: Optional[str] = None,
) -> str:
    lines = [
        "Vericov upload dry run",
        f"  config: {config_path or 'none'}",
        f"  repository_id: {repository_id or 'inferred from API key'}",
        f"  idempotency_key: {idempotency_key}",
        f"  base_sha: {base_sha or 'none'}",
        "  artifacts:",
    ]
    for artifact in artifacts:
        lines.append(
            f"    - {artifact.name}: {artifact.path} ({artifact.kind}, {artifact.format}, {format_bytes(artifact.size_bytes)})"
        )
    return "\n".join(lines)


def accepted_text(accepted: UploadAccepted, artifact_count: int, total_bytes: int) -> str:
    return "\n".join(
        [
            "Vericov upload accepted",
            f"  upload_id: {accepted.upload_id}",
            f"  status: {accepted.status}",
            f"  poll_url: {accepted.poll_url}",
            f"  artifacts: {artifact_count} files, {format_bytes(total_bytes)}",
        ]
    )


def completed_text(status: UploadStatus) -> str:
    return "\n".join(
        [
            "Vericov upload completed",
            f"  upload_id: {status.upload_id}",
            f"  status: {status.status}",
        ]
    )
