import json

from vericov_mcp.rendering import MAX_RESULT_BYTES, render


def test_renders_small_payload_as_compact_json() -> None:
    result = render({"b": 1, "a": 2}, "narrow it")

    assert json.loads(result) == {"a": 2, "b": 1}
    assert result == '{"a":2,"b":1}'


def test_truncates_oversized_payload_with_hint() -> None:
    huge = {"files": ["x" * 100] * 2000}

    result = render(huge, "Narrow with a smaller limit.")
    parsed = json.loads(result)

    assert parsed["truncated"] is True
    assert "Narrow with a smaller limit." in parsed["hint"]
    assert len(result.encode("utf-8")) < MAX_RESULT_BYTES
