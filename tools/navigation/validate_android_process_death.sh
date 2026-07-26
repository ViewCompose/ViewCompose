#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
package_name="com.gzq.uiframework"
activity_name="com.viewcompose.NavigationBackTestActivity"
component_name="$package_name/$activity_name"
certification_extra="com.viewcompose.extra.PROCESS_DEATH_CERTIFICATION"
status_prefix="PROCESS_DEATH|"

adb_binary="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/platform-tools/adb"
if [[ ! -x "$adb_binary" ]]; then
    adb_binary="$(command -v adb || true)"
fi
if [[ -z "$adb_binary" || ! -x "$adb_binary" ]]; then
    echo "adb was not found. Set ANDROID_HOME or ANDROID_SDK_ROOT." >&2
    exit 1
fi
if ! command -v android >/dev/null 2>&1; then
    echo "The Android CLI is required to inspect the restored UI." >&2
    exit 1
fi
if ! command -v ruby >/dev/null 2>&1; then
    echo "ruby is required to read Android CLI layout output." >&2
    exit 1
fi

device_serial="${ANDROID_SERIAL:-}"
if [[ -z "$device_serial" ]]; then
    device_serials="$(
        "$adb_binary" devices |
            awk '$1 !~ /^List/ && $2 == "device" { print $1 }'
    )"
    device_count="$(printf '%s\n' "$device_serials" | sed '/^$/d' | wc -l | tr -d ' ')"
    if [[ "$device_count" != "1" ]]; then
        echo "Connect exactly one Android device, or set ANDROID_SERIAL." >&2
        exit 1
    fi
    device_serial="$device_serials"
fi

cleanup() {
    "$adb_binary" -s "$device_serial" shell am task lock stop >/dev/null 2>&1 || true
    "$adb_binary" -s "$device_serial" shell am force-stop "$package_name" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

read_status() {
    android layout --device "$device_serial" 2>/dev/null |
        ruby -rjson -e '
            nodes = JSON.parse(STDIN.read)
            node = nodes.find do |candidate|
              candidate["text"]&.start_with?(ARGV.fetch(0))
            end
            puts node["text"] if node
        ' "$status_prefix"
}

wait_for_seeded_status() {
    local status=""
    for _ in $(seq 1 80); do
        status="$(read_status || true)"
        if [[ "$status" == *"top=details"* &&
            "$status" == *"home@"*"[saveable=11,handle=101]"* &&
            "$status" == *"details@"*"[saveable=29,handle=202]"* ]]; then
            printf '%s\n' "$status"
            return 0
        fi
        sleep 0.25
    done
    echo "Timed out waiting for the seeded navigation state. Last status: $status" >&2
    return 1
}

wait_for_process_exit() {
    local old_pid="$1"
    local current_pid=""
    for _ in $(seq 1 40); do
        current_pid="$(
            "$adb_binary" -s "$device_serial" shell pidof "$package_name" |
                tr -d '\r' ||
                true
        )"
        if [[ -z "$current_pid" || "$current_pid" != "$old_pid" ]]; then
            return 0
        fi
        sleep 0.1
    done
    echo "The application process was not killed." >&2
    return 1
}

cd "$repo_root"
./gradlew :app:installDebug --console=plain

"$adb_binary" -s "$device_serial" shell am force-stop "$package_name"
"$adb_binary" -s "$device_serial" shell am start -W \
    -n "$component_name" \
    -f 0x10008000 \
    --ez "$certification_extra" true

initial_status="$(wait_for_seeded_status)"
initial_pid="$(
    "$adb_binary" -s "$device_serial" shell pidof "$package_name" |
        tr -d '\r'
)"
task_id="$(
    "$adb_binary" -s "$device_serial" shell dumpsys activity activities |
        sed -n "s/.*${activity_name}.* t\\([0-9][0-9]*\\).*/\\1/p" |
        head -1 |
        tr -d '\r'
)"
if [[ -z "$initial_pid" || -z "$task_id" ]]; then
    echo "Unable to resolve the initial process or Android task." >&2
    exit 1
fi

"$adb_binary" -s "$device_serial" shell input keyevent HOME
sleep 1
"$adb_binary" -s "$device_serial" shell am kill "$package_name"
wait_for_process_exit "$initial_pid"

"$adb_binary" -s "$device_serial" shell am task lock "$task_id"
restored_status="$(wait_for_seeded_status)"
restored_pid="$(
    "$adb_binary" -s "$device_serial" shell pidof "$package_name" |
        tr -d '\r'
)"
"$adb_binary" -s "$device_serial" shell am task lock stop >/dev/null 2>&1 || true

if [[ -z "$restored_pid" || "$restored_pid" == "$initial_pid" ]]; then
    echo "The restored Activity did not run in a new process." >&2
    exit 1
fi

normalized_initial="$(
    printf '%s\n' "$initial_status" |
        sed -E 's/pid=[0-9]+/pid=<process>/'
)"
normalized_restored="$(
    printf '%s\n' "$restored_status" |
        sed -E 's/pid=[0-9]+/pid=<process>/'
)"
if [[ "$normalized_initial" != "$normalized_restored" ]]; then
    echo "Navigation state changed across process death." >&2
    echo "Before: $initial_status" >&2
    echo "After:  $restored_status" >&2
    exit 1
fi

echo "Process-death navigation restoration passed on $device_serial."
echo "PID: $initial_pid -> $restored_pid"
echo "$restored_status"
