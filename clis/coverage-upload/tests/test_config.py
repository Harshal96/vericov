from pathlib import Path

import pytest

from vericov_coverage_upload.domain.errors import VericovCliError
from vericov_coverage_upload.infrastructure.config_loader import load_config


def test_loads_no_config_with_defaults(tmp_path: Path) -> None:
    resolved = load_config(tmp_path)

    assert resolved.path is None
    assert resolved.upload.api_url == "https://api.vericov.dev"


def test_rejects_both_default_config_names(tmp_path: Path) -> None:
    (tmp_path / "vericov.yml").write_text("version: 1\n", encoding="utf-8")
    (tmp_path / ".vericov.yml").write_text("version: 1\n", encoding="utf-8")

    with pytest.raises(VericovCliError) as error:
        load_config(tmp_path)

    assert error.value.code == "multiple_configs"


def test_rejects_secrets_in_config(tmp_path: Path) -> None:
    (tmp_path / "vericov.yml").write_text("version: 1\napi_key: vc_live_bad\n", encoding="utf-8")

    with pytest.raises(VericovCliError) as error:
        load_config(tmp_path)

    assert error.value.code == "secret_in_config"


def test_rejects_unknown_keys(tmp_path: Path) -> None:
    (tmp_path / "vericov.yml").write_text("version: 1\nupload:\n  typo: true\n", encoding="utf-8")

    with pytest.raises(VericovCliError) as error:
        load_config(tmp_path)

    assert error.value.code == "unknown_config_key"


def test_parses_upload_config(tmp_path: Path) -> None:
    (tmp_path / "vericov.yml").write_text(
        """
version: 1
api:
  url: http://localhost:9000
upload:
  repository_id: 4d607f16-1af7-4d3b-ac38-06454cba463c
  flags: [unit]
  coverage:
    - coverage/lcov.info
""",
        encoding="utf-8",
    )

    resolved = load_config(tmp_path)

    assert resolved.upload.api_url == "http://localhost:9000"
    assert resolved.upload.flags == ("unit",)
    assert resolved.upload.coverage == ("coverage/lcov.info",)
