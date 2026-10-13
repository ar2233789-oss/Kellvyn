# Kellvyn IA

Kellvyn IA is a native Android app under active development, built with Kotlin and Jetpack Compose. The project aims for an offline-first assistant with an optional online provider.

## Implemented in the current source

- Dark chat interface with neon-blue accents.
- Copy control with a small page icon below every assistant reply; it copies the full reply to Android's clipboard.
- Portuguese speech-to-text input through the speech recognizer installed on the device.
- Private conversation history stored on the device, with a clear-history action and bounded history size.
- User-controlled long-term memory: add, inspect, remove one fact, or clear all facts. Nothing is automatically remembered without the user's action.
- Local inference adapter using Google's LiteRT-LM Kotlin API. The user can import a compatible `.litertlm` model, see its stored size, and remove it to reclaim space.
- Optional OpenAI-compatible Chat Completions provider over HTTPS. Endpoint/model are configurable; an optional API token is encrypted with Android Keystore.
- AI app builder that asks the selected model to generate project source files, validates required paths, stores files privately, and exports a ZIP.
- Local source editor for generated project files.
- GitHub Actions workflow that compiles a debug APK and uploads it as a workflow artifact.
- App namespace: `com.kellvyn.assistant`; min Android API 26; Java/Kotlin target 17.

## Important limitations

The model weights are not bundled into the APK. The local engine still needs to be tested with a real compatible model on the target phone, and the optional online provider needs a real configured endpoint and credentials. Speech input depends on a speech-recognition service installed on the phone.

The app builder generates, saves, edits and exports source files. It does **not** yet compile arbitrary generated projects into APKs directly on the phone. Generated code must be reviewed and built/tested separately. The latest successful build verifies compilation in CI, not installation or inference on a physical Poco F6.

## Configure a local model

Follow [docs/LOCAL_AI.md](docs/LOCAL_AI.md). The first candidate is a quantized Gemma 3 1B IT `.litertlm` model of about 584 MB. It is not bundled. Download availability and license terms are handled by the model host; the candidate has not been performance-tested on the target phone.

## Build a debug APK

Pushes to `main` and pull requests trigger the [Android build workflow](https://github.com/ar2233789-oss/Kellvyn/actions). After a successful run, download the artifact named `kellvyn-debug-apk`.

The workflow uses JDK 17, Android SDK Platform 35, Build Tools 35.0.0 and Gradle 8.9. For a local build with those tools installed, run:

```sh
gradle :app:assembleDebug
```

Do not embed API credentials in source code. In online mode, chat messages and relevant saved memories are sent to the endpoint explicitly configured by the user; in local mode, inference prompts stay on the device.
