import hashlib
import json
from pathlib import Path

import pytest

from vericov_coverage_upload.domain.component_config import (
    ConfigSnapshot,
    InvalidComponentConfig,
    parse_components,
)

CONTRACT_ROOT = Path(__file__).parents[3] / "test-contracts"


def valid_components():
    return [
        {
            "key": "commerce",
            "name": "Commerce",
            "owners": ["team-commerce"],
            "gates": {"line": 80, "branch": 70},
            "components": [
                {
                    "key": "payments",
                    "gates": {"line": 90},
                    "components": [
                        {
                            "key": "payments-api",
                            "owners": ["team-payments"],
                            "paths": ["services/payments/api/**"],
                        }
                    ],
                }
            ],
        }
    ]


def test_parses_nested_components_and_canonicalizes_snapshot() -> None:
    components = parse_components(valid_components())
    snapshot = ConfigSnapshot(1, ("generated/**",), components)

    assert components[0].name == "Commerce"
    assert components[0].components[0].name == "payments"
    assert components[0].components[0].owners is None
    assert components[0].components[0].gates_dict == {"line": 90}
    assert components[0].components[0].components[0].paths == (
        "services/payments/api/**",
    )
    assert snapshot.to_data()["components"][0]["owners"] == ["team-commerce"]
    assert snapshot.canonical_json == json.dumps(
        snapshot.to_data(),
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )
    assert snapshot.sha256 == hashlib.sha256(snapshot.canonical_json.encode()).hexdigest()


@pytest.mark.parametrize(
    ("components", "message"),
    [
        ([], "must contain at least one component"),
        ([{"key": "both", "paths": ["src/**"], "components": [{"key": "leaf", "paths": ["x/**"]}]}], "exactly one"),
        ([{"key": "neither"}], "exactly one"),
        ([{"key": "unassigned", "paths": ["src/**"]}], "reserved"),
        ([{"key": "Bad Key", "paths": ["src/**"]}], "invalid"),
        ([{"key": "same", "paths": ["src/**"]}, {"key": "same", "paths": ["other/**"]}], "duplicate component key"),
        ([{"key": "a", "paths": ["src/**"]}, {"key": "b", "paths": ["src/**"]}], "duplicate component path"),
        ([{"key": "a", "owners": [], "paths": ["src/**"]}], "owners"),
        ([{"key": "a", "gates": {"line": 101}, "paths": ["src/**"]}], "between 0 and 100"),
        ([{"key": "a", "paths": ["!src/**"]}], "negation"),
        ([{"key": "a", "paths": ["src/**"], "unknown": True}], "unknown"),
    ],
)
def test_rejects_invalid_component_structures(components, message: str) -> None:
    with pytest.raises(InvalidComponentConfig) as error:
        parse_components(components)

    assert message in str(error.value).lower()


@pytest.mark.parametrize("threshold", [float("nan"), float("inf"), float("-inf")])
def test_rejects_non_finite_gate_thresholds(threshold: float) -> None:
    with pytest.raises(InvalidComponentConfig, match="between 0 and 100"):
        parse_components([
            {
                "key": "api",
                "gates": {"line": threshold},
                "paths": ["services/api/**"],
            }
        ])


def test_snapshot_preserves_inheritance_markers_and_explicit_defaults() -> None:
    component = parse_components([{"key": "api", "paths": ["services/api/**"]}])[0]

    assert component.to_data() == {
        "components": [],
        "gates": {},
        "key": "api",
        "name": "api",
        "owners": None,
        "paths": ["services/api/**"],
    }


def test_snapshot_contract_fixture_matches_python_canonicalization() -> None:
    contract = json.loads(
        (CONTRACT_ROOT / "component-config-snapshots.json").read_text(encoding="utf-8")
    )

    for case in contract["cases"]:
        snapshot_data = case["snapshot"]
        snapshot = ConfigSnapshot.from_data(snapshot_data)
        assert snapshot.canonical_json == case["canonical"], case["name"]
        assert snapshot.sha256 == case["sha256"], case["name"]
