"""Renders tool results as compact JSON, truncating oversized payloads."""

import json
from typing import Any, Dict

MAX_RESULT_BYTES = 50 * 1024


def render(data: Dict[str, Any], narrow_hint: str) -> str:
    encoded = json.dumps(data, sort_keys=True, separators=(",", ":"))
    if len(encoded.encode("utf-8")) <= MAX_RESULT_BYTES:
        return encoded
    notice = {
        "truncated": True,
        "reason": f"Result exceeds {MAX_RESULT_BYTES} bytes.",
        "hint": narrow_hint,
    }
    return json.dumps(notice, sort_keys=True, separators=(",", ":"))
