#!/usr/bin/env python3
"""Measure one request-driven device diagnostics operation through the public adb protocol."""

from __future__ import annotations

import argparse
import json
import math
import statistics
import subprocess
import time
import uuid
from pathlib import Path
from typing import Any, Sequence


ACTION = "com.viewcompose.preview.action.REQUEST_DEVICE_DSL_SOURCE"
REPORT_PATH = "cache/viewcompose/device-dsl-source-v7.json"
PROTOCOL_VERSION = 7


def percentile(values: Sequence[float], quantile: float) -> float:
    """Return a linearly interpolated percentile for a non-empty sample."""

    if not values:
        raise ValueError("percentile requires at least one value")
    ordered = sorted(values)
    position = (len(ordered) - 1) * quantile
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return ordered[lower]
    weight = position - lower
    return ordered[lower] * (1.0 - weight) + ordered[upper] * weight


def summarize(values: Sequence[float]) -> dict[str, Any]:
    """Build stable absolute statistics while retaining the raw samples."""

    if not values:
        raise ValueError("summary requires at least one value")
    return {
        "minimum": min(values),
        "p50": statistics.median(values),
        "p95": percentile(values, 0.95),
        "maximum": max(values),
        "samples": list(values),
    }


def validate_response(
    response: dict[str, Any],
    request_id: str,
    operation: str,
    package_name: str,
) -> None:
    """Reject stale, incompatible, or cross-process protocol responses."""

    expected = {
        "protocolVersion": PROTOCOL_VERSION,
        "requestId": request_id,
        "operation": operation,
        "packageName": package_name,
    }
    mismatches = [
        f"{key}={response.get(key)!r}, expected {value!r}"
        for key, value in expected.items()
        if response.get(key) != value
    ]
    if mismatches:
        raise ValueError("invalid diagnostics response: " + "; ".join(mismatches))


class AdbDevice:
    """Small adb boundary used by the reproducible host-side measurement."""

    def __init__(self, adb: str, serial: str, package_name: str) -> None:
        self.adb = adb
        self.serial = serial
        self.package_name = package_name

    def run(self, *arguments: str, check: bool = True) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [self.adb, "-s", self.serial, *arguments],
            check=check,
            capture_output=True,
            text=True,
        )

    def shell(self, *arguments: str, check: bool = True) -> subprocess.CompletedProcess[str]:
        return self.run("shell", *arguments, check=check)

    def property(self, name: str) -> str:
        return self.shell("getprop", name).stdout.strip()

    def battery_temperature_celsius(self) -> float | None:
        output = self.shell("dumpsys", "battery").stdout
        for line in output.splitlines():
            if line.strip().startswith("temperature:"):
                return int(line.split(":", 1)[1].strip()) / 10.0
        return None

    def remove_report(self) -> None:
        self.shell("run-as", self.package_name, "rm", "-f", REPORT_PATH)

    def read_report(self) -> str:
        result = self.shell(
            "run-as",
            self.package_name,
            "cat",
            REPORT_PATH,
            check=False,
        )
        return result.stdout if result.returncode == 0 else ""

    def request(self, request_id: str, operation: str, session_id: int | None) -> None:
        arguments = [
            "am",
            "broadcast",
            "-a",
            ACTION,
            "-p",
            self.package_name,
            "--es",
            "request_id",
            request_id,
            "--es",
            "operation",
            operation,
        ]
        if session_id is not None:
            arguments += ["--el", "session_id", str(session_id)]
        self.shell(*arguments)


def measure_once(
    device: AdbDevice,
    operation: str,
    session_id: int | None,
    timeout_seconds: float,
    poll_seconds: float,
) -> tuple[float, int, dict[str, Any]]:
    """Measure broadcast-through-matching-report latency in host monotonic time."""

    request_id = f"phase6-{uuid.uuid4().hex}"
    device.remove_report()
    started_ns = time.monotonic_ns()
    device.request(request_id, operation, session_id)
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        payload = device.read_report()
        if payload:
            try:
                response = json.loads(payload)
            except json.JSONDecodeError:
                response = None
            if isinstance(response, dict) and response.get("requestId") == request_id:
                elapsed_ms = (time.monotonic_ns() - started_ns) / 1_000_000.0
                validate_response(response, request_id, operation, device.package_name)
                return elapsed_ms, len(payload.encode("utf-8")), response
        time.sleep(poll_seconds)
    raise TimeoutError(f"diagnostics request {request_id} did not produce a matching report")


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--package", default="com.gzq.uiframework")
    parser.add_argument("--operation", choices=("source", "nodes"), default="source")
    parser.add_argument("--session-id", type=int)
    parser.add_argument("--iterations", type=int, default=20)
    parser.add_argument("--warmups", type=int, default=5)
    parser.add_argument("--timeout-seconds", type=float, default=10.0)
    parser.add_argument("--poll-millis", type=float, default=10.0)
    parser.add_argument("--clock-policy", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--adb", default="adb")
    return parser.parse_args()


def main() -> None:
    args = parse_arguments()
    if args.iterations <= 0 or args.warmups < 0:
        raise ValueError("iterations must be positive and warmups cannot be negative")
    if args.operation == "nodes" and args.session_id is None:
        raise ValueError("--session-id is required for the nodes operation")
    device = AdbDevice(args.adb, args.serial, args.package)
    device.shell("pidof", args.package)
    start_temperature = device.battery_temperature_celsius()
    latency_ms: list[float] = []
    response_bytes: list[float] = []
    last_response: dict[str, Any] = {}
    for index in range(args.warmups + args.iterations):
        elapsed, size, response = measure_once(
            device,
            args.operation,
            args.session_id,
            args.timeout_seconds,
            args.poll_millis / 1_000.0,
        )
        if index >= args.warmups:
            latency_ms.append(round(elapsed, 3))
            response_bytes.append(float(size))
        last_response = response
    result = {
        "schemaVersion": 1,
        "measurement": "host-adb-broadcast-through-matching-report",
        "clockPolicy": args.clock_policy,
        "request": {
            "operation": args.operation,
            "sessionId": args.session_id,
            "warmups": args.warmups,
            "iterations": args.iterations,
        },
        "device": {
            "serial": args.serial,
            "manufacturer": device.property("ro.product.manufacturer"),
            "model": device.property("ro.product.model"),
            "sdk": int(device.property("ro.build.version.sdk")),
            "fingerprint": device.property("ro.build.fingerprint"),
            "batteryTemperatureCelsiusStart": start_temperature,
            "batteryTemperatureCelsiusEnd": device.battery_temperature_celsius(),
        },
        "latencyMilliseconds": summarize(latency_ms),
        "responseBytes": summarize(response_bytes),
        "lastResponse": {
            "processId": last_response.get("processId"),
            "sessionCount": len(last_response.get("sessions", [])),
        },
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
