#!/usr/bin/env bash
# Sign APKs with the platform key and verify the result against keys/platform.x509.pem.
#
# Used by .github/workflows/build.yml and release.yml. Both VeloLauncher and
# SerialBridge are signed with the same key so every release has the same
# certificate (Android rejects in-place updates whose signer changed).
#
# Usage: scripts/ci-platform-sign.sh <in.apk> <out.apk> [<in.apk> <out.apk> ...]
#
# Env:
#   PLATFORM_PK8_B64   base64 of the PKCS#8 DER private key (GitHub secret)
#   PLATFORM_CERT_B64  base64 of the x509 PEM certificate  (GitHub secret)
#   BUILD_TOOLS        Android build-tools dir (default: $ANDROID_HOME/build-tools/34.0.0)
set -euo pipefail

if [ $# -eq 0 ] || [ $(( $# % 2 )) -ne 0 ]; then
  echo "usage: $0 <in.apk> <out.apk> [<in.apk> <out.apk> ...]" >&2
  exit 2
fi
: "${PLATFORM_PK8_B64:?PLATFORM_PK8_B64 not set}"
: "${PLATFORM_CERT_B64:?PLATFORM_CERT_B64 not set}"

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EXPECTED_PEM="$REPO_ROOT/keys/platform.x509.pem"
BUILD_TOOLS="${BUILD_TOOLS:-${ANDROID_HOME}/build-tools/34.0.0}"

# SHA-256 of a certificate, PEM or DER (apksigner --cert accepts either)
cert_sha256() {
  { openssl x509 -in "$1" -noout -fingerprint -sha256 2>/dev/null \
    || openssl x509 -inform DER -in "$1" -noout -fingerprint -sha256; } \
    | cut -d= -f2 | tr -d ':' | tr 'A-F' 'a-f'
}

KEYDIR="$(mktemp -d)"
trap 'rm -rf "$KEYDIR"' EXIT
echo "$PLATFORM_PK8_B64" | base64 -d > "$KEYDIR/platform.pk8"
echo "$PLATFORM_CERT_B64" | base64 -d > "$KEYDIR/platform.x509.pem"

EXPECTED_SHA256="$(cert_sha256 "$EXPECTED_PEM")"
SECRET_SHA256="$(cert_sha256 "$KEYDIR/platform.x509.pem")"
if [ "$SECRET_SHA256" != "$EXPECTED_SHA256" ]; then
  echo "::error::PLATFORM_CERT_B64 ($SECRET_SHA256) does not match keys/platform.x509.pem ($EXPECTED_SHA256)"
  exit 1
fi

while [ $# -gt 0 ]; do
  APK_IN="$1"; APK_OUT="$2"; shift 2
  APK_ALIGNED="$KEYDIR/aligned.apk"

  "${BUILD_TOOLS}/zipalign" -f 4 "$APK_IN" "$APK_ALIGNED"
  "${BUILD_TOOLS}/apksigner" sign \
    --key "$KEYDIR/platform.pk8" \
    --cert "$KEYDIR/platform.x509.pem" \
    --out "$APK_OUT" \
    "$APK_ALIGNED"
  rm -f "$APK_ALIGNED" "$APK_OUT.idsig"

  CERTS="$("${BUILD_TOOLS}/apksigner" verify --print-certs "$APK_OUT")"
  echo "$CERTS"
  SIGNED_SHA256="$(echo "$CERTS" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')"
  SIGNERS="$(echo "$CERTS" | grep -c '^Signer #[0-9]* certificate DN' || true)"
  if [ "$SIGNERS" != "1" ] || [ "$SIGNED_SHA256" != "$EXPECTED_SHA256" ]; then
    echo "::error::$APK_OUT signer ($SIGNED_SHA256, $SIGNERS signer(s)) does not match keys/platform.x509.pem ($EXPECTED_SHA256)"
    exit 1
  fi
  echo "Signed: $APK_OUT (cert SHA-256 $SIGNED_SHA256)"
done
