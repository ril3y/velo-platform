#!/bin/bash
# Tap a UI element by text or content-description
# Usage: ./scripts/velo-tap.sh "About"              # by visible text
#        ./scripts/velo-tap.sh --desc "Settings"     # by content description
DEVICE="${VELO_DEVICE:-192.168.1.147:5555}"

MODE="text"
QUERY="$1"
if [ "$1" = "--desc" ]; then
    MODE="content-desc"
    QUERY="$2"
fi

# Dump UI hierarchy (use exec-out to avoid MSYS path conversion issues)
export MSYS_NO_PATHCONV=1
adb -s "$DEVICE" shell uiautomator dump /sdcard/velo_ui.xml 2>/dev/null
UI_XML=$(adb -s "$DEVICE" exec-out cat /sdcard/velo_ui.xml 2>/dev/null)

# Parse bounds for the matching element
BOUNDS=$(echo "$UI_XML" | \
    tr '>' '\n' | \
    grep "${MODE}=\"${QUERY}\"" | \
    head -1 | \
    grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | \
    grep -oE '[0-9]+' )

if [ -z "$BOUNDS" ]; then
    echo "ERROR: Element not found: ${MODE}=\"${QUERY}\"" >&2
    echo "Available elements:" >&2
    echo "$UI_XML" | \
        tr '>' '\n' | \
        grep -oE '(text|content-desc)="[^"]*"' | \
        sort -u | head -20 >&2
    exit 1
fi

# Parse x1,y1,x2,y2 and compute center
read X1 Y1 X2 Y2 <<< $(echo $BOUNDS | tr '\n' ' ')
CX=$(( (X1 + X2) / 2 ))
CY=$(( (Y1 + Y2) / 2 ))

echo "Tapping '${QUERY}' at ($CX, $CY)"
adb -s "$DEVICE" shell input tap $CX $CY
