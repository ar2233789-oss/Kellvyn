#!/usr/bin/env bash
# Run the entire integration procedure in one Bash process so conditionals
# retain their scope when invoked by the emulator runner.
set +e

mkdir -p app/build/qa
gradle --no-daemon testDebugUnitTest assembleDebug assembleDebugAndroidTest
build_status=$?

install_status=99
push_status=99
android_status=99

echo "Emulator disk state before APK installation:" > app/build/qa/emulator-diagnostics.txt
adb shell df -h /data >> app/build/qa/emulator-diagnostics.txt 2>&1 || true

if [ "$build_status" -eq 0 ]; then
  adb install -r app/build/outputs/apk/debug/app-debug.apk >> app/build/qa/emulator-diagnostics.txt 2>&1
  install_status=$?
  if [ "$install_status" -eq 0 ]; then
    adb shell mkdir -p /sdcard/Android/data/com.kellvyn.assistant/files/kellvyn_qa >> app/build/qa/emulator-diagnostics.txt 2>&1
    adb push "$KELLVYN_QA_MODEL_PATH" /sdcard/Android/data/com.kellvyn.assistant/files/kellvyn_qa/kellvyn-model.litertlm >> app/build/qa/emulator-diagnostics.txt 2>&1
    push_status=$?
    if [ "$push_status" -eq 0 ]; then
      adb shell run-as com.kellvyn.assistant mkdir -p files/models >> app/build/qa/emulator-diagnostics.txt 2>&1
      adb shell run-as com.kellvyn.assistant cp /sdcard/Android/data/com.kellvyn.assistant/files/kellvyn_qa/kellvyn-model.litertlm files/models/kellvyn-model.litertlm >> app/build/qa/emulator-diagnostics.txt 2>&1
      adb shell run-as com.kellvyn.assistant ls -l files/models/kellvyn-model.litertlm >> app/build/qa/emulator-diagnostics.txt 2>&1
      gradle --no-daemon connectedDebugAndroidTest
      android_status=$?
    fi
  fi
fi

adb shell df -h /data >> app/build/qa/emulator-diagnostics.txt 2>&1 || true
adb shell pm list packages com.kellvyn.assistant >> app/build/qa/emulator-diagnostics.txt 2>&1 || true
adb shell run-as com.kellvyn.assistant cat files/kellvyn_qa/model-acceptance-report.md > app/build/qa/model-acceptance-report.md 2> app/build/qa/report-pull-error.txt || true
adb pull /sdcard/kellvyn_game_screen.png app/build/qa/kellvyn_game_screen.png >> app/build/qa/emulator-diagnostics.txt 2>&1 || true
adb pull /sdcard/kellvyn_music_screen.png app/build/qa/kellvyn_music_screen.png >> app/build/qa/emulator-diagnostics.txt 2>&1 || true
adb pull /sdcard/Android/data/com.kellvyn.assistant/files/kellvyn_qa/generated-rain-game.apk app/build/qa/generated-rain-game.apk >> app/build/qa/emulator-diagnostics.txt 2>&1 || true
adb pull /sdcard/Android/data/com.kellvyn.assistant/files/kellvyn_qa/generated-music-player.apk app/build/qa/generated-music-player.apk >> app/build/qa/emulator-diagnostics.txt 2>&1 || true

if [ -s app/build/qa/model-acceptance-report.md ]; then
  echo "Model acceptance report retrieved from emulator."
else
  echo "No model acceptance report was produced or could be retrieved."
fi

set -e
if [ "$build_status" -ne 0 ] || [ "$install_status" -ne 0 ] || [ "$push_status" -ne 0 ] || [ "$android_status" -ne 0 ]; then
  exit 1
fi
