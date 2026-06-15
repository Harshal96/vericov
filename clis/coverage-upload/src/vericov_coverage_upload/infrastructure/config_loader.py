"""YAML configuration loading and validation."""

import os
import re
from pathlib import Path
from typing import Any, Dict, Iterable, Mapping, Optional, Tuple
from urllib.parse import urlparse

import yaml

from vericov_coverage_upload.domain.config import (
    DEFAULT_API_URL,
    DEFAULT_MAX_ARTIFACT_BYTES,
    DEFAULT_MAX_TOTAL_BYTES,
    DEFAULT_TIMEOUT_SECONDS,
    DiscoveryConfig,
    ResolvedConfig,
    UploadConfig,
)
from vericov_coverage_upload.domain.coverage_ignore import (
    CoverageIgnoreRules,
    InvalidCoverageIgnoreRule,
)
from vericov_coverage_upload.domain.errors import ExitCode, VericovCliError

CANONICAL_CONFIG_NAME = ".vericov.yml"
LEGACY_CONFIG_NAME = "vericov.yml"
SECRET_KEY_RE = re.compile(r"(api[_-]?key|token|secret)", re.IGNORECASE)
UUID_RE = re.compile(
    r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
)

TOP_LEVEL_KEYS = {"version", "ignore", "api", "upload"}
API_KEYS = {"url"}
UPLOAD_KEYS = {
    "repository_id",
    "commit_sha",
    "branch",
    "pull_request_number",
    "ci_provider",
    "ci_build_id",
    "ci_build_url",
    "flags",
    "component",
    "package",
    "coverage",
    "test_results",
    "discover",
    "wait",
    "timeout_seconds",
    "max_artifact_bytes",
    "max_total_bytes",
}
DISCOVER_KEYS = {"enabled", "roots", "include", "exclude"}


def load_config(cwd: Path, explicit_path: Optional[str] = None) -> ResolvedConfig:
    path = resolve_config_path(cwd, explicit_path)
    if path is None:
        return ResolvedConfig(None, UploadConfig())
    try:
        raw = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    except yaml.YAMLError as error:
        raise _config_error(
            "invalid_yaml",
            f"Invalid config {path}: malformed YAML.",
        ) from error
    if not isinstance(raw, dict):
        raise _config_error(
            "config_type",
            f"Invalid config {path}: file must contain a YAML mapping.",
        )
    validate_no_secrets(raw)
    upload = parse_upload_config(raw, str(path))
    return ResolvedConfig(str(path), upload)


def resolve_config_path(cwd: Path, explicit_path: Optional[str] = None) -> Optional[Path]:
    if explicit_path:
        path = Path(explicit_path)
        if path.name != CANONICAL_CONFIG_NAME:
            raise _config_error(
                "invalid_config_filename",
                f"Config file must be named {CANONICAL_CONFIG_NAME}.",
            )
        resolved = path if path.is_absolute() else cwd / path
        if not resolved.exists():
            raise _config_error("config_not_found", f"Config file not found: {explicit_path}")
        return resolved.resolve()

    legacy = cwd / LEGACY_CONFIG_NAME
    if legacy.exists():
        raise _config_error(
            "legacy_config_filename",
            f"Found legacy config {legacy}. Rename it to {CANONICAL_CONFIG_NAME}.",
        )
    canonical = cwd / CANONICAL_CONFIG_NAME
    return canonical.resolve() if canonical.exists() else None


def parse_upload_config(raw: Mapping[str, Any], config_path: str = CANONICAL_CONFIG_NAME) -> UploadConfig:
    _reject_unknown(raw.keys(), TOP_LEVEL_KEYS, "config")
    version = raw.get("version", 1)
    if version != 1:
        raise _config_error("unsupported_version", "Only config version 1 is supported.")

    api = _mapping(raw.get("api", {}), "api")
    upload = _mapping(raw.get("upload", {}), "upload")
    _reject_unknown(api.keys(), API_KEYS, "api")
    _reject_unknown(upload.keys(), UPLOAD_KEYS, "upload")

    discover_raw = _mapping(upload.get("discover", {}), "upload.discover")
    _reject_unknown(discover_raw.keys(), DISCOVER_KEYS, "upload.discover")

    repository_id = _optional_string(upload, "repository_id")
    if repository_id and not UUID_RE.match(repository_id):
        raise _config_error("invalid_repository_id", "upload.repository_id must be a UUID.")

    api_url = _optional_string(api, "url") or DEFAULT_API_URL
    validate_api_url(api_url)

    return UploadConfig(
        api_url=api_url,
        repository_id=repository_id,
        commit_sha=_optional_string(upload, "commit_sha"),
        branch=_optional_string(upload, "branch"),
        pull_request_number=_optional_int(upload, "pull_request_number"),
        ci_provider=_optional_string(upload, "ci_provider"),
        ci_build_id=_optional_string(upload, "ci_build_id"),
        ci_build_url=_optional_string(upload, "ci_build_url"),
        flags=_string_tuple(upload.get("flags", ()), "upload.flags"),
        ignore=_ignore_tuple(raw.get("ignore"), config_path),
        component=_optional_string(upload, "component"),
        package=_optional_string(upload, "package"),
        coverage=_string_tuple(upload.get("coverage", ()), "upload.coverage"),
        test_results=_string_tuple(upload.get("test_results", ()), "upload.test_results"),
        discover=DiscoveryConfig(
            enabled=_optional_bool(discover_raw, "enabled"),
            roots=_string_tuple(discover_raw.get("roots", (".")), "upload.discover.roots"),
            include=_string_tuple(discover_raw.get("include", ()), "upload.discover.include"),
            exclude=_string_tuple(discover_raw.get("exclude", ()), "upload.discover.exclude"),
        ),
        wait=bool(upload.get("wait", False)),
        timeout_seconds=_positive_int(upload.get("timeout_seconds", DEFAULT_TIMEOUT_SECONDS), "upload.timeout_seconds"),
        max_artifact_bytes=_positive_int(
            upload.get("max_artifact_bytes", DEFAULT_MAX_ARTIFACT_BYTES),
            "upload.max_artifact_bytes",
        ),
        max_total_bytes=_positive_int(
            upload.get("max_total_bytes", DEFAULT_MAX_TOTAL_BYTES),
            "upload.max_total_bytes",
        ),
    )


def validate_no_secrets(value: Any, path: str = "config") -> None:
    if isinstance(value, Mapping):
        for key, child in value.items():
            child_path = f"{path}.{key}"
            if SECRET_KEY_RE.search(str(key)):
                raise _config_error(
                    "secret_in_config",
                    f"{child_path} looks like a secret. Use VERICOV_API_KEY or CI secret storage instead.",
                )
            validate_no_secrets(child, child_path)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            validate_no_secrets(child, f"{path}[{index}]")


def merge_environment(config: UploadConfig, env: Mapping[str, str]) -> UploadConfig:
    return UploadConfig(
        api_url=env.get("VERICOV_API_URL", config.api_url),
        repository_id=env.get("VERICOV_REPOSITORY_ID", config.repository_id),
        commit_sha=env.get("VERICOV_COMMIT_SHA", config.commit_sha),
        branch=env.get("VERICOV_BRANCH", config.branch),
        pull_request_number=config.pull_request_number,
        ci_provider=config.ci_provider,
        ci_build_id=config.ci_build_id,
        ci_build_url=config.ci_build_url,
        flags=config.flags,
        ignore=config.ignore,
        component=config.component,
        package=config.package,
        coverage=config.coverage,
        test_results=config.test_results,
        discover=config.discover,
        wait=config.wait,
        timeout_seconds=config.timeout_seconds,
        max_artifact_bytes=config.max_artifact_bytes,
        max_total_bytes=config.max_total_bytes,
    )


def validate_api_url(value: str) -> None:
    parsed = urlparse(value)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        raise _config_error("invalid_api_url", "api.url must be an HTTP or HTTPS URL.")
    if parsed.scheme == "http" and parsed.hostname not in {"localhost", "127.0.0.1", "::1"}:
        raise _config_error("insecure_api_url", "HTTP api.url is allowed only for localhost.")


def parse_size(value: str) -> int:
    normalized = value.strip().lower()
    units = (
        ("mib", 1024 * 1024),
        ("mb", 1000 * 1000),
        ("kib", 1024),
        ("kb", 1000),
    )
    for suffix, multiplier in units:
        if normalized.endswith(suffix):
            return _positive_int(int(float(normalized[: -len(suffix)]) * multiplier), "size")
    return _positive_int(int(normalized), "size")


def parse_duration(value: str) -> int:
    normalized = value.strip().lower()
    if normalized.endswith("s"):
        return _positive_int(int(normalized[:-1]), "duration")
    if normalized.endswith("m"):
        return _positive_int(int(normalized[:-1]) * 60, "duration")
    if normalized.endswith("h"):
        return _positive_int(int(normalized[:-1]) * 3600, "duration")
    return _positive_int(int(normalized), "duration")


def _mapping(value: Any, path: str) -> Mapping[str, Any]:
    if value is None:
        return {}
    if not isinstance(value, Mapping):
        raise _config_error("invalid_mapping", f"{path} must be a mapping.")
    return value


def _reject_unknown(keys: Iterable[str], allowed: set, path: str) -> None:
    unknown = sorted(set(keys) - allowed)
    if unknown:
        raise _config_error("unknown_config_key", f"Unknown {path} key: {unknown[0]}")


def _string_tuple(value: Any, path: str) -> Tuple[str, ...]:
    if value is None:
        return ()
    if isinstance(value, str):
        return (value,)
    if not isinstance(value, (list, tuple)):
        raise _config_error("invalid_list", f"{path} must be a string or list of strings.")
    result = []
    for item in value:
        if not isinstance(item, str) or not item.strip():
            raise _config_error("invalid_string", f"{path} entries must be non-empty strings.")
        result.append(item)
    return tuple(result)


def _ignore_tuple(value: Any, config_path: str) -> Tuple[str, ...]:
    if value is None:
        return ()
    if not isinstance(value, (list, tuple)):
        raise _config_error(
            "invalid_ignore_list",
            f"Invalid config {config_path}: ignore must be a list of strings.",
        )
    try:
        return CoverageIgnoreRules(tuple(value)).rules
    except InvalidCoverageIgnoreRule as error:
        raise _config_error(
            "invalid_ignore_rule",
            f"Invalid config {config_path}: {error}.",
        ) from error


def _optional_string(mapping: Mapping[str, Any], key: str) -> Optional[str]:
    value = mapping.get(key)
    if value is None:
        return None
    if not isinstance(value, str) or not value.strip():
        raise _config_error("invalid_string", f"{key} must be a non-empty string.")
    return value


def _optional_int(mapping: Mapping[str, Any], key: str) -> Optional[int]:
    value = mapping.get(key)
    if value is None:
        return None
    return _positive_int(value, key)


def _optional_bool(mapping: Mapping[str, Any], key: str) -> Optional[bool]:
    value = mapping.get(key)
    if value is None:
        return None
    if not isinstance(value, bool):
        raise _config_error("invalid_bool", f"{key} must be true or false.")
    return value


def _positive_int(value: Any, path: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise _config_error("invalid_positive_integer", f"{path} must be a positive integer.")
    return value


def _config_error(code: str, message: str) -> VericovCliError:
    return VericovCliError(code, message, ExitCode.USAGE)
