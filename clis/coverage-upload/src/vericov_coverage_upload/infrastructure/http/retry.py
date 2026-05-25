"""Retry policy for upload service calls."""

import random
import time
from dataclasses import dataclass
from typing import Callable, Iterable, Optional, TypeVar

from vericov_coverage_upload.domain.errors import ExitCode, VericovCliError

T = TypeVar("T")


RETRYABLE_STATUS = {408, 425, 429, 500, 502, 503, 504}
NON_RETRYABLE_STATUS = {400, 401, 403, 404, 409, 413, 422}


@dataclass(frozen=True)
class RetryPolicy:
    attempts: int = 4
    initial_backoff_seconds: float = 0.5
    max_backoff_seconds: float = 8.0


class RetryableHttpError(Exception):
    def __init__(self, status: int, message: str, retry_after_seconds: Optional[float] = None):
        super().__init__(message)
        self.status = status
        self.retry_after_seconds = retry_after_seconds


def with_retries(
    operation: Callable[[], T],
    policy: RetryPolicy = RetryPolicy(),
    sleeper: Callable[[float], None] = time.sleep,
    jitter: Callable[[], float] = lambda: random.uniform(0, 0.25),
) -> T:
    last_error: Optional[Exception] = None
    for attempt in range(1, policy.attempts + 1):
        try:
            return operation()
        except RetryableHttpError as error:
            last_error = error
            if attempt == policy.attempts:
                break
            delay = error.retry_after_seconds
            if delay is None:
                delay = min(policy.initial_backoff_seconds * (2 ** (attempt - 1)), policy.max_backoff_seconds)
                delay += jitter()
            sleeper(min(delay, 30.0))
        except OSError as error:
            last_error = error
            if attempt == policy.attempts:
                break
            delay = min(policy.initial_backoff_seconds * (2 ** (attempt - 1)), policy.max_backoff_seconds)
            sleeper(delay + jitter())
    message = str(last_error) if last_error else "Upload retry budget exhausted."
    raise VericovCliError("retry_exhausted", message, ExitCode.RETRY_EXHAUSTED)
