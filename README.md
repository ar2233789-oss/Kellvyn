# Kellvyn IA

Kellvyn IA is a native Android application under active development, using Kotlin and Jetpack Compose.

## Current implementation

- Dark chat interface with blue neon accents.
- Local conversation persistence in app-private storage, with a clear-history control.
- `AssistantEngine` contract for model-provider implementations.
- Local inference adapter using Google's LiteRT-LM Kotlin API.
- UI flow to import a compatible `.litertlm` model from the Android document picker.
- CPU inference runs on-device; messages are not sent to a remote service by this local engine.
- GitHub Actions workflow to compile a debug APK and upload it as a workflow artifact.
- App namespace: `com.kellvyn.assistant`.
- Minimum Android API: 26; Java/Kotlin target: 17.

## Current limitations

The project has not yet been validated on the target phone. The user must provide a compatible model file; model weights are intentionally not bundled in the APK. The optional online AI provider is not implemented yet. The debug APK should not be called ready until the automated build succeeds and device installation tests are completed.

## Set up a local model

See [docs/LOCAL_AI.md](docs/LOCAL_AI.md). The first suggested candidate is the quantized Gemma 3 1B IT `.litertlm` file, around 584 MB. Hugging Face requires the user to log in and accept the applicable Gemma license/terms before downloading. The candidate is not yet performance-tested on a Poco F6.

## Build

GitHub Actions runs on pushes to `main` and on pull requests. Open the [Actions page](https://github.com/ar2233789-oss/Kellvyn/actions) and download the artifact named `kellvyn-debug-apk` after a successful run.

For a local build, use JDK 17, Android SDK Platform 35, Build Tools 35.0.0 and Gradle 8.9, then run:

```sh
gradle :app:assembleDebug
```

Do not put API keys or credentials directly into application source code.
