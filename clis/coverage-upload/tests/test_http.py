import io
import json
from urllib.error import HTTPError

import pytest

from vericov_coverage_upload.domain.errors import ExitCode, VericovCliError
from vericov_coverage_upload.domain.metadata import UploadMetadata
from vericov_coverage_upload.domain.upload_request import UploadAuth, UploadRequest
from vericov_coverage_upload.infrastructure.http.direct_url_upload_gateway import DirectUrlUploadGateway
from vericov_coverage_upload.infrastructure.http.retry import RetryPolicy, RetryableHttpError, with_retries
from vericov_coverage_upload.infrastructure.http.urls import resolve_poll_url, uploads_url


class FakeResponse:
    def __init__(self, payload: dict) -> None:
        self.payload = payload

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, traceback) -> None:
        return None

    def read(self) -> bytes:
        return json.dumps(self.payload).encode("utf-8")


def upload_request() -> UploadRequest:
    return UploadRequest(
        api_url="https://api.vericov.dev/base",
        repository_id=None,
        metadata=UploadMetadata(commit_sha="abc123", branch="main"),
        flags=("unit",),
        component=None,
        package=None,
        artifacts=(),
        idempotency_key="request-key",
    )


def http_error(status: int, body: str, headers=None) -> HTTPError:
    return HTTPError(
        "https://api.vericov.dev/api/v1/uploads",
        status,
        "error",
        headers or {},
        io.BytesIO(body.encode("utf-8")),
    )


def test_create_upload_sends_authenticated_json_request(monkeypatch) -> None:
    captured = {}

    def fake_urlopen(request, timeout):
        captured["request"] = request
        captured["timeout"] = timeout
        return FakeResponse(
            {
                "data": {
                    "upload_id": "upload-1",
                    "status": "accepted",
                    "poll_url": "/api/v1/uploads/upload-1",
                    "repository_id": "repo-1",
                    "commit_sha": "abc123",
                    "analysis_job_id": "job-1",
                }
            }
        )

    monkeypatch.setattr(
        "vericov_coverage_upload.infrastructure.http.direct_url_upload_gateway.urlopen",
        fake_urlopen,
    )

    accepted = DirectUrlUploadGateway(timeout_seconds=12).create_upload(
        upload_request(),
        UploadAuth("vc_live_test"),
    )

    request = captured["request"]
    assert request.full_url == "https://api.vericov.dev/api/v1/uploads"
    assert request.get_method() == "POST"
    assert request.get_header("Authorization") == "Bearer vc_live_test"
    assert request.get_header("Idempotency-key") == "request-key"
    assert json.loads(request.data)["commit_sha"] == "abc123"
    assert captured["timeout"] == 12
    assert accepted.upload_id == "upload-1"
    assert accepted.analysis_job_id == "job-1"


def test_create_upload_retries_retryable_http_error(monkeypatch) -> None:
    responses = iter(
        [
            http_error(503, '{"message": "try again"}', {"Retry-After": "0"}),
            FakeResponse(
                {
                    "upload_id": "upload-2",
                    "status": "accepted",
                    "poll_url": "/uploads/upload-2",
                    "commit_sha": "abc123",
                }
            ),
        ]
    )
    attempts = []

    def fake_urlopen(request, timeout):
        attempts.append(request.full_url)
        response = next(responses)
        if isinstance(response, Exception):
            raise response
        return response

    monkeypatch.setattr(
        "vericov_coverage_upload.infrastructure.http.direct_url_upload_gateway.urlopen",
        fake_urlopen,
    )

    accepted = DirectUrlUploadGateway(retry_policy=RetryPolicy(attempts=2)).create_upload(
        upload_request(),
        UploadAuth("vc_live_test"),
    )

    assert accepted.upload_id == "upload-2"
    assert len(attempts) == 2


def test_create_upload_maps_auth_error_from_api(monkeypatch) -> None:
    def fake_urlopen(request, timeout):
        raise http_error(
            401,
            '{"error": {"code": "invalid_api_key", "message": "API key rejected"}}',
        )

    monkeypatch.setattr(
        "vericov_coverage_upload.infrastructure.http.direct_url_upload_gateway.urlopen",
        fake_urlopen,
    )

    with pytest.raises(VericovCliError) as error:
        DirectUrlUploadGateway().create_upload(upload_request(), UploadAuth("bad-key"))

    assert error.value.code == "invalid_api_key"
    assert error.value.message == "API key rejected"
    assert error.value.exit_code == ExitCode.AUTH


def test_get_upload_status_resolves_relative_url_and_alternate_id(monkeypatch) -> None:
    captured = {}

    def fake_urlopen(request, timeout):
        captured["request"] = request
        return FakeResponse({"data": {"upload_id": "upload-3", "status": "completed"}})

    monkeypatch.setattr(
        "vericov_coverage_upload.infrastructure.http.direct_url_upload_gateway.urlopen",
        fake_urlopen,
    )

    status = DirectUrlUploadGateway().get_upload_status(
        "https://api.vericov.dev",
        "/api/v1/uploads/upload-3",
        UploadAuth("vc_live_test"),
    )

    assert captured["request"].get_method() == "GET"
    assert captured["request"].full_url == "https://api.vericov.dev/api/v1/uploads/upload-3"
    assert status.upload_id == "upload-3"
    assert status.status == "completed"


def test_with_retries_uses_backoff_and_returns_success() -> None:
    attempts = iter(
        [
            RetryableHttpError(503, "busy"),
            OSError("connection reset"),
            "ok",
        ]
    )
    sleeps = []

    def operation():
        result = next(attempts)
        if isinstance(result, Exception):
            raise result
        return result

    result = with_retries(
        operation,
        RetryPolicy(attempts=3, initial_backoff_seconds=1, max_backoff_seconds=4),
        sleeper=sleeps.append,
        jitter=lambda: 0.25,
    )

    assert result == "ok"
    assert sleeps == [1.25, 2.25]


def test_with_retries_honors_capped_retry_after() -> None:
    sleeps = []

    with pytest.raises(VericovCliError) as error:
        with_retries(
            lambda: (_ for _ in ()).throw(RetryableHttpError(429, "rate limited", 60)),
            RetryPolicy(attempts=2),
            sleeper=sleeps.append,
        )

    assert sleeps == [30.0]
    assert error.value.code == "retry_exhausted"
    assert error.value.exit_code == ExitCode.RETRY_EXHAUSTED


def test_upload_url_replaces_existing_base_path() -> None:
    assert uploads_url("https://api.vericov.dev/prefix/") == "https://api.vericov.dev/api/v1/uploads"


def test_poll_url_accepts_same_host_and_rejects_cross_host() -> None:
    assert (
        resolve_poll_url("https://api.vericov.dev/base", "/api/v1/uploads/1")
        == "https://api.vericov.dev/api/v1/uploads/1"
    )

    with pytest.raises(VericovCliError) as error:
        resolve_poll_url("https://api.vericov.dev", "https://evil.example/uploads/1")

    assert error.value.code == "cross_host_poll_url"
    assert error.value.exit_code == ExitCode.API
