"""Thin read-only HTTP client for the coverage query API.

Used by `vericov gaps` to fetch the gap manifest. Mirrors
`direct_url_upload_gateway.py`'s error handling and retry conventions but
never mutates anything — it issues a single GET request per call.
"""

import json
from typing import Any, Dict, Optional
from urllib.error import HTTPError
from urllib.parse import urlencode, urljoin
from urllib.request import Request, urlopen

from vericov_coverage_upload import __version__
from vericov_coverage_upload.domain.errors import ExitCode, VericovCliError
from vericov_coverage_upload.domain.upload_request import UploadAuth
from vericov_coverage_upload.infrastructure.http.retry import (
    NON_RETRYABLE_STATUS,
    RETRYABLE_STATUS,
    RetryPolicy,
    RetryableHttpError,
    with_retries,
)


class CoverageQueryGateway:
    def __init__(self, timeout_seconds: int = 30, retry_policy: RetryPolicy = RetryPolicy()):
        self.timeout_seconds = timeout_seconds
        self.retry_policy = retry_policy

    def get_gap_manifest(
        self,
        api_url: str,
        auth: UploadAuth,
        *,
        ref: Optional[str] = None,
        pull_request: Optional[int] = None,
        next_action: Optional[str] = None,
        min_risk_level: Optional[str] = None,
        limit: Optional[int] = None,
    ) -> Dict[str, Any]:
        params = {
            "ref": ref,
            "pull_request": pull_request,
            "next_action": next_action,
            "min_risk_level": min_risk_level,
            "limit": limit,
        }
        query = {key: value for key, value in params.items() if value is not None}
        url = urljoin(api_url.rstrip("/") + "/", "api/v1/coverage/gap-manifest")
        if query:
            url += "?" + urlencode(query)

        def operation() -> Dict[str, Any]:
            response = self._json_request(url, auth)
            data = response.get("data", response)
            return data if isinstance(data, dict) else {}

        return with_retries(operation, self.retry_policy)

    def _json_request(self, url: str, auth: UploadAuth) -> Dict[str, Any]:
        request = Request(
            url,
            method="GET",
            headers={
                "Accept": "application/json",
                "Authorization": f"Bearer {auth.api_key}",
                "User-Agent": f"vericov-coverage-upload/{__version__}",
            },
        )
        try:
            with urlopen(request, timeout=self.timeout_seconds) as response:
                raw = response.read().decode("utf-8")
                return json.loads(raw) if raw else {}
        except HTTPError as error:
            self._raise_http_error(error)

    def _raise_http_error(self, error: HTTPError) -> None:
        body = _safe_body(error)
        message, code = _error_message(body, error)
        if error.code in RETRYABLE_STATUS:
            raise RetryableHttpError(error.code, message, _retry_after(error.headers.get("Retry-After")))
        if error.code in {401, 403}:
            raise VericovCliError(code, message, ExitCode.AUTH)
        if error.code == 404:
            raise VericovCliError(code, message, ExitCode.API)
        if error.code in NON_RETRYABLE_STATUS:
            raise VericovCliError(code, message, ExitCode.API)
        raise VericovCliError(code, message, ExitCode.API)


def _safe_body(error: HTTPError) -> str:
    try:
        return error.read(4096).decode("utf-8", errors="replace")
    except Exception:
        return ""


def _error_message(body: str, error: HTTPError):
    try:
        parsed = json.loads(body)
    except json.JSONDecodeError:
        return f"Coverage query API returned HTTP {error.code}.", f"http_{error.code}"
    if isinstance(parsed, dict) and isinstance(parsed.get("error"), dict):
        error_body = parsed["error"]
        return str(error_body.get("message", f"HTTP {error.code}")), str(error_body.get("code", f"http_{error.code}"))
    return f"Coverage query API returned HTTP {error.code}.", f"http_{error.code}"


def _retry_after(value: Optional[str]) -> Optional[float]:
    if not value:
        return None
    try:
        return float(value)
    except ValueError:
        return None
