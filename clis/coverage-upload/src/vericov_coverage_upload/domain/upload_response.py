"""Upload API response value objects."""

from dataclasses import dataclass
from typing import Optional


@dataclass(frozen=True)
class UploadAccepted:
    upload_id: str
    status: str
    poll_url: str
    repository_id: Optional[str]
    commit_sha: str
    analysis_job_id: Optional[str]


@dataclass(frozen=True)
class UploadStatus:
    upload_id: str
    status: str
    analysis_job_id: Optional[str] = None
