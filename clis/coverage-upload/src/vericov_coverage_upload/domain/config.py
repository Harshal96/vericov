"""Configuration value objects."""

from dataclasses import dataclass
from typing import Optional, Tuple

DEFAULT_API_URL = "https://api.vericov.dev"
DEFAULT_MAX_ARTIFACT_BYTES = 25 * 1024 * 1024
DEFAULT_MAX_TOTAL_BYTES = 50 * 1024 * 1024
DEFAULT_TIMEOUT_SECONDS = 300


@dataclass(frozen=True)
class DiscoveryConfig:
    enabled: Optional[bool] = None
    roots: Tuple[str, ...] = (".",)
    include: Tuple[str, ...] = ()
    exclude: Tuple[str, ...] = ()


@dataclass(frozen=True)
class UploadConfig:
    api_url: str = DEFAULT_API_URL
    repository_id: Optional[str] = None
    commit_sha: Optional[str] = None
    branch: Optional[str] = None
    pull_request_number: Optional[int] = None
    ci_provider: Optional[str] = None
    ci_build_id: Optional[str] = None
    ci_build_url: Optional[str] = None
    flags: Tuple[str, ...] = ()
    ignore: Tuple[str, ...] = ()
    component: Optional[str] = None
    package: Optional[str] = None
    coverage: Tuple[str, ...] = ()
    test_results: Tuple[str, ...] = ()
    discover: DiscoveryConfig = DiscoveryConfig()
    wait: bool = False
    timeout_seconds: int = DEFAULT_TIMEOUT_SECONDS
    max_artifact_bytes: int = DEFAULT_MAX_ARTIFACT_BYTES
    max_total_bytes: int = DEFAULT_MAX_TOTAL_BYTES


@dataclass(frozen=True)
class ResolvedConfig:
    path: Optional[str]
    upload: UploadConfig
