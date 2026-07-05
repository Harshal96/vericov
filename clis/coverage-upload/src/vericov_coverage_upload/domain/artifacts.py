"""Artifact value objects and payload helpers."""

import base64
import hashlib
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Tuple

from vericov_coverage_upload.domain.errors import ExitCode, VericovCliError


SAFE_NAME_RE = re.compile(r"[^A-Za-z0-9._-]+")


@dataclass(frozen=True)
class ArtifactCandidate:
    path: Path
    kind: str


@dataclass(frozen=True)
class UploadArtifact:
    path: Path
    name: str
    kind: str
    format: str
    content_type: str
    size_bytes: int
    sha256: str
    content: Optional[bytes] = None

    def read_bytes(self) -> bytes:
        return self.content if self.content is not None else self.path.read_bytes()

    def to_request(self) -> Dict[str, str]:
        content = self.read_bytes()
        return {
            "name": self.name,
            "kind": self.kind,
            "format": self.format,
            "content_type": self.content_type,
            "content_base64": base64.b64encode(content).decode("ascii"),
        }


def build_diff_artifact(name: str, content: bytes) -> UploadArtifact:
    return UploadArtifact(
        path=Path(name),
        name=name,
        kind="diff",
        format="git_unified_diff",
        content_type="text/x-diff",
        size_bytes=len(content),
        sha256=hashlib.sha256(content).hexdigest(),
        content=content,
    )


def validate_candidate_files(
    candidates: Iterable[ArtifactCandidate],
    project_root: Path,
    max_artifact_bytes: int,
    max_total_bytes: int,
) -> Tuple[ArtifactCandidate, ...]:
    resolved_root = project_root.resolve()
    unique: Dict[Path, ArtifactCandidate] = {}
    total = 0
    for candidate in candidates:
        resolved = candidate.path.resolve()
        if not _is_relative_to(resolved, resolved_root):
            raise VericovCliError(
                "artifact_outside_root",
                f"{candidate.path} is outside the project root.",
                ExitCode.ARTIFACT_VALIDATION,
            )
        if not resolved.exists():
            raise VericovCliError(
                "artifact_missing",
                f"{candidate.path} does not exist.",
                ExitCode.ARTIFACT_VALIDATION,
            )
        if not resolved.is_file():
            raise VericovCliError(
                "artifact_not_file",
                f"{candidate.path} is not a file.",
                ExitCode.ARTIFACT_VALIDATION,
            )
        size = resolved.stat().st_size
        if size == 0:
            raise VericovCliError(
                "artifact_empty",
                f"{candidate.path} is empty.",
                ExitCode.ARTIFACT_VALIDATION,
            )
        if size > max_artifact_bytes:
            raise VericovCliError(
                "artifact_too_large",
                f"{candidate.path} is above the {format_bytes(max_artifact_bytes)} per-artifact limit.",
                ExitCode.ARTIFACT_VALIDATION,
            )
        total += size
        if total > max_total_bytes:
            raise VericovCliError(
                "artifacts_too_large",
                f"Selected artifacts exceed the {format_bytes(max_total_bytes)} total raw byte limit.",
                ExitCode.ARTIFACT_VALIDATION,
            )
        unique[resolved] = ArtifactCandidate(resolved, candidate.kind)
    return tuple(unique[path] for path in sorted(unique))


def build_artifacts(
    candidates: Iterable[ArtifactCandidate],
    project_root: Path,
    detected_formats: Dict[Path, Tuple[str, str]],
) -> Tuple[UploadArtifact, ...]:
    used_names: Dict[str, Path] = {}
    artifacts: List[UploadArtifact] = []
    for candidate in candidates:
        relative = candidate.path.resolve().relative_to(project_root.resolve())
        name = safe_artifact_name(relative, used_names)
        content = candidate.path.read_bytes()
        digest = hashlib.sha256(content).hexdigest()
        artifact_format, content_type = detected_formats[candidate.path.resolve()]
        used_names[name] = candidate.path
        artifacts.append(
            UploadArtifact(
                path=candidate.path,
                name=name,
                kind=candidate.kind,
                format=artifact_format,
                content_type=content_type,
                size_bytes=len(content),
                sha256=digest,
            )
        )
    return tuple(artifacts)


def safe_artifact_name(relative_path: Path, used_names: Dict[str, Path]) -> str:
    raw = "__".join(relative_path.parts)
    cleaned = SAFE_NAME_RE.sub("-", raw).strip(".-")
    if len(cleaned) > 180:
        suffix = "".join(Path(cleaned).suffixes)
        stem = cleaned[: max(1, 180 - len(suffix))]
        cleaned = stem + suffix
    if cleaned not in used_names:
        return cleaned
    digest = hashlib.sha256(str(relative_path).encode("utf-8")).hexdigest()[:10]
    suffix = "".join(Path(cleaned).suffixes)
    stem = cleaned[: max(1, 180 - len(suffix) - len(digest) - 1)]
    return f"{stem}-{digest}{suffix}"


def format_bytes(value: int) -> str:
    if value >= 1024 * 1024:
        return f"{value / (1024 * 1024):.1f} MiB"
    if value >= 1024:
        return f"{value / 1024:.1f} KiB"
    return f"{value} bytes"


def _is_relative_to(path: Path, parent: Path) -> bool:
    try:
        path.relative_to(parent)
        return True
    except ValueError:
        return False
