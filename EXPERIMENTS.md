# EXPERIMENTS (append-only)

## 2026-02-11 — ObjectBox + ML Kit OCR baseline
- **Tried:** ObjectBox 5.1.0 (kept)
- **Why:** Required persistence + vector search.
- **Outcome:** Build and codegen working; CRUD test passes.

- **Tried:** ML Kit Text Recognition (Latin) 16.0.1 (kept)
- **Why:** Required OCR engine for Android.
- **Outcome:** On-device OCR works in instrumented tests with generated bitmap fixtures.

## Final Tech Stack (pinned)
- Kotlin: 2.3.0
- AGP: 9.0.0
- Gradle: 9.1.0
- Compose BOM: 2026.01.01
- ObjectBox: 5.1.0
- ML Kit Text Recognition: 16.0.1

## 2026-02-11 — androidTest toUri compile failure
- **Failure:** `OcrInstrumentedTest` failed to compile due to unresolved `toUri()` in androidTest sources.
- **Change:** Replaced `toUri()` with `Uri.fromFile(...)` in `OcrInstrumentedTest` (no new dependency).
- **Outcome:** `:app:connectedAndroidTest` now compiles; task fails only due to no connected device.

## 2026-02-11 — MediaPipe Text Embedder (USE) integration
- **Tried:** MediaPipe `tasks-text` 0.10.32 with model asset path `models/text_embedder.tflite` (initial)
- **Why:** Required on-device semantic text embeddings.
- **Outcome:** Initially coded for 512-dim (per SPECS); later discovered the USE TFLite model actually outputs 100-dim. Code updated to 100-dim (see 2026-02-13 entry). Semantic search uses ObjectBox HNSW (COSINE). Requires model asset present.

## 2026-02-11 — USE text embedder model asset download
- **Tried:** Downloaded USE model to `android-kotlin/app/src/main/assets/models/text_embedder.tflite` via curl.
- **Why:** Required for MediaPipe Text Embedder runtime/tests.
- **Outcome:** Model present (~5.8MB). `connectedAndroidTest` build passes, run requires device.

## 2026-02-11 — MediaPipe Image Embedder integration
- **Tried:** MediaPipe `tasks-vision` 0.10.32 with model asset path `models/image_embedder.tflite` (initial)
- **Why:** Required on-device image similarity embeddings.
- **Outcome:** 1024-dim embeddings with L2 norm checks; ObjectBox HNSW (COSINE) used for similarity search. Requires model asset present.

## 2026-02-11 — Text Embedder graph not found in connected tests
- **Failure:** `connectedAndroidTest` fails with `MediaPipeException` missing `TextEmbedderGraph` (calculator not found).
- **Change:** Added `androidTestImplementation` for `mediapipe-tasks-text` and `mediapipe-tasks-vision` to ensure test APK includes JNI deps.
- **Outcome:** Failure persists; requires further investigation or environment change.

## 2026-02-11 — Text Embedder graph error diagnostics
- **Inspected:** `app-debug.apk` contains `libmediapipe_tasks_text_jni.so` and `libmediapipe_tasks_vision_jni.so` for all ABIs.
- **Inspected:** `app-debug-androidTest.apk` has no native libs (as expected for test APK).
- **Inspected:** `tasks-text` AAR ships JNI libs for all ABIs; no mediapipe assets in AAR.
- **Inspected:** No conflicting JNI lib names between tasks-text and tasks-vision.
- **Attempt:** Added `androidTestImplementation` for `tasks-text`/`tasks-vision`.
- **Attempt:** Explicit `System.loadLibrary("mediapipe_tasks_text_jni")` before creating `TextEmbedder`.
- **Outcome:** `TextEmbedderGraph` missing error persists on Pixel_6(AVD) - 16.

## 2026-02-11 — Text EmbedderGraph failure across versions (emulator + device)
- **Purpose:** Fix `TextEmbedderGraph` missing error in connected tests.
- **Environment:** Pixel 9 Pro device + Pixel 6/Pixel 6 API 35 emulators (arm64-v8a).
- **Observed failure:** `MediaPipeException` — `TextEmbedderGraph` not registered (see stacktrace in report).
- **Attempts:**
  - Attempt 1: verified dependency alignment (tasks-text/core/vision all 0.10.32) — failed.
  - Attempt 2: downgrade to 0.10.29 — failed.
  - Attempt 3: upgrade to 0.20230731 — image embedder test crashed.
  - Attempt 4: downgrade to 0.10.26.1 — failed.
  - Attempt 5: downgrade to 0.10.15 — failed.
- **Outcome:** Text embedder tests still fail; image embedder tests pass (except on 0.20230731 where crash occurred).

## 2026-02-11 — Attempt 1: version alignment for Text EmbedderGraph
- **Purpose:** Resolve TextEmbedderGraph missing error in connected tests by verifying and aligning MediaPipe versions.
- **Environment:** Pixel 9 Pro device (arm64-v8a), Pixel 6 emulators (arm64-v8a).
- **Action:** Aligned `tasks-text`, `tasks-core`, `tasks-vision` to 0.10.15 (verified via `debugAndroidTestRuntimeClasspath`).
- **Result:** `./gradlew :app:assembleDebug :app:test` succeeded; `:app:connectedAndroidTest` still fails with `TextEmbedderGraph` not registered for text embedder tests.

## 2026-02-13 — JNI conflict investigation and working hypothesis

### Problem
When both `tasks-text` and `tasks-vision` are dependencies, `connectedAndroidTest` fails with `TextEmbedderGraph` not registered. Image embedder tests pass; text embedder tests fail consistently.

### What we tried (traces visible in codebase)
These attempted fixes are still present in the code as residual changes:
- `androidTestImplementation` for both `tasks-text` and `tasks-vision` → in `build.gradle.kts` lines 82–83. **Did not fix.**
- Explicit `System.loadLibrary("mediapipe_tasks_text_jni")` before creating TextEmbedder → in `TextEmbedderWrapper.kt` line 29. **Did not fix.**
- `useLegacyPackaging = true` → in `build.gradle.kts` line 50. **Did not fix.**

### What we tried (version changes, reverted)
- Downgraded MediaPipe to 0.10.29, 0.10.26.1, 0.10.15 — all failed with same error.
- Tried 0.20230731 — image embedder crashed. Reverted to 0.10.32.

### Working hypothesis (from interactive debugging, not yet independently reproduced)
During an interactive debugging session, the following was observed but should be **re-verified** before relying on it:
1. Both `.so` files export the same JNI symbol (`Java_com_google_mediapipe_framework_Graph_nativeCreateGraph`) with separate calculator registries.
2. Text embedder passed when run in isolation (vision dependency temporarily removed).
3. Load order determines which embedder fails — whichever library's `Graph` native methods ART binds first wins; the other embedder's graph type is not found.
4. Neither library's `.so` contains the other's calculator (checked via symbol dump).
5. Neither library has `JNI_OnLoad` — both rely on automatic JNI resolution.

**If confirmed**, this would mean MediaPipe's Android SDK cannot run `tasks-text` and `tasks-vision` in the same process.

### To verify this hypothesis
1. Remove `tasks-vision` dependency temporarily → run only text embedder test on device → should pass.
2. Restore `tasks-vision`, remove `tasks-text` → run only image embedder test → should pass.
3. Use `nm -D` or `readelf` on both `.so` files from the APK to confirm duplicate symbols.

### Potential solutions (not yet tried)
1. **TFLite Interpreter directly:** Load the USE `.tflite` model via `org.tensorflow:tensorflow-lite` interpreter (no MediaPipe Graph layer), keeping MediaPipe only for image embeddings. Simplest approach.
2. **Process isolation:** Run text and image embedders in separate Android processes (e.g., via a bound `Service` with `android:process=":embedder_text"`).
3. **ONNX Runtime for text embeddings:** Use ONNX Runtime with a USE/MiniLM ONNX model, keeping MediaPipe only for image embeddings.
4. **Build MediaPipe from source** with both calculators linked into a single `.so`. Requires Bazel + MediaPipe source — high effort.
5. **Check `tasks-genai`:** See if it ships a combined native library (unlikely).

## 2026-02-13 — USE model outputs 100 dimensions, not 512
- **Discovery:** The Universal Sentence Encoder TFLite model downloaded for text embeddings outputs **100-dimensional** vectors, not 512 as stated in SPECS.
- **Action:** Updated ObjectBox `@HnswIndex(dimensions=100)` on `textEmbedding` field in `Screenshot.kt`. Updated test assertion to `assertEquals(100, vector.size)` in `TextEmbeddingInstrumentedTest.kt`. These changes are in the working tree (uncommitted).
- **Impact:** Semantic search still works (COSINE distance); 100-dim is smaller/faster but lower capacity than 512-dim. A different USE variant or model could provide 512-dim if needed.
- **Note:** The SPECS reference to 512-dim likely assumed the full TF Hub USE model; the TFLite-compatible variant is 100-dim.
- **Not yet verified on device** — the dimension was observed during the previous debugging session. Should be confirmed by running `TextEmbeddingInstrumentedTest` on a connected device.

## 2026-02-11 — Attempt 2: split flavors to avoid MediaPipe JNI conflict (PASS)
- **Purpose:** Avoid JNI symbol collision between `tasks-text` and `tasks-vision` by isolating them per flavor.
- **Change:** Added flavors `textOnly` and `visionOnly`; moved text tests to `textOnlyAndroidTest` and image tests to `visionOnlyAndroidTest`; moved embedder wrappers to flavor source sets; added stubs in the opposite flavor; scoped dependencies per flavor.
- **Result:** `./gradlew :app:assembleDebug :app:test :app:connectedAndroidTest` **PASS** (runs both flavor test APKs separately).

## 2026-02-16 — MediaPipe version pin (current state)
- **Change:** Pinned MediaPipe `tasks-text` and `tasks-vision` to 0.10.15 in `android-kotlin/gradle/libs.versions.toml`.
- **Why:** Earlier downgrades were required during investigation; this is the currently configured version.
- **Outcome:** With flavor split, `connectedAndroidTest` passes for both flavors on device/emulator.
