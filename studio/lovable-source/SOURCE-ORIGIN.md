# Snapshot of the original Lovable project

This directory preserves the source snapshot used as the reference for the Kellvyn IA interface and native port.

- Upstream repository: https://github.com/ar2233789-oss/kellvyn-ai-assistant
- Pinned upstream commit: d64ddeef348ea5378e26e855bc3870b8f264e462
- Snapshot files imported: 119 of 119 tracked source files, including the original web UI, the Android project, Kotlin/Java runtime, build files, tests, and static assets.
- Snapshot root: `studio/lovable-source/`
- Web UI entry: `src/components/kellvyn/KellvynApp.tsx`
- Rain/effects reference: `src/components/kellvyn/DigitalRain.tsx`
- Existing native Android entry: `android/app/src/main/java/com/kellvyn/assistant/MainActivity.java`
- Native Android rain/effects must be audited inside the native implementation; the web canvas is a visual reference, not proof of native parity.

## Non-negotiable workflow

1. Treat this snapshot as the visual and behavioral source of truth. Do not redesign it.
2. Inventory each original control, its label, click handler, state change, and actual effect before changing native code.
3. Compare the three-dot settings panel, effect toggles, rain + lightning combination, navigation, and all animations against the original.
4. Make port changes in a separate native working area; keep this source snapshot untouched.
5. Test the actual APK build and animations before claiming completion. A source copy or static check alone does not prove parity on the Poco F6.

This snapshot is a reference copy, not a claim that the web UI has already been converted to native Android or that an APK has passed device testing.
