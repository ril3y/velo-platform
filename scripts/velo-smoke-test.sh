#!/bin/bash
# Smoke test for VeloLauncher on the bike
# Runs through key user flows using velo-tap.sh and checks for crashes
# Usage: ./scripts/velo-smoke-test.sh
set -e
DEVICE="${VELO_DEVICE:-192.168.1.147:5555}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PASS=0
FAIL=0

tap() { bash "$SCRIPT_DIR/velo-tap.sh" "$@"; }
tapdesc() { bash "$SCRIPT_DIR/velo-tap.sh" --desc "$@"; }
screenshot() { bash "$SCRIPT_DIR/velo-screenshot.sh" "$1" 2>/dev/null; }
check_crash() {
    local crashes=$(adb -s "$DEVICE" shell logcat -d | grep "FATAL EXCEPTION" | grep "io.freewheel.launcher" | wc -l)
    if [ "$crashes" -gt 0 ]; then
        echo "  FAIL: Crash detected!"
        adb -s "$DEVICE" shell logcat -d | grep -A5 "FATAL EXCEPTION" | head -10
        FAIL=$((FAIL + 1))
        return 1
    else
        echo "  PASS: No crash"
        PASS=$((PASS + 1))
        return 0
    fi
}

echo "=== VeloLauncher Smoke Test ==="
echo ""

# Ensure overlay permission
adb -s "$DEVICE" shell appops set io.freewheel.launcher SYSTEM_ALERT_WINDOW allow 2>/dev/null

# Test 1: Launch home
echo "Test 1: Launch home screen"
adb -s "$DEVICE" shell logcat -c
adb -s "$DEVICE" shell am force-stop io.freewheel.launcher
sleep 1
bash "$SCRIPT_DIR/velo-nav.sh" home
sleep 3
check_crash

# Test 2: Navigate to settings and back
echo "Test 2: Settings navigation"
adb -s "$DEVICE" shell logcat -c
tapdesc "Settings"
sleep 2
tap "Services"
sleep 1
tap "About"
sleep 1
# Go back to home
adb -s "$DEVICE" shell am start -n io.freewheel.launcher/.MainActivity
sleep 2
check_crash

# Test 3: Free Ride flow
echo "Test 3: Free Ride start/stop"
adb -s "$DEVICE" shell logcat -c
tap "Free Ride"
sleep 3
check_crash

# Stop ride if active (try END WORKOUT button)
tap "END WORKOUT" 2>/dev/null || true
sleep 2

# Test 4: Workout picker
echo "Test 4: Workout picker navigation"
adb -s "$DEVICE" shell logcat -c
bash "$SCRIPT_DIR/velo-nav.sh" home
sleep 2
tap "Browse Workouts"
sleep 2
check_crash

# Test 5: Workout detail
echo "Test 5: Select workout"
adb -s "$DEVICE" shell logcat -c
tap "5 Min Blast" 2>/dev/null || tap "Tabata Blast" 2>/dev/null || true
sleep 2
check_crash

# Test 6: Workout with media (the crash that was fixed)
echo "Test 6: Start workout with media"
adb -s "$DEVICE" shell logcat -c
tap "Netflix" 2>/dev/null || true
sleep 1
tap "Go" 2>/dev/null || true
sleep 3
check_crash

# Return home
adb -s "$DEVICE" shell am start -n io.freewheel.launcher/.MainActivity
sleep 2

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="
[ "$FAIL" -eq 0 ] && echo "ALL TESTS PASSED" || echo "SOME TESTS FAILED"
exit $FAIL
