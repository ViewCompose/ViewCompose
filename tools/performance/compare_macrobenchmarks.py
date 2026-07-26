#!/usr/bin/env python3
"""Compare paired ViewCompose and Jetpack Compose Macrobenchmark results."""

from __future__ import annotations

import argparse
import json
import math
import statistics
import sys
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any, Iterable


SCENARIOS = (
    ("list_scroll", "viewComposeListScroll", "composeListScroll"),
    ("list_mutation", "viewComposeListMutation", "composeListMutation"),
    (
        "complex_layout_scroll",
        "viewComposeComplexLayoutScroll",
        "composeComplexLayoutScroll",
    ),
    (
        "complex_layout_update",
        "viewComposeComplexLayoutUpdate",
        "composeComplexLayoutUpdate",
    ),
)
SAMPLED_METRICS = ("frameDurationCpuMs", "frameOverrunMs")
SAMPLED_PERCENTILES = ("P50", "P95")
MEMORY_METRICS = ("memoryHeapSizeMaxKb", "memoryRssAnonMaxKb")


@dataclass(frozen=True)
class Comparison:
    scenario: str
    metric: str
    statistic: str
    viewcompose: float
    compose: float
    delta: float
    relative_percent: float | None


@dataclass(frozen=True)
class Stability:
    scenario: str
    engine: str
    metric: str
    statistic: str
    coefficient_of_variation: float | None
    stable: bool


@dataclass(frozen=True)
class Regression:
    scenario: str
    metric: str
    statistic: str
    current: float
    baseline: float
    raw_regression_percent: float
    normalized_regression_percent: float
    delta: float
    failed: bool


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Generate a paired ViewCompose/Compose performance report from AndroidX "
            "Macrobenchmark JSON."
        ),
    )
    parser.add_argument(
        "current",
        nargs="?",
        default=(
            "viewcompose-benchmark/build/outputs/"
            "connected_android_test_additional_output"
        ),
        help="Current benchmarkData.json file or directory containing one.",
    )
    parser.add_argument(
        "--baseline",
        help="Previous benchmarkData.json file or directory used for regression checks.",
    )
    parser.add_argument(
        "--policy",
        default=str(Path(__file__).with_name("benchmark_policy.json")),
        help="Regression policy JSON.",
    )
    parser.add_argument(
        "--output",
        default="build/reports/benchmarks/compose-comparison.md",
        help="Markdown report path.",
    )
    parser.add_argument(
        "--json-output",
        default="build/reports/benchmarks/compose-comparison.json",
        help="Machine-readable report path.",
    )
    parser.add_argument(
        "--enforce",
        action="store_true",
        help="Exit non-zero when a baseline regression crosses the policy.",
    )
    parser.add_argument(
        "--allow-context-mismatch",
        action="store_true",
        help="Allow baseline comparison across different device/OS contexts.",
    )
    return parser.parse_args(argv)


def resolve_result_path(value: str | Path) -> Path:
    path = Path(value)
    if path.is_file():
        return path
    if not path.exists():
        raise ValueError(f"Benchmark result path does not exist: {path}")
    candidates = list(path.rglob("*benchmarkData.json"))
    if not candidates:
        raise ValueError(f"No benchmarkData.json found under: {path}")
    return max(candidates, key=lambda candidate: candidate.stat().st_mtime)


def load_json(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as source:
        value = json.load(source)
    if not isinstance(value, dict):
        raise ValueError(f"Expected an object in {path}")
    return value


def benchmark_entries(result: dict[str, Any]) -> dict[str, dict[str, Any]]:
    entries = result.get("benchmarks")
    if not isinstance(entries, list):
        raise ValueError("Macrobenchmark result has no benchmarks array.")
    indexed: dict[str, dict[str, Any]] = {}
    for entry in entries:
        if isinstance(entry, dict) and isinstance(entry.get("name"), str):
            indexed[entry["name"]] = entry
    required = {
        benchmark_name
        for _, viewcompose_name, compose_name in SCENARIOS
        for benchmark_name in (viewcompose_name, compose_name)
    }
    missing = sorted(required - indexed.keys())
    if missing:
        raise ValueError(
            "Benchmark result is missing comparison methods: " + ", ".join(missing),
        )
    return indexed


def metric_value(
    entry: dict[str, Any],
    metric: str,
    statistic: str,
) -> float | None:
    if statistic.startswith("P"):
        metric_data = entry.get("sampledMetrics", {}).get(metric, {})
    else:
        metric_data = entry.get("metrics", {}).get(metric, {})
    value = metric_data.get(statistic)
    return float(value) if isinstance(value, (int, float)) else None


def build_comparisons(
    entries: dict[str, dict[str, Any]],
) -> list[Comparison]:
    comparisons: list[Comparison] = []
    for scenario, viewcompose_name, compose_name in SCENARIOS:
        viewcompose_entry = entries[viewcompose_name]
        compose_entry = entries[compose_name]
        for metric in SAMPLED_METRICS:
            for statistic in SAMPLED_PERCENTILES:
                comparison = create_comparison(
                    scenario=scenario,
                    metric=metric,
                    statistic=statistic,
                    viewcompose=metric_value(viewcompose_entry, metric, statistic),
                    compose=metric_value(compose_entry, metric, statistic),
                )
                if comparison is not None:
                    comparisons.append(comparison)
        for metric in MEMORY_METRICS:
            comparison = create_comparison(
                scenario=scenario,
                metric=metric,
                statistic="median",
                viewcompose=metric_value(viewcompose_entry, metric, "median"),
                compose=metric_value(compose_entry, metric, "median"),
            )
            if comparison is not None:
                comparisons.append(comparison)
    return comparisons


def create_comparison(
    scenario: str,
    metric: str,
    statistic: str,
    viewcompose: float | None,
    compose: float | None,
) -> Comparison | None:
    if viewcompose is None or compose is None:
        return None
    relative_percent = None
    if compose > 0 and metric != "frameOverrunMs":
        relative_percent = (viewcompose / compose - 1.0) * 100.0
    return Comparison(
        scenario=scenario,
        metric=metric,
        statistic=statistic,
        viewcompose=viewcompose,
        compose=compose,
        delta=viewcompose - compose,
        relative_percent=relative_percent,
    )


def percentile(values: list[float], percentile_value: int) -> float:
    if not values:
        raise ValueError("Cannot calculate a percentile from an empty run.")
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    position = (len(ordered) - 1) * percentile_value / 100.0
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return ordered[lower]
    fraction = position - lower
    return ordered[lower] + (ordered[upper] - ordered[lower]) * fraction


def coefficient_of_variation(values: Iterable[float]) -> float | None:
    materialized = list(values)
    if len(materialized) < 2:
        return None
    mean = statistics.fmean(materialized)
    if mean == 0:
        return None
    return statistics.pstdev(materialized) / abs(mean)


def build_stability(
    entries: dict[str, dict[str, Any]],
    max_coefficient_of_variation: float,
) -> list[Stability]:
    stability: list[Stability] = []
    for scenario, viewcompose_name, compose_name in SCENARIOS:
        for engine, benchmark_name in (
            ("ViewCompose", viewcompose_name),
            ("Compose", compose_name),
        ):
            entry = entries[benchmark_name]
            for metric in SAMPLED_METRICS:
                runs = entry.get("sampledMetrics", {}).get(metric, {}).get("runs", [])
                if not isinstance(runs, list):
                    continue
                run_percentiles = [
                    percentile(
                        [float(value) for value in run if isinstance(value, (int, float))],
                        50,
                    )
                    for run in runs
                    if isinstance(run, list) and run
                ]
                if not run_percentiles:
                    continue
                variation = coefficient_of_variation(run_percentiles)
                stability.append(
                    Stability(
                        scenario=scenario,
                        engine=engine,
                        metric=metric,
                        statistic="run-P50",
                        coefficient_of_variation=variation,
                        stable=(
                            variation is None
                            or variation <= max_coefficient_of_variation
                        ),
                    ),
                )
    return stability


def context_identity(result: dict[str, Any]) -> dict[str, Any]:
    context = result.get("context", {})
    build = context.get("build", {})
    version = build.get("version", {})
    return {
        "brand": build.get("brand"),
        "model": build.get("model"),
        "fingerprint": build.get("fingerprint"),
        "sdk": version.get("sdk"),
        "cpuLocked": context.get("cpuLocked"),
        "cpuMaxFreqHz": context.get("cpuMaxFreqHz"),
        "compilationMode": context.get("compilationMode"),
    }


def require_matching_context(
    current: dict[str, Any],
    baseline: dict[str, Any],
) -> None:
    current_identity = context_identity(current)
    baseline_identity = context_identity(baseline)
    keys = ("model", "fingerprint", "sdk", "cpuLocked", "compilationMode")
    mismatches = [
        key
        for key in keys
        if current_identity.get(key) != baseline_identity.get(key)
    ]
    if mismatches:
        detail = ", ".join(
            f"{key}={baseline_identity.get(key)!r}->{current_identity.get(key)!r}"
            for key in mismatches
        )
        raise ValueError(
            "Current and baseline benchmark contexts differ: " + detail,
        )


def comparison_index(
    comparisons: Iterable[Comparison],
) -> dict[tuple[str, str, str], Comparison]:
    return {
        (comparison.scenario, comparison.metric, comparison.statistic): comparison
        for comparison in comparisons
    }


def build_regressions(
    current: list[Comparison],
    baseline: list[Comparison],
    policy: dict[str, Any],
) -> list[Regression]:
    current_index = comparison_index(current)
    baseline_index = comparison_index(baseline)
    thresholds = policy.get("regressionThresholds", {})
    regressions: list[Regression] = []
    for key, threshold in thresholds.items():
        metric, statistic = key.rsplit(".", 1)
        for scenario, _, _ in SCENARIOS:
            index_key = (scenario, metric, statistic)
            current_value = current_index.get(index_key)
            baseline_value = baseline_index.get(index_key)
            if current_value is None or baseline_value is None:
                continue
            raw_percent = percentage_change(
                current_value.viewcompose,
                baseline_value.viewcompose,
            )
            current_ratio = safe_ratio(
                current_value.viewcompose,
                current_value.compose,
            )
            baseline_ratio = safe_ratio(
                baseline_value.viewcompose,
                baseline_value.compose,
            )
            normalized_percent = percentage_change(current_ratio, baseline_ratio)
            delta = current_value.viewcompose - baseline_value.viewcompose
            maximum_percent = float(threshold["maxRegressionPercent"])
            minimum_delta = float(threshold["minimumDelta"])
            failed = (
                raw_percent > maximum_percent
                and normalized_percent > maximum_percent
                and delta > minimum_delta
            )
            regressions.append(
                Regression(
                    scenario=scenario,
                    metric=metric,
                    statistic=statistic,
                    current=current_value.viewcompose,
                    baseline=baseline_value.viewcompose,
                    raw_regression_percent=raw_percent,
                    normalized_regression_percent=normalized_percent,
                    delta=delta,
                    failed=failed,
                ),
            )
    return regressions


def safe_ratio(numerator: float, denominator: float) -> float:
    if denominator <= 0:
        raise ValueError("Normalized comparison requires positive metric values.")
    return numerator / denominator


def percentage_change(current: float, baseline: float) -> float:
    if baseline <= 0:
        raise ValueError("Regression comparison requires positive baseline values.")
    return (current / baseline - 1.0) * 100.0


def render_markdown(
    current_path: Path,
    baseline_path: Path | None,
    context: dict[str, Any],
    comparisons: list[Comparison],
    stability: list[Stability],
    regressions: list[Regression],
) -> str:
    lines = [
        "# ViewCompose / Compose Performance Comparison",
        "",
        f"- Current: `{current_path}`",
        f"- Device: `{context.get('brand')} {context.get('model')}`",
        f"- SDK: `{context.get('sdk')}`",
        f"- CPU locked: `{context.get('cpuLocked')}`",
        f"- Compilation mode: `{context.get('compilationMode')}`",
    ]
    if baseline_path is not None:
        lines.append(f"- Baseline: `{baseline_path}`")
    lines.extend(
        [
            "",
            "## Paired results",
            "",
            "| Scenario | Metric | Stat | ViewCompose | Compose | Delta | Relative |",
            "| --- | --- | ---: | ---: | ---: | ---: | ---: |",
        ],
    )
    for comparison in comparisons:
        relative = (
            "n/a"
            if comparison.relative_percent is None
            else f"{comparison.relative_percent:+.1f}%"
        )
        lines.append(
            "| "
            f"{comparison.scenario} | {comparison.metric} | {comparison.statistic} | "
            f"{comparison.viewcompose:.3f} | {comparison.compose:.3f} | "
            f"{comparison.delta:+.3f} | {relative} |",
        )
    lines.extend(
        [
            "",
            "## Run stability",
            "",
            "| Scenario | Engine | Metric | CV | Status |",
            "| --- | --- | --- | ---: | --- |",
        ],
    )
    for item in stability:
        variation = (
            "n/a"
            if item.coefficient_of_variation is None
            else f"{item.coefficient_of_variation:.3f}"
        )
        lines.append(
            f"| {item.scenario} | {item.engine} | {item.metric} | "
            f"{variation} | {'stable' if item.stable else 'unstable'} |",
        )
    if baseline_path is not None:
        lines.extend(
            [
                "",
                "## Regression gate",
                "",
                "| Scenario | Metric | Stat | Raw | Normalized | Delta | Status |",
                "| --- | --- | ---: | ---: | ---: | ---: | --- |",
            ],
        )
        for regression in regressions:
            lines.append(
                f"| {regression.scenario} | {regression.metric} | "
                f"{regression.statistic} | "
                f"{regression.raw_regression_percent:+.1f}% | "
                f"{regression.normalized_regression_percent:+.1f}% | "
                f"{regression.delta:+.3f} | "
                f"{'FAIL' if regression.failed else 'PASS'} |",
            )
    lines.extend(
        [
            "",
            "Relative values describe ViewCompose versus Compose in the same run; "
            "they are not cross-device scores.",
            "",
        ],
    )
    return "\n".join(lines)


def write_report(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    if args.enforce and not args.baseline:
        raise ValueError("--enforce requires --baseline.")
    current_path = resolve_result_path(args.current)
    current_result = load_json(current_path)
    policy = load_json(Path(args.policy))
    current_entries = benchmark_entries(current_result)
    comparisons = build_comparisons(current_entries)
    stability = build_stability(
        current_entries,
        max_coefficient_of_variation=float(
            policy["maxRunCoefficientOfVariation"],
        ),
    )
    baseline_path: Path | None = None
    regressions: list[Regression] = []
    if args.baseline:
        baseline_path = resolve_result_path(args.baseline)
        baseline_result = load_json(baseline_path)
        if not args.allow_context_mismatch:
            require_matching_context(current_result, baseline_result)
        baseline_comparisons = build_comparisons(
            benchmark_entries(baseline_result),
        )
        regressions = build_regressions(
            current=comparisons,
            baseline=baseline_comparisons,
            policy=policy,
        )
    context = context_identity(current_result)
    markdown = render_markdown(
        current_path=current_path,
        baseline_path=baseline_path,
        context=context,
        comparisons=comparisons,
        stability=stability,
        regressions=regressions,
    )
    write_report(Path(args.output), markdown)
    summary = {
        "current": str(current_path),
        "baseline": None if baseline_path is None else str(baseline_path),
        "context": context,
        "comparisons": [asdict(comparison) for comparison in comparisons],
        "stability": [asdict(item) for item in stability],
        "regressions": [asdict(regression) for regression in regressions],
        "gateStatus": (
            "FAIL"
            if any(regression.failed for regression in regressions)
            else "PASS" if baseline_path is not None else "NOT_RUN"
        ),
    }
    write_report(
        Path(args.json_output),
        json.dumps(summary, indent=2, ensure_ascii=False) + "\n",
    )
    print(markdown)
    if args.enforce and any(regression.failed for regression in regressions):
        return 2
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ValueError as error:
        print(f"error: {error}", file=sys.stderr)
        raise SystemExit(1)
