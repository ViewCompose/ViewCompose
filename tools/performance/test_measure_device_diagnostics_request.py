"""Unit tests for request-driven device diagnostics measurement helpers."""

import unittest

import measure_device_diagnostics_request as measurement


class MeasureDeviceDiagnosticsRequestTest(unittest.TestCase):
    def test_percentile_interpolates_and_summary_retains_samples(self) -> None:
        values = [10.0, 20.0, 30.0, 40.0]

        self.assertEqual(38.5, measurement.percentile(values, 0.95))
        self.assertEqual(
            {
                "minimum": 10.0,
                "p50": 25.0,
                "p95": 38.5,
                "maximum": 40.0,
                "samples": values,
            },
            measurement.summarize(values),
        )

    def test_validate_response_accepts_exact_protocol_identity(self) -> None:
        measurement.validate_response(
            {
                "protocolVersion": 7,
                "requestId": "request-1",
                "operation": "source",
                "packageName": "com.example",
            },
            "request-1",
            "source",
            "com.example",
        )

    def test_validate_response_rejects_stale_or_incompatible_report(self) -> None:
        with self.assertRaisesRegex(ValueError, "protocolVersion"):
            measurement.validate_response(
                {
                    "protocolVersion": 6,
                    "requestId": "stale",
                    "operation": "nodes",
                    "packageName": "com.other",
                },
                "request-1",
                "source",
                "com.example",
            )


if __name__ == "__main__":
    unittest.main()
