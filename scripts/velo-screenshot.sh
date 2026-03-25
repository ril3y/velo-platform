#!/bin/bash
# Screenshot the bike screen and save locally
# Usage: ./scripts/velo-screenshot.sh [name]
DEVICE="${VELO_DEVICE:-192.168.1.147:5555}"
NAME="${1:-screen_$(date +%H%M%S)}"
OUTDIR="${VELO_SCREENSHOT_DIR:-/tmp}"
adb -s "$DEVICE" exec-out screencap -p > "${OUTDIR}/${NAME}.png"
echo "${OUTDIR}/${NAME}.png"
