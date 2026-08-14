#!/usr/bin/env python3
"""对比成对的 ViewCompose 与 Jetpack Compose Macrobenchmark 结果。
Compare paired ViewCompose and Jetpack Compose Macrobenchmark results.
"""

from __future__ import annotations

import argparse
import json
import math
import statistics
import sys
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any, Iterable


# 成对 benchmark 方法映射: 报告场景名、ViewCompose 方法名、Compose 方法名。
# Paired benchmark method mapping: report scenario, ViewCompose method, Compose method.
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
    (
        "shadow_list_scroll",
        "viewComposeShadowListScroll",
        "composeShadowListScroll",
    ),
    (
        "shadow_list_mutation",
        "viewComposeShadowListMutation",
        "composeShadowListMutation",
    ),
    (
        "shadow_complex_layout_scroll",
        "viewComposeShadowComplexLayoutScroll",
        "composeShadowComplexLayoutScroll",
    ),
    (
        "shadow_complex_layout_update",
        "viewComposeShadowComplexLayoutUpdate",
        "composeShadowComplexLayoutUpdate",
    ),
)

# Report rows may split one fixture into several measured actions, but every row must retain the
# owning scenario identity and workload revision so unlike workloads cannot be compared silently.
SCENARIO_CONTRACTS = {
    "list_scroll": ("performance.list", 1),
    "list_mutation": ("performance.list", 1),
    "complex_layout_scroll": ("performance.complex-layout", 1),
    "complex_layout_update": ("performance.complex-layout", 1),
    "shadow_list_scroll": ("performance.shadow-list", 1),
    "shadow_list_mutation": ("performance.shadow-list", 1),
    "shadow_complex_layout_scroll": ("performance.shadow-complex-layout", 1),
    "shadow_complex_layout_update": ("performance.shadow-complex-layout", 1),
}

SAMPLED_METRICS = ("frameDurationCpuMs", "frameOverrunMs")
SAMPLED_PERCENTILES = ("P50", "P95")
MEMORY_METRICS = ("memoryHeapSizeMaxKb", "memoryRssAnonMaxKb")


@dataclass(frozen=True)
class Comparison:
    """单个 ViewCompose/Compose 指标对比结果。
    One paired ViewCompose/Compose metric comparison result.
    """

    scenario: str
    scenario_id: str
    workload_revision: int
    metric: str
    statistic: str
    viewcompose: float
    compose: float
    delta: float
    relative_percent: float | None


@dataclass(frozen=True)
class Stability:
    """单个 benchmark run 序列的稳定性摘要。
    Stability summary for one benchmark run series.
    """

    scenario: str
    scenario_id: str
    workload_revision: int
    engine: str
    metric: str
    statistic: str
    coefficient_of_variation: float | None
    stable: bool


@dataclass(frozen=True)
class Regression:
    """当前结果相对 baseline 的回归判定。
    Regression decision for current results compared with a baseline.
    """

    scenario: str
    scenario_id: str
    workload_revision: int
    metric: str
    statistic: str
    current: float
    baseline: float
    raw_regression_percent: float
    normalized_regression_percent: float
    delta: float
    failed: bool


def parse_args(argv: list[str]) -> argparse.Namespace:
    """解析 CLI 参数。
    Parses CLI arguments.
    """

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
    """解析 benchmarkData.json 文件路径，目录输入会选择最新结果。
    Resolves a benchmarkData.json path, choosing the newest result when a directory is passed.
    """

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
    """加载 JSON object，非 object 输入直接失败。
    Loads a JSON object and fails fast for non-object input.
    """

    with path.open(encoding="utf-8") as source:
        value = json.load(source)
    if not isinstance(value, dict):
        raise ValueError(f"Expected an object in {path}")
    return value


def benchmark_entries(result: dict[str, Any]) -> dict[str, dict[str, Any]]:
    """提取 Macrobenchmark 条目并校验每个场景必须成对出现。
    Extracts Macrobenchmark entries and requires each present scenario to be paired.

    完整门禁可以包含全部场景；后端实验也可以只运行一个 benchmark class。两种情况下
    都必须同时包含 ViewCompose 与 Compose 控制组，避免生成没有归一化基线的报告。
    A full gate may contain every scenario, while backend experiments may run one benchmark class.
    Both forms must retain the ViewCompose and Compose pair used for normalization.
    """

    entries = result.get("benchmarks")
    if not isinstance(entries, list):
        raise ValueError("Macrobenchmark result has no benchmarks array.")
    indexed: dict[str, dict[str, Any]] = {}
    for entry in entries:
        if isinstance(entry, dict) and isinstance(entry.get("name"), str):
            indexed[entry["name"]] = entry
    incomplete: list[str] = []
    complete_pairs = 0
    for scenario, viewcompose_name, compose_name in SCENARIOS:
        viewcompose_present = viewcompose_name in indexed
        compose_present = compose_name in indexed
        if viewcompose_present and compose_present:
            complete_pairs += 1
        elif viewcompose_present or compose_present:
            missing_name = compose_name if viewcompose_present else viewcompose_name
            incomplete.append(f"{scenario}: {missing_name}")
    if incomplete:
        raise ValueError(
            "Benchmark result has incomplete comparison pairs: " + ", ".join(incomplete),
        )
    if complete_pairs == 0:
        raise ValueError("Benchmark result contains no supported comparison pairs.")
    return indexed


def available_scenarios(
    entries: dict[str, dict[str, Any]],
) -> tuple[tuple[str, str, str], ...]:
    """返回当前结果中完整存在的成对场景。
    Returns paired scenarios fully present in the current result.
    """

    return tuple(
        scenario
        for scenario in SCENARIOS
        if scenario[1] in entries and scenario[2] in entries
    )


def metric_value(
    entry: dict[str, Any],
    metric: str,
    statistic: str,
) -> float | None:
    """读取 sampledMetrics 或 metrics 中的数值。
    Reads a numeric value from sampledMetrics or metrics.
    """

    if statistic.startswith("P"):
        metric_data = entry.get("sampledMetrics", {}).get(metric, {})
    else:
        metric_data = entry.get("metrics", {}).get(metric, {})
    value = metric_data.get(statistic)
    return float(value) if isinstance(value, (int, float)) else None


def build_comparisons(
    entries: dict[str, dict[str, Any]],
) -> list[Comparison]:
    """构建所有场景的成对 ViewCompose/Compose 对比。
    Builds paired ViewCompose/Compose comparisons for all scenarios.
    """

    comparisons: list[Comparison] = []
    for scenario, viewcompose_name, compose_name in available_scenarios(entries):
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
    """创建单个指标对比；缺失数据会跳过。
    Creates one metric comparison and skips missing data.
    """

    if viewcompose is None or compose is None:
        return None
    scenario_id, workload_revision = SCENARIO_CONTRACTS[scenario]
    relative_percent = None
    if compose > 0 and metric != "frameOverrunMs":
        relative_percent = (viewcompose / compose - 1.0) * 100.0
    return Comparison(
        scenario=scenario,
        scenario_id=scenario_id,
        workload_revision=workload_revision,
        metric=metric,
        statistic=statistic,
        viewcompose=viewcompose,
        compose=compose,
        delta=viewcompose - compose,
        relative_percent=relative_percent,
    )


def percentile(values: list[float], percentile_value: int) -> float:
    """使用线性插值计算百分位数。
    Calculates a percentile with linear interpolation.
    """

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
    """计算变异系数；样本不足或均值为零时返回 None。
    Calculates coefficient of variation, returning None for insufficient samples or zero mean.
    """

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
    """基于每次 run 的 P50 计算稳定性。
    Builds stability results from per-run P50 values.
    """

    stability: list[Stability] = []
    for scenario, viewcompose_name, compose_name in available_scenarios(entries):
        scenario_id, workload_revision = SCENARIO_CONTRACTS[scenario]
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
                        scenario_id=scenario_id,
                        workload_revision=workload_revision,
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
    """提取用于 baseline 对比的设备和编译上下文身份。
    Extracts device and compilation identity used for baseline comparisons.
    """

    context = result.get("context", {})
    if isinstance(context, dict) and "build" not in context:
        return {
            "brand": context.get("brand"),
            "model": context.get("model"),
            "fingerprint": context.get("fingerprint"),
            "sdk": context.get("sdk"),
            "cpuLocked": context.get("cpuLocked"),
            "cpuMaxFreqHz": context.get("cpuMaxFreqHz"),
            "compilationMode": context.get("compilationMode"),
        }
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


def revisioned_baseline_comparisons(result: dict[str, Any]) -> list[Comparison]:
    """Loads comparisons from a report that preserved scenario IDs and workload revisions."""

    entries = result.get("comparisons")
    if not isinstance(entries, list):
        raise ValueError(
            "Baseline must be a revisioned compose-comparison.json report, not raw "
            "benchmarkData.json.",
        )
    comparisons: list[Comparison] = []
    for entry in entries:
        if not isinstance(entry, dict):
            raise ValueError("Baseline report contains an invalid comparison entry.")
        try:
            comparisons.append(Comparison(**entry))
        except (TypeError, ValueError) as error:
            raise ValueError(
                "Baseline report comparison is missing revisioned workload metadata.",
            ) from error
    if not comparisons:
        raise ValueError("Baseline report contains no comparisons.")
    return comparisons


def require_matching_context(
    current: dict[str, Any],
    baseline: dict[str, Any],
) -> None:
    """要求 current 和 baseline 来自同一类设备/系统/编译上下文。
    Requires current and baseline results to share the same device, system, and compilation context.
    """

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
    """按场景、指标和统计量索引对比结果。
    Indexes comparisons by scenario, metric, and statistic.
    """

    return {
        (comparison.scenario, comparison.metric, comparison.statistic): comparison
        for comparison in comparisons
    }


def build_regressions(
    current: list[Comparison],
    baseline: list[Comparison],
    policy: dict[str, Any],
) -> list[Regression]:
    """按 policy 构建回归门禁结果。
    Builds regression-gate results from the policy.
    回归需要原始值和 Compose 归一化后的比例同时越过阈值，降低整机变慢造成的误报。
    A failure requires both raw values and Compose-normalized ratios to cross thresholds,
    reducing false positives from common device slowdown.
    """

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
            if (
                current_value.scenario_id != baseline_value.scenario_id
                or current_value.workload_revision
                != baseline_value.workload_revision
            ):
                raise ValueError(
                    "Cannot compare different workload contracts for "
                    f"{scenario}: "
                    f"{baseline_value.scenario_id}@{baseline_value.workload_revision} -> "
                    f"{current_value.scenario_id}@{current_value.workload_revision}",
                )
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
                    scenario_id=current_value.scenario_id,
                    workload_revision=current_value.workload_revision,
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
    """计算正数分母比例。
    Calculates a ratio with a positive denominator.
    """

    if denominator <= 0:
        raise ValueError("Normalized comparison requires positive metric values.")
    return numerator / denominator


def percentage_change(current: float, baseline: float) -> float:
    """计算 current 相对 baseline 的百分比变化。
    Calculates percentage change from baseline to current.
    """

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
    """渲染人工可读 Markdown 性能报告。
    Renders a human-readable Markdown performance report.
    """

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
            "| Workload | Action | Metric | Stat | ViewCompose | Compose | Delta | Relative |",
            "| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |",
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
            f"{comparison.scenario_id}@{comparison.workload_revision} | "
            f"{comparison.scenario} | {comparison.metric} | {comparison.statistic} | "
            f"{comparison.viewcompose:.3f} | {comparison.compose:.3f} | "
            f"{comparison.delta:+.3f} | {relative} |",
        )
    lines.extend(
        [
            "",
            "## Run stability",
            "",
            "| Workload | Action | Engine | Metric | CV | Status |",
            "| --- | --- | --- | --- | ---: | --- |",
        ],
    )
    for item in stability:
        variation = (
            "n/a"
            if item.coefficient_of_variation is None
            else f"{item.coefficient_of_variation:.3f}"
        )
        lines.append(
            f"| {item.scenario_id}@{item.workload_revision} | {item.scenario} | "
            f"{item.engine} | {item.metric} | "
            f"{variation} | {'stable' if item.stable else 'unstable'} |",
        )
    if baseline_path is not None:
        lines.extend(
            [
                "",
                "## Regression gate",
                "",
                "| Workload | Action | Metric | Stat | Raw | Normalized | Delta | Status |",
                "| --- | --- | --- | ---: | ---: | ---: | ---: | --- |",
            ],
        )
        for regression in regressions:
            lines.append(
                f"| {regression.scenario_id}@{regression.workload_revision} | "
                f"{regression.scenario} | {regression.metric} | "
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
    """写入报告文件并创建父目录。
    Writes a report file and creates parent directories.
    """

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    """CLI 入口，输出 Markdown 与 JSON 报告并按需执行门禁。
    CLI entrypoint that emits Markdown and JSON reports and optionally enforces the gate.
    """

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
        baseline_comparisons = revisioned_baseline_comparisons(baseline_result)
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
