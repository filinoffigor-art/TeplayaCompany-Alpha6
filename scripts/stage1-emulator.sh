#!/usr/bin/env bash
set -euo pipefail
node scripts/stage1-migration-test.cjs
mkdir -p qa
adb shell wm size 824x1830
adb shell wm density 320
adb shell svc wifi disable
adb shell svc data disable

write_sentinel() {
  printf '%s' '<?xml version="1.0" encoding="utf-8"?><map><string name="stage1_upgrade_sentinel">preserved</string><string name="state">parent-state-must-not-be-deleted</string></map>' > qa/parent.xml
  adb push qa/parent.xml /data/local/tmp/stage1-parent.xml
  adb shell run-as ru.teplayakompaniya.tk4 mkdir -p shared_prefs
  adb shell run-as ru.teplayakompaniya.tk4 cp /data/local/tmp/stage1-parent.xml shared_prefs/tk4_connected.xml
}

if [ -n "${TK4_PARENT_APK:-}" ]; then
  echo "QA path: install stable parent then update over it"
  adb install "$TK4_PARENT_APK"
  write_sentinel
  adb install -r app/build/outputs/apk/debug/app-debug.apk
elif [ "${TK4_FRESH_PARENT:-0}" = "1" ]; then
  echo "QA path: fresh stable parent"
  adb install app/build/outputs/apk/debug/app-debug.apk
else
  echo "QA path: legacy baseline upgrade"
  adb install baseline/app/build/outputs/apk/debug/app-debug.apk
  write_sentinel
  adb install -r app/build/outputs/apk/debug/app-debug.apk
fi

adb install app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb logcat -c
adb shell am instrument -w ru.teplayakompaniya.tk4.test/ru.teplayakompaniya.tk4.Stage1Smoke | tee qa/instrumentation.txt
adb pull /sdcard/Android/data/ru.teplayakompaniya.tk4/files/stage1-qa qa/screenshots || true
adb logcat -d -s AndroidRuntime:E > qa/crashes.txt

if [ -n "${TK4_PARENT_APK:-}" ] || [ "${TK4_FRESH_PARENT:-0}" != "1" ]; then
  adb shell run-as ru.teplayakompaniya.tk4 cat shared_prefs/tk4_connected.xml > qa/after-upgrade.xml
  grep -q 'parent-state-must-not-be-deleted' qa/after-upgrade.xml
fi

grep -q 'STAGE1_SMOKE_PASSED' qa/instrumentation.txt
if grep -q 'FATAL EXCEPTION' qa/crashes.txt; then exit 1; fi
