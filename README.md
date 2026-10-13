# Kellvyn IA

Kellvyn IA is a native Android application under active development, built with Kotlin and Jetpack Compose. It aims for an offline-first assistant with an optional online provider.

## Implemented in the source

- Dark chat UI with neon-blue accents.
- Copy button with a small two-page icon below each assistant response.
- Portuguese speech-to-text input through the Android speech recognizer and text-to-speech playback through an available Portuguese voice.
- Private conversation history stored on the device, clear-history control, and bounded history size.
- User-controlled long-term memory: add, inspect, delete a fact, or clear all; facts enter the model context only when explicitly saved.
- Local inference adapter using Google's LiteRT-LM Kotlin API.
- One-tap download for a public 374 MB SmolLM2-360M-Instruct LiteRT-LM model under Apache-2.0, verified by expected size and SHA-256; compatible model files can also be imported manually.
- Optional OpenAI-compatible Chat Completions endpoint over HTTPS. Endpoint/model settings are configurable; optional API token is encrypted with Android Keystore.
- AI app builder that asks the selected model for structured project source, validates paths and size, stores files privately, and exports a ZIP.
- In-app source editor for generated project files.
- Local project workspace and project listing.
- GitHub Actions workflow that builds, verifies package ID/signature/archive, and publishes a debug APK.
- App namespace: `com.kellvyn.assistant`; minimum Android API 26; Java/Kotlin target 17.

## Important limitations

The small offline model is for initial on-device chat and basic tasks; it is not equivalent to a large coding model. Model speed, quality, RAM use and heat have not yet been tested on the target phone. Larger compatible models may be imported via the file picker.

The online provider is optional and must be configured by the user. In online mode, conversation context and relevant saved memories are sent to the selected endpoint; providers may charge for usage. Voice features depend on speech and TTS services installed on Android.

The app builder creates, validates, saves, edits and exports project source files. It does **not** yet compile arbitrary generated projects into APKs directly on the phone. Generated code must be reviewed and compiled/tested separately. Successful CI builds do not prove installation or inference on a physical Poco F6.

## Local model setup

See [docs/LOCAL_AI.md](docs/LOCAL_AI.md). The quick-start model is available at [SmolLM2-360M-Instruct for LiteRT-LM](https://huggingface.co/litert-community/SmolLM2-360M-Instruct), license Apache-2.0, about 374 MB. The app downloads and verifies it when the user taps the download button; weights are not bundled into the APK.

## Build the APK

Pushes to `main` and pull requests trigger the [Android build workflow](https://github.com/ar2233789-oss/Kellvyn/actions). After a successful run, download the artifact named `kellvyn-debug-apk`.

The workflow uses JDK 17, Android SDK Platform 35, Build Tools 35.0.0, and Gradle 8.9. For a local build with the same tools installed, run:

```sh
gradle :app:assembleDebug
```

Do not embed API keys or credentials in source code. In local mode, prompts remain on-device; in online mode, messages go only to the explicitly configured HTTPS endpoint.
