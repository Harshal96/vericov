"""Git metadata fallback."""

import subprocess
from pathlib import Path
from typing import Callable, Optional, Sequence

from vericov_coverage_upload.domain.metadata import UploadMetadata

CommandRunner = Callable[[Sequence[str], Path], str]


def resolve_git_metadata(cwd: Path, runner: Optional[CommandRunner] = None) -> Optional[UploadMetadata]:
    run = runner or _run
    try:
        commit = run(("git", "rev-parse", "HEAD"), cwd).strip()
        branch = run(("git", "branch", "--show-current"), cwd).strip()
    except Exception:
        return None
    if not commit or not branch:
        return None
    return UploadMetadata(commit_sha=commit, branch=branch)


def git_root(cwd: Path, runner: Optional[CommandRunner] = None) -> Path:
    run = runner or _run
    try:
        root = run(("git", "rev-parse", "--show-toplevel"), cwd).strip()
    except Exception:
        return cwd.resolve()
    return Path(root).resolve() if root else cwd.resolve()


def _run(command: Sequence[str], cwd: Path) -> str:
    completed = subprocess.run(
        list(command),
        cwd=str(cwd),
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        timeout=5,
    )
    return completed.stdout
