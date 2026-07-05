"""Thin HTTP client for Vericov's read-only coverage query API.

No database access, no git access, no LLM calls. Every method issues one GET
request against `/api/v1/coverage/*` and returns the parsed JSON `data`
envelope, or raises `CoverageApiError` with the endpoint's structured error.
"""

import json
from typing import Any, Dict, Optional
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from vericov_mcp import __version__
from vericov_mcp.config import ServerConfig

DEFAULT_TIMEOUT_SECONDS = 30


class CoverageApiError(Exception):
    def __init__(self, code: str, message: str, status: Optional[int] = None):
        super().__init__(message)
        self.code = code
        self.message = message
        self.status = status


class CoverageApiClient:
    def __init__(self, config: ServerConfig, timeout_seconds: int = DEFAULT_TIMEOUT_SECONDS):
        self.config = config
        self.timeout_seconds = timeout_seconds

    def get_summary(self, ref: Optional[str] = None) -> Dict[str, Any]:
        return self._get("/api/v1/coverage/summary", {"ref": ref})

    def get_components(self, ref: Optional[str] = None) -> Dict[str, Any]:
        return self._get("/api/v1/coverage/components", {"ref": ref})

    def list_files(
        self,
        ref: Optional[str] = None,
        path_prefix: Optional[str] = None,
        component: Optional[str] = None,
        sort: Optional[str] = None,
        limit: Optional[int] = None,
        cursor: Optional[str] = None,
    ) -> Dict[str, Any]:
        return self._get(
            "/api/v1/coverage/files",
            {
                "ref": ref,
                "path_prefix": path_prefix,
                "component": component,
                "sort": sort,
                "limit": limit,
                "cursor": cursor,
            },
        )

    def get_file(self, path: str, ref: Optional[str] = None) -> Dict[str, Any]:
        return self._get("/api/v1/coverage/file", {"ref": ref, "path": path})

    def get_pull_request_patch(self, number: int) -> Dict[str, Any]:
        return self._get(f"/api/v1/coverage/pull-requests/{number}", {})

    def list_gaps(
        self,
        ref: Optional[str] = None,
        component: Optional[str] = None,
        min_risk_level: Optional[str] = None,
        status: Optional[str] = None,
        limit: Optional[int] = None,
        cursor: Optional[str] = None,
    ) -> Dict[str, Any]:
        return self._get(
            "/api/v1/coverage/gaps",
            {
                "ref": ref,
                "component": component,
                "min_risk_level": min_risk_level,
                "status": status,
                "limit": limit,
                "cursor": cursor,
            },
        )

    def get_gates(self, ref: Optional[str] = None) -> Dict[str, Any]:
        return self._get("/api/v1/coverage/gates", {"ref": ref})

    def get_gap_manifest(
        self,
        ref: Optional[str] = None,
        pull_request: Optional[int] = None,
        next_action: Optional[str] = None,
        min_risk_level: Optional[str] = None,
        limit: Optional[int] = None,
    ) -> Dict[str, Any]:
        return self._get(
            "/api/v1/coverage/gap-manifest",
            {
                "ref": ref,
                "pull_request": pull_request,
                "next_action": next_action,
                "min_risk_level": min_risk_level,
                "limit": limit,
            },
        )

    def _get(self, path: str, params: Dict[str, Any]) -> Dict[str, Any]:
        query = {key: value for key, value in params.items() if value is not None}
        url = self.config.api_url + path
        if query:
            url += "?" + urlencode(query)
        request = Request(
            url,
            method="GET",
            headers={
                "Accept": "application/json",
                "Authorization": f"Bearer {self.config.api_key}",
                "User-Agent": f"vericov-mcp/{__version__}",
            },
        )
        try:
            with urlopen(request, timeout=self.timeout_seconds) as response:
                raw = response.read().decode("utf-8")
                body = json.loads(raw) if raw else {}
                return body.get("data", body)
        except HTTPError as error:
            raise self._api_error(error) from error
        except URLError as error:
            raise CoverageApiError("connection_failed", f"Failed to reach {self.config.api_url}: {error.reason}")

    @staticmethod
    def _api_error(error: HTTPError) -> CoverageApiError:
        try:
            body = json.loads(error.read().decode("utf-8", errors="replace"))
        except (json.JSONDecodeError, UnicodeDecodeError):
            body = {}
        error_body = body.get("error", {}) if isinstance(body, dict) else {}
        code = error_body.get("code", f"http_{error.code}")
        message = error_body.get("message", f"Vericov API returned HTTP {error.code}")
        details = error_body.get("details") or []
        if details:
            suggestions = ", ".join(str(detail.get("message")) for detail in details if detail.get("message"))
            if suggestions:
                message = f"{message} (did you mean: {suggestions})"
        return CoverageApiError(code, message, error.code)
