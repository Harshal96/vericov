"""Direct upload service URL gateway.

This is intentionally narrow so a future shared/generated API client can replace
this class without changing Typer commands or upload workflows.
"""

import json
from typing import Any, Dict, Optional, Protocol, Tuple
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from vericov_coverage_upload import __version__
from vericov_coverage_upload.domain.errors import ExitCode, VericovCliError
from vericov_coverage_upload.domain.upload_request import UploadAuth, UploadRequest
from vericov_coverage_upload.domain.upload_response import UploadAccepted, UploadStatus
from vericov_coverage_upload.infrastructure.http.retry import (
    NON_RETRYABLE_STATUS,
    RETRYABLE_STATUS,
    RetryPolicy,
    RetryableHttpError,
    with_retries,
)
from vericov_coverage_upload.infrastructure.http.urls import resolve_poll_url, uploads_url


class UploadGateway(Protocol):
    def create_upload(self, request: UploadRequest, auth: UploadAuth) -> UploadAccepted:
        ...

    def get_upload_status(self, api_url: str, poll_url: str, auth: UploadAuth) -> UploadStatus:
        ...


class DirectUrlUploadGateway:
    def __init__(self, timeout_seconds: int = 30, retry_policy: RetryPolicy = RetryPolicy()):
        self.timeout_seconds = timeout_seconds
        self.retry_policy = retry_policy

    def create_upload(self, request: UploadRequest, auth: UploadAuth) -> UploadAccepted:
        url = uploads_url(request.api_url)
        idempotency_key = request.resolved_idempotency_key()
        body = request.to_json()

        def operation() -> UploadAccepted:
            response = self._json_request(
                "POST",
                url,
                auth,
                idempotency_key=idempotency_key,
                body=body,
            )
            data = _data(response)
            return UploadAccepted(
                upload_id=str(data.get("upload_id", "")),
                status=str(data.get("status", "")),
                poll_url=str(data.get("poll_url", "")),
                repository_id=data.get("repository_id"),
                commit_sha=str(data.get("commit_sha", "")),
                analysis_job_id=data.get("analysis_job_id"),
            )

        return with_retries(operation, self.retry_policy)

    def get_upload_status(self, api_url: str, poll_url: str, auth: UploadAuth) -> UploadStatus:
        url = resolve_poll_url(api_url, poll_url)
        response = self._json_request("GET", url, auth)
        data = _data(response)
        return UploadStatus(
            upload_id=str(data.get("id") or data.get("upload_id") or ""),
            status=str(data.get("status") or ""),
            analysis_job_id=data.get("analysis_job_id"),
        )

    def _json_request(
        self,
        method: str,
        url: str,
        auth: UploadAuth,
        idempotency_key: Optional[str] = None,
        body: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        encoded = json.dumps(body).encode("utf-8") if body is not None else None
        headers = {
            "Accept": "application/json",
            "Authorization": f"Bearer {auth.api_key}",
            "User-Agent": f"vericov-coverage-upload/{__version__}",
        }
        if encoded is not None:
            headers["Content-Type"] = "application/json"
        if idempotency_key:
            headers["Idempotency-Key"] = idempotency_key

        request = Request(url, data=encoded, method=method, headers=headers)
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
        if error.code in NON_RETRYABLE_STATUS:
            raise VericovCliError(code, message, ExitCode.API)
        raise VericovCliError(code, message, ExitCode.API)


def _data(response: Dict[str, Any]) -> Dict[str, Any]:
    data = response.get("data", response)
    return data if isinstance(data, dict) else {}


def _safe_body(error: HTTPError) -> str:
    try:
        return error.read(4096).decode("utf-8", errors="replace")
    except Exception:
        return ""


def _error_message(body: str, error: HTTPError) -> Tuple[str, str]:
    try:
        parsed = json.loads(body)
    except json.JSONDecodeError:
        return f"Upload API returned HTTP {error.code}.", f"http_{error.code}"
    if isinstance(parsed, dict):
        if "error" in parsed and isinstance(parsed["error"], dict):
            error_body = parsed["error"]
            return str(error_body.get("message", f"HTTP {error.code}")), str(error_body.get("code", f"http_{error.code}"))
        if "message" in parsed:
            return str(parsed["message"]), str(parsed.get("code", f"http_{error.code}"))
    return f"Upload API returned HTTP {error.code}.", f"http_{error.code}"


def _retry_after(value: Optional[str]) -> Optional[float]:
    if not value:
        return None
    try:
        return float(value)
    except ValueError:
        return None
