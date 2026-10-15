#!/usr/bin/env bash
set +e

mkdir -p android-test-reports

case "${ACCEPTANCE_SUITE:-model-acceptance}" in
  compiler-smoke)
    TEST_METHOD="com.kellvyn.assistant.workspace.LocalCompilerAgentIntegrationTest#builderProjectCanBeBuiltByTheAgentToolIntoVerifiedInstallableApk"
    ;;
  model-acceptance)
    TEST_METHOD="com.kellvyn.assistant.workspace.LocalCompilerAgentIntegrationTest#realLocalModelAcceptanceSuiteChatsBuildsRainGameAndMusicPlayer"
    ;;
  *)
    echo "Unknown acceptance suite: ${ACCEPTANCE_SUITE}"
    exit 2
    ;;
esac

./gradlew --no-daemon --stacktrace connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=${TEST_METHOD}"
TEST_EXIT=$?

adb shell run-as com.kellvyn.assistant cat files/kellvyn_qa/model-acceptance-report.md > android-test-reports/model-acceptance-report.md 2>/dev/null || true
adb pull /sdcard/Android/data/com.kellvyn.assistant/files/kellvyn_qa android-test-reports/device-artifacts >/dev/null 2>&1 || true
adb logcat -d -t 8000 > android-test-reports/logcat.txt 2>/dev/null || true
printf "%s\n" "$TEST_EXIT" > android-test-reports/exit-code.txt

exit "$TEST_EXIT"
