#!/usr/bin/env bash
set +e

mkdir -p android-test-reports

case "${ACCEPTANCE_SUITE:-model-acceptance}" in
  compiler-smoke)
    TEST_FILTER="com.kellvyn.assistant.workspace.LocalCompilerAgentIntegrationTest#builderProjectCanBeBuiltByTheAgentToolIntoVerifiedInstallableApk"
    ;;
  model-acceptance)
    TEST_FILTER="com.kellvyn.assistant.workspace.LocalCompilerAgentIntegrationTest#realLocalModelAcceptanceSuiteChatsBuildsRainGameAndMusicPlayer"
    ;;
  svg-model-smoke)
    TEST_FILTER="com.kellvyn.assistant.attachments.LocalSvgImageGeneratorTest"
    ;;
  *)
    echo "Unknown acceptance suite: ${ACCEPTANCE_SUITE}"
    exit 2
    ;;
esac

./gradlew --no-daemon --stacktrace connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=${TEST_FILTER}"
TEST_EXIT=$?

if [ "${ACCEPTANCE_SUITE:-model-acceptance}" = "model-acceptance" ] && [ "$TEST_EXIT" -eq 0 ]; then
  echo "Running local document and image attachment extraction tests..."
  ./gradlew --no-daemon --stacktrace connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.kellvyn.assistant.attachments.LocalAttachmentExtractionTest"
  ATTACHMENT_EXIT=$?
  if [ "$ATTACHMENT_EXIT" -ne 0 ]; then TEST_EXIT=$ATTACHMENT_EXIT; fi
fi

adb shell run-as com.kellvyn.assistant cat files/kellvyn_qa/model-acceptance-report.md > android-test-reports/model-acceptance-report.md 2>/dev/null || true
adb pull /sdcard/Android/data/com.kellvyn.assistant/files/kellvyn_qa android-test-reports/device-artifacts >/dev/null 2>&1 || true
adb logcat -d -t 8000 > android-test-reports/logcat.txt 2>/dev/null || true
printf "%s\n" "$TEST_EXIT" > android-test-reports/exit-code.txt

exit "$TEST_EXIT"
