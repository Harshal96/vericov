"""Immutable hierarchical component configuration."""

from __future__ import annotations

import hashlib
import json
import math
import re
from dataclasses import dataclass
from typing import Any, Mapping, Optional, Sequence, Tuple

from vericov_coverage_upload.domain.coverage_ignore import (
    CoverageIgnoreRules,
    CoveragePathPattern,
    InvalidCoverageIgnoreRule,
)


MAX_SNAPSHOT_BYTES = 256 * 1024
MAX_COMPONENTS = 1000
MAX_DEPTH = 20
MAX_PATHS_PER_LEAF = 100
MAX_PATTERN_LENGTH = 1024
MAX_OWNER_LENGTH = 200
KEY_RE = re.compile(r"^[a-z0-9][a-z0-9._-]{0,119}$")
GATE_METRICS = frozenset({"line", "branch", "function", "statement"})
COMPONENT_KEYS = frozenset({"key", "name", "owners", "gates", "paths", "components"})


class InvalidComponentConfig(ValueError):
    pass


@dataclass(frozen=True)
class ComponentDefinition:
    key: str
    name: str
    owners: Optional[Tuple[str, ...]]
    gates: Tuple[Tuple[str, int | float], ...]
    paths: Tuple[str, ...]
    components: Tuple["ComponentDefinition", ...]

    @property
    def gates_dict(self) -> dict[str, int | float]:
        return dict(self.gates)

    def to_data(self) -> dict[str, Any]:
        return {
            "components": [child.to_data() for child in self.components],
            "gates": self.gates_dict,
            "key": self.key,
            "name": self.name,
            "owners": None if self.owners is None else list(self.owners),
            "paths": list(self.paths),
        }


@dataclass(frozen=True)
class ConfigSnapshot:
    version: int = 1
    ignore: Tuple[str, ...] = ()
    components: Tuple[ComponentDefinition, ...] = ()

    def __post_init__(self) -> None:
        if self.version != 1:
            raise InvalidComponentConfig("only config version 1 is supported")
        object.__setattr__(self, "ignore", CoverageIgnoreRules(self.ignore).rules)
        object.__setattr__(self, "components", tuple(self.components))
        if len(self.canonical_json.encode("utf-8")) > MAX_SNAPSHOT_BYTES:
            raise InvalidComponentConfig("canonical config snapshot exceeds 256 KiB")

    def to_data(self) -> dict[str, Any]:
        return {
            "components": [component.to_data() for component in self.components],
            "ignore": list(self.ignore),
            "version": self.version,
        }

    @classmethod
    def from_data(cls, value: Any) -> "ConfigSnapshot":
        if not isinstance(value, Mapping):
            raise InvalidComponentConfig("config snapshot must be a mapping")
        if set(value) != {"version", "ignore", "components"}:
            raise InvalidComponentConfig(
                "config snapshot must contain only version, ignore, and components"
            )
        ignore = value["ignore"]
        if not isinstance(ignore, (list, tuple)):
            raise InvalidComponentConfig("ignore must be a list")
        return cls(
            value["version"],
            tuple(ignore),
            _parse_components(value["components"], normalized=True),
        )

    @property
    def canonical_json(self) -> str:
        return json.dumps(
            self.to_data(),
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
        )

    @property
    def sha256(self) -> str:
        return hashlib.sha256(self.canonical_json.encode("utf-8")).hexdigest()


def parse_components(value: Any) -> Tuple[ComponentDefinition, ...]:
    return _parse_components(value, normalized=False)


def _parse_components(
    value: Any,
    *,
    normalized: bool,
) -> Tuple[ComponentDefinition, ...]:
    if not isinstance(value, (list, tuple)):
        raise InvalidComponentConfig("components must be a list")
    if not value and not normalized:
        raise InvalidComponentConfig("components must contain at least one component")
    state = _ParseState()
    components = tuple(
        _parse_component(item, f"components[{index}]", 0, state, normalized)
        for index, item in enumerate(value)
    )
    return components


class _ParseState:
    def __init__(self) -> None:
        self.keys: set[str] = set()
        self.patterns: dict[str, str] = {}
        self.count = 0


def _parse_component(
    value: Any,
    path: str,
    depth: int,
    state: _ParseState,
    normalized: bool,
) -> ComponentDefinition:
    if depth >= MAX_DEPTH:
        raise InvalidComponentConfig(f"{path} exceeds maximum component depth {MAX_DEPTH}")
    if not isinstance(value, Mapping):
        raise InvalidComponentConfig(f"{path} must be a mapping")
    unknown = sorted(set(value) - COMPONENT_KEYS)
    if unknown:
        raise InvalidComponentConfig(f"{path} contains unknown field {unknown[0]}")

    state.count += 1
    if state.count > MAX_COMPONENTS:
        raise InvalidComponentConfig(f"components exceed maximum count {MAX_COMPONENTS}")

    key = value.get("key")
    if not isinstance(key, str) or not KEY_RE.fullmatch(key):
        raise InvalidComponentConfig(f"{path}.key is invalid")
    if key == "unassigned":
        raise InvalidComponentConfig(f"{path}.key uses reserved key unassigned")
    if key in state.keys:
        raise InvalidComponentConfig(f"{path}.key is a duplicate component key: {key}")
    state.keys.add(key)

    name = value.get("name", key)
    if not isinstance(name, str) or not name.strip():
        raise InvalidComponentConfig(f"{path}.name must be a non-empty string")

    owners = _parse_owners(value.get("owners"), f"{path}.owners")
    gates = _parse_gates(value.get("gates", {}), f"{path}.gates")

    paths_value = value.get("paths")
    children_value = value.get("components")
    has_paths = isinstance(paths_value, (list, tuple)) and bool(paths_value)
    has_children = isinstance(children_value, (list, tuple)) and bool(children_value)
    if normalized:
        if "paths" not in value or "components" not in value:
            raise InvalidComponentConfig(
                f"{path} normalized form requires paths and components arrays"
            )
    elif ("paths" in value) == ("components" in value):
        raise InvalidComponentConfig(
            f"{path} must define exactly one of non-empty paths or components"
        )
    if has_paths == has_children:
        raise InvalidComponentConfig(
            f"{path} must define exactly one of non-empty paths or components"
        )

    if has_paths:
        paths = _parse_paths(paths_value, f"{path}.paths", key, state)
        children: Tuple[ComponentDefinition, ...] = ()
    else:
        paths = ()
        children = tuple(
            _parse_component(
                child,
                f"{path}.components[{index}]",
                depth + 1,
                state,
                normalized,
            )
            for index, child in enumerate(children_value)
        )

    return ComponentDefinition(
        key=key,
        name=name,
        owners=owners,
        gates=tuple(sorted(gates.items())),
        paths=paths,
        components=children,
    )


def _parse_owners(value: Any, path: str) -> Optional[Tuple[str, ...]]:
    if value is None:
        return None
    if not isinstance(value, (list, tuple)) or not value:
        raise InvalidComponentConfig(f"{path} must be a non-empty list")
    owners = []
    seen = set()
    for index, owner in enumerate(value):
        if not isinstance(owner, str) or not owner.strip():
            raise InvalidComponentConfig(f"{path}[{index}] must be a non-empty string")
        if len(owner) > MAX_OWNER_LENGTH:
            raise InvalidComponentConfig(f"{path}[{index}] exceeds {MAX_OWNER_LENGTH} characters")
        if owner in seen:
            raise InvalidComponentConfig(f"{path}[{index}] duplicates owner {owner}")
        seen.add(owner)
        owners.append(owner)
    return tuple(owners)


def _parse_gates(value: Any, path: str) -> dict[str, int | float]:
    if not isinstance(value, Mapping):
        raise InvalidComponentConfig(f"{path} must be a mapping")
    unknown = sorted(set(value) - GATE_METRICS)
    if unknown:
        raise InvalidComponentConfig(f"{path} contains unsupported metric {unknown[0]}")
    gates: dict[str, int | float] = {}
    for metric, threshold in value.items():
        if isinstance(threshold, bool) or not isinstance(threshold, (int, float)):
            raise InvalidComponentConfig(f"{path}.{metric} must be numeric")
        if not math.isfinite(threshold) or threshold < 0 or threshold > 100:
            raise InvalidComponentConfig(f"{path}.{metric} must be between 0 and 100")
        gates[metric] = int(threshold) if float(threshold).is_integer() else threshold
    return gates


def _parse_paths(
    value: Any,
    path: str,
    component_key: str,
    state: _ParseState,
) -> Tuple[str, ...]:
    if not isinstance(value, (list, tuple)) or not value:
        raise InvalidComponentConfig(f"{path} must be a non-empty list")
    if len(value) > MAX_PATHS_PER_LEAF:
        raise InvalidComponentConfig(
            f"{path} exceeds maximum path count {MAX_PATHS_PER_LEAF}"
        )
    patterns = []
    local = set()
    for index, pattern in enumerate(value):
        if not isinstance(pattern, str) or not pattern.strip():
            raise InvalidComponentConfig(f"{path}[{index}] must be a non-empty string")
        if len(pattern) > MAX_PATTERN_LENGTH:
            raise InvalidComponentConfig(
                f"{path}[{index}] exceeds {MAX_PATTERN_LENGTH} characters"
            )
        try:
            CoveragePathPattern(pattern)
        except InvalidCoverageIgnoreRule as error:
            detail = str(error).replace("paths[0]", f"{path}[{index}]")
            raise InvalidComponentConfig(detail) from error
        normalized = pattern
        if normalized in local:
            raise InvalidComponentConfig(f"{path}[{index}] is a duplicate component path")
        if normalized in state.patterns:
            raise InvalidComponentConfig(
                f"{path}[{index}] is a duplicate component path from {state.patterns[normalized]}"
            )
        local.add(normalized)
        state.patterns[normalized] = component_key
        patterns.append(pattern)
    return tuple(patterns)
