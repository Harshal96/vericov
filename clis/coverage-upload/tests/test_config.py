from pathlib import Path

import pytest

from vericov_coverage_upload.domain.errors import VericovCliError
from vericov_coverage_upload.infrastructure.config_loader import load_config


def test_loads_no_config_with_defaults(tmp_path: Path) -> None:
    resolved = load_config(tmp_path)

    assert resolved.path is None
    assert resolved.upload.api_url == "https://api.vericov.dev"
    assert resolved.upload.ignore == ()
    assert resolved.upload.components == ()


def test_rejects_legacy_config_name_with_rename_instruction(tmp_path: Path) -> None:
    (tmp_path / "vericov.yml").write_text("version: 1\n", encoding="utf-8")

    with pytest.raises(VericovCliError) as error:
        load_config(tmp_path)

    assert error.value.code == "legacy_config_filename"
    assert "rename" in error.value.message.lower()
    assert ".vericov.yml" in error.value.message


def test_rejects_both_config_names_with_rename_instruction(tmp_path: Path) -> None:
    (tmp_path / "vericov.yml").write_text("version: 1\n", encoding="utf-8")
    (tmp_path / ".vericov.yml").write_text("version: 1\n", encoding="utf-8")

    with pytest.raises(VericovCliError) as error:
        load_config(tmp_path)

    assert error.value.code == "legacy_config_filename"
    assert "rename" in error.value.message.lower()


def test_rejects_explicit_config_with_noncanonical_filename(tmp_path: Path) -> None:
    custom = tmp_path / "custom.yml"
    custom.write_text("version: 1\n", encoding="utf-8")

    with pytest.raises(VericovCliError) as error:
        load_config(tmp_path, str(custom))

    assert error.value.code == "invalid_config_filename"
    assert ".vericov.yml" in error.value.message


def test_rejects_secrets_in_config(tmp_path: Path) -> None:
    (tmp_path / ".vericov.yml").write_text("version: 1\napi_key: vc_live_bad\n", encoding="utf-8")

    with pytest.raises(VericovCliError) as error:
        load_config(tmp_path)

    assert error.value.code == "secret_in_config"


def test_rejects_unknown_keys(tmp_path: Path) -> None:
    (tmp_path / ".vericov.yml").write_text("version: 1\nupload:\n  typo: true\n", encoding="utf-8")

    with pytest.raises(VericovCliError) as error:
        load_config(tmp_path)

    assert error.value.code == "unknown_config_key"


def test_parses_upload_config(tmp_path: Path) -> None:
    (tmp_path / ".vericov.yml").write_text(
        """
version: 1
ignore:
  - generated/**
  - vendor/**
  - "!vendor/maintained/**"
components:
  - key: api
    owners: [team-api]
    paths: [services/api/**]
api:
  url: http://localhost:8080
upload:
  repository_id: 4d607f16-1af7-4d3b-ac38-06454cba463c
  flags: [unit]
  coverage:
    - coverage/lcov.info
""",
        encoding="utf-8",
    )

    resolved = load_config(tmp_path)

    assert resolved.upload.api_url == "http://localhost:8080"
    assert resolved.upload.flags == ("unit",)
    assert resolved.upload.coverage == ("coverage/lcov.info",)
    assert resolved.upload.ignore == (
        "generated/**",
        "vendor/**",
        "!vendor/maintained/**",
    )
    assert resolved.upload.components[0].key == "api"


def test_rejects_oversized_config_before_yaml_parsing(tmp_path: Path) -> None:
    config = tmp_path / ".vericov.yml"
    config.write_bytes(b"x" * (256 * 1024 + 1))

    with pytest.raises(VericovCliError) as error:
        load_config(tmp_path)

    assert error.value.code == "config_too_large"


def test_rejects_non_list_ignore_value(tmp_path: Path) -> None:
    (tmp_path / ".vericov.yml").write_text(
        "version: 1\nignore: generated/**\n",
        encoding="utf-8",
    )

    with pytest.raises(VericovCliError) as error:
        load_config(tmp_path)

    assert error.value.code == "invalid_ignore_list"
    assert "ignore must be a list" in error.value.message


def test_rejects_invalid_ignore_rule_with_path_and_index(tmp_path: Path) -> None:
    config = tmp_path / ".vericov.yml"
    config.write_text(
        "version: 1\nignore:\n  - generated/**\n  - ../secret.py\n",
        encoding="utf-8",
    )

    with pytest.raises(VericovCliError) as error:
        load_config(tmp_path)

    assert error.value.code == "invalid_ignore_rule"
    assert str(config) in error.value.message
    assert "ignore[1]" in error.value.message
    assert "parent traversal" in error.value.message


def test_rejects_malformed_yaml_with_config_path(tmp_path: Path) -> None:
    config = tmp_path / ".vericov.yml"
    config.write_text("version: [\n", encoding="utf-8")

    with pytest.raises(VericovCliError) as error:
        load_config(tmp_path)

    assert error.value.code == "invalid_yaml"
    assert str(config) in error.value.message
    assert "malformed YAML" in error.value.message
