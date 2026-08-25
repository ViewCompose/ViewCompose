"""Tests for the fixed Paging Macrobenchmark result contract."""

import unittest

import summarize_paging_macrobenchmark as paging


def benchmark_entry(name: str, run_scale: float = 1.0) -> dict:
    """Build one complete five-run Paging Macrobenchmark entry."""

    runs = [
        [4.0 * run_scale, 5.0 * run_scale, 6.0 * run_scale],
        [4.1 * run_scale, 5.1 * run_scale, 6.1 * run_scale],
        [3.9 * run_scale, 4.9 * run_scale, 5.9 * run_scale],
        [4.0 * run_scale, 5.0 * run_scale, 6.0 * run_scale],
        [4.1 * run_scale, 5.1 * run_scale, 6.1 * run_scale],
    ]
    return {
        "name": name,
        "sampledMetrics": {
            "frameDurationCpuMs": {
                "P50": 5.0 * run_scale,
                "P90": 5.8 * run_scale,
                "P95": 6.0 * run_scale,
                "P99": 6.1 * run_scale,
                "runs": runs,
            },
        },
        "metrics": {
            "memoryHeapSizeMaxKb": {"median": 12_000 * run_scale},
            "memoryRssAnonMaxKb": {"median": 24_000 * run_scale},
        },
    }


def result() -> dict:
    """Build the complete three-action contract."""

    return {
        "benchmarks": [
            benchmark_entry(method, index + 1.0)
            for index, method in enumerate(paging.PAGING_METHODS.values())
        ],
    }


class SummarizePagingMacrobenchmarkTest(unittest.TestCase):
    """Verify completeness, absolute values, and run-stability decisions."""

    def test_builds_complete_stable_summary(self) -> None:
        measurements = paging.build_summary(result(), 5, 0.15)

        self.assertEqual(list(paging.PAGING_METHODS), [item.action for item in measurements])
        self.assertEqual(5.0, measurements[0].frame_p50_ms)
        self.assertEqual(6.1, measurements[0].frame_p99_ms)
        self.assertEqual(12_000, measurements[0].median_peak_heap_kib)
        self.assertTrue(all(item.stable for item in measurements))

    def test_rejects_an_incomplete_method_set(self) -> None:
        incomplete = result()
        incomplete["benchmarks"] = incomplete["benchmarks"][:-1]

        with self.assertRaisesRegex(ValueError, "pagingScroll"):
            paging.build_summary(incomplete, 5, 0.15)

    def test_rejects_the_wrong_formal_iteration_count(self) -> None:
        invalid = result()
        invalid["benchmarks"][0]["sampledMetrics"]["frameDurationCpuMs"]["runs"].pop()

        with self.assertRaisesRegex(ValueError, "exactly 5"):
            paging.build_summary(invalid, 5, 0.15)

    def test_accepts_api_28_heap_only_memory_output(self) -> None:
        heap_only = result()
        for entry in heap_only["benchmarks"]:
            del entry["metrics"]["memoryRssAnonMaxKb"]

        measurements = paging.build_summary(heap_only, 5, 0.15)

        self.assertTrue(all(item.median_peak_rss_anon_kib is None for item in measurements))

    def test_marks_an_unstable_run_series(self) -> None:
        unstable = result()
        unstable["benchmarks"][0]["sampledMetrics"]["frameDurationCpuMs"]["runs"] = [
            [1.0, 1.0, 1.0],
            [2.0, 2.0, 2.0],
            [3.0, 3.0, 3.0],
            [4.0, 4.0, 4.0],
            [5.0, 5.0, 5.0],
        ]

        measurements = paging.build_summary(unstable, 5, 0.15)

        self.assertFalse(measurements[0].stable)
        self.assertGreater(measurements[0].run_p50_cv, 0.15)


if __name__ == "__main__":
    unittest.main()
