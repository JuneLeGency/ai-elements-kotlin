#!/usr/bin/env bash
# Capture real, deterministic Compose examples for the bilingual site and README.
set -euo pipefail
cd "$(dirname "$0")/.."
serial="${1:-${ANDROID_SERIAL:-}}"
case "$serial" in emulator-*) ;; *) echo 'Usage: tools/capture-release-screenshots.sh emulator-PORT' >&2; exit 2 ;; esac
command -v adb >/dev/null
command -v cwebp >/dev/null
./gradlew :demo:assembleDebug :demo:assembleDebugAndroidTest --max-workers=2
adb -s "$serial" install -r demo/build/outputs/apk/debug/demo-debug.apk
adb -s "$serial" install -r demo/build/outputs/apk/androidTest/debug/demo-debug-androidTest.apk
# Direct instrumentation keeps its external files available for adb pull; connected tests may uninstall the app.
adb -s "$serial" shell am instrument -w \
  -e class dev.ai.elements.demo.ReleaseScreenshotsTest -e releaseScreenshots true \
  dev.ai.elements.demo.test/androidx.test.runner.AndroidJUnitRunner | tee build/release-screenshots.log
grep -q 'OK (1 test)' build/release-screenshots.log
adb -s "$serial" pull /sdcard/Android/data/dev.ai.elements.demo/files/release-screens build/
for language in en zh-CN; do
  for theme in light dark; do
    cwebp -quiet -q 88 "build/release-screens/landing-$theme-$language.png" \
      -o "docs/assets/screenshots/landing-$theme-$language.webp"
  done
done
printf 'Captured four screenshots. Inspect them before committing.\n'
