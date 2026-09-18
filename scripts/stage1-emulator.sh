#!/usr/bin/env bash
set -euo pipefail
mkdir -p qa
adb shell wm size 824x1830
adb shell wm density 320
adb shell svc wifi disable
adb shell svc data disable
adb install baseline/app/build/outputs/apk/debug/app-debug.apk
printf '%s' '<?xml version="1.0" encoding="utf-8"?><map><string name="stage1_upgrade_sentinel">preserved</string><string name="state">legacy-state-must-not-be-deleted</string></map>' > qa/legacy.xml
adb push qa/legacy.xml /data/local/tmp/stage1-legacy.xml
adb shell run-as ru.teplayakompaniya.tk4 mkdir -p shared_prefs
adb shell run-as ru.teplayakompaniya.tk4 cp /data/local/tmp/stage1-legacy.xml shared_prefs/tk4_connected.xml
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb logcat -c
adb shell am instrument -w ru.teplayakompaniya.tk4.test/ru.teplayakompaniya.tk4.Stage1Smoke | tee qa/instrumentation.txt
adb pull /sdcard/Android/data/ru.teplayakompaniya.tk4/files/stage1-qa qa/screenshots || true
adb logcat -d -s AndroidRuntime:E > qa/crashes.txt
adb shell run-as ru.teplayakompaniya.tk4 cat shared_prefs/tk4_connected.xml > qa/after-upgrade.xml
grep -q 'legacy-state-must-not-be-deleted' qa/after-upgrade.xml
grep -q 'STAGE1_SMOKE_PASSED' qa/instrumentation.txt
if grep -q 'FATAL EXCEPTION' qa/crashes.txt; then exit 1; fi
