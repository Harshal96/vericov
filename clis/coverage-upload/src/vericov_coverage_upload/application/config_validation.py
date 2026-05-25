"""Config validation use case."""

from pathlib import Path
from typing import Optional

from vericov_coverage_upload.domain.config import ResolvedConfig
from vericov_coverage_upload.infrastructure.config_loader import load_config


def validate_config(cwd: Path, config_path: Optional[str] = None) -> ResolvedConfig:
    return load_config(cwd, config_path)
