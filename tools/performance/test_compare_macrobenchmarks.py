"""性能报告脚本的单元测试。
Unit tests for the performance report script.
"""

import json
import tempfile
import unittest
from contextlib import redirect_stdout
from dataclasses import asdict, replace
from io import StringIO
from pathlib import Path

import compare_macrobenchmarks as comparison


def benchmark_entry(
    name: str,
    frame_p50: float,
    frame_p95: float,
    memory_kb: float,
) -> dict:
    """构造一个最小 Macrobenchmark 条目。
    Builds one minimal Macrobenchmark entry.
    """

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
    fingerprint: str = "device/build",
) -> dict:
    """构造包含所有配对场景的 Macrobenchmark 结果。
    Builds a Macrobenchmark result containing all paired scenarios.
    """

    entries = []
    for _, viewcompose_name, compose_name in comparison.SCENARIOS:
        entries.append(
            benchmark_entry(
                viewcompose_name,
                4.0 * viewcompose_multiplier,
                8.0 * viewcompose_multiplier,
                20_000 * viewcompose_multiplier,
            ),
        )
        entries.append(
            benchmark_entry(
                compose_name,
                2.0 * compose_multiplier,
                4.0 * compose_multiplier,
                10_000 * compose_multiplier,
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
            "cpuLocked": True,
            "cpuMaxFreqHz": 1,
            "compilationMode": "run-from-apk",
        },
        "benchmarks": entries,
    }


class CompareMacrobenchmarksTest(unittest.TestCase):
    """验证配对对比、归一化回归门禁和 CLI 输出。
    Verifies paired comparisons, normalized regression gating, and CLI output.
    """

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

    def test_builds_all_paired_comparisons(self) -> None:
        entries = comparison.benchmark_entries(result())
        comparisons = comparison.build_comparisons(entries)

        self.assertEqual(len(comparison.SCENARIOS) * 6, len(comparisons))
        list_scroll = next(
            item
            for item in comparisons
            if item.scenario == "list_scroll"
            and item.metric == "frameDurationCpuMs"
            and item.statistic == "P50"
        )
        self.assertEqual(4.0, list_scroll.viewcompose)
        self.assertEqual(2.0, list_scroll.compose)
        self.assertEqual(100.0, list_scroll.relative_percent)
        self.assertEqual("performance.list", list_scroll.scenario_id)
        self.assertEqual(2, list_scroll.workload_revision)
        self.assertTrue(
            any(item.scenario == "shadow_list_scroll" for item in comparisons),
        )

    def test_builds_partial_shadow_report_when_pairs_are_complete(self) -> None:
        partial = result()
        shadow_names = {
            name
            for scenario, viewcompose_name, compose_name in comparison.SCENARIOS
            if scenario.startswith("shadow_")
            for name in (viewcompose_name, compose_name)
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

        self.assertEqual(len(comparison.SCENARIOS) * 2, len(stability))
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

    def test_rejects_partial_scenario_without_compose_control(self) -> None:
        partial = result()
        partial["benchmarks"] = [
            entry
            for entry in partial["benchmarks"]
            if entry["name"] == "viewComposeShadowListScroll"
        ]

        with self.assertRaisesRegex(ValueError, "incomplete comparison pairs"):
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
                ),
            ),
        )

        regressions = comparison.build_regressions(
            current=current,
            baseline=baseline,
            policy=self.policy,
        )

        self.assertFalse(any(item.failed for item in regressions))

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

    def test_rejects_unrevisioned_raw_baseline(self) -> None:
        with self.assertRaisesRegex(ValueError, "revisioned compose-comparison.json"):
            comparison.revisioned_baseline_comparisons(result())

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
                "performance.list@2",
                markdown.read_text(encoding="utf-8"),
            )
            summary = json.loads(json_output.read_text(encoding="utf-8"))
            self.assertEqual(
                "NOT_RUN",
                summary["gateStatus"],
            )
            self.assertEqual(
                {"performance.list", "performance.complex-layout",
                 "performance.shadow-list", "performance.shadow-complex-layout"},
                {item["scenario_id"] for item in summary["comparisons"]},
            )


if __name__ == "__main__":
    unittest.main()
