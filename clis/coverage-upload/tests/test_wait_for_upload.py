from dataclasses import dataclass

import pytest

from vericov_coverage_upload.application.wait_for_upload import wait_for_upload
from vericov_coverage_upload.domain.errors import ExitCode, VericovCliError
from vericov_coverage_upload.domain.upload_request import UploadAuth
from vericov_coverage_upload.domain.upload_response import UploadAccepted, UploadStatus


@dataclass
class StatusGateway:
    statuses: list

    def get_upload_status(self, api_url, poll_url, auth):
        return self.statuses.pop(0)


def accepted_upload() -> UploadAccepted:
    return UploadAccepted(
        upload_id="upload-1",
        status="accepted",
        poll_url="/api/v1/uploads/upload-1",
        repository_id=None,
        commit_sha="abc123",
        analysis_job_id=None,
    )


def test_wait_returns_completed_status_after_pending_poll(monkeypatch) -> None:
    gateway = StatusGateway(
        [
            UploadStatus("upload-1", "processing"),
            UploadStatus("upload-1", "completed", "job-1"),
        ]
    )
    sleeps = []
    monkeypatch.setattr("vericov_coverage_upload.application.wait_for_upload.random.uniform", lambda start, end: 0)

    status = wait_for_upload(
        gateway,
        accepted_upload(),
        "https://api.vericov.dev",
        UploadAuth("vc_live_test"),
        timeout_seconds=10,
        poll_interval_seconds=2,
        monotonic=lambda: 0,
        sleeper=sleeps.append,
    )

    assert status.status == "completed"
    assert status.analysis_job_id == "job-1"
    assert sleeps == [2]


def test_wait_raises_processing_failure() -> None:
    gateway = StatusGateway([UploadStatus("upload-1", "failed")])

    with pytest.raises(VericovCliError) as error:
        wait_for_upload(
            gateway,
            accepted_upload(),
            "https://api.vericov.dev",
            UploadAuth("vc_live_test"),
            timeout_seconds=10,
            monotonic=lambda: 0,
        )

    assert error.value.code == "upload_processing_failed"
    assert error.value.exit_code == ExitCode.PROCESSING_FAILED


def test_wait_caps_sleep_to_remaining_time_then_times_out(monkeypatch) -> None:
    gateway = StatusGateway([UploadStatus("upload-1", "queued")])
    clock = iter([0, 0, 0.75, 2])
    sleeps = []
    monkeypatch.setattr("vericov_coverage_upload.application.wait_for_upload.random.uniform", lambda start, end: 0.2)

    with pytest.raises(VericovCliError) as error:
        wait_for_upload(
            gateway,
            accepted_upload(),
            "https://api.vericov.dev",
            UploadAuth("vc_live_test"),
            timeout_seconds=1,
            monotonic=lambda: next(clock),
            sleeper=sleeps.append,
        )

    assert sleeps == [0.25]
    assert error.value.code == "upload_wait_timeout"
    assert error.value.exit_code == ExitCode.WAIT_TIMEOUT
