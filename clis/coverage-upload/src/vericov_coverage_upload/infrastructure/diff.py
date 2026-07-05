"""Pull request merge-base resolution and unified diff generation.

Vericov computes patch coverage from a diff supplied by the CI checkout, not
from a Git provider API. This module resolves the merge-base between the pull
request head and its target branch, and generates the unified diff between
them, using `git` as an argument-vector subprocess (never a shell).
"""

import subprocess
from pathlib import Path
from typing import Callable, Mapping, Optional, Sequence

CommandRunner = Callable[[Sequence[str], Path], str]

# (environment variable, ref template) pairs, checked in order.
_CI_BASE_REF_ENV_VARS = (
    ("GITHUB_BASE_REF", "origin/{value}"),
    ("CI_MERGE_REQUEST_TARGET_BRANCH_NAME", "origin/{value}"),
    ("BITBUCKET_PR_DESTINATION_BRANCH", "origin/{value}"),
)

_DEEPEN_FETCH_HINT = (
    "If this is a shallow clone, deepen the fetch (for example "
    "`fetch-depth: 0` on GitHub Actions) so the merge-base commit is present."
)


class DiffResolutionError(Exception):
    def __init__(self, message: str, hint: Optional[str] = None) -> None:
        super().__init__(message)
        self.message = message
        self.hint = hint

    def __str__(self) -> str:
        return f"{self.message} {self.hint}" if self.hint else self.message


def detect_ci_base_ref(env: Mapping[str, str]) -> Optional[str]:
    """Detects a CI target-branch environment variable, resolved to a ref."""
    for var, template in _CI_BASE_REF_ENV_VARS:
        value = env.get(var)
        if value:
            return template.format(value=value)
    return None


def resolve_merge_base(
    cwd: Path,
    base_ref: str,
    head_sha: str,
    runner: Optional[CommandRunner] = None,
) -> str:
    run = runner or _run
    try:
        output = run(("git", "merge-base", base_ref, head_sha), cwd)
    except Exception as error:  # noqa: BLE001 - surfaced as a typed CLI error
        raise DiffResolutionError(
            f"Failed to resolve merge-base of {head_sha} against {base_ref!r}: {error}",
            hint=_DEEPEN_FETCH_HINT,
        ) from error
    merge_base = output.strip()
    if not merge_base:
        raise DiffResolutionError(f"git merge-base returned no commit for {base_ref!r}")
    return merge_base


def generate_unified_diff(
    cwd: Path,
    base_sha: str,
    head_sha: str,
    runner: Optional[CommandRunner] = None,
) -> bytes:
    run = runner or _run
    try:
        output = run(
            (
                "git",
                "-c",
                "core.quotePath=false",
                "diff",
                "--no-color",
                "--no-ext-diff",
                "--find-renames",
                "--unified=0",
                base_sha,
                head_sha,
            ),
            cwd,
        )
    except Exception as error:  # noqa: BLE001 - surfaced as a typed CLI error
        raise DiffResolutionError(
            f"Failed to generate a diff between {base_sha} and {head_sha}: {error}"
        ) from error
    return output.encode("utf-8")


def parses_as_unified_diff(content: bytes) -> bool:
    return b"diff --git " in content


def _run(command: Sequence[str], cwd: Path) -> str:
    completed = subprocess.run(
        list(command),
        cwd=str(cwd),
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        timeout=30,
    )
    return completed.stdout
