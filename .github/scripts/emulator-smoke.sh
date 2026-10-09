#!/bin/sh
# Single-process POSIX shell smoke test used by android-emulator-runner.
# This must remain a file: the runner invokes each script input line with /bin/sh -c.
set -u

api_level="${1:?Pass the Android API level as the first argument}"
mkdir -p build/emulator-smoke
activity_log="build/emulator-smoke/activities-api-${api_level}.txt"
runtime_log="build/emulator-smoke/runtime-api-${api_level}.log"
start_log="build/emulator-smoke/start-api-${api_level}.txt"
notification_log="build/emulator-smoke/notifications-api-${api_level}.txt"
pairing_start_log="build/emulator-smoke/pairing-service-api-${api_level}.txt"
device_state=""

# Wait for the ADB transport to become online after the emulator's cold boot.
attempt=0
while [ "$attempt" -lt 45 ]; do
    device_state="$(adb get-state 2>/dev/null || true)"
    [ "$device_state" = "device" ] && break
    adb reconnect offline >/dev/null 2>&1 || true
    sleep 2
    attempt=$((attempt + 1))
done

if [ "$device_state" != "device" ]; then
    adb devices -l > "build/emulator-smoke/adb-devices-api-${api_level}.txt" 2>&1 || true
    adb logcat -d > "$runtime_log" 2>&1 || true
    echo "API ${api_level}: emulator ADB did not become online; FLY8 was not tested." >> "$GITHUB_STEP_SUMMARY"
    cat "build/emulator-smoke/adb-devices-api-${api_level}.txt" "$runtime_log"
    exit 1
fi

if ! adb install -r build/apk/app-debug.apk; then
    adb logcat -d > "$runtime_log" 2>&1 || true
    echo "API ${api_level}: APK installation failed before FLY8 startup." >> "$GITHUB_STEP_SUMMARY"
    cat "$runtime_log"
    exit 1
fi

adb logcat -c || true
start_output="$(adb shell am start -W -n com.fl8y.lightcloner/.MainActivity 2>&1)"
start_result=$?
printf '%s\n' "$start_output" > "$start_log"

sleep 15
app_pid="$(adb shell pidof com.fl8y.lightcloner 2>/dev/null | tr -d '\r' || true)"
adb shell dumpsys activity activities > "$activity_log" 2>&1 || true
adb logcat -d -s AndroidRuntime:E FLY8.VirtualRuntime:I FLY8.VirtualAppEngine:V FLY8.Diagnostics:V WirelessPairingService:V > "$runtime_log" 2>&1 || adb logcat -d > "$runtime_log" 2>&1 || true

if [ "$start_result" -ne 0 ] || [ -z "$app_pid" ]; then
    echo "API ${api_level}: FLY8 host process did not remain alive after launch." >> "$GITHUB_STEP_SUMMARY"
    cat "$start_log" "$runtime_log"
    exit 1
fi
if ! grep -E 'mResumedActivity|topResumedActivity' "$activity_log" | grep -q 'com.fl8y.lightcloner'; then
    echo "API ${api_level}: FLY8 MainActivity was not found as the resumed Activity." >> "$GITHUB_STEP_SUMMARY"
    cat "$activity_log" "$runtime_log"
    exit 1
fi
if grep -q 'FATAL EXCEPTION' "$runtime_log"; then
    echo "API ${api_level}: fatal AndroidRuntime exception detected." >> "$GITHUB_STEP_SUMMARY"
    cat "$runtime_log"
    exit 1
fi
if ! grep -q 'Virtual service answered; virtual-user count=' "$runtime_log"; then
    echo "API ${api_level}: host Activity is alive, but VirtualCore did not confirm Binder/user-service readiness." >> "$GITHUB_STEP_SUMMARY"
    cat "$start_log" "$runtime_log"
    exit 1
fi

# Verify that the notification-led wireless pairing foreground service can start.
# No pairing endpoint is expected inside CI; this tests notification/service startup only.
adb shell pm grant com.fl8y.lightcloner android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
adb shell am start-foreground-service -n com.fl8y.lightcloner/.connection.WirelessPairingService -a com.fl8y.lightcloner.adb.START_PAIRING > "$pairing_start_log" 2>&1
pairing_start_result=$?
sleep 3
adb shell dumpsys notification --noredact > "$notification_log" 2>&1 || true
adb logcat -d -s AndroidRuntime:E FLY8.VirtualRuntime:I FLY8.VirtualAppEngine:V FLY8.Diagnostics:V WirelessPairingService:V > "$runtime_log" 2>&1 || adb logcat -d > "$runtime_log" 2>&1 || true
if [ "$pairing_start_result" -ne 0 ] || ! grep -E 'Wireless Debugging|FLY8' "$notification_log" | grep -q 'Wireless Debugging'; then
    echo "API ${api_level}: notification-led pairing service did not publish an observable Wireless Debugging notification." >> "$GITHUB_STEP_SUMMARY"
    cat "$pairing_start_log" "$notification_log" "$runtime_log"
    exit 1
fi

echo "API ${api_level}: FLY8 host Activity/process, VirtualCore Binder/user-service readiness, and pairing foreground-notification startup passed. No actual pairing endpoint exists in CI; guest UI/render/task/AFK parity remains unverified." >> "$GITHUB_STEP_SUMMARY"
