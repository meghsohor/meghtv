#!/usr/bin/env bash
# Builds and signs a release APK locally. Usage: scripts/build-local-release.sh [--personal] [output-path]
# --personal: the personal build type, release plus local/tools/channels/*.json; for the owner's own devices only.
# The keystore and its passwords live outside the repo; MEGHTV_KEYSTORE / MEGHTV_KEYSTORE_CREDS override their paths.
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
# A shell profile can point ANDROID_HOME at Homebrew's command-line tools, which have no build-tools.
[[ -d "$ANDROID_HOME/build-tools" ]] || ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_HOME
JAVA_BIN="/opt/homebrew/opt/openjdk@17/bin"
KEYSTORE="${MEGHTV_KEYSTORE:-$HOME/Shuvo/Documents/meghtv-signing/release.keystore}"
CREDS_FILE="${MEGHTV_KEYSTORE_CREDS:-$HOME/Shuvo/Documents/meghtv-signing/passwords.txt}"
KEY_ALIAS="meghtv-release"

BUILD_TYPE="release"
if [[ "${1:-}" == "--personal" ]]; then
  BUILD_TYPE="personal"
  shift
  compgen -G "$(dirname "${BASH_SOURCE[0]}")/../local/tools/channels/*.json" >/dev/null || { echo "no channel files in local/tools/channels" >&2; exit 1; }
fi
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

if [[ ! -f "$KEYSTORE" ]]; then
  echo "Keystore not found at $KEYSTORE (set MEGHTV_KEYSTORE to override)" >&2
  exit 1
fi
if [[ ! -f "$CREDS_FILE" ]]; then
  echo "Keystore credentials not found at $CREDS_FILE (set MEGHTV_KEYSTORE_CREDS to override)" >&2
  exit 1
fi
# shellcheck disable=SC1090
source "$CREDS_FILE"

BUILD_TOOLS="$(find "$ANDROID_HOME/build-tools" -maxdepth 1 -mindepth 1 -type d | sort -V | tail -1)"
if [[ -z "$BUILD_TOOLS" ]]; then
  echo "No build-tools found under $ANDROID_HOME/build-tools" >&2
  exit 1
fi

VERSION_NAME="$(sed -n 's/.*versionName *= *"\([^"]*\)".*/\1/p' app/build.gradle.kts | head -1)"
SUFFIX="local"
[[ "$BUILD_TYPE" == "personal" ]] && SUFFIX="personal"
OUTPUT="${1:-$HOME/Downloads/MeghTV-v${VERSION_NAME}-${SUFFIX}.apk}"

export PATH="$JAVA_BIN:$PATH"

echo "Building $BUILD_TYPE APK (versionName $VERSION_NAME)..."
if [[ "$BUILD_TYPE" == "personal" ]]; then ./gradlew assemblePersonal; else ./gradlew assembleRelease; fi

TMP_ALIGNED="$(mktemp -t meghtv-aligned).apk"
trap 'rm -f "$TMP_ALIGNED"' EXIT

echo "Zipaligning..."
"$BUILD_TOOLS/zipalign" -f -p 4 \
  "app/build/outputs/apk/$BUILD_TYPE/app-$BUILD_TYPE-unsigned.apk" \
  "$TMP_ALIGNED"

echo "Signing..."
"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-pass "pass:$STORE_PW" \
  --ks-key-alias "$KEY_ALIAS" \
  --v4-signing-enabled false \
  --out "$OUTPUT" \
  "$TMP_ALIGNED"

echo "Done: $OUTPUT"
