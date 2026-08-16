#!/usr/bin/env python3
"""Generate engine-neutral ViewCompose performance comparisons."""

from __future__ import annotations

import argparse
import json
import math
import statistics
import sys
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any, Iterable


VIEWCOMPOSE_ENGINE = "viewcompose"
COMPOSE_ENGINE = "compose"
ANDROID_VIEWS_ENGINE = "android_views"
ENGINE_DISPLAY_NAMES = {
    VIEWCOMPOSE_ENGINE: "ViewCompose",
    COMPOSE_ENGINE: "Compose",
    ANDROID_VIEWS_ENGINE: "Android Views",
}


@dataclass(frozen=True)
class ScenarioContract:
    """One measured action and the benchmark method owned by every supported engine."""

    name: str
    scenario_id: str
    workload_revision: int
    methods: dict[str, str]


SCENARIOS = (
    ScenarioContract(
        "list_scroll",
        "performance.list",
        3,
        {
            VIEWCOMPOSE_ENGINE: "viewComposeListScroll",
            COMPOSE_ENGINE: "composeListScroll",
            ANDROID_VIEWS_ENGINE: "androidViewsListScroll",
        },
    ),
    ScenarioContract(
        "list_mutation",
        "performance.list",
        3,
        {
            VIEWCOMPOSE_ENGINE: "viewComposeListMutation",
            COMPOSE_ENGINE: "composeListMutation",
            ANDROID_VIEWS_ENGINE: "androidViewsListMutation",
        },
    ),
    ScenarioContract(
        "complex_layout_scroll",
        "performance.complex-layout",
        4,
        {
            VIEWCOMPOSE_ENGINE: "viewComposeComplexLayoutScroll",
            COMPOSE_ENGINE: "composeComplexLayoutScroll",
            ANDROID_VIEWS_ENGINE: "androidViewsComplexLayoutScroll",
        },
    ),
    ScenarioContract(
        "complex_layout_property_update",
        "performance.complex-layout",
        4,
        {
            VIEWCOMPOSE_ENGINE: "viewComposeComplexLayoutUpdate",
            COMPOSE_ENGINE: "composeComplexLayoutUpdate",
            ANDROID_VIEWS_ENGINE: "androidViewsComplexLayoutUpdate",
        },
    ),
    ScenarioContract(
        "complex_layout_structure_update",
        "performance.complex-layout",
        4,
        {
            VIEWCOMPOSE_ENGINE: "viewComposeComplexLayoutStructureUpdate",
            COMPOSE_ENGINE: "composeComplexLayoutStructureUpdate",
            ANDROID_VIEWS_ENGINE: "androidViewsComplexLayoutStructureUpdate",
        },
    ),
    ScenarioContract(
        "shadow_list_scroll",
        "performance.shadow-list",
        2,
        {
            VIEWCOMPOSE_ENGINE: "viewComposeShadowListScroll",
            COMPOSE_ENGINE: "composeShadowListScroll",
        },
    ),
    ScenarioContract(
        "shadow_list_mutation",
        "performance.shadow-list",
        2,
        {
            VIEWCOMPOSE_ENGINE: "viewComposeShadowListMutation",
            COMPOSE_ENGINE: "composeShadowListMutation",
        },
    ),
    ScenarioContract(
        "shadow_complex_layout_scroll",
        "performance.shadow-complex-layout",
        3,
        {
            VIEWCOMPOSE_ENGINE: "viewComposeShadowComplexLayoutScroll",
            COMPOSE_ENGINE: "composeShadowComplexLayoutScroll",
        },
    ),
    ScenarioContract(
        "shadow_complex_layout_property_update",
        "performance.shadow-complex-layout",
        3,
        {
            VIEWCOMPOSE_ENGINE: "viewComposeShadowComplexLayoutUpdate",
            COMPOSE_ENGINE: "composeShadowComplexLayoutUpdate",
        },
    ),
)

SCENARIO_CONTRACTS = {
    contract.name: (contract.scenario_id, contract.workload_revision)
    for contract in SCENARIOS
}

SAMPLED_METRICS = ("frameDurationCpuMs", "frameOverrunMs")
SAMPLED_PERCENTILES = ("P50", "P95")
MEMORY_METRICS = ("memoryHeapSizeMaxKb", "memoryRssAnonMaxKb")
# Coefficient of variation is meaningful only for ratio-scale metrics with a strictly positive
# origin. Frame overrun is signed around zero, so its CV can explode as the mean approaches zero
# even when the underlying frame distribution is stable.
STABILITY_METRICS = ("frameDurationCpuMs",)


@dataclass(frozen=True)
class EngineMeasurement:
    """One absolute metric emitted by one engine."""

    scenario: str
    scenario_id: str
    workload_revision: int
    engine: str
    metric: str
    statistic: str
    value: float


@dataclass(frozen=True)
class Comparison:
    """One explicit subject/control comparison between two engines."""

    scenario: str
    scenario_id: str
    workload_revision: int
    metric: str
    statistic: str
    subject_engine: str
    control_engine: str
    subject_value: float
    control_value: float
    delta: float
    relative_percent: float | None


@dataclass(frozen=True)
class Stability:
    """Stability summary for one benchmark run series."""

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
    """Regression decision for current results compared with a baseline."""

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
    """Parse CLI arguments."""

    parser = argparse.ArgumentParser(
        description=(
            "Generate an engine-neutral ViewCompose performance report from AndroidX "
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
        help=(
            "Current benchmarkData.json file or directory containing split method results. "
            "Directory inputs merge matching contexts and reject duplicate benchmark names."
        ),
    )
    parser.add_argument(
        "--baseline",
        help=(
            "Previous revisioned engine-comparison v2 or compose-comparison v1 report "
            "file or directory used for regression checks."
        ),
    )
    parser.add_argument(
        "--policy",
        default=str(Path(__file__).with_name("benchmark_policy.json")),
        help="Regression policy JSON.",
    )
    parser.add_argument(
        "--output",
        default="build/reports/benchmarks/engine-comparison.md",
        help="Markdown report path.",
    )
    parser.add_argument(
        "--json-output",
        default="build/reports/benchmarks/engine-comparison.json",
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


def resolve_baseline_path(value: str | Path) -> Path:
    """Resolve a revisioned historical report path, preferring report v2."""

    path = Path(value)
    if path.is_file():
        return path
    if not path.exists():
        raise ValueError(f"Benchmark baseline path does not exist: {path}")
    candidates = list(path.rglob("*engine-comparison.json"))
    if not candidates:
        candidates = list(path.rglob("*compose-comparison.json"))
    if not candidates:
        raise ValueError(f"No revisioned engine comparison report found under: {path}")
    return max(candidates, key=lambda candidate: candidate.stat().st_mtime)


def load_json(path: Path) -> dict[str, Any]:
    """Load a JSON object and fail fast for non-object input."""

    with path.open(encoding="utf-8") as source:
        value = json.load(source)
    if not isinstance(value, dict):
        raise ValueError(f"Expected an object in {path}")
    return value


def load_current_result(value: str | Path) -> tuple[Path, dict[str, Any]]:
    """Load one result or merge same-context results captured with per-method cooldown.

    Directory merging is deterministic: every file must share device/system/compilation context and
    benchmark names must be unique. The report never silently selects one newer partial run or
    overwrites a failed retry.
    """

    path = Path(value)
    if path.is_file():
        return path, load_json(path)
    if not path.exists():
        raise ValueError(f"Benchmark result path does not exist: {path}")
    candidates = sorted(path.rglob("*benchmarkData.json"))
    if not candidates:
        raise ValueError(f"No benchmarkData.json found under: {path}")

    results = [(candidate, load_json(candidate)) for candidate in candidates]
    expected_context = context_identity(results[0][1])
    observed_cpu_locked: set[bool] = set()
    merged_entries: list[dict[str, Any]] = []
    owners: dict[str, Path] = {}
    for candidate, result in results:
        actual_context = context_identity(result)
        mismatches = context_mismatches(expected_context, actual_context)
        if mismatches:
            raise ValueError(
                "Split current benchmark contexts differ: "
                f"{', '.join(mismatches)}; "
                f"{results[0][0]}={expected_context!r}, "
                f"{candidate}={actual_context!r}",
            )
        for value in actual_context.get("cpuLockedSnapshots", []):
            if isinstance(value, bool):
                observed_cpu_locked.add(value)
        entries = result.get("benchmarks")
        if not isinstance(entries, list):
            raise ValueError(f"Macrobenchmark result has no benchmarks array: {candidate}")
        for entry in entries:
            if not isinstance(entry, dict) or not isinstance(entry.get("name"), str):
                raise ValueError(f"Invalid benchmark entry in: {candidate}")
            name = entry["name"]
            previous_owner = owners.get(name)
            if previous_owner is not None:
                raise ValueError(
                    f"Duplicate benchmark name {name!r} in split results: "
                    f"{previous_owner} and {candidate}",
                )
            owners[name] = candidate
            merged_entries.append(entry)

    merged = dict(results[0][1])
    merged["benchmarks"] = merged_entries
    merged["viewcomposeCpuLockedSnapshots"] = sorted(observed_cpu_locked)
    return path, merged


def benchmark_entries(result: dict[str, Any]) -> dict[str, dict[str, Any]]:
    """Extract entries and require every present scenario's complete engine set."""

    entries = result.get("benchmarks")
    if not isinstance(entries, list):
        raise ValueError("Macrobenchmark result has no benchmarks array.")
    indexed: dict[str, dict[str, Any]] = {}
    for entry in entries:
        if isinstance(entry, dict) and isinstance(entry.get("name"), str):
            indexed[entry["name"]] = entry
    incomplete: list[str] = []
    complete_scenarios = 0
    for contract in SCENARIOS:
        present = {
            engine: benchmark_name in indexed
            for engine, benchmark_name in contract.methods.items()
        }
        if all(present.values()):
            complete_scenarios += 1
        elif any(present.values()):
            missing = [
                contract.methods[engine]
                for engine, is_present in present.items()
                if not is_present
            ]
            incomplete.append(f"{contract.name}: {', '.join(missing)}")
    if incomplete:
        raise ValueError(
            "Benchmark result has incomplete engine sets: " + ", ".join(incomplete),
        )
    if complete_scenarios == 0:
        raise ValueError("Benchmark result contains no supported comparison scenarios.")
    return indexed


def available_scenarios(
    entries: dict[str, dict[str, Any]],
) -> tuple[ScenarioContract, ...]:
    """Return scenarios whose complete engine method set is present."""

    return tuple(
        contract
        for contract in SCENARIOS
        if all(name in entries for name in contract.methods.values())
    )


def metric_value(
    entry: dict[str, Any],
    metric: str,
    statistic: str,
) -> float | None:
    """Read a numeric value from sampledMetrics or metrics."""

    if statistic.startswith("P"):
        metric_data = entry.get("sampledMetrics", {}).get(metric, {})
    else:
        metric_data = entry.get("metrics", {}).get(metric, {})
    value = metric_data.get(statistic)
    return float(value) if isinstance(value, (int, float)) else None


def build_measurements(
    entries: dict[str, dict[str, Any]],
) -> list[EngineMeasurement]:
    """Build absolute engine measurements for every complete scenario."""

    measurements: list[EngineMeasurement] = []
    for contract in available_scenarios(entries):
        for engine, benchmark_name in contract.methods.items():
            entry = entries[benchmark_name]
            for metric in SAMPLED_METRICS:
                for statistic in SAMPLED_PERCENTILES:
                    value = metric_value(entry, metric, statistic)
                    if value is not None:
                        measurements.append(
                            EngineMeasurement(
                                scenario=contract.name,
                                scenario_id=contract.scenario_id,
                                workload_revision=contract.workload_revision,
                                engine=engine,
                                metric=metric,
                                statistic=statistic,
                                value=value,
                            ),
                        )
            for metric in MEMORY_METRICS:
                value = metric_value(entry, metric, "median")
                if value is not None:
                    measurements.append(
                        EngineMeasurement(
                            scenario=contract.name,
                            scenario_id=contract.scenario_id,
                            workload_revision=contract.workload_revision,
                            engine=engine,
                            metric=metric,
                            statistic="median",
                            value=value,
                        ),
                    )
    return measurements


def build_comparisons(
    entries: dict[str, dict[str, Any]],
) -> list[Comparison]:
    """Build explicit pairwise observations without hiding absolute engine values."""

    measurement_index = {
        (item.scenario, item.engine, item.metric, item.statistic): item
        for item in build_measurements(entries)
    }
    comparisons: list[Comparison] = []
    for contract in available_scenarios(entries):
        pairs = [(VIEWCOMPOSE_ENGINE, COMPOSE_ENGINE)]
        if ANDROID_VIEWS_ENGINE in contract.methods:
            pairs.append((VIEWCOMPOSE_ENGINE, ANDROID_VIEWS_ENGINE))
        for subject_engine, control_engine in pairs:
            for metric in SAMPLED_METRICS:
                for statistic in SAMPLED_PERCENTILES:
                    comparison = create_comparison(
                        contract=contract,
                        metric=metric,
                        statistic=statistic,
                        subject_engine=subject_engine,
                        control_engine=control_engine,
                        subject=measurement_index.get(
                            (contract.name, subject_engine, metric, statistic),
                        ),
                        control=measurement_index.get(
                            (contract.name, control_engine, metric, statistic),
                        ),
                    )
                    if comparison is not None:
                        comparisons.append(comparison)
            for metric in MEMORY_METRICS:
                comparison = create_comparison(
                    contract=contract,
                    metric=metric,
                    statistic="median",
                    subject_engine=subject_engine,
                    control_engine=control_engine,
                    subject=measurement_index.get(
                        (contract.name, subject_engine, metric, "median"),
                    ),
                    control=measurement_index.get(
                        (contract.name, control_engine, metric, "median"),
                    ),
                )
                if comparison is not None:
                    comparisons.append(comparison)
    return comparisons


def create_comparison(
    contract: ScenarioContract,
    metric: str,
    statistic: str,
    subject_engine: str,
    control_engine: str,
    subject: EngineMeasurement | None,
    control: EngineMeasurement | None,
) -> Comparison | None:
    """Create one explicit subject/control comparison, skipping missing metrics."""

    if subject is None or control is None:
        return None
    relative_percent = None
    if control.value > 0 and metric != "frameOverrunMs":
        relative_percent = (subject.value / control.value - 1.0) * 100.0
    return Comparison(
        scenario=contract.name,
        scenario_id=contract.scenario_id,
        workload_revision=contract.workload_revision,
        metric=metric,
        statistic=statistic,
        subject_engine=subject_engine,
        control_engine=control_engine,
        subject_value=subject.value,
        control_value=control.value,
        delta=subject.value - control.value,
        relative_percent=relative_percent,
    )


def percentile(values: list[float], percentile_value: int) -> float:
    """Calculate a percentile with linear interpolation."""

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
    """Calculate coefficient of variation, or return None when it is undefined."""

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
    """Build stability results from per-run P50 values."""

    stability: list[Stability] = []
    for contract in available_scenarios(entries):
        for engine, benchmark_name in contract.methods.items():
            entry = entries[benchmark_name]
            for metric in STABILITY_METRICS:
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
                        scenario=contract.name,
                        scenario_id=contract.scenario_id,
                        workload_revision=contract.workload_revision,
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
    """Extract device and compilation identity used for baseline comparisons."""

    context = result.get("context", {})
    if isinstance(context, dict) and "build" not in context:
        cpu_locked = context.get("cpuLocked")
        snapshots = context.get("cpuLockedSnapshots", [cpu_locked])
        return {
            "brand": context.get("brand"),
            "model": context.get("model"),
            "fingerprint": context.get("fingerprint"),
            "sdk": context.get("sdk"),
            "cpuLocked": cpu_locked,
            "cpuLockedSnapshots": snapshots,
            "cpuMaxFreqHz": context.get("cpuMaxFreqHz"),
            "compilationMode": context.get("compilationMode"),
            "clockPolicy": context.get("clockPolicy"),
        }
    build = context.get("build", {})
    version = build.get("version", {})
    cpu_locked = context.get("cpuLocked")
    payload = context.get("payload", {})
    clock_policy = payload.get("clockPolicy") if isinstance(payload, dict) else None
    snapshots = result.get("viewcomposeCpuLockedSnapshots", [cpu_locked])
    return {
        "brand": build.get("brand"),
        "model": build.get("model"),
        "fingerprint": build.get("fingerprint"),
        "sdk": version.get("sdk"),
        "cpuLocked": cpu_locked,
        "cpuLockedSnapshots": snapshots,
        "cpuMaxFreqHz": context.get("cpuMaxFreqHz"),
        "compilationMode": context.get("compilationMode"),
        "clockPolicy": clock_policy,
    }


def context_mismatches(
    expected: dict[str, Any],
    actual: dict[str, Any],
) -> list[str]:
    """Compare reproducible device, compilation, and clock policy.

    AndroidX reads ``scaling_min_freq`` while the instrumentation process starts. OEM launch
    boosting can therefore alternate ``cpuLocked`` on one unlocked consumer device. A caller may
    opt into the explicit, host-verified clock protocol carried in BenchmarkData payload. Legacy
    results without that protocol retain the strict ``cpuLocked`` comparison.
    """

    keys = ("brand", "model", "fingerprint", "sdk", "cpuMaxFreqHz", "compilationMode")
    mismatches = [
        key
        for key in keys
        if expected.get(key) != actual.get(key)
    ]
    expected_policy = expected.get("clockPolicy")
    actual_policy = actual.get("clockPolicy")
    if expected_policy is not None or actual_policy is not None:
        if expected_policy != actual_policy:
            mismatches.append("clockPolicy")
    elif expected.get("cpuLocked") != actual.get("cpuLocked"):
        mismatches.append("cpuLocked")
    return mismatches


def revisioned_baseline_comparisons(result: dict[str, Any]) -> list[Comparison]:
    """Load report-v2 comparisons or adapt accepted two-engine report-v1 rows."""

    entries = result.get("comparisons")
    if not isinstance(entries, list):
        raise ValueError(
            "Baseline must be a revisioned engine-comparison v2 or compose-comparison v1 "
            "report, not raw benchmarkData.json.",
        )
    comparisons: list[Comparison] = []
    for entry in entries:
        if not isinstance(entry, dict):
            raise ValueError("Baseline report contains an invalid comparison entry.")
        try:
            if "subject_engine" in entry:
                comparisons.append(Comparison(**entry))
            else:
                comparisons.append(
                    Comparison(
                        scenario=entry["scenario"],
                        scenario_id=entry["scenario_id"],
                        workload_revision=entry["workload_revision"],
                        metric=entry["metric"],
                        statistic=entry["statistic"],
                        subject_engine=VIEWCOMPOSE_ENGINE,
                        control_engine=COMPOSE_ENGINE,
                        subject_value=entry["viewcompose"],
                        control_value=entry["compose"],
                        delta=entry["delta"],
                        relative_percent=entry["relative_percent"],
                    ),
                )
        except (KeyError, TypeError, ValueError) as error:
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
    """Require current and baseline to share device, system, and compilation context."""

    current_identity = context_identity(current)
    baseline_identity = context_identity(baseline)
    mismatches = context_mismatches(baseline_identity, current_identity)
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
) -> dict[tuple[str, str, str, str, str], Comparison]:
    """Index comparisons by scenario, engine pair, metric, and statistic."""

    return {
        (
            comparison.scenario,
            comparison.subject_engine,
            comparison.control_engine,
            comparison.metric,
            comparison.statistic,
        ): comparison
        for comparison in comparisons
    }


def build_regressions(
    current: list[Comparison],
    baseline: list[Comparison],
    policy: dict[str, Any],
) -> list[Regression]:
    """Build regression-gate results from the policy.

    A failure requires both raw values and Compose-normalized ratios to cross thresholds,
    reducing false positives from common device slowdown.
    """

    current_index = comparison_index(current)
    baseline_index = comparison_index(baseline)
    thresholds = policy.get("regressionThresholds", {})
    regressions: list[Regression] = []
    for key, threshold in thresholds.items():
        metric, statistic = key.rsplit(".", 1)
        for contract in SCENARIOS:
            index_key = (
                contract.name,
                VIEWCOMPOSE_ENGINE,
                COMPOSE_ENGINE,
                metric,
                statistic,
            )
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
                    f"{contract.name}: "
                    f"{baseline_value.scenario_id}@{baseline_value.workload_revision} -> "
                    f"{current_value.scenario_id}@{current_value.workload_revision}",
                )
            raw_percent = percentage_change(
                current_value.subject_value,
                baseline_value.subject_value,
            )
            current_ratio = safe_ratio(
                current_value.subject_value,
                current_value.control_value,
            )
            baseline_ratio = safe_ratio(
                baseline_value.subject_value,
                baseline_value.control_value,
            )
            normalized_percent = percentage_change(current_ratio, baseline_ratio)
            delta = current_value.subject_value - baseline_value.subject_value
            maximum_percent = float(threshold["maxRegressionPercent"])
            minimum_delta = float(threshold["minimumDelta"])
            failed = (
                raw_percent > maximum_percent
                and normalized_percent > maximum_percent
                and delta > minimum_delta
            )
            regressions.append(
                Regression(
                    scenario=contract.name,
                    scenario_id=current_value.scenario_id,
                    workload_revision=current_value.workload_revision,
                    metric=metric,
                    statistic=statistic,
                    current=current_value.subject_value,
                    baseline=baseline_value.subject_value,
                    raw_regression_percent=raw_percent,
                    normalized_regression_percent=normalized_percent,
                    delta=delta,
                    failed=failed,
                ),
            )
    return regressions


def safe_ratio(numerator: float, denominator: float) -> float:
    """Calculate a ratio with a positive denominator."""

    if denominator <= 0:
        raise ValueError("Normalized comparison requires positive metric values.")
    return numerator / denominator


def percentage_change(current: float, baseline: float) -> float:
    """Calculate percentage change from baseline to current."""

    if baseline <= 0:
        raise ValueError("Regression comparison requires positive baseline values.")
    return (current / baseline - 1.0) * 100.0


def render_markdown(
    current_path: Path,
    baseline_path: Path | None,
    context: dict[str, Any],
    measurements: list[EngineMeasurement],
    comparisons: list[Comparison],
    stability: list[Stability],
    regressions: list[Regression],
) -> str:
    """Render a human-readable Markdown performance report."""

    lines = [
        "# ViewCompose Engine Performance Comparison",
        "",
        f"- Current: `{current_path}`",
        f"- Device: `{context.get('brand')} {context.get('model')}`",
        f"- SDK: `{context.get('sdk')}`",
        f"- Clock policy: `{context.get('clockPolicy') or 'androidx-cpu-lock-snapshot'}`",
        f"- AndroidX CPU locked snapshots: `{context.get('cpuLockedSnapshots')}`",
        f"- Compilation mode: `{context.get('compilationMode')}`",
    ]
    if baseline_path is not None:
        lines.append(f"- Baseline: `{baseline_path}`")
    lines.extend(
        [
            "",
            "## Absolute engine results",
            "",
            "| Workload | Action | Engine | Metric | Stat | Value |",
            "| --- | --- | --- | --- | ---: | ---: |",
        ],
    )
    for measurement in measurements:
        lines.append(
            f"| {measurement.scenario_id}@{measurement.workload_revision} | "
            f"{measurement.scenario} | "
            f"{ENGINE_DISPLAY_NAMES.get(measurement.engine, measurement.engine)} | "
            f"{measurement.metric} | {measurement.statistic} | "
            f"{measurement.value:.3f} |",
        )
    lines.extend(
        [
            "",
            "## Pairwise observations",
            "",
            "| Workload | Action | Subject | Control | Metric | Stat | Delta | Relative |",
            "| --- | --- | --- | --- | --- | ---: | ---: | ---: |",
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
            f"{comparison.scenario} | "
            f"{ENGINE_DISPLAY_NAMES.get(comparison.subject_engine, comparison.subject_engine)} | "
            f"{ENGINE_DISPLAY_NAMES.get(comparison.control_engine, comparison.control_engine)} | "
            f"{comparison.metric} | {comparison.statistic} | "
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
            f"{ENGINE_DISPLAY_NAMES.get(item.engine, item.engine)} | {item.metric} | "
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
            "Relative values name their subject and control explicitly and describe same-run "
            "observations; they are not cross-device scores. The longitudinal regression gate "
            "continues to use ViewCompose versus Compose.",
            "",
        ],
    )
    return "\n".join(lines)


def write_report(path: Path, content: str) -> None:
    """Write a report file and create parent directories."""

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    """Emit Markdown and JSON reports and optionally enforce the gate."""

    args = parse_args(sys.argv[1:] if argv is None else argv)
    if args.enforce and not args.baseline:
        raise ValueError("--enforce requires --baseline.")
    current_path, current_result = load_current_result(args.current)
    policy = load_json(Path(args.policy))
    current_entries = benchmark_entries(current_result)
    measurements = build_measurements(current_entries)
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
        baseline_path = resolve_baseline_path(args.baseline)
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
        measurements=measurements,
        comparisons=comparisons,
        stability=stability,
        regressions=regressions,
    )
    write_report(Path(args.output), markdown)
    summary = {
        "schemaVersion": 2,
        "current": str(current_path),
        "baseline": None if baseline_path is None else str(baseline_path),
        "context": context,
        "measurements": [asdict(measurement) for measurement in measurements],
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
