"""Upload request value objects."""

import hashlib
import json
from dataclasses import dataclass
from typing import Any, Dict, Optional, Tuple

from vericov_coverage_upload.domain.artifacts import UploadArtifact
from vericov_coverage_upload.domain.component_config import (
    ComponentDefinition,
    ConfigSnapshot,
)
from vericov_coverage_upload.domain.metadata import UploadMetadata


@dataclass(frozen=True)
class UploadRequest:
    api_url: str
    repository_id: Optional[str]
    metadata: UploadMetadata
    flags: Tuple[str, ...]
    component: Optional[str]
    package: Optional[str]
    artifacts: Tuple[UploadArtifact, ...]
    ignore: Tuple[str, ...] = ()
    components: Tuple[ComponentDefinition, ...] = ()
    idempotency_key: Optional[str] = None

    def __post_init__(self) -> None:
        object.__setattr__(self, "flags", tuple(self.flags))
        object.__setattr__(self, "artifacts", tuple(self.artifacts))
        object.__setattr__(self, "ignore", tuple(self.ignore))
        object.__setattr__(self, "components", tuple(self.components))

    @property
    def config_snapshot(self) -> ConfigSnapshot:
        return ConfigSnapshot(1, self.ignore, self.components)

    def to_json(self) -> Dict[str, Any]:
        payload: Dict[str, Any] = {
            "repository_id": self.repository_id,
            "commit_sha": self.metadata.commit_sha,
            "branch": self.metadata.branch,
            "pull_request_number": self.metadata.pull_request_number,
            "ci_provider": self.metadata.ci_provider,
            "ci_build_id": self.metadata.ci_build_id,
            "ci_build_url": self.metadata.ci_build_url,
            "flags": list(self.flags),
            "ignore": list(self.ignore),
            "components": [component.to_data() for component in self.components],
            "config_sha256": self.config_snapshot.sha256,
            "component": self.component,
            "package": self.package,
            "artifacts": [artifact.to_request() for artifact in self.artifacts],
        }
        return {key: value for key, value in payload.items() if value is not None}

    def resolved_idempotency_key(self) -> str:
        if self.idempotency_key:
            return self.idempotency_key
        material = {
            "version": "vericov-upload-v1",
            "repository_id": self.repository_id,
            "commit_sha": self.metadata.commit_sha,
            "branch": self.metadata.branch,
            "pull_request_number": self.metadata.pull_request_number,
            "ci_provider": self.metadata.ci_provider,
            "ci_build_id": self.metadata.ci_build_id,
            "flags": list(self.flags),
            "ignore": list(self.ignore),
            "components": [component.to_data() for component in self.components],
            "config_sha256": self.config_snapshot.sha256,
            "component": self.component,
            "package": self.package,
            "artifacts": [
                {
                    "name": artifact.name,
                    "kind": artifact.kind,
                    "format": artifact.format,
                    "sha256": artifact.sha256,
                }
                for artifact in self.artifacts
            ],
        }
        encoded = json.dumps(material, sort_keys=True, separators=(",", ":")).encode("utf-8")
        return "vericov-upload-v1-" + hashlib.sha256(encoded).hexdigest()[:48]


@dataclass(frozen=True)
class UploadAuth:
    api_key: str
