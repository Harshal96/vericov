from pathlib import Path

import pytest

from vericov_coverage_upload.domain.coverage_ignore import (
    CoveragePathPattern,
    InvalidCoverageIgnoreRule,
)


CONTRACT_ROOT = Path(__file__).parents[3] / "test-contracts"


def _contract_rows(name: str):
    for line in (CONTRACT_ROOT / name).read_text(encoding="utf-8").splitlines():
        if line and not line.startswith("#"):
            yield line.split("\t")


@pytest.mark.parametrize(
    ("name", "pattern", "path", "matches", "literal_segments", "literal_characters"),
    [
        (
            name,
            pattern,
            path,
            matches == "true",
            int(literal_segments),
            int(literal_characters),
        )
        for name, pattern, path, matches, literal_segments, literal_characters in _contract_rows(
            "component-path-matches.tsv"
        )
    ],
)
def test_component_path_contract(
    name: str,
    pattern: str,
    path: str,
    matches: bool,
    literal_segments: int,
    literal_characters: int,
) -> None:
    compiled = CoveragePathPattern(pattern)

    assert compiled.matches(path) is matches, name
    assert compiled.specificity == (literal_segments, literal_characters), name


@pytest.mark.parametrize("pattern", ["!src/**", "../src/**", "C:\\src\\**", "src/[z-a].py"])
def test_component_path_rejects_invalid_patterns(pattern: str) -> None:
    with pytest.raises(InvalidCoverageIgnoreRule):
        CoveragePathPattern(pattern)
