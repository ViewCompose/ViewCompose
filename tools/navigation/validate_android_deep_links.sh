#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
package_name="com.gzq.uiframework"
activity_name="com.viewcompose.NavigationDeepLinkTestActivity"
status_prefix="DEEP_LINK|"

adb_binary="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/platform-tools/adb"
if [[ ! -x "$adb_binary" ]]; then
    adb_binary="$(command -v adb || true)"
fi
if [[ -z "$adb_binary" || ! -x "$adb_binary" ]]; then
    echo "adb was not found. Set ANDROID_HOME or ANDROID_SDK_ROOT." >&2
    exit 1
fi
if ! command -v android >/dev/null 2>&1; then
    echo "The Android CLI is required to inspect the deep-link UI." >&2
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

wait_for_status() {
    local expected="$1"
    local status=""
    for _ in $(seq 1 80); do
        status="$(read_status || true)"
        if [[ "$status" == *"$expected"* ]]; then
            printf '%s\n' "$status"
            return 0
        fi
        sleep 0.25
    done
    echo "Timed out waiting for '$expected'. Last status: $status" >&2
    return 1
}

send_deep_link() {
    local uri="$1"
    "$adb_binary" -s "$device_serial" shell am start -W \
        -a android.intent.action.VIEW \
        -c android.intent.category.BROWSABLE \
        -d "$uri" \
        --activity-single-top >/dev/null
}

cd "$repo_root"
./gradlew :app:installDebug --console=plain

"$adb_binary" -s "$device_serial" shell am force-stop "$package_name"
send_deep_link "viewcompose://navigation/account/42"
initial="$(
    wait_for_status \
        "outcome=navigated|active=deep-link-account|history=deep-link-home|top=security|userId=42|home=home|account=security"
)"

send_deep_link "viewcompose://navigation/account/not-a-long"
rejected="$(
    wait_for_status \
        "outcome=rejected|active=deep-link-account|history=deep-link-home|top=security|userId=42|home=home|account=security"
)"

send_deep_link "viewcompose://navigation/home"
home="$(
    wait_for_status \
        "outcome=navigated|active=deep-link-home|history=deep-link-account|top=home|userId=none|home=home|account=security"
)"

"$adb_binary" -s "$device_serial" shell input keyevent BACK
returned="$(
    wait_for_status \
        "active=deep-link-account|history=|top=security|userId=42|home=home|account=security"
)"

echo "Android deep-link certification passed on $device_serial."
echo "$initial"
echo "$rejected"
echo "$home"
echo "$returned"
