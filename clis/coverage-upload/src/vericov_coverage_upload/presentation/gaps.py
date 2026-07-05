"""Rendering for the `vericov gaps` command."""

import json
from typing import Any, Dict


def manifest_json(manifest: Dict[str, Any]) -> str:
    return json.dumps(manifest, sort_keys=True)


def manifest_text(manifest: Dict[str, Any]) -> str:
    lines = [_gate_summary_line(manifest)]
    failed_gates = manifest.get("failed_gates") or []
    for gate in failed_gates:
        lines.append(
            f"  ✗ {gate.get('gate_name')}: {gate.get('actual')} < {gate.get('threshold')} ({gate.get('metric')})"
        )

    entries = manifest.get("entries") or []
    if not entries:
        lines.append("")
        lines.append("No coverage gaps match the current filters.")
        return "\n".join(lines)

    lines.append("")
    lines.append(f"{'RANK':<5} {'RISK':<9} {'FILE':<45} {'LINES':<12} {'REASON':<28} OWNERS")
    for entry in entries:
        line_range = _format_range(entry.get("line_start"), entry.get("line_end"))
        risk = entry.get("risk") or {}
        owners = ", ".join(entry.get("owners") or [])
        lines.append(
            f"{entry.get('rank'):<5} "
            f"{_risk_cell(risk):<9} "
            f"{_truncate(entry.get('file_path', ''), 45):<45} "
            f"{line_range:<12} "
            f"{_truncate(entry.get('reason_code', ''), 28):<28} "
            f"{owners}"
        )

    if manifest.get("truncated"):
        lines.append("")
        lines.append(
            f"Showing {len(entries)} of more entries; narrow with --min-risk-level "
            "or a smaller --limit to see the rest."
        )
    return "\n".join(lines)


def _gate_summary_line(manifest: Dict[str, Any]) -> str:
    report = manifest.get("report") or {}
    gate_status = report.get("gate_status", "not_evaluated")
    ref_description = report.get("branch") or report.get("commit_sha") or "unknown ref"
    return f"Vericov coverage gaps for {ref_description}: gate_status={gate_status}"


def _risk_cell(risk: Dict[str, Any]) -> str:
    level = risk.get("level", "")
    score = risk.get("score", "")
    return f"{level}({score})" if level else str(score)


def _format_range(start, end) -> str:
    if start is None:
        return "-"
    if end is None or end == start:
        return str(start)
    return f"{start}-{end}"


def _truncate(value: str, width: int) -> str:
    return value if len(value) <= width else value[: width - 1] + "…"
