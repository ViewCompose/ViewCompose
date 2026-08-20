"""Unit tests for the engine-neutral performance report script."""

import json
import re
import tempfile
import unittest
from contextlib import redirect_stdout
from dataclasses import asdict, replace
from io import StringIO
from pathlib import Path
from typing import Optional

import compare_macrobenchmarks as comparison


def benchmark_entry(
    name: str,
    frame_p50: float,
    frame_p95: float,
    memory_kb: float,
) -> dict:
    """Build one minimal Macrobenchmark entry."""

    return {
        "name": name,
        "metrics": {
            "memoryHeapSizeMaxKb": {
                "median": memory_kb,
                "runs": [memory_kb, memory_kb * 1.01],
            },
            "memoryRssAnonMaxKb": {
                "median": memory_kb * 2,
                "runs": [memory_kb * 2, memory_kb * 2.01],
            },
        },
        "sampledMetrics": {
            "frameDurationCpuMs": {
                "P50": frame_p50,
                "P95": frame_p95,
                "runs": [
                    [frame_p50 * 0.9, frame_p50, frame_p95],
                    [frame_p50, frame_p50 * 1.05, frame_p95 * 1.02],
                ],
            },
            "frameOverrunMs": {
                "P50": -10.0,
                "P95": -2.0,
                "runs": [[-11.0, -10.0, -2.0], [-10.5, -9.5, -1.5]],
            },
        },
    }


def result(
    viewcompose_multiplier: float = 1.0,
    compose_multiplier: float = 1.0,
    android_views_multiplier: float = 1.0,
    fingerprint: str = "device/build",
    cpu_locked: bool = True,
    clock_policy: Optional[str] = None,
) -> dict:
    """Build a Macrobenchmark result containing each scenario's required engines."""

    entries = []
    engine_values = {
        comparison.VIEWCOMPOSE_ENGINE: (4.0, viewcompose_multiplier),
        comparison.COMPOSE_ENGINE: (2.0, compose_multiplier),
        comparison.ANDROID_VIEWS_ENGINE: (1.5, android_views_multiplier),
    }
    for contract in comparison.SCENARIOS:
        for engine, benchmark_name in contract.methods.items():
            base, multiplier = engine_values[engine]
            entries.append(
                benchmark_entry(
                    benchmark_name,
                    base * multiplier,
                    base * 2 * multiplier,
                    base * 5_000 * multiplier,
                ),
            )
    return {
        "context": {
            "build": {
                "brand": "test",
                "model": "device",
                "fingerprint": fingerprint,
                "version": {"sdk": 36},
            },
            "cpuLocked": cpu_locked,
            "cpuMaxFreqHz": 1,
            "compilationMode": "run-from-apk",
            "payload": {} if clock_policy is None else {"clockPolicy": clock_policy},
        },
        "benchmarks": entries,
    }


class CompareMacrobenchmarksTest(unittest.TestCase):
    """Verify three-engine results, compatibility, stability, and regression gating."""

    def setUp(self) -> None:
        self.policy = {
            "maxRunCoefficientOfVariation": 0.15,
            "regressionThresholds": {
                "frameDurationCpuMs.P50": {
                    "maxRegressionPercent": 10.0,
                    "minimumDelta": 0.3,
                },
            },
        }

    def test_report_contracts_match_demo_registry_revisions(self) -> None:
        registry_path = (
            Path(__file__).resolve().parents[2]
            / "app/src/main/java/com/viewcompose/demo/registry/DemoScenarioRegistry.kt"
        )
        source = registry_path.read_text(encoding="utf-8")
        registry_revisions = {}
        cursor = 0
        while True:
            start = source.find("performanceScenario(", cursor)
            if start < 0:
                break
            depth = 0
            end = start
            while end < len(source):
                character = source[end]
                if character == "(":
                    depth += 1
                elif character == ")":
                    depth -= 1
                    if depth == 0:
                        end += 1
                        break
                end += 1
            block = source[start:end]
            scenario_id = re.search(r"id\s*=\s*DemoScenarioIds\.(\w+)", block)
            revision = re.search(r"benchmarkRevision\s*=\s*(\d+)", block)
            if scenario_id:
                registry_revisions[scenario_id.group(1)] = (
                    int(revision.group(1)) if revision else 1
                )
            cursor = end

        kotlin_ids = {
            "performance.list": "PerformanceList",
            "performance.complex-layout": "PerformanceComplexLayout",
            "performance.shadow-list": "PerformanceShadowList",
            "performance.shadow-complex-layout": "PerformanceShadowComplexLayout",
        }
        report_revisions = {}
        for scenario_id, workload_revision in comparison.SCENARIO_CONTRACTS.values():
            previous = report_revisions.setdefault(scenario_id, workload_revision)
            self.assertEqual(previous, workload_revision)

        self.assertEqual(
            {
                scenario_id: registry_revisions[kotlin_id]
                for scenario_id, kotlin_id in kotlin_ids.items()
            },
            report_revisions,
        )

    def test_builds_absolute_measurements_and_explicit_comparisons(self) -> None:
        entries = comparison.benchmark_entries(result())
        measurements = comparison.build_measurements(entries)
        comparisons = comparison.build_comparisons(entries)

        self.assertEqual(
            sum(len(contract.methods) * 6 for contract in comparison.SCENARIOS),
            len(measurements),
        )
        self.assertEqual(
            sum(
                (len(contract.methods) - 1) * 6
                for contract in comparison.SCENARIOS
            ),
            len(comparisons),
        )
        list_scroll = next(
            item
            for item in comparisons
            if item.scenario == "list_scroll"
            and item.metric == "frameDurationCpuMs"
            and item.statistic == "P50"
            and item.control_engine == comparison.COMPOSE_ENGINE
        )
        self.assertEqual(4.0, list_scroll.subject_value)
        self.assertEqual(2.0, list_scroll.control_value)
        self.assertEqual(100.0, list_scroll.relative_percent)
        self.assertEqual("performance.list", list_scroll.scenario_id)
        self.assertEqual(5, list_scroll.workload_revision)
        native_control = next(
            item
            for item in comparisons
            if item.scenario == "list_scroll"
            and item.metric == "frameDurationCpuMs"
            and item.statistic == "P50"
            and item.control_engine == comparison.ANDROID_VIEWS_ENGINE
        )
        self.assertEqual(1.5, native_control.control_value)
        self.assertTrue(
            any(item.scenario == "shadow_list_scroll" for item in comparisons),
        )
        constraint_control = next(
            item
            for item in comparisons
            if item.scenario == "constraint_layout_topology_100"
            and item.metric == "frameDurationCpuMs"
            and item.statistic == "P50"
        )
        self.assertEqual(comparison.ANDROID_VIEWS_ENGINE, constraint_control.control_engine)
        self.assertEqual(4, constraint_control.workload_revision)

    def test_builds_partial_shadow_report_when_pairs_are_complete(self) -> None:
        partial = result()
        shadow_names = {
            name
            for contract in comparison.SCENARIOS
            if contract.name.startswith("shadow_")
            for name in contract.methods.values()
        }
        partial["benchmarks"] = [
            entry
            for entry in partial["benchmarks"]
            if entry["name"] in shadow_names
        ]

        entries = comparison.benchmark_entries(partial)
        comparisons = comparison.build_comparisons(entries)
        stability = comparison.build_stability(entries, 0.15)

        self.assertEqual(4 * 6, len(comparisons))
        self.assertTrue(
            all(item.scenario.startswith("shadow_") for item in comparisons),
        )
        self.assertTrue(
            all(item.scenario.startswith("shadow_") for item in stability),
        )

    def test_stability_excludes_signed_frame_overrun(self) -> None:
        entries = comparison.benchmark_entries(result())

        stability = comparison.build_stability(entries, 0.15)

        self.assertEqual(
            sum(len(contract.methods) for contract in comparison.SCENARIOS),
            len(stability),
        )
        self.assertEqual(
            {"frameDurationCpuMs"},
            {item.metric for item in stability},
        )

    def test_merges_split_method_results_from_one_context(self) -> None:
        complete = result()
        midpoint = len(complete["benchmarks"]) // 2
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            first = dict(complete)
            first["benchmarks"] = complete["benchmarks"][:midpoint]
            second = dict(complete)
            second["benchmarks"] = complete["benchmarks"][midpoint:]
            (root / "first-benchmarkData.json").write_text(
                json.dumps(first),
                encoding="utf-8",
            )
            (root / "second-benchmarkData.json").write_text(
                json.dumps(second),
                encoding="utf-8",
            )

            source, merged = comparison.load_current_result(root)

        self.assertEqual(root, source)
        self.assertEqual(complete["benchmarks"], merged["benchmarks"])

    def test_rejects_duplicate_method_in_split_results(self) -> None:
        complete = result()
        partial = dict(complete)
        partial["benchmarks"] = complete["benchmarks"][:1]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in ("first", "second"):
                (root / f"{name}-benchmarkData.json").write_text(
                    json.dumps(partial),
                    encoding="utf-8",
                )

            with self.assertRaisesRegex(ValueError, "Duplicate benchmark name"):
                comparison.load_current_result(root)

    def test_rejects_context_mismatch_in_split_results(self) -> None:
        first = result(fingerprint="device/first")
        first["benchmarks"] = first["benchmarks"][:1]
        second = result(fingerprint="device/second")
        second["benchmarks"] = second["benchmarks"][1:2]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "first-benchmarkData.json").write_text(
                json.dumps(first),
                encoding="utf-8",
            )
            (root / "second-benchmarkData.json").write_text(
                json.dumps(second),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(ValueError, "contexts differ"):
                comparison.load_current_result(root)

    def test_merges_transient_cpu_lock_snapshots_under_explicit_clock_policy(self) -> None:
        first = result(cpu_locked=False, clock_policy="unlocked-dvfs-preflight-v1")
        first["benchmarks"] = first["benchmarks"][:1]
        second = result(cpu_locked=True, clock_policy="unlocked-dvfs-preflight-v1")
        second["benchmarks"] = second["benchmarks"][1:2]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "first-benchmarkData.json").write_text(
                json.dumps(first),
                encoding="utf-8",
            )
            (root / "second-benchmarkData.json").write_text(
                json.dumps(second),
                encoding="utf-8",
            )

            _, merged = comparison.load_current_result(root)

        identity = comparison.context_identity(merged)
        self.assertEqual("unlocked-dvfs-preflight-v1", identity["clockPolicy"])
        self.assertEqual([False, True], identity["cpuLockedSnapshots"])

    def test_rejects_transient_cpu_lock_mismatch_without_clock_policy(self) -> None:
        first = result(cpu_locked=False)
        first["benchmarks"] = first["benchmarks"][:1]
        second = result(cpu_locked=True)
        second["benchmarks"] = second["benchmarks"][1:2]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "first-benchmarkData.json").write_text(
                json.dumps(first),
                encoding="utf-8",
            )
            (root / "second-benchmarkData.json").write_text(
                json.dumps(second),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(ValueError, "cpuLocked"):
                comparison.load_current_result(root)

    def test_rejects_explicit_clock_policy_mismatch(self) -> None:
        first = comparison.context_identity(
            result(clock_policy="unlocked-dvfs-preflight-v1"),
        )
        second = comparison.context_identity(
            result(clock_policy="locked-clocks-v1"),
        )

        self.assertEqual(
            ["clockPolicy"],
            comparison.context_mismatches(first, second),
        )

    def test_rejects_partial_shadow_scenario_without_compose_control(self) -> None:
        partial = result()
        partial["benchmarks"] = [
            entry
            for entry in partial["benchmarks"]
            if entry["name"] == "viewComposeShadowListScroll"
        ]

        with self.assertRaisesRegex(ValueError, "incomplete engine sets"):
            comparison.benchmark_entries(partial)

    def test_rejects_non_shadow_scenario_without_android_views_control(self) -> None:
        partial = result()
        partial["benchmarks"] = [
            entry
            for entry in partial["benchmarks"]
            if entry["name"] in {
                "viewComposeListScroll",
                "composeListScroll",
            }
        ]

        with self.assertRaisesRegex(ValueError, "androidViewsListScroll"):
            comparison.benchmark_entries(partial)

    def test_detects_regression_when_compose_control_is_stable(self) -> None:
        baseline = comparison.build_comparisons(
            comparison.benchmark_entries(result()),
        )
        current = comparison.build_comparisons(
            comparison.benchmark_entries(
                result(viewcompose_multiplier=1.25),
            ),
        )

        regressions = comparison.build_regressions(
            current=current,
            baseline=baseline,
            policy=self.policy,
        )

        self.assertTrue(all(item.failed for item in regressions))

    def test_common_device_slowdown_does_not_fail_normalized_gate(self) -> None:
        baseline = comparison.build_comparisons(
            comparison.benchmark_entries(result()),
        )
        current = comparison.build_comparisons(
            comparison.benchmark_entries(
                result(
                    viewcompose_multiplier=1.25,
                    compose_multiplier=1.25,
                    android_views_multiplier=1.25,
                ),
            ),
        )

        regressions = comparison.build_regressions(
            current=current,
            baseline=baseline,
            policy=self.policy,
        )

        self.assertFalse(any(item.failed for item in regressions))

    def test_uses_android_views_for_scenarios_without_compose(self) -> None:
        baseline = comparison.build_comparisons(
            comparison.benchmark_entries(result()),
        )
        current = comparison.build_comparisons(
            comparison.benchmark_entries(
                result(viewcompose_multiplier=1.25),
            ),
        )

        regressions = comparison.build_regressions(
            current=current,
            baseline=baseline,
            policy=self.policy,
        )
        constraint_regressions = [
            item
            for item in regressions
            if item.scenario.startswith("constraint_layout_")
        ]

        self.assertTrue(constraint_regressions)
        self.assertTrue(all(item.failed for item in constraint_regressions))
        self.assertEqual(
            {comparison.ANDROID_VIEWS_ENGINE},
            {item.control_engine for item in constraint_regressions},
        )

    def test_unstable_control_pair_is_inconclusive_not_failed(self) -> None:
        baseline_entries = comparison.benchmark_entries(result())
        current_entries = comparison.benchmark_entries(
            result(viewcompose_multiplier=1.25),
        )
        baseline_stability = comparison.build_stability(baseline_entries, 0.15)
        current_stability = [
            replace(item, stable=False)
            if item.scenario == "constraint_layout_topology_100"
            and item.engine == comparison.VIEWCOMPOSE_ENGINE
            else item
            for item in comparison.build_stability(current_entries, 0.15)
        ]

        regressions = comparison.build_regressions(
            current=comparison.build_comparisons(current_entries),
            baseline=comparison.build_comparisons(baseline_entries),
            policy=self.policy,
            current_stability=current_stability,
            baseline_stability=baseline_stability,
        )
        topology = next(
            item
            for item in regressions
            if item.scenario == "constraint_layout_topology_100"
        )

        self.assertFalse(topology.interpretable)
        self.assertFalse(topology.failed)

    def test_rejects_cross_revision_regression_comparison(self) -> None:
        baseline = comparison.build_comparisons(
            comparison.benchmark_entries(result()),
        )
        current = [
            replace(item, workload_revision=item.workload_revision + 1)
            if item.scenario == "list_scroll"
            else item
            for item in baseline
        ]

        with self.assertRaisesRegex(ValueError, "different workload contracts"):
            comparison.build_regressions(
                current=current,
                baseline=baseline,
                policy=self.policy,
            )

    def test_revisioned_baseline_report_preserves_workload_contracts(self) -> None:
        raw = result()
        baseline_report = {
            "context": comparison.context_identity(raw),
            "comparisons": [
                asdict(item)
                for item in comparison.build_comparisons(
                    comparison.benchmark_entries(raw),
                )
            ],
        }

        loaded = comparison.revisioned_baseline_comparisons(baseline_report)

        self.assertTrue(loaded)
        self.assertTrue(
            all(
                item.workload_revision
                == comparison.SCENARIO_CONTRACTS[item.scenario][1]
                for item in loaded
            ),
        )

    def test_loads_accepted_two_engine_v1_baseline(self) -> None:
        raw = result()
        current = comparison.build_comparisons(
            comparison.benchmark_entries(raw),
        )
        legacy_rows = []
        for item in current:
            if item.control_engine != comparison.COMPOSE_ENGINE:
                continue
            legacy_rows.append(
                {
                    "scenario": item.scenario,
                    "scenario_id": item.scenario_id,
                    "workload_revision": item.workload_revision,
                    "metric": item.metric,
                    "statistic": item.statistic,
                    "viewcompose": item.subject_value,
                    "compose": item.control_value,
                    "delta": item.delta,
                    "relative_percent": item.relative_percent,
                },
            )

        loaded = comparison.revisioned_baseline_comparisons(
            {"comparisons": legacy_rows},
        )

        self.assertTrue(loaded)
        self.assertEqual(
            {comparison.COMPOSE_ENGINE},
            {item.control_engine for item in loaded},
        )

    def test_rejects_unrevisioned_raw_baseline(self) -> None:
        with self.assertRaisesRegex(ValueError, "revisioned engine-comparison"):
            comparison.revisioned_baseline_comparisons(result())

    def test_baseline_directory_prefers_engine_report_v2_over_newer_v1(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report_v2 = root / "engine-comparison.json"
            report_v1 = root / "compose-comparison.json"
            report_v2.write_text("{}", encoding="utf-8")
            report_v1.write_text("{}", encoding="utf-8")
            report_v1.touch()

            self.assertEqual(report_v2, comparison.resolve_baseline_path(root))

    def test_cli_writes_markdown_and_json_reports(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            current = root / "benchmarkData.json"
            current.write_text(json.dumps(result()), encoding="utf-8")
            policy = root / "policy.json"
            policy.write_text(json.dumps(self.policy), encoding="utf-8")
            markdown = root / "report.md"
            json_output = root / "report.json"

            with redirect_stdout(StringIO()):
                exit_code = comparison.main(
                    [
                        str(current),
                        "--policy",
                        str(policy),
                        "--output",
                        str(markdown),
                        "--json-output",
                        str(json_output),
                    ],
                )

            self.assertEqual(0, exit_code)
            self.assertIn(
                "performance.list@5",
                markdown.read_text(encoding="utf-8"),
            )
            summary = json.loads(json_output.read_text(encoding="utf-8"))
            self.assertEqual(2, summary["schemaVersion"])
            self.assertEqual(
                "NOT_RUN",
                summary["gateStatus"],
            )
            self.assertIn(
                comparison.ANDROID_VIEWS_ENGINE,
                {item["engine"] for item in summary["measurements"]},
            )
            self.assertEqual(
                {"performance.list", "performance.complex-layout",
                 "performance.shadow-list", "performance.shadow-complex-layout"},
                {item["scenario_id"] for item in summary["comparisons"]},
            )


if __name__ == "__main__":
    unittest.main()
