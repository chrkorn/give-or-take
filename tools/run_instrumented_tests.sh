#!/usr/bin/env bash
#
# Run the instrumented (Espresso) test suite against an emulator.
#
# Instrumented tests are deliberately excluded from CI: an emulator there is slow and
# flaky. The cost of that exclusion is that nothing notices when the harness itself
# breaks -- which is exactly what happened. Espresso 3.5.1 cannot initialise on an
# API 37 image, so five test classes accumulated without ever executing.
#
# This script makes the local run a single command, so it is cheap enough to do before
# every tag rather than "when I get round to it".
#
# Usage:
#   tools/run_instrumented_tests.sh              # pick a suitable AVD automatically
#   tools/run_instrumented_tests.sh Pixel_6_API_34
#
set -euo pipefail

# Without this, `set -e` aborts with no output at all and the caller is left guessing.
trap 'status=$?; [ $status -ne 0 ] && echo "aborted at line $LINENO (exit $status): $BASH_COMMAND" >&2' ERR

# Espresso 3.5.1 with androidx.test.ext:junit 1.1.5 -- the Android Studio template
# defaults pinned in gradle/libs.versions.toml -- fail during framework initialisation
# on newer platform images. minSdk is 26, so running the suite below targetSdk is
# ordinary practice rather than a compromise. Raise this only if the versions change.
MAX_SUPPORTED_API=35
CANARY='de.christiankorn.giveortake.ExampleInstrumentedTest'

here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$here"

# Android Studio installs the SDK but does not put it on PATH, so locate it rather than
# making the caller configure a shell. local.properties already records it for Gradle.
find_sdk() {
  local candidate
  for candidate in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}"; do
    [ -n "$candidate" ] && [ -d "$candidate/platform-tools" ] && { echo "$candidate"; return; }
  done
  if [ -f local.properties ]; then
    candidate="$(sed -n 's/^sdk\.dir=//p' local.properties | head -1 | sed 's/\\\\/\//g')"
    [ -n "$candidate" ] && [ -d "$candidate/platform-tools" ] && { echo "$candidate"; return; }
  fi
  for candidate in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
    [ -d "$candidate/platform-tools" ] && { echo "$candidate"; return; }
  done
}

SDK="$(find_sdk || true)"
if [ -z "$SDK" ]; then
  cat >&2 <<'HINT'
Could not find the Android SDK.

Looked at $ANDROID_HOME, $ANDROID_SDK_ROOT, sdk.dir in local.properties, and the
default install locations. Set ANDROID_HOME to your SDK directory and try again --
in Android Studio it is shown under Settings > Languages & Frameworks > Android SDK.
HINT
  exit 1
fi
echo "SDK: $SDK"
export ANDROID_HOME="$SDK" ANDROID_SDK_ROOT="$SDK"
export PATH="$SDK/platform-tools:$SDK/emulator:$SDK/cmdline-tools/latest/bin:$SDK/tools/bin:$PATH"

need() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "not found in the SDK at $SDK: $1" >&2
    echo "install it from Android Studio's SDK Manager, or with sdkmanager" >&2
    exit 1
  }
}
need adb
need emulator

avd_api() {   # echo the API level of an AVD, or nothing
  # Handles dotted levels such as android-37.1 by taking the major number only.
  # Every branch is guarded: a bare `[ -f x ] && ...` that finds nothing returns 1, and
  # a command substitution returning non-zero under `set -e` aborts the whole script.
  local ini="$HOME/.android/avd/$1.ini" cfg="$HOME/.android/avd/$1.avd/config.ini"
  if [ -f "$cfg" ]; then
    sed -n 's/^image\.sysdir\.1=.*android-\([0-9][0-9]*\).*/\1/p' "$cfg" || true
  fi
  if [ -f "$ini" ]; then
    sed -n 's/^target=android-\([0-9][0-9]*\).*/\1/p' "$ini" || true
  fi
  return 0
}

choose_avd() {
  local best="" best_api=0 api
  while read -r name; do
    [ -z "$name" ] && continue
    api="$(avd_api "$name" | head -1)"
    [ -z "$api" ] && continue
    if [ "$api" -le "$MAX_SUPPORTED_API" ] && [ "$api" -gt "$best_api" ]; then
      best="$name"; best_api="$api"
    fi
  done < <(emulator -list-avds)
  [ -n "$best" ] && echo "$best|$best_api"
}

avd="${1:-}"
if [ -z "$avd" ]; then
  picked="$(choose_avd || true)"
  if [ -z "$picked" ]; then
    echo "No AVD at API $MAX_SUPPORTED_API or below. Available:" >&2
    emulator -list-avds | sed 's/^/  /' >&2
    cat >&2 <<'HINT'

Create one, for example:

  sdkmanager "system-images;android-34;google_apis;arm64-v8a"
  avdmanager create avd -n Pixel_6_API_34 \
      -k "system-images;android-34;google_apis;arm64-v8a" -d pixel_6

Use x86_64 instead of arm64-v8a on an Intel Mac.
HINT
    exit 1
  fi
  avd="${picked%%|*}"; api="${picked##*|}"
else
  api="$(avd_api "$avd" | head -1)"
fi
if [ -z "${api:-}" ]; then
  echo "Could not read an API level for AVD '$avd'. Continuing anyway." >&2
fi
echo "AVD: $avd (API ${api:-unknown})"

started_here=0
if [ -z "$(adb devices | sed -n '2p' | cut -f1)" ]; then
  echo "Booting $avd ..."
  emulator -avd "$avd" -no-snapshot-save -no-boot-anim >/dev/null 2>&1 &
  started_here=1
  adb wait-for-device
  until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    sleep 2
  done
  echo "Booted."
fi

# Keep the device from interfering with Espresso: animations must be off, and the
# screen must be on and unlocked or taps land nowhere.
for s in window_animation_scale transition_animation_scale animator_duration_scale; do
  adb shell settings put global "$s" 0 || true
done
adb shell input keyevent 82 >/dev/null 2>&1 || true

echo
echo "== canary: $CANARY =="
# Prove the harness initialises before trusting any real assertion. If this fails, the
# problem is the environment, not the tests.
if ! ./gradlew connectedDebugAndroidTest \
      -Pandroid.testInstrumentationRunnerArguments.class="$CANARY" --console=plain; then
  echo >&2
  echo "The harness itself failed. Do not touch the tests -- this is the environment." >&2
  echo "Report: app/build/reports/androidTests/connected/debug/index.html" >&2
  exit 1
fi

echo
echo "== full instrumented suite =="
./gradlew connectedDebugAndroidTest --console=plain
status=$?

echo
echo "Report: app/build/reports/androidTests/connected/debug/index.html"
echo "Verified on API ${api:-unknown} -- record this; the report must name the level"
echo "the suite was actually verified against rather than implying all of them."

[ "$started_here" = "1" ] && echo "(the emulator this script started is still running)"
exit $status
