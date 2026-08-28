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

run_with_timeout() {
    local timeout_seconds="$1"
    shift
    perl -MPOSIX=setpgid -e '
        use strict;
        use warnings;
        my $seconds = shift @ARGV;
        my $pid = fork();
        die "fork failed: $!" unless defined $pid;
        if ($pid == 0) {
            setpgid(0, 0);
            exec @ARGV;
            exit 127;
        }
        $SIG{ALRM} = sub {
            kill "TERM", -$pid;
            select undef, undef, undef, 0.2;
            kill "KILL", -$pid;
            waitpid($pid, 0);
            exit 124;
        };
        alarm $seconds;
        waitpid($pid, 0);
        alarm 0;
        exit($? == -1 ? 127 : $? >> 8);
    ' "$timeout_seconds" "$@"
}

read_status() {
    # UIAutomator can occasionally stall on older vendor builds; bound each poll so the outer
    # certification deadline remains real and the next poll can recover.
    run_with_timeout 8 android layout --device "$device_serial" 2>/dev/null |
        ruby -rjson -e '
            payload = STDIN.read
            begin
              nodes = JSON.parse(payload)
            rescue JSON::ParserError
              exit
            end
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
        if [[ "$status" == *"active=process-death-account-stack"* &&
            "$status" == *"history=process-death-home-stack"* &&
            "$status" == *"top=process-death-security"* &&
            "$status" == *"process-death-home-stack{"*"home@"*"[saveable=11,handle=101]"* &&
            "$status" == *"confirmation@"*"[saveable=17,handle=151]"* &&
            "$status" == *"process-death-account-stack{"*"details@"*"[saveable=29,handle=202]"* &&
            "$status" == *"process-death-security@"*"[saveable=31,handle=252]"* &&
            "$status" == *"process-death-account@"*"[saveable=37,handle=303,arg=42]"* ]]; then
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
"$adb_binary" -s "$device_serial" shell am make-uid-idle --user current "$package_name"
"$adb_binary" -s "$device_serial" shell am kill --user current "$package_name"
if ! wait_for_process_exit "$initial_pid"; then
    # Some emulator builds keep a process at perceptible priority while UI automation is attached
    # and silently ignore `am kill`. A root SIGKILL on userdebug emulators preserves the task and
    # does not mark the package force-stopped, so Android can still restore the saved Activity.
    if "$adb_binary" -s "$device_serial" shell su 0 id >/dev/null 2>&1; then
        "$adb_binary" -s "$device_serial" shell su 0 kill -9 "$initial_pid"
    fi
    if ! wait_for_process_exit "$initial_pid"; then
        echo "The application process was not killed." >&2
        exit 1
    fi
fi

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
