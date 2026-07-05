"""JSON output rendering."""

import json
from typing import Any, Dict, Iterable, Optional

from vericov_coverage_upload.domain.artifacts import UploadArtifact
from vericov_coverage_upload.domain.errors import VericovCliError
from vericov_coverage_upload.domain.upload_response import UploadAccepted, UploadStatus


def dump(payload: Dict[str, Any]) -> str:
    return json.dumps(payload, sort_keys=True)


def error_payload(error: VericovCliError) -> str:
    return dump({"ok": False, "error": {"code": error.code, "message": error.message}})


def dry_run_payload(
    config_path: Optional[str],
    repository_id: Optional[str],
    idempotency_key: str,
    artifacts: Iterable[UploadArtifact],
    base_sha: Optional[str] = None,
) -> str:
    return dump(
        {
            "ok": True,
            "dry_run": True,
            "config": config_path,
            "repository_id": repository_id,
            "idempotency_key": idempotency_key,
            "base_sha": base_sha,
            "artifacts": [_artifact_payload(artifact) for artifact in artifacts],
        }
    )


def accepted_payload(accepted: UploadAccepted, artifacts: Iterable[UploadArtifact]) -> str:
    return dump(
        {
            "ok": True,
            "upload_id": accepted.upload_id,
            "status": accepted.status,
            "poll_url": accepted.poll_url,
            "analysis_job_id": accepted.analysis_job_id,
            "artifacts": [_artifact_payload(artifact) for artifact in artifacts],
        }
    )


def completed_payload(status: UploadStatus) -> str:
    return dump({"ok": True, "upload_id": status.upload_id, "status": status.status})


def _artifact_payload(artifact: UploadArtifact) -> Dict[str, Any]:
    return {
        "path": str(artifact.path),
        "name": artifact.name,
        "kind": artifact.kind,
        "format": artifact.format,
        "size_bytes": artifact.size_bytes,
    }
