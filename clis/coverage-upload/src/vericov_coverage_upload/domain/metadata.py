"""Resolved upload metadata."""

from dataclasses import dataclass
from typing import Optional


@dataclass(frozen=True)
class UploadMetadata:
    commit_sha: str
    branch: str
    pull_request_number: Optional[int] = None
    ci_provider: Optional[str] = None
    ci_build_id: Optional[str] = None
    ci_build_url: Optional[str] = None
