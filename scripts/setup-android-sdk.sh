#!/usr/bin/env bash
# Installs the Android SDK pieces this project needs into ~/android-sdk (or $ANDROID_HOME),
# and writes local.properties so Gradle can find it. Safe to run again: it skips what is there.
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/android-sdk}"
TOOLS_ZIP="commandlinetools-linux-13114758_latest.zip"
PLATFORM="platforms;android-35"
BUILD_TOOLS="build-tools;35.0.0"

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "Downloading the Android command-line tools…"
  mkdir -p "$SDK/cmdline-tools"
  tmp="$(mktemp -d)"
  curl -fsSL "https://dl.google.com/android/repository/$TOOLS_ZIP" -o "$tmp/tools.zip"
  unzip -q "$tmp/tools.zip" -d "$tmp"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$tmp"
fi

SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
yes | "$SDKMANAGER" --sdk_root="$SDK" --licenses > /dev/null || true
"$SDKMANAGER" --sdk_root="$SDK" "platform-tools" "$PLATFORM" "$BUILD_TOOLS" > /dev/null

project="$(cd "$(dirname "$0")/.." && pwd)"
echo "sdk.dir=$SDK" > "$project/local.properties"
echo "Android SDK ready in $SDK"
