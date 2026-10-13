# Kellvyn IA

Kellvyn IA is being built as a native Android application, using Kotlin and Jetpack Compose.

## Current foundation

- Native Android Gradle project.
- Dark chat interface with blue neon accents.
- Conversation UI and visible implementation status.
- App namespace: `com.kellvyn.assistant`.
- Minimum Android API: 26.
- Java/Kotlin target: 17.

## Important status

This is the initial application shell, not a finished AI assistant. The chat currently records messages in the interface and explicitly reports that a real inference engine is not connected. No cloud service, local model, API key, or APK build has been configured or verified yet.

## Next engineering milestones

1. Verify project structure and build a debug APK.
2. Add persistent local conversation storage.
3. Define a model-provider interface so local/offline and online inference can be integrated without pretending either is available before configuration.
4. Evaluate on-device inference options against the target phone's RAM and storage.
5. Add permissions, privacy controls, error handling, and tests before calling the app release-ready.

## Build

Open this repository in Android Studio and run the `app` configuration, or use a compatible Gradle environment with Android SDK 35 and JDK 17 installed.
