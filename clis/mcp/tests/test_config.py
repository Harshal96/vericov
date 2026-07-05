import pytest

from vericov_mcp.config import ConfigurationError, resolve_config, validate_api_url


def test_resolves_from_environment() -> None:
    config = resolve_config({"VERICOV_API_URL": "http://localhost:8080", "VERICOV_API_KEY": "vc_repo_test"})

    assert config.api_url == "http://localhost:8080"
    assert config.api_key == "vc_repo_test"


def test_cli_overrides_take_precedence_over_environment() -> None:
    config = resolve_config(
        {"VERICOV_API_URL": "http://localhost:8080", "VERICOV_API_KEY": "env-key"},
        api_url_override="https://vericov.example.internal",
        api_key_override="override-key",
    )

    assert config.api_url == "https://vericov.example.internal"
    assert config.api_key == "override-key"


def test_missing_api_url_raises() -> None:
    with pytest.raises(ConfigurationError):
        resolve_config({"VERICOV_API_KEY": "vc_repo_test"})


def test_missing_api_key_raises() -> None:
    with pytest.raises(ConfigurationError):
        resolve_config({"VERICOV_API_URL": "http://localhost:8080"})


def test_strips_trailing_slash_from_api_url() -> None:
    config = resolve_config({"VERICOV_API_URL": "http://localhost:8080/", "VERICOV_API_KEY": "vc_repo_test"})

    assert config.api_url == "http://localhost:8080"


def test_rejects_plain_http_to_non_loopback_host() -> None:
    with pytest.raises(ConfigurationError):
        validate_api_url("http://vericov.example.internal")


def test_allows_plain_http_to_loopback_hosts() -> None:
    validate_api_url("http://localhost:8080")
    validate_api_url("http://127.0.0.1:8080")


def test_allows_https_to_any_host() -> None:
    validate_api_url("https://vericov.example.internal")


def test_rejects_non_http_scheme() -> None:
    with pytest.raises(ConfigurationError):
        validate_api_url("ftp://vericov.example.internal")
