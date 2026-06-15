from pathlib import Path

import pytest

from vericov_coverage_upload.domain.coverage_ignore import (
    CoverageIgnoreRules,
    InvalidCoverageIgnoreRule,
)


CONTRACT_ROOT = Path(__file__).parents[3] / "test-contracts"


def _contract_rows(name: str):
    for line in (CONTRACT_ROOT / name).read_text(encoding="utf-8").splitlines():
        if line and not line.startswith("#"):
            yield line.split("\t")


@pytest.mark.parametrize(
    ("name", "rules", "path", "ignored"),
    [
        (
            name,
            tuple(filter(None, encoded_rules.split(";;"))),
            path,
            ignored == "true",
        )
        for name, encoded_rules, path, ignored in _contract_rows(
            "coverage-ignore-matches.tsv"
        )
    ],
)
def test_shared_match_contract(
    name: str,
    rules: tuple[str, ...],
    path: str,
    ignored: bool,
) -> None:
    matcher = CoverageIgnoreRules(rules)

    assert matcher.is_ignored(path) is ignored, name


@pytest.mark.parametrize(
    ("name", "rule", "error_code"),
    [
        (
            name,
            "" if encoded_rule == "<empty>" else "   " if encoded_rule == "<spaces>" else encoded_rule,
            error_code,
        )
        for name, encoded_rule, error_code in _contract_rows(
            "coverage-ignore-invalid.tsv"
        )
    ],
)
def test_shared_invalid_rule_contract(
    name: str,
    rule: str,
    error_code: str,
) -> None:
    with pytest.raises(InvalidCoverageIgnoreRule) as error:
        CoverageIgnoreRules((rule,))

    assert error.value.code == error_code, name
    assert error.value.index == 0


def test_rules_are_immutable_and_preserve_order() -> None:
    source = ["vendor/**", "!vendor/maintained/**"]

    matcher = CoverageIgnoreRules(source)
    source.reverse()

    assert matcher.rules == ("vendor/**", "!vendor/maintained/**")
