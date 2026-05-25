"""URL helpers for upload service calls."""

from urllib.parse import urljoin, urlparse

from vericov_coverage_upload.domain.errors import ExitCode, VericovCliError


def uploads_url(api_url: str) -> str:
    return urljoin(_base(api_url), "/api/v1/uploads")


def resolve_poll_url(api_url: str, poll_url: str) -> str:
    resolved = urljoin(_base(api_url), poll_url)
    api_host = urlparse(api_url).netloc
    poll_host = urlparse(resolved).netloc
    if api_host != poll_host:
        raise VericovCliError(
            "cross_host_poll_url",
            "Upload poll URL points to a different host than api_url.",
            ExitCode.API,
        )
    return resolved


def _base(api_url: str) -> str:
    return api_url.rstrip("/") + "/"
