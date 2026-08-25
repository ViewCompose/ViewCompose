#!/usr/bin/env python3
"""Validate and summarize the fixed ViewCompose Paging Macrobenchmark workload."""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

import compare_macrobenchmarks as comparison


PAGING_METHODS = {
    "append_drop": "pagingAppendDrop",
    "query_replacement": "pagingQueryReplacement",
    "scroll": "pagingScroll",
}
FRAME_METRIC = "frameDurationCpuMs"
FRAME_PERCENTILES = ("P50", "P90", "P95", "P99")


@dataclass(frozen=True)
class PagingMeasurement:
    """Absolute frame, memory, and run-stability result for one fixed action."""

    action: str
    method: str
    frame_p50_ms: float
    frame_p90_ms: float
    frame_p95_ms: float
    frame_p99_ms: float
    median_peak_heap_kib: float
    median_peak_rss_anon_kib: float | None
    run_p50_cv: float
    stable: bool


def parse_args(argv: list[str]) -> argparse.Namespace:
    """Parse command-line arguments."""

    parser = argparse.ArgumentParser(
        description="Summarize the fixed one-million-position Paging Macrobenchmark.",
    )
    parser.add_argument("current", help="BenchmarkData JSON file or split-result directory.")
    parser.add_argument(
        "--output",
        default="build/reports/benchmarks/paging-baseline.md",
        help="Markdown report path.",
    )
    parser.add_argument(
        "--json-output",
        default="build/reports/benchmarks/paging-baseline.json",
        help="Machine-readable report path.",
    )
    parser.add_argument("--expected-iterations", type=int, default=5)
    parser.add_argument("--max-run-p50-cv", type=float, default=0.15)
    parser.add_argument(
        "--enforce",
        action="store_true",
        help="Exit non-zero when any method exceeds the stability ceiling.",
    )
    return parser.parse_args(argv)


def require_number(value: Any, label: str) -> float:
    """Return one numeric metric or reject an incomplete result."""

    if not isinstance(value, (int, float)):
        raise ValueError(f"Paging benchmark is missing numeric {label}.")
    return float(value)


def optional_number(value: Any, label: str) -> float | None:
    """Return an optional numeric metric while rejecting malformed present values."""

    if value is None:
        return None
    return require_number(value, label)


def require_runs(entry: dict[str, Any], expected_iterations: int) -> list[list[float]]:
    """Read the exact formal frame-sample runs for one method."""

    raw_runs = entry.get("sampledMetrics", {}).get(FRAME_METRIC, {}).get("runs")
    if not isinstance(raw_runs, list) or len(raw_runs) != expected_iterations:
        raise ValueError(
            f"{entry.get('name')} must contain exactly {expected_iterations} "
            f"{FRAME_METRIC} runs.",
        )
    runs: list[list[float]] = []
    for index, raw_run in enumerate(raw_runs):
        if not isinstance(raw_run, list) or not raw_run:
            raise ValueError(f"{entry.get('name')} run {index + 1} has no frame samples.")
        values = [float(value) for value in raw_run if isinstance(value, (int, float))]
        if len(values) != len(raw_run):
            raise ValueError(f"{entry.get('name')} run {index + 1} has non-numeric samples.")
        runs.append(values)
    return runs


def summarize_entry(
    action: str,
    entry: dict[str, Any],
    expected_iterations: int,
    max_run_p50_cv: float,
) -> PagingMeasurement:
    """Build one absolute measurement and stability decision."""

    frame = entry.get("sampledMetrics", {}).get(FRAME_METRIC, {})
    if not isinstance(frame, dict):
        raise ValueError(f"{entry.get('name')} has no {FRAME_METRIC} metric.")
    runs = require_runs(entry, expected_iterations)
    run_p50_values = [comparison.percentile(run, 50) for run in runs]
    run_p50_cv = comparison.coefficient_of_variation(run_p50_values)
    if run_p50_cv is None:
        raise ValueError(f"{entry.get('name')} run-P50 CV is undefined.")

    memory = entry.get("metrics", {})
    if not isinstance(memory, dict):
        raise ValueError(f"{entry.get('name')} has no memory metrics.")
    percentiles = {
        statistic: require_number(frame.get(statistic), f"{entry.get('name')} {statistic}")
        for statistic in FRAME_PERCENTILES
    }
    heap_value = require_number(
        memory.get("memoryHeapSizeMaxKb", {}).get("median"),
        f"{entry.get('name')} memoryHeapSizeMaxKb.median",
    )
    rss_value = optional_number(
        memory.get("memoryRssAnonMaxKb", {}).get("median"),
        f"{entry.get('name')} memoryRssAnonMaxKb.median",
    )
    return PagingMeasurement(
        action=action,
        method=str(entry["name"]),
        frame_p50_ms=percentiles["P50"],
        frame_p90_ms=percentiles["P90"],
        frame_p95_ms=percentiles["P95"],
        frame_p99_ms=percentiles["P99"],
        median_peak_heap_kib=heap_value,
        median_peak_rss_anon_kib=rss_value,
        run_p50_cv=run_p50_cv,
        stable=run_p50_cv <= max_run_p50_cv,
    )


def build_summary(
    result: dict[str, Any],
    expected_iterations: int,
    max_run_p50_cv: float,
) -> list[PagingMeasurement]:
    """Require the complete three-method contract and summarize it."""

    raw_entries = result.get("benchmarks")
    if not isinstance(raw_entries, list):
        raise ValueError("Paging Macrobenchmark result has no benchmarks array.")
    entries = {
        entry.get("name"): entry
        for entry in raw_entries
        if isinstance(entry, dict) and isinstance(entry.get("name"), str)
    }
    missing = [method for method in PAGING_METHODS.values() if method not in entries]
    if missing:
        raise ValueError("Paging Macrobenchmark result is incomplete: " + ", ".join(missing))
    return [
        summarize_entry(
            action=action,
            entry=entries[method],
            expected_iterations=expected_iterations,
            max_run_p50_cv=max_run_p50_cv,
        )
        for action, method in PAGING_METHODS.items()
    ]


def render_markdown(
    source: Path,
    context: dict[str, Any],
    measurements: list[PagingMeasurement],
    expected_iterations: int,
    max_run_p50_cv: float,
) -> str:
    """Render a reviewable absolute-baseline report."""

    lines = [
        "# ViewCompose Paging Performance Baseline",
        "",
        f"- Source: `{source}`",
        f"- Device: `{context.get('brand')} {context.get('model')}`",
        f"- SDK: `{context.get('sdk')}`",
        f"- Fingerprint: `{context.get('fingerprint')}`",
        f"- Clock policy: `{context.get('clockPolicy') or 'missing'}`",
        f"- Compilation mode: `{context.get('compilationMode')}`",
        f"- Iterations per action: `{expected_iterations}`",
        f"- Run-P50 CV ceiling: `{max_run_p50_cv:.3f}`",
        "",
        "| Action | Frame P50/P90/P95/P99, ms | Median peak heap, KiB | "
        "Median peak RSS anon, KiB | Run-P50 CV | Stability |",
        "| --- | ---: | ---: | ---: | ---: | --- |",
    ]
    for item in measurements:
        rss = (
            "n/a"
            if item.median_peak_rss_anon_kib is None
            else f"{item.median_peak_rss_anon_kib:.0f}"
        )
        lines.append(
            f"| `{item.action}` | {item.frame_p50_ms:.3f}/{item.frame_p90_ms:.3f}/"
            f"{item.frame_p95_ms:.3f}/{item.frame_p99_ms:.3f} | "
            f"{item.median_peak_heap_kib:.0f} | {rss} | "
            f"{item.run_p50_cv:.3f} | {'stable' if item.stable else 'unstable'} |",
        )
    lines.extend(
        [
            "",
            "This is a first absolute baseline. Without a compatible earlier workload, normalized "
            "performance direction remains inconclusive; stability only determines whether the "
            "absolute values are acceptable evidence.",
            "",
        ],
    )
    return "\n".join(lines)


def write_text(path: Path, content: str) -> None:
    """Create a report parent and write UTF-8 content."""

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    """Validate input, emit both report forms, and optionally enforce stability."""

    args = parse_args(sys.argv[1:] if argv is None else argv)
    source, result = comparison.load_current_result(args.current)
    context = comparison.context_identity(result)
    measurements = build_summary(
        result=result,
        expected_iterations=args.expected_iterations,
        max_run_p50_cv=args.max_run_p50_cv,
    )
    report = render_markdown(
        source=source,
        context=context,
        measurements=measurements,
        expected_iterations=args.expected_iterations,
        max_run_p50_cv=args.max_run_p50_cv,
    )
    write_text(Path(args.output), report)
    write_text(
        Path(args.json_output),
        json.dumps(
            {
                "schemaVersion": 1,
                "workload": "performance.paging@1",
                "source": str(source),
                "context": context,
                "expectedIterations": args.expected_iterations,
                "maxRunP50Cv": args.max_run_p50_cv,
                "measurements": [asdict(item) for item in measurements],
                "normalizedDirection": "inconclusive",
            },
            indent=2,
            sort_keys=True,
        )
        + "\n",
    )
    print(report)
    return 1 if args.enforce and any(not item.stable for item in measurements) else 0


if __name__ == "__main__":
    raise SystemExit(main())
