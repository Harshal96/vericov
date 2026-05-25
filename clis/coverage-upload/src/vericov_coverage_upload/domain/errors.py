"""Typed errors and exit codes for the coverage upload CLI."""

from dataclasses import dataclass
from typing import Optional


class ExitCode:
    OK = 0
    USAGE = 1
    NO_ARTIFACTS = 2
    ARTIFACT_VALIDATION = 3
    AUTH = 4
    API = 5
    RETRY_EXHAUSTED = 6
    PROCESSING_FAILED = 7
    WAIT_TIMEOUT = 8
    UNEXPECTED = 9


@dataclass
class VericovCliError(Exception):
    code: str
    message: str
    exit_code: int = ExitCode.USAGE
    detail: Optional[str] = None

    def __str__(self) -> str:
        return self.message


def usage_error(code: str, message: str) -> VericovCliError:
    return VericovCliError(code, message, ExitCode.USAGE)
