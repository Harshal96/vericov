"""Server configuration: API URL/key resolution and transport safety checks."""

from dataclasses import dataclass
from typing import Mapping, Optional
from urllib.parse import urlparse

LOOPBACK_HOSTS = {"localhost", "127.0.0.1", "::1"}


class ConfigurationError(Exception):
    pass


@dataclass(frozen=True)
class ServerConfig:
    api_url: str
    api_key: str


def resolve_config(
    env: Mapping[str, str],
    api_url_override: Optional[str] = None,
    api_key_override: Optional[str] = None,
) -> ServerConfig:
    api_url = api_url_override or env.get("VERICOV_API_URL")
    api_key = api_key_override or env.get("VERICOV_API_KEY")
    if not api_url:
        raise ConfigurationError("VERICOV_API_URL (or --api-url) is required.")
    if not api_key:
        raise ConfigurationError("VERICOV_API_KEY (or --api-key) is required.")
    validate_api_url(api_url)
    return ServerConfig(api_url=api_url.rstrip("/"), api_key=api_key)


def validate_api_url(api_url: str) -> None:
    parsed = urlparse(api_url)
    if parsed.scheme not in ("http", "https"):
        raise ConfigurationError(f"{api_url!r} is not a valid http(s) URL.")
    if parsed.scheme == "http" and parsed.hostname not in LOOPBACK_HOSTS:
        raise ConfigurationError(
            f"Refusing to send the API key over plain HTTP to non-loopback host {parsed.hostname!r}. "
            "Use https:// or point VERICOV_API_URL at localhost/127.0.0.1."
        )
