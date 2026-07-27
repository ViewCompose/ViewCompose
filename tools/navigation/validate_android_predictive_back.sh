#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
adb_binary="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/platform-tools/adb"
if [[ ! -x "$adb_binary" ]]; then
    adb_binary="$(command -v adb || true)"
fi
if [[ -z "$adb_binary" || ! -x "$adb_binary" ]]; then
    echo "adb was not found. Set ANDROID_HOME or ANDROID_SDK_ROOT." >&2
    exit 1
fi

device_serial="${ANDROID_SERIAL:-}"
if [[ -z "$device_serial" ]]; then
    emulator_serials="$(
        "$adb_binary" devices |
            awk '$1 ~ /^emulator-/ && $2 == "device" { print $1 }'
    )"
    emulator_count="$(printf '%s\n' "$emulator_serials" | sed '/^$/d' | wc -l | tr -d ' ')"
    if [[ "$emulator_count" != "1" ]]; then
        echo "Connect exactly one emulator, or set ANDROID_SERIAL." >&2
        exit 1
    fi
    device_serial="$emulator_serials"
fi
if [[ "$device_serial" != emulator-* ]]; then
    echo "The platform cancellation runner requires an Android Emulator." >&2
    exit 1
fi

api_level="$("$adb_binary" -s "$device_serial" shell getprop ro.build.version.sdk | tr -d '\r')"
navigation_mode="$(
    "$adb_binary" -s "$device_serial" shell settings get secure navigation_mode |
        tr -d '\r'
)"
if (( api_level < 34 )); then
    echo "Predictive-back progress requires API 34 or newer; found API $api_level." >&2
    exit 1
fi
if [[ "$navigation_mode" != "2" ]]; then
    echo "Gesture navigation is required; navigation_mode=$navigation_mode." >&2
    exit 1
fi

console_port="${device_serial#emulator-}"
running_dir="$HOME/Library/Caches/TemporaryItems/avd/running"
emulator_ini=""
for candidate in "$running_dir"/pid_*.ini; do
    [[ -f "$candidate" ]] || continue
    if grep -q "^port.serial=$console_port$" "$candidate"; then
        emulator_ini="$candidate"
        break
    fi
done
if [[ -z "$emulator_ini" ]]; then
    echo "The running emulator metadata could not be located." >&2
    exit 1
fi

grpc_port="$(sed -n 's/^grpc.port=//p' "$emulator_ini")"
grpc_token="$(sed -n 's/^grpc.token=//p' "$emulator_ini")"
if [[ -z "$grpc_port" || -z "$grpc_token" ]]; then
    echo "The emulator gRPC endpoint is unavailable." >&2
    exit 1
fi
if ! curl --version | head -1 | grep -q 'nghttp2'; then
    echo "curl with HTTP/2 support is required." >&2
    exit 1
fi
if ! command -v ruby >/dev/null 2>&1; then
    echo "ruby is required to encode emulator touch events." >&2
    exit 1
fi

display_size="$(
    "$adb_binary" -s "$device_serial" shell wm size |
        sed -n 's/.*size: //p' |
        tail -1 |
        tr -d '\r'
)"
display_width="${display_size%x*}"
display_height="${display_size#*x}"
if [[ -z "$display_width" || -z "$display_height" ]]; then
    echo "Unable to read the emulator display size." >&2
    exit 1
fi

send_touch() {
    local touch_x="$1"
    local touch_y="$2"
    local pressure="$3"
    local response_headers
    response_headers="$(
        ruby -e '
            def varint(value)
              bytes = []
              loop do
                byte = value & 0x7f
                value >>= 7
                byte |= 0x80 unless value.zero?
                bytes << byte
                break if value.zero?
              end
              bytes.pack("C*")
            end

            x, y, pressure = ARGV.map(&:to_i)
            touch = "\x08".b + varint(x) +
              "\x10".b + varint(y) +
              "\x18\x01".b
            touch << "\x20".b + varint(pressure) unless pressure.zero?
            touch << "\x38\x01".b
            event = "\x0a".b + varint(touch.bytesize) + touch
            STDOUT.write("\x00".b + [event.bytesize].pack("N") + event)
        ' "$touch_x" "$touch_y" "$pressure" |
            curl \
                --http2-prior-knowledge \
                --silent \
                --show-error \
                --dump-header - \
                --output /dev/null \
                -H "authorization: Bearer $grpc_token" \
                -H 'content-type: application/grpc' \
                -H 'te: trailers' \
                --data-binary @- \
                "http://127.0.0.1:$grpc_port/android.emulation.control.EmulatorController/sendTouch"
    )"
    if ! printf '%s\n' "$response_headers" | grep -qi '^grpc-status: 0'; then
        echo "The emulator rejected a touch event." >&2
        return 1
    fi
}

send_cancel_gesture() {
    local start_x=1
    local center_y=$((display_height / 2))
    local peak_x=$((display_width * 32 / 100))
    local move_steps=12
    local step
    local touch_x

    send_touch "$start_x" "$center_y" 1
    for step in $(seq 1 "$move_steps"); do
        touch_x=$((start_x + (peak_x - start_x) * step / move_steps))
        sleep 0.04
        send_touch "$touch_x" "$center_y" 1
    done
    sleep 0.12
    for step in $(seq $((move_steps - 1)) -1 0); do
        touch_x=$((start_x + (peak_x - start_x) * step / move_steps))
        sleep 0.04
        send_touch "$touch_x" "$center_y" 1
    done
    sleep 0.04
    send_touch "$start_x" "$center_y" 0
}

send_commit_gesture() {
    local start_x=1
    local center_y=$((display_height / 2))
    local end_x=$((display_width * 90 / 100))
    local move_steps=18
    local step
    local touch_x

    send_touch "$start_x" "$center_y" 1
    for step in $(seq 1 "$move_steps"); do
        touch_x=$((start_x + (end_x - start_x) * step / move_steps))
        sleep 0.02
        send_touch "$touch_x" "$center_y" 1
    done
    sleep 0.04
    send_touch "$end_x" "$center_y" 0
}

log_tag="ViewComposeNavigationBack"
original_back_animation="$(
    "$adb_binary" -s "$device_serial" shell settings get global enable_back_animation |
        tr -d '\r'
)"
gradle_pid=""

restore_back_animation() {
    if [[ -z "$original_back_animation" || "$original_back_animation" == "null" ]]; then
        "$adb_binary" -s "$device_serial" shell \
            settings delete global enable_back_animation >/dev/null
    else
        "$adb_binary" -s "$device_serial" shell \
            settings put global enable_back_animation "$original_back_animation"
    fi
}

cleanup() {
    if [[ -n "$gradle_pid" ]] && kill -0 "$gradle_pid" >/dev/null 2>&1; then
        kill "$gradle_pid" >/dev/null 2>&1 || true
        wait "$gradle_pid" >/dev/null 2>&1 || true
    fi
    restore_back_animation
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

"$adb_binary" -s "$device_serial" shell settings put global enable_back_animation 1
if [[ "$(
    "$adb_binary" -s "$device_serial" shell settings get global enable_back_animation |
        tr -d '\r'
)" != "1" ]]; then
    echo "Unable to enable predictive-back animations on $device_serial." >&2
    exit 1
fi

run_platform_test() {
    local test_method="$1"
    local ready_message="$2"
    local gesture_kind="$3"
    local gesture_ready=false
    local test_exit

    "$adb_binary" -s "$device_serial" logcat -c
    cd "$repo_root"
    ./gradlew \
        :app:connectedDebugAndroidTest \
        "-Pandroid.testInstrumentationRunnerArguments.class=com.viewcompose.NavigationBackDeviceTest#$test_method" \
        -Pandroid.testInstrumentationRunnerArguments.platformPredictiveBackGesture=true \
        --no-configuration-cache \
        --console=plain &
    gradle_pid=$!

    for _ in $(seq 1 600); do
        if "$adb_binary" -s "$device_serial" logcat -d -s "$log_tag:I" '*:S' |
            grep -q "$ready_message"; then
            gesture_ready=true
            break
        fi
        if ! kill -0 "$gradle_pid" >/dev/null 2>&1; then
            break
        fi
        sleep 0.2
    done
    if [[ "$gesture_ready" != "true" ]]; then
        wait "$gradle_pid" >/dev/null 2>&1 || true
        gradle_pid=""
        echo "Timed out waiting for $test_method." >&2
        return 1
    fi

    if [[ "$gesture_kind" == "cancel" ]]; then
        send_cancel_gesture
    else
        send_commit_gesture
    fi

    set +e
    wait "$gradle_pid"
    test_exit=$?
    set -e
    gradle_pid=""
    return "$test_exit"
}

run_platform_test \
    platformEdgeGestureProgressAndCommitPopTheStack \
    READY_FOR_COMMIT_GESTURE \
    commit
run_platform_test \
    platformEdgeGestureProgressAndCancellationDriveRealViews \
    READY_FOR_CANCEL_GESTURE \
    cancel
