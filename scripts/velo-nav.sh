#!/bin/bash
# Quick navigation helpers for VeloLauncher
# Usage: ./scripts/velo-nav.sh home
#        ./scripts/velo-nav.sh settings
#        ./scripts/velo-nav.sh settings:About
#        ./scripts/velo-nav.sh settings:Diagnostics
DEVICE="${VELO_DEVICE:-192.168.1.147:5555}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

case "$1" in
    home)
        adb -s "$DEVICE" shell am start -n io.freewheel.launcher/.MainActivity
        ;;
    wake)
        adb -s "$DEVICE" shell input keyevent KEYCODE_WAKEUP
        sleep 1
        adb -s "$DEVICE" shell input tap 960 540  # tap to dismiss screensaver
        ;;
    settings)
        adb -s "$DEVICE" shell am start -n io.freewheel.launcher/.MainActivity
        sleep 2
        "$SCRIPT_DIR/velo-tap.sh" --desc "Settings"
        ;;
    settings:*)
        TAB="${1#settings:}"
        "$SCRIPT_DIR/velo-nav.sh" settings
        sleep 1
        "$SCRIPT_DIR/velo-tap.sh" "$TAB"
        ;;
    *)
        echo "Usage: $0 {home|wake|settings|settings:<TabName>}"
        echo "Tabs: Services, Ride Data, Display, Home Tiles, Diagnostics, UCB Firmware, Calibration, System, About"
        exit 1
        ;;
esac
