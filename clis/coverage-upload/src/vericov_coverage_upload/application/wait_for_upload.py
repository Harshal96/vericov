"""Upload status polling."""

import random
import time
from typing import Callable

from vericov_coverage_upload.domain.errors import ExitCode, VericovCliError
from vericov_coverage_upload.domain.upload_request import UploadAuth
from vericov_coverage_upload.domain.upload_response import UploadAccepted, UploadStatus
from vericov_coverage_upload.infrastructure.http.direct_url_upload_gateway import UploadGateway

PENDING_STATUSES = {"accepted", "queued", "processing"}
SUCCESS_STATUS = "completed"
FAILURE_STATUS = "failed"


def wait_for_upload(
    gateway: UploadGateway,
    accepted: UploadAccepted,
    api_url: str,
    auth: UploadAuth,
    timeout_seconds: int,
    poll_interval_seconds: float = 2.0,
    monotonic: Callable[[], float] = time.monotonic,
    sleeper: Callable[[float], None] = time.sleep,
) -> UploadStatus:
    deadline = monotonic() + timeout_seconds
    interval = poll_interval_seconds
    while monotonic() < deadline:
        status = gateway.get_upload_status(api_url, accepted.poll_url, auth)
        if status.status == SUCCESS_STATUS:
            return status
        if status.status == FAILURE_STATUS:
            raise VericovCliError(
                "upload_processing_failed",
                f"Upload {accepted.upload_id} failed during processing.",
                ExitCode.PROCESSING_FAILED,
            )
        remaining = deadline - monotonic()
        sleeper(min(max(0.0, remaining), interval + random.uniform(0, 0.25)))
        interval = min(interval * 1.5, 10.0)
    raise VericovCliError(
        "upload_wait_timeout",
        f"Upload {accepted.upload_id} did not complete within {timeout_seconds} seconds.",
        ExitCode.WAIT_TIMEOUT,
    )
