"""Coverage source-file ignore rule validation and matching."""

import re
from dataclasses import dataclass
from typing import Iterable, Sequence, Tuple


_ALLOWED_ESCAPES = frozenset("!*?[]\\# /")
_WINDOWS_ABSOLUTE_RE = re.compile(r"^[A-Za-z]:[\\/]")
_URI_RE = re.compile(r"^[A-Za-z][A-Za-z0-9+.-]*://")


class InvalidCoverageIgnoreRule(ValueError):
    def __init__(self, code: str, index: int, message: str):
        super().__init__(message)
        self.code = code
        self.index = index


@dataclass(frozen=True)
class _CompiledRule:
    negated: bool
    pattern: re.Pattern[str]


@dataclass(frozen=True)
class CoveragePathPattern:
    value: str

    def __post_init__(self) -> None:
        compiled = _compile_rule(self.value, 0, allow_negation=False, field="paths")
        object.__setattr__(self, "_compiled", compiled.pattern)
        object.__setattr__(self, "specificity", _specificity(self.value))

    def matches(self, path: str) -> bool:
        return self._compiled.fullmatch(normalize_repository_path(path)) is not None


class CoverageIgnoreRules:
    def __init__(self, rules: Sequence[str] = ()):
        self.rules: Tuple[str, ...] = tuple(rules)
        self._compiled = tuple(
            _compile_rule(rule, index, allow_negation=True, field="ignore")
            for index, rule in enumerate(self.rules)
        )

    def is_ignored(self, path: str) -> bool:
        normalized = normalize_repository_path(path)
        ignored = False
        for rule in self._compiled:
            if rule.pattern.fullmatch(normalized):
                ignored = not rule.negated
        return ignored


def validate_coverage_ignore_rules(rules: Iterable[str]) -> Tuple[str, ...]:
    matcher = CoverageIgnoreRules(tuple(rules))
    return matcher.rules


def normalize_repository_path(path: str) -> str:
    normalized = (path or "").strip().replace("\\", "/")
    while normalized.startswith("./"):
        normalized = normalized[2:]
    return normalized


def _compile_rule(
    rule: str,
    index: int,
    *,
    allow_negation: bool,
    field: str,
) -> _CompiledRule:
    if not isinstance(rule, str) or not rule.strip():
        raise _invalid("empty", index, "must be a non-empty string", field)

    negated = rule.startswith("!")
    if negated and not allow_negation:
        raise _invalid(
            "negation_not_allowed",
            index,
            "must not use negation",
            field,
        )
    pattern = rule[1:] if negated else rule
    if not pattern:
        raise _invalid("bare_negation", index, "bare ! is not a valid rule", field)
    if _is_absolute_filesystem_path(pattern):
        raise _invalid(
            "absolute_path",
            index,
            "must describe a repository-relative path",
            field,
        )

    anchored = pattern.startswith("/")
    if anchored:
        pattern = pattern[1:]

    directory_only = pattern.endswith("/")
    if directory_only:
        pattern = pattern[:-1]
    if not pattern:
        raise _invalid("empty", index, "must contain a path pattern", field)

    if any(segment == ".." for segment in pattern.split("/")):
        raise _invalid(
            "parent_traversal",
            index,
            "must not contain parent traversal",
            field,
        )

    translated = _translate_glob(pattern, index, field)
    prefix = "^" if anchored else r"^(?:.*/)?"
    suffix = r"/.*$" if directory_only else r"(?:/.*)?$"
    try:
        compiled = re.compile(prefix + translated + suffix)
    except re.error as error:
        raise _invalid(
            "malformed_range",
            index,
            "contains a malformed character range",
            field,
        ) from error
    return _CompiledRule(negated, compiled)


def _translate_glob(pattern: str, index: int, field: str) -> str:
    translated = []
    position = 0
    while position < len(pattern):
        character = pattern[position]
        if character == "\\":
            if position + 1 >= len(pattern):
                raise _invalid(
                    "invalid_escape",
                    index,
                    "ends with an invalid escape",
                    field,
                )
            escaped = pattern[position + 1]
            if escaped not in _ALLOWED_ESCAPES:
                raise _invalid(
                    "invalid_escape",
                    index,
                    "contains an invalid escape",
                    field,
                )
            translated.append(re.escape(escaped))
            position += 2
            continue
        if character == "*":
            star_end = position + 1
            while star_end < len(pattern) and pattern[star_end] == "*":
                star_end += 1
            is_globstar = (
                star_end - position >= 2
                and (position == 0 or pattern[position - 1] == "/")
                and (star_end == len(pattern) or pattern[star_end] == "/")
            )
            if is_globstar:
                if star_end < len(pattern) and pattern[star_end] == "/":
                    translated.append(r"(?:.*/)?")
                    position = star_end + 1
                else:
                    translated.append(r".*")
                    position = star_end
                continue
            translated.extend(r"[^/]*" for _ in range(star_end - position))
            position = star_end
            continue
        if character == "?":
            translated.append(r"[^/]")
            position += 1
            continue
        if character == "[":
            character_class, position = _translate_character_class(
                pattern,
                position,
                index,
                field,
            )
            translated.append(character_class)
            continue
        translated.append(re.escape(character))
        position += 1
    return "".join(translated)


def _translate_character_class(
    pattern: str,
    start: int,
    index: int,
    field: str,
) -> tuple[str, int]:
    end = pattern.find("]", start + 1)
    if end < 0:
        raise _invalid(
            "malformed_range",
            index,
            "contains an unclosed character range",
            field,
        )
    content = pattern[start + 1 : end]
    negated = content.startswith(("!", "^"))
    body = content[1:] if negated else content
    if not body:
        raise _invalid(
            "malformed_range",
            index,
            "contains an empty character range",
            field,
        )
    _validate_ranges(body, index, field)
    escaped_body = body.replace("\\", r"\\").replace("]", r"\]")
    if escaped_body.startswith("^"):
        escaped_body = "\\" + escaped_body
    prefix = "^" if negated else ""
    return "[" + prefix + escaped_body + "]", end + 1


def _validate_ranges(body: str, index: int, field: str) -> None:
    for position in range(1, len(body) - 1):
        if body[position] == "-" and ord(body[position - 1]) > ord(body[position + 1]):
            raise _invalid(
                "malformed_range",
                index,
                "contains a descending character range",
                field,
            )


def _is_absolute_filesystem_path(pattern: str) -> bool:
    return (
        pattern.startswith(("\\\\", "//"))
        or _WINDOWS_ABSOLUTE_RE.match(pattern) is not None
        or _URI_RE.match(pattern) is not None
    )


def _specificity(pattern: str) -> tuple[int, int]:
    glob = pattern[1:] if pattern.startswith("/") else pattern
    literal = []
    wildcard_found = False
    position = 0
    while position < len(glob):
        character = glob[position]
        if character == "\\" and position + 1 < len(glob):
            literal.append(glob[position + 1])
            position += 2
            continue
        if character in "*?[":
            wildcard_found = True
            break
        literal.append(character)
        position += 1
    prefix = "".join(literal)
    if wildcard_found:
        literal_segments = prefix.count("/")
    else:
        literal_segments = len([segment for segment in prefix.split("/") if segment])
    return literal_segments, len(prefix)


def _invalid(
    code: str,
    index: int,
    detail: str,
    field: str,
) -> InvalidCoverageIgnoreRule:
    return InvalidCoverageIgnoreRule(code, index, f"{field}[{index}] {detail}")
