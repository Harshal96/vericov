"""Artifact format detection."""

import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Tuple

from vericov_coverage_upload.domain.errors import ExitCode, VericovCliError

SNIFF_BYTES = 64 * 1024


def detect_format(path: Path, kind: str) -> Tuple[str, str]:
    prefix = path.read_bytes()[:SNIFF_BYTES]
    lower_name = path.name.lower()
    text = prefix.decode("utf-8", errors="ignore")

    if kind == "test_results":
        if lower_name.endswith(".xml") and _xml_root_name(prefix) in {"testsuite", "testsuites"}:
            return "junit", "application/xml"
        raise _unsupported(path, kind)

    if lower_name.endswith((".info", ".lcov")) or ("SF:" in text and "DA:" in text):
        return "lcov", "text/plain"
    if lower_name.endswith(".gcov") or ("Source:" in text and "#####:" in text):
        return "gcov", "text/plain"
    if text.lstrip().startswith("mode:"):
        return "go_cover", "text/plain"
    if lower_name.endswith(".xml"):
        root = _xml_root_name(prefix)
        if root == "report" or "jacoco" in lower_name:
            return "jacoco", "application/xml"
        if "clover" in lower_name:
            return "clover", "application/xml"
        if root == "coverage":
            if "clover" in text[:4096].lower():
                return "clover", "application/xml"
            return "cobertura", "application/xml"
    raise _unsupported(path, kind)


def _xml_root_name(prefix: bytes) -> str:
    try:
        parser = ET.XMLParser()
        root = ET.fromstring(prefix, parser=parser)
        return _strip_namespace(root.tag)
    except ET.ParseError:
        return ""


def _strip_namespace(tag: str) -> str:
    return tag.rsplit("}", 1)[-1] if "}" in tag else tag


def _unsupported(path: Path, kind: str) -> VericovCliError:
    return VericovCliError(
        "unsupported_artifact_format",
        f"Could not infer {kind} format for {path}.",
        ExitCode.ARTIFACT_VALIDATION,
    )
