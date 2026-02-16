# REPRODUCIBILITY

## macOS (Android)

### Toolchain
- JDK: 17 (Temurin or equivalent)
- Gradle: 9.1.0 (wrapper)
- Android Gradle Plugin: 9.0.0
- Android SDK:
  - Platform: 35
  - Build-tools: 36.0.0 (auto-installed by Gradle during build)

### Build
From repo root:

```
cd android-kotlin
./gradlew :app:assembleDebug
./gradlew :app:test
```

### Run
- Open `android-kotlin/` in Android Studio.
- Run the `app` configuration on an emulator or device (minSdk 26).

### Notes / pitfalls
- On first build, Gradle may download Android SDK components (build-tools/platform).
- Instrumented tests require a connected device or emulator.

### Notes / pitfalls (androidTest)
- `androidTest` code should avoid `toUri()` unless `androidx.core:core-ktx` is added to the androidTest configuration; prefer `Uri.fromFile(...)` for file URIs in tests.

### MediaPipe Text Embedder model
- Place the text embedder model at: `android-kotlin/app/src/main/assets/models/text_embedder.tflite`
- The app and instrumented tests will fail if the model asset is missing.

### Model assets
- Text Embedder model downloaded to `android-kotlin/app/src/main/assets/models/text_embedder.tflite` (USE). Required for semantic embedding tests.
- `connectedAndroidTest` requires a running emulator or device.

### MediaPipe Image Embedder model
- Place the image embedder model at: `android-kotlin/app/src/main/assets/models/image_embedder.tflite`
- Image similarity tests require the model asset and a connected device.

### MediaPipe Text Embedder test failures (resolved)
- `connectedAndroidTest` previously failed with `TextEmbedderGraph` missing when both `tasks-text` and `tasks-vision` were present.
- **Resolution:** Split into flavors `textOnly` and `visionOnly` so each APK includes only one MediaPipe tasks library. This avoids the JNI symbol collision and allows both test suites to run (separately) on a device/emulator.

### MediaPipe JNI conflict: tasks-text + tasks-vision (workaround in place)
- **Current approach:** Use product flavors:
  - `textOnly` for text embeddings.
  - `visionOnly` for image embeddings.
- `./gradlew :app:connectedAndroidTest` runs both `connectedTextOnlyDebugAndroidTest` and `connectedVisionOnlyDebugAndroidTest`.
- **Limitation:** A single process should not load both `tasks-text` and `tasks-vision` simultaneously; keep runtime usage aligned to the selected flavor.

### Text embedder model: 100-dim, not 512-dim
- The USE TFLite model outputs **100-dimensional** vectors, not 512 as SPECS assumed.
- `Screenshot.kt` entity and `TextEmbeddingInstrumentedTest.kt` are updated to 100-dim in the working tree (uncommitted).
- Not yet verified on device — should be confirmed by running `TextEmbeddingInstrumentedTest` on a connected device.
