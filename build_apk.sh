#!/usr/bin/env bash
# Build APK wrapper for Unix-like shells (Linux / macOS / Git Bash)
# Usage: ./build_apk.sh [destination_path]
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Prefer bundled JDK if present
if [ -d "$ROOT_DIR/jdk8/jdk1.8.0_502" ]; then
  export JAVA_HOME="$ROOT_DIR/jdk8/jdk1.8.0_502"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

echo "Using JAVA_HOME=${JAVA_HOME:-(system default)}"

cd "$ROOT_DIR"
./gradlew assembleDebug --no-daemon

APK="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK" ]; then
  echo "APK not found: $APK" >&2
  exit 1
fi

if [ "${1-}" = "" ]; then
  echo "APK produced: $APK"
else
  cp -f "$APK" "$1"
  echo "APK copied to $1"
fi
