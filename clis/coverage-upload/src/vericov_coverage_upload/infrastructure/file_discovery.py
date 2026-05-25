"""Coverage and test-result artifact discovery."""

import fnmatch
import glob
from pathlib import Path
from typing import Iterable, List, Optional, Sequence, Tuple

from vericov_coverage_upload.domain.artifacts import ArtifactCandidate
from vericov_coverage_upload.domain.config import DiscoveryConfig
from vericov_coverage_upload.domain.errors import ExitCode, VericovCliError

DEFAULT_COVERAGE_PATTERNS = (
    "coverage/lcov.info",
    "coverage/**/*.info",
    "coverage/**/*.lcov",
    "coverage.xml",
    "coverage/**/*.xml",
    "**/jacoco.xml",
    "**/jacocoTestReport.xml",
    "**/site/jacoco/*.xml",
    "**/cobertura-coverage.xml",
    "**/clover.xml",
    "**/coverage.out",
    "**/cover.out",
    "**/*.gcov",
)
DEFAULT_TEST_RESULT_PATTERNS = (
    "junit.xml",
    "test-results/**/*.xml",
    "build/test-results/**/*.xml",
    "target/surefire-reports/*.xml",
    "target/failsafe-reports/*.xml",
)
DEFAULT_EXCLUDES = (
    ".git/**",
    ".hg/**",
    ".svn/**",
    ".tox/**",
    ".nox/**",
    ".venv/**",
    "venv/**",
    "node_modules/**",
    "vendor/**",
    "dist/**",
    "build/tmp/**",
    "tmp/**",
    ".pytest_cache/**",
    ".mypy_cache/**",
    ".ruff_cache/**",
    "__pycache__/**",
)


def collect_candidates(
    project_root: Path,
    coverage_patterns: Sequence[str],
    test_result_patterns: Sequence[str],
    discovery: DiscoveryConfig,
    discover_override: Optional[bool] = None,
) -> Tuple[ArtifactCandidate, ...]:
    explicit = bool(coverage_patterns or test_result_patterns)
    discover_enabled = _discover_enabled(explicit, discovery.enabled, discover_override)
    candidates: List[ArtifactCandidate] = []

    candidates.extend(_expand_patterns(project_root, coverage_patterns, "coverage", ()))
    candidates.extend(_expand_patterns(project_root, test_result_patterns, "test_results", ()))

    if discover_enabled:
        coverage = discovery.include or DEFAULT_COVERAGE_PATTERNS
        roots = discovery.roots or (".",)
        excludes = DEFAULT_EXCLUDES + tuple(discovery.exclude)
        for root in roots:
            scoped_root = (project_root / root).resolve()
            candidates.extend(_expand_patterns(scoped_root, coverage, "coverage", excludes, project_root))
            candidates.extend(
                _expand_patterns(scoped_root, DEFAULT_TEST_RESULT_PATTERNS, "test_results", excludes, project_root)
            )

    if not candidates:
        raise VericovCliError(
            "no_artifacts",
            "No coverage or test-result artifacts were found.",
            ExitCode.NO_ARTIFACTS,
        )
    unique = {}
    for candidate in candidates:
        unique[candidate.path.resolve()] = candidate
    return tuple(unique[path] for path in sorted(unique))


def _discover_enabled(explicit: bool, configured: Optional[bool], override: Optional[bool]) -> bool:
    if override is not None:
        return override
    if configured is not None:
        return configured
    return not explicit


def _expand_patterns(
    root: Path,
    patterns: Sequence[str],
    kind: str,
    excludes: Iterable[str],
    project_root: Optional[Path] = None,
) -> List[ArtifactCandidate]:
    base = project_root or root
    results: List[ArtifactCandidate] = []
    for pattern in patterns:
        for match in glob.glob(str(root / pattern), recursive=True):
            path = Path(match)
            if _excluded(path, base, excludes):
                continue
            if path.is_file():
                results.append(ArtifactCandidate(path.resolve(), kind))
    return results


def _excluded(path: Path, project_root: Path, excludes: Iterable[str]) -> bool:
    try:
        relative = path.resolve().relative_to(project_root.resolve())
    except ValueError:
        return False
    normalized = relative.as_posix()
    return any(fnmatch.fnmatch(normalized, pattern) for pattern in excludes)
