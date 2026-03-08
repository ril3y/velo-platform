#!/bin/bash
# Platform-sign the SerialBridge APK for Bowflex device (uid=1000 system access)
#
# Key sources (in priority order):
#   1. Environment vars: PLATFORM_PK8_PATH + PLATFORM_CERT_PATH
#   2. Local files: serialbridge/signing/platform.pk8 + device_platform.x509.pem
#
# For CI/CD, set the env vars or decode from base64 secrets:
#   echo "$PLATFORM_PK8_B64" | base64 -d > /tmp/platform.pk8
#   export PLATFORM_PK8_PATH=/tmp/platform.pk8
#   echo "$PLATFORM_CERT_B64" | base64 -d > /tmp/device_platform.x509.pem
#   export PLATFORM_CERT_PATH=/tmp/device_platform.x509.pem
set -e

ANDROID_SDK="${ANDROID_SDK:-$LOCALAPPDATA/Android/Sdk}"
BUILD_TOOLS="$ANDROID_SDK/build-tools/34.0.0"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd -W)"
APK_IN="$SCRIPT_DIR/build/outputs/apk/debug/serialbridge-debug.apk"
APK_ALIGNED="$SCRIPT_DIR/build/outputs/apk/serialbridge-aligned.apk"
APK_OUT="$SCRIPT_DIR/build/outputs/apk/serialbridge.apk"

# Resolve signing keys
PLATFORM_PK8="${PLATFORM_PK8_PATH:-$SCRIPT_DIR/signing/platform.pk8}"
PLATFORM_CERT="${PLATFORM_CERT_PATH:-$SCRIPT_DIR/signing/device_platform.x509.pem}"

if [ ! -f "$PLATFORM_PK8" ]; then
    echo "ERROR: Platform key not found at $PLATFORM_PK8"
    echo "Either place keys in serialbridge/signing/ or set PLATFORM_PK8_PATH env var"
    exit 1
fi
if [ ! -f "$PLATFORM_CERT" ]; then
    echo "ERROR: Platform cert not found at $PLATFORM_CERT"
    echo "Either place keys in serialbridge/signing/ or set PLATFORM_CERT_PATH env var"
    exit 1
fi

if [ ! -f "$APK_IN" ]; then
    echo "ERROR: Debug APK not found at $APK_IN"
    echo "Run './gradlew :serialbridge:assembleDebug' first."
    exit 1
fi

echo "=== Step 1: Zipalign ==="
MSYS_NO_PATHCONV=1 "$BUILD_TOOLS/zipalign" -f 4 "$APK_IN" "$APK_ALIGNED"

echo "=== Step 2: Sign with platform key ==="
MSYS_NO_PATHCONV=1 "$BUILD_TOOLS/apksigner.bat" sign \
    --key "$PLATFORM_PK8" \
    --cert "$PLATFORM_CERT" \
    --out "$APK_OUT" \
    "$APK_ALIGNED"

rm -f "$APK_ALIGNED"

echo "=== Step 3: Verify ==="
MSYS_NO_PATHCONV=1 "$BUILD_TOOLS/apksigner.bat" verify --print-certs "$APK_OUT"
ls -la "$APK_OUT"

echo ""
echo "=== BUILD COMPLETE ==="
echo "APK: $APK_OUT"
echo ""
echo "Install:"
echo "  adb -s 192.168.1.156:5555 install -r $APK_OUT"
