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
- ML Kit Image Labeling: 17.0.8
- TensorFlow Lite: 2.17.0

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

## 2026-02-11 — ~~MediaPipe Image Embedder integration~~
~~- Tried: MediaPipe `tasks-vision` 0.10.32 with model asset path `models/image_embedder.tflite` (initial)~~
~~- Why: Required on-device image similarity embeddings.~~
~~- Outcome: 1024-dim embeddings with L2 norm checks; ObjectBox HNSW (COSINE) used for similarity search. Requires model asset present.~~

## 2026-02-11 — ~~Text Embedder graph not found in connected tests~~
~~- Failure: `connectedAndroidTest` fails with `MediaPipeException` missing `TextEmbedderGraph` (calculator not found).~~
~~- Change: Added `androidTestImplementation` for `mediapipe-tasks-text` and `mediapipe-tasks-vision` to ensure test APK includes JNI deps.~~
~~- Outcome: Failure persists; requires further investigation or environment change.~~

## 2026-02-11 — ~~Text Embedder graph error diagnostics~~
~~- Inspected: `app-debug.apk` contains `libmediapipe_tasks_text_jni.so` and `libmediapipe_tasks_vision_jni.so` for all ABIs.~~
~~- Inspected: `app-debug-androidTest.apk` has no native libs (as expected for test APK).~~
~~- Inspected: `tasks-text` AAR ships JNI libs for all ABIs; no mediapipe assets in AAR.~~
~~- Inspected: No conflicting JNI lib names between tasks-text and tasks-vision.~~
~~- Attempt: Added `androidTestImplementation` for `tasks-text`/`tasks-vision`.~~
~~- Attempt: Explicit `System.loadLibrary("mediapipe_tasks_text_jni")` before creating `TextEmbedder`.~~
~~- Outcome: `TextEmbedderGraph` missing error persists on Pixel_6(AVD) - 16.~~

## 2026-02-11 — ~~Text EmbedderGraph failure across versions (emulator + device)~~
~~- Purpose: Fix `TextEmbedderGraph` missing error in connected tests.~~
~~- Environment: Pixel 9 Pro device + Pixel 6/Pixel 6 API 35 emulators (arm64-v8a).~~
~~- Observed failure: `MediaPipeException` — `TextEmbedderGraph` not registered (see stacktrace in report).~~
~~- Attempts:~~
~~  - Attempt 1: verified dependency alignment (tasks-text/core/vision all 0.10.32) — failed.~~
~~  - Attempt 2: downgrade to 0.10.29 — failed.~~
~~  - Attempt 3: upgrade to 0.20230731 — image embedder test crashed.~~
~~  - Attempt 4: downgrade to 0.10.26.1 — failed.~~
~~  - Attempt 5: downgrade to 0.10.15 — failed.~~
~~- Outcome: Text embedder tests still fail; image embedder tests pass (except on 0.20230731 where crash occurred).~~

## 2026-02-11 — Attempt 1: version alignment for Text EmbedderGraph
- **Purpose:** Resolve TextEmbedderGraph missing error in connected tests by verifying and aligning MediaPipe versions.
- **Environment:** Pixel 9 Pro device (arm64-v8a), Pixel 6 emulators (arm64-v8a).
~~- Action: Aligned `tasks-text`, `tasks-core`, `tasks-vision` to 0.10.15 (verified via `debugAndroidTestRuntimeClasspath`).~~
- **Result:** `./gradlew :app:assembleDebug :app:test` succeeded; `:app:connectedAndroidTest` still fails with `TextEmbedderGraph` not registered for text embedder tests.

## 2026-02-13 — ~~JNI conflict investigation and working hypothesis~~

### ~~Problem~~
~~When both `tasks-text` and `tasks-vision` are dependencies, `connectedAndroidTest` fails with `TextEmbedderGraph` not registered. Image embedder tests pass; text embedder tests fail consistently.~~

### ~~What we tried (traces visible in codebase)~~
~~These attempted fixes are still present in the code as residual changes:~~
~~- `androidTestImplementation` for both `tasks-text` and `tasks-vision` → in `build.gradle.kts` lines 82–83. Did not fix.~~
~~- Explicit `System.loadLibrary("mediapipe_tasks_text_jni")` before creating TextEmbedder → in `TextEmbedderWrapper.kt` line 29. Did not fix.~~
~~- `useLegacyPackaging = true` → in `build.gradle.kts` line 50. Did not fix.~~

### ~~What we tried (version changes, reverted)~~
~~- Downgraded MediaPipe to 0.10.29, 0.10.26.1, 0.10.15 — all failed with same error.~~
~~- Tried 0.20230731 — image embedder crashed. Reverted to 0.10.32.~~

### ~~Working hypothesis (from interactive debugging, not yet independently reproduced)~~
~~During an interactive debugging session, the following was observed but should be re-verified before relying on it:~~
~~1. Both `.so` files export the same JNI symbol (`Java_com_google_mediapipe_framework_Graph_nativeCreateGraph`) with separate calculator registries.~~
~~2. Text embedder passed when run in isolation (vision dependency temporarily removed).~~
~~3. Load order determines which embedder fails — whichever library's `Graph` native methods ART binds first wins; the other embedder's graph type is not found.~~
~~4. Neither library's `.so` contains the other's calculator (checked via symbol dump).~~
~~5. Neither library has `JNI_OnLoad` — both rely on automatic JNI resolution.~~
~~If confirmed, this would mean MediaPipe's Android SDK cannot run `tasks-text` and `tasks-vision` in the same process.~~

### ~~To verify this hypothesis~~
~~1. Remove `tasks-vision` dependency temporarily → run only text embedder test on device → should pass.~~
~~2. Restore `tasks-vision`, remove `tasks-text` → run only image embedder test → should pass.~~
~~3. Use `nm -D` or `readelf` on both `.so` files from the APK to confirm duplicate symbols.~~

### ~~Potential solutions (not yet tried)~~
~~1. TFLite Interpreter directly: Load the USE `.tflite` model via `org.tensorflow:tensorflow-lite` interpreter (no MediaPipe Graph layer), keeping MediaPipe only for image embeddings.~~
~~2. Process isolation: Run text and image embedders in separate Android processes.~~
~~3. ONNX Runtime for text embeddings: Use ONNX Runtime with a USE/MiniLM ONNX model, keeping MediaPipe only for image embeddings.~~
~~4. Build MediaPipe from source with both calculators linked into a single `.so`.~~
~~5. Check `tasks-genai`: See if it ships a combined native library.~~

## 2026-02-13 — USE model outputs 100 dimensions, not 512
- **Discovery:** The Universal Sentence Encoder TFLite model downloaded for text embeddings outputs **100-dimensional** vectors, not 512 as stated in SPECS.
- **Action:** Updated ObjectBox `@HnswIndex(dimensions=100)` on `textEmbedding` field in `Screenshot.kt`. Updated test assertion to `assertEquals(100, vector.size)` in `TextEmbeddingInstrumentedTest.kt`.
- **Impact:** Semantic search still works (COSINE distance); 100-dim is smaller/faster but lower capacity than 512-dim. A different USE variant or model could provide 512-dim if needed.
- **Note:** The SPECS reference to 512-dim likely assumed the full TF Hub USE model; the TFLite-compatible variant is 100-dim.
- **Verified on device** via `TextEmbeddingInstrumentedTest` (connected Android test).

## 2026-02-11 — ~~Attempt 2: split flavors to avoid MediaPipe JNI conflict (PASS)~~
~~- Purpose: Avoid JNI symbol collision between `tasks-text` and `tasks-vision` by isolating them per flavor.~~
~~- Change: Added flavors `textOnly` and `visionOnly`; moved text tests to `textOnlyAndroidTest` and image tests to `visionOnlyAndroidTest`; moved embedder wrappers to flavor source sets; added stubs in the opposite flavor; scoped dependencies per flavor.~~
~~- Result: `./gradlew :app:assembleDebug :app:test :app:connectedAndroidTest` PASS (runs both flavor test APKs separately).~~

## 2026-02-16 — MediaPipe version pin (current state)
- **Change:** Pinned MediaPipe `tasks-text` to 0.10.15 in `android-kotlin/gradle/libs.versions.toml`.
- **Why:** Earlier downgrades were required during investigation; this is the currently configured version.
- **Outcome:** `connectedAndroidTest` passes on device/emulator.

## 2026-02-16 — ML Kit Image Labeling + tag filters (Package 4)
- **Tried:** ML Kit Image Labeling 17.0.8 (default on-device model).
- **Why:** Required for label tags and filter chips.
- **Change:** Added labeling stage, stored top 10 labels with confidence; added filter chips; tags shown on Detail screen for confidence ≥ 0.70.
- **Tests:** Added `ImageLabelingInstrumentedTest` with `label_fixture.jpg` test asset and JVM label filter test.
~~- Outcome: `./gradlew :app:test :app:connectedAndroidTest` passed on Pixel_6 AVD (textOnly + visionOnly flavors).~~

## 2026-02-16 — Metadata capture + derived description/colors (Package 4.1)
- **Purpose:** Persist MediaStore/EXIF metadata; add derived description and dominant colors; add metadata filters.
- **Change:** Added metadata extraction (EXIF + MediaStore + file fallback), description builder (labels + OCR), dominant colors extractor, and metadata filter UI/state.
- **Tests:** Added metadata extraction instrumented test and JVM tests for description + metadata filters.
~~- Outcome: `./gradlew :app:test :app:connectedAndroidTest` passed on Pixel 9 Pro device (textOnly + visionOnly flavors).~~

## 2026-02-16 — Show-and-Tell captioning model integration (Package 4.1)
- **Purpose:** Replace heuristic description with an on-device image captioning model.
- **Change:** Integrated Show-and-Tell (CNN encoder + LSTM decoder) using the droidfringe reference models (`inceptionv3_1.tflite`, `lstm_2.tflite`, `word_counts.txt`). Caption stored in `description` and embedded via text embedder when available.
- **Source:** Model files sourced from the droidfringe ImageCaptioningAndroid reference repo (Show-and-Tell).
- **Tests:** Added `CaptioningInstrumentedTest`.
**Outcome:** `./gradlew :app:test :app:connectedAndroidTest` passed on Pixel 9 Pro device (single-flavor setup).

## 2026-02-16 — Remove image similarity embedding (scope change)
- **Purpose:** Remove image similarity search to avoid split build/runtime and keep a single combined build.
- **Change:** Dropped MediaPipe `tasks-vision`, removed image embedding field/search/UI mode, removed flavors and related tests/assets.
- **Outcome:** `./gradlew :app:test :app:connectedAndroidTest` passed on Pixel 9 Pro device (single-flavor).

## 2026-02-16 — Captioner preprocessing alignment
- **Purpose:** Ensure Show-and-Tell input preprocessing matches model expectations.
- **Change:** Use model input tensor shape for resize; apply `[-1, 1]` normalization when input is 299×299, otherwise fallback to 0..1 scaling.
- **Outcome:** `./gradlew :app:test :app:connectedAndroidTest` passed on Pixel 9 Pro device.

## 2026-02-16 — Beam search for captioning
- **Purpose:** Improve caption quality vs greedy decoding.
- **Change:** Implemented beam search (beam size 3) for Show-and-Tell decoder.
- **Outcome:** `./gradlew :app:test :app:connectedAndroidTest` passed on Pixel 9 Pro device.

## 2026-02-16 — Show-and-Tell caption quality regression (switch decision)
- **Observation:** Captions degraded for real images (e.g., Apple logo miscaptioned as unrelated objects). This persisted after preprocessing alignment and beam search.
- **Conclusion:** Show-and-Tell appears too biased/underfit for current assets; we will replace the captioning model with a newer on-device model.
- **Next step:** Evaluate MediaPipe LLM Inference with a Gemma vision-capable model (pending model selection and assets).

## 2026-02-16 — Switch captioning to Gemma-3n (MediaPipe LLM Inference)
- **Purpose:** Replace Show-and-Tell captioner due to poor caption accuracy on real images.
- **Model:** `gemma-3n-E2B-it-int4.litertlm` (MediaPipe LiteRT-LM format), stored under `android-kotlin/app/src/main/assets/models/llm/` and copied to app files dir at runtime.
- **Dependency:** Added `com.google.mediapipe:tasks-genai:0.10.27` and removed TensorFlow Lite interpreter usage for captioning.
- **Implementation:** New `GemmaCaptioner` uses `LlmInference` + `LlmInferenceSession` with vision modality enabled; prompt is a single-sentence description.
- **Outcome:** Build compiles; device-only validation needed (LLM Inference not reliable on emulators).

## 2026-02-16 — Gemma-3n integration blockers (attempts)
- **Attempt 1:** `tasks-genai:0.10.27` (per docs) failed to compile on JDK 17 with classfile version 65 (Java 21) in `LlmInference`.
- **Attempt 2:** Downgraded to `tasks-genai:0.10.18` (JDK17 compatible), but vision-related APIs were missing (`LlmInferenceOptions`, `setGraphOptions`, `addImage`). This version appears text-only.
- **Next decision:** Either move Android toolchain to JDK 21 to use `tasks-genai:0.10.27`, or select a different on-device captioning model that is compatible with JDK 17.
- **Decision:** Move toolchain to JDK 21 to support `tasks-genai:0.10.27` (required for Gemma-3n vision).
- **Build check:** With JDK 21 configured and API imports corrected (GraphOptions + nested LlmInferenceOptions), `./gradlew :app:assembleDebug :app:test` succeeds.
- **Model packaging fix:** Removed Gemma-3n `.litertlm` from app assets to avoid APK install failures; model now loaded from `/data/local/tmp/llm/` via `adb push`.
- **Crash (device):** SIGABRT in `libllm_inference_engine_jni.so` with message `Unknown model type: tf_lite_audio_adapter` while initializing Gemma-3n.
- **Hypothesis:** Current `.litertlm` bundle includes an audio adapter not supported by this runtime; try alternate model build.
- **Action:** Added fallback to look for `gemma-3n-E2B-it-int4-Web.litertlm` under `/data/local/tmp/llm/`.

## 2026-02-18 — Gemma captioning crash (maxTokens too low)
- **Observation:** Device logcat shows `OUT_OF_RANGE` in `LlmExecutorCalculator`: `current_step(12) + input_size(258) was not less than maxTokens(64)` followed by SIGSEGV in `libllm_inference_engine_jni.so` during `GemmaCaptioner.caption`.
- **Change:** Increased `MAX_TOKENS` from 64 to 512 in `GemmaCaptioner` to accommodate vision prompt/image tokens plus output.
- **Files:** `android-kotlin/app/src/main/java/com/screenshotsearcher/infra/captioning/GemmaCaptioner.kt`
- **Tests:** Not run (needs device run to validate).

## 2026-02-18 — Empty library after fix (no seed images)
- **Observation:** App shows “No screenshots yet” after startup. `seedFromAssetsIfEmpty` only loads images from app assets root, but `app/src/main/assets/` currently contains only `models/` (no `.jpg/.png`).
- **Conclusion:** No images are auto-seeded; use the Import button or add sample images to `app/src/main/assets/` if we want auto-seed.
- **Change:** None (diagnosis only).

## 2026-02-18 — Seed images not found (assets subdir fallback)
- **Observation:** Sample images live under `/shared/samples`, but the app still shows “No screenshots yet.”
- **Hypothesis:** Assets may be packaged under a `samples/` subdirectory instead of the assets root, so `assets.list("")` misses them.
- **Change:** Update seeding to also list `assets.list("samples")` and open `samples/<file>` paths.
- **Files:** `android-kotlin/app/src/main/java/com/screenshotsearcher/core/data/ScreenshotRepository.kt`
- **Tests:** Not run (needs device run to validate).

## 2026-02-18 — ObjectBox path logging (API access issue)
- **Observation:** Attempting to log `ObjectBoxStore.store.directoryPath` / `.directory` fails to compile; directory is not publicly accessible on this ObjectBox version.
- **Change:** Log default Android path based on `context.filesDir/objectbox`.
- **Files:** `android-kotlin/app/src/main/java/com/screenshotsearcher/ScreenshotSearcherApp.kt`
- **Tests:** Not run (compile fix only).

## 2026-02-18 — App data reset via pm clear
- **Observation:** `adb shell pm clear com.screenshotsearcher` used to reset app data after seeding issues.
- **Effect:** Clears ObjectBox DB, prefs, and caches; forces fresh seed on next launch.
- **Change:** None (runtime action only).

## 2026-02-18 — Search signal expansion + UI diagnostics
- **Observation:** Search results only reflected OCR keyword + OCR semantic signals; description text and description embeddings were not used in search ranking/diagnostics.
- **Change:** Added description keyword search and description semantic search (caption embeddings), and show per-result diagnostics (OCR keyword match, description keyword match, OCR semantic similarity, description semantic similarity). Added search-on-IME action and a live “Found X results” line. Separated Categories vs Metadata in filters UI with a clear categories button.
- **Files:** 
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/core/data/ScreenshotRepository.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/search/SearchViewModel.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/home/HomeScreen.kt`
- **Tests:** Not run.

## 2026-02-18 — Caption cleanup (Gemma special tokens)
- **Observation:** Some captions included repeated `<end_of_turn>` tokens.
- **Change:** Sanitize Gemma responses by truncating at known special tokens.
- **Files:** `android-kotlin/app/src/main/java/com/screenshotsearcher/infra/captioning/GemmaCaptioner.kt`
- **Tests:** Not run.

## 2026-02-18 — Filters UX (bottom sheet + tabs)
- **Observation:** Filters dropdown was cramped and hard to scan.
- **Change:** Move filters into a modal bottom sheet with tabs for Categories and Metadata, plus a clear-all button.
- **Files:** `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/home/HomeScreen.kt`
- **Tests:** Not run.

## 2026-02-18 — Build fix: SearchScreen diagnostics + tab row
- **Observation:** Build failed due to `SearchResult.score` removal; SearchScreen still referenced it. TabRow was deprecated.
- **Change:** Update SearchScreen to use new per-signal diagnostics; switch to PrimaryTabRow.
- **Files:** 
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/search/SearchScreen.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/home/HomeScreen.kt`
- **Tests:** `./gradlew :app:assembleDebug` (passes).

## 2026-02-18 — Test fixes after search API changes
- **Observation:** Unit and instrumented tests referenced removed APIs (`searchByKeyword`, `searchBySemantic`).
- **Change:** Update tests to use `searchByKeywordOcr` and `searchBySemanticText`.
- **Files:** 
  - `android-kotlin/app/src/test/java/com/screenshotsearcher/KeywordSearchTest.kt`
  - `android-kotlin/app/src/androidTest/java/com/screenshotsearcher/SemanticSearchInstrumentedTest.kt`
- **Tests:** 
  - `./gradlew :app:test` (passes).
  - `./gradlew :app:connectedAndroidTest` on Pixel 9 Pro - API 16 (passes, 8 tests).

## 2026-02-19 — Seeding diagnostics + HEIC support
- **Observation:** App still shows “No screenshots yet” after `pm clear`; asset seeding may be failing or assets not found.
- **Change:** Add log of asset counts (root, samples, image count) and log decode failures. Add HEIC/HEIF asset decode via ImageDecoder (API 28+).
- **Files:** `android-kotlin/app/src/main/java/com/screenshotsearcher/core/data/ScreenshotRepository.kt`
- **Tests:** 
  - `./gradlew :app:assembleDebug` (passes).
  - `./gradlew :app:test` (passes).
  - `./gradlew :app:connectedAndroidTest` on Pixel 9 Pro - API 16 (passes, 8 tests).

## 2026-02-19 — Seed loop exception logging
- **Observation:** Seed logs show assets found, but UI still says “No screenshots yet”; need to detect per-file failures.
- **Change:** Wrap seed loop in try/catch, log exceptions per asset, and log inserted/total counts after seeding.
- **Files:** `android-kotlin/app/src/main/java/com/screenshotsearcher/core/data/ScreenshotRepository.kt`
- **Tests:** Not run.

## 2026-02-19 — Seed progress logging
- **Observation:** Only the initial seed log appears; no visibility into per-file progress or stalls.
- **Change:** Log each file as it starts and when it successfully seeds.
- **Files:** `android-kotlin/app/src/main/java/com/screenshotsearcher/core/data/ScreenshotRepository.kt`
- **Tests:** Not run.

## 2026-02-19 — Seed stage timing
- **Observation:** Seeding stalls on first asset; need to isolate which stage hangs.
- **Change:** Log stage start/end and elapsed time for thumbnail, OCR, labeling, metadata, colors, caption, and embeddings.
- **Files:** `android-kotlin/app/src/main/java/com/screenshotsearcher/core/data/ScreenshotRepository.kt`
- **Tests:** Not run.

## 2026-02-19 — Seed caption timeout
- **Observation:** Caption stage can take ~60s per image, blocking seeding and leaving UI empty.
- **Change:** Add timeout for captioning during seed (8s); on timeout, skip caption for that item and continue.
- **Files:** `android-kotlin/app/src/main/java/com/screenshotsearcher/core/data/ScreenshotRepository.kt`
- **Tests:** 
  - `./gradlew :app:test` (passes).
  - `./gradlew :app:connectedAndroidTest` on Pixel 9 Pro - API 16 (passes, 8 tests).

## 2026-02-19 — Seed UX improvements (progress + early thumbnails)
- **Observation:** Seeding is slow; UI shows empty grid until all assets finish.
- **Change:** Refresh after each seeded item so thumbnails appear progressively. Limit captioning during seeding to the first 3 assets; add a lightweight overlay showing captioning progress.
- **Files:** 
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/core/data/ScreenshotRepository.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/home/HomeScreen.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/home/HomeViewModel.kt`
- **Tests:** 
  - `./gradlew :app:test` (passes).
  - `./gradlew :app:connectedAndroidTest` on Pixel 9 Pro - API 16 (passes, 8 tests).

## 2026-02-19 — Seed two-phase insert (caption after insert)
- **Observation:** First caption blocks ~30–60s, so no thumbnails appear until it completes.
- **Change:** Insert screenshot immediately without caption; captioning runs in background and updates entities later while thumbnails show.
- **Files:** `android-kotlin/app/src/main/java/com/screenshotsearcher/core/data/ScreenshotRepository.kt`
- **Tests:** 
  - `./gradlew :app:test` (passes).
  - `./gradlew :app:connectedAndroidTest` on Pixel 9 Pro - API 16 (passes, 8 tests).

## 2026-02-19 — Caption progress overlay
- **Observation:** Need visible indicator while captioning continues in background.
- **Change:** Add caption progress state flow and overlay in Home screen.
- **Files:**
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/core/data/ScreenshotRepository.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/home/HomeViewModel.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/home/HomeScreen.kt`
- **Tests:** 
  - `./gradlew :app:test` (passes).
  - `./gradlew :app:connectedAndroidTest` on Pixel 9 Pro - API 16 (passes, 8 tests).

## 2026-02-19 — Crash fix: duplicate LazyGrid keys
- **Observation:** App crashed with `IllegalArgumentException: Key "1" was already used` in LazyGrid.
- **Change:** Deduplicate search results by screenshot id before rendering grids.
- **Files:**
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/home/HomeScreen.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/search/SearchScreen.kt`
- **Tests:** 
  - `./gradlew :app:test` (passes).
  - `./gradlew :app:connectedAndroidTest` on Pixel 9 Pro - API 16 (passes, 8 tests).

## 2026-02-19 — Crash fix: unique LazyGrid keys in search results
- **Observation:** Crash persisted with duplicate LazyGrid keys after deduping results.
- **Change:** Use `itemsIndexed` with composite key (`id-uri-index`) in search ResultsGrid.
- **Files:**
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/search/SearchScreen.kt`
- **Tests:**
  - `./gradlew :app:test` (passes).
  - `./gradlew :app:connectedAndroidTest` on Pixel 9 Pro - API 16 (passes, 8 tests).

## 2026-02-19 — LLM crash mitigation (serialize captioning)
- **Observation:** App crashes with SIGSEGV/SIGABRT in `libllm_inference_engine_jni.so` with XNNPack weight cache errors while captioning on Home grid.
- **Change:** Serialize captioning to a single thread and guard caption generation with a mutex to avoid concurrent LLM/XNNPack initialization.
- **Files:**
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/core/data/ScreenshotRepository.kt`
- **Tests:**
  - `./gradlew :app:test` (passes).
  - `./gradlew :app:connectedAndroidTest` on Pixel 9 Pro - API 16 (passes, 8 tests).

## 2026-02-19 — Caption overlay reposition + teal background
- **Observation:** Captioning progress text overlaps Android menu items; hard to read.
- **Change:** Move overlay down and add a translucent teal background.
- **Files:**
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/home/HomeScreen.kt`
- **Tests:**
  - `./gradlew :app:test` (passes).
  - `./gradlew :app:connectedAndroidTest` on Pixel 9 Pro - API 16 (passes, 8 tests).

## 2026-02-19 — Search ranking + semantic threshold + combined score
- **Observation:** Semantic results were too broad and result ordering did not follow keyword priority.
- **Change:** Raise semantic threshold to 0.70, merge keyword/semantic signals into a single SearchResult, and sort by keyword priority (both keywords > OCR only > description only > semantic-only), then semantic max, then recency. Display combined semantic score in UI.
- **Files:**
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/core/data/ScreenshotRepository.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/search/SearchViewModel.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/home/HomeScreen.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/search/SearchScreen.kt`
- **Tests:**
  - `./gradlew :app:test` (passes).
  - `./gradlew :app:connectedAndroidTest` on Pixel 9 Pro - API 16 (passes, 8 tests).

## 2026-02-19 — Home search bar alignment + caption overlay tweak + show all screenshots when idle
- **Observation:** Home search bar looked different from search screen; caption overlay needs slight reposition and rounded corners; home grid showed no images when not searching.
- **Change:** Align Home search bar to match search screen style, move caption overlay up 8dp with rounded corners, and show all screenshots when query is blank and no search results exist.
- **Files:**
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/home/HomeScreen.kt`
- **Tests:** Not run.

## 2026-02-19 — Rank by average semantic score
- **Observation:** Ranking should use the average of OCR/description semantic scores per object.
- **Change:** Compute average semantic score per result and use it for ranking (after keyword buckets). UI label now shows `Semantic avg`.
- **Files:**
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/search/SearchViewModel.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/home/HomeScreen.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/search/SearchScreen.kt`
- **Tests:** Not run.

## 2026-02-19 — Show all images when search query is empty (Search screen)
- **Observation:** Clearing the search field resulted in an empty screen.
- **Change:** If query is empty, return all screenshots (filtered) and skip keyword/semantic matching.
- **Files:**
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/search/SearchViewModel.kt`
- **Tests:** Not run.

## 2026-02-19 — Log ObjectBox semantic raw distance vs similarity
- **Observation:** Need to verify whether ObjectBox score is a distance where lower is better.
- **Change:** Log top 5 semantic results with raw distance and converted similarity for text + caption embeddings (debug only).
- **Files:**
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/core/data/ScreenshotRepository.kt`
- **Tests:** Not run.

## 2026-02-19 — Verify ObjectBox semantic raw distance vs similarity
- **Observation:** Need to validate if ObjectBox `findWithScores()` returns a distance where lower is better.
- **Change:** Log top 5 semantic results with raw distance and converted similarity for both text and caption embeddings (debug builds only).
- **Files:**
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/core/data/ScreenshotRepository.kt`
- **Findings:** Pending logcat output (need to confirm whether smaller `rawDistance` corresponds to better results).
- **Tests:** Not run.

## 2026-02-19 — Surface semantic debug lines on screen
- **Observation:** Logcat is unavailable; need on-screen visibility of raw ObjectBox distances.
- **Change:** Show semantic debug lines (raw distance + similarity) in Search screen UI when available.
- **Files:**
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/core/data/ScreenshotRepository.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/search/SearchViewModel.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/search/SearchScreen.kt`
- **Findings:** Pending (run a semantic search and review on-screen values).
- **Tests:** Not run.

## 2026-02-19 — Always show semantic debug lines (not debug-only)
- **Observation:** On-screen semantic debug lines were not visible.
- **Change:** Removed debug-only gate so semantic debug lines update for all builds.
- **Files:**
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/core/data/ScreenshotRepository.kt`
- **Findings:** Pending (run semantic search and confirm on-screen list appears).
- **Tests:** Not run.

## 2026-02-19 — Always show semantic debug box (even when empty)
- **Observation:** Semantic debug lines were still not visible on screen.
- **Change:** Always render the debug box and show line count plus “No semantic debug yet” when empty.
- **Files:**
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/search/SearchScreen.kt`
- **Findings:** Pending.
- **Tests:** Not run.

## 2026-02-19 — Show semantic debug box on Home screen
- **Observation:** User sees “Found X results”, which is Home screen; debug box was only on Search screen.
- **Change:** Render semantic debug box on Home screen as well.
- **Files:**
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/home/HomeScreen.kt`
- **Findings:** Pending.
- **Tests:** Not run.

## 2026-02-19 — Show raw semantic distances next to semantic scores
- **Observation:** On-screen debug was hard to interpret; needed raw distance values next to semantic similarities.
- **Change:** Format semantic debug lines to 3 decimals, and show raw distance alongside OCR/description/avg semantic scores under each image.
- **Files:**
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/core/data/ScreenshotRepository.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/home/HomeScreen.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/search/SearchScreen.kt`
- **Findings:** Pending.
- **Tests:** Not run.

## 2026-02-19 — Simplify search UI and tighten semantic ranking
- **Observation:** Search should be a single input (no keyword/semantic toggles); filters should sit to the right of the field; semantic debug should be removed; ranking should prioritize keyword matches and only use semantic avg >= 0.8.
- **Change:** Removed search mode toggles and semantic debug UI/state. Search now always runs keyword + semantic. Filters button placed to the right of the search field. Semantic inclusion threshold raised to 0.80 and ranking keeps keyword buckets with semantic avg used for sorting within buckets.
- **Files:**
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/home/HomeScreen.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/search/SearchScreen.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/search/SearchActivity.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/search/SearchViewModel.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/core/data/ScreenshotRepository.kt`
- **Tests:** Not run.

## 2026-02-19 — Only show semantic-only results when no keyword matches exist
- **Observation:** Semantic-only hits should not be appended if any keyword match exists; use semantic avg >= 0.8 only for sorting within buckets.
- **Change:** If any keyword match exists, filter out semantic-only results entirely. If no keyword matches exist, show only semantic results with avg >= 0.8. Sorting uses semantic avg (>= 0.8) within buckets, then recency.
- **Files:**
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/search/SearchViewModel.kt`
- **Tests:** Not run.

## 2026-02-19 — Fix build errors (Log import, Row import)
- **Observation:** Build failed due to unresolved `Log` and `Row`.
- **Change:** Re-added `android.util.Log` import and `Row` import in Search screen.
- **Files:**
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/core/data/ScreenshotRepository.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/search/SearchScreen.kt`
- **Tests:** Not run.

## 2026-02-19 — Backfill missing descriptions/caption embeddings
- **Observation:** Older or interrupted entries can have missing descriptions/caption embeddings; overlay may not appear if no captioning job is running.
- **Change:** Added caption backfill that scans for missing descriptions/embeddings and regenerates them in the background with progress updates. Triggered on app start and after imports.
- **Files:**
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/core/data/ScreenshotRepository.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/home/HomeViewModel.kt`
- **Tests:** Not run.

## 2026-02-20 — Simplify result labels and remove raw distance display
- **Observation:** Raw distance values cluttered the demo and labels were too long.
- **Change:** Removed raw distance display and shortened labels (OCR keyw./Desc. keyw./OCR sem. sim/Desc. sem. sim/Sem. avg).
- **Files:**
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/home/HomeScreen.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/search/SearchScreen.kt`
- **Tests:** Not run.

## 2026-02-20 — Align ranking with SPECS fusion + add AppState entity
- **Observation:** Ranking diverged from SPECS fusion model; Package 5 requires AppState persistence.
- **Change:** Reintroduced SPECS fusion scoring (coverage boost + weighted keyword/semantic/tag scores) and consistent modality thresholds. Added AppState entity and ObjectBox accessors for AppState/ModuleConfig.
- **Files:**
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/search/SearchViewModel.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/home/HomeScreen.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/core/model/AppState.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/infra/objectbox/ObjectBoxStore.kt`
- **Tests:** Not run.

## 2026-02-20 — Seed missing asset samples even when DB is non-empty
- **Observation:** After DB resets, only a few asset images were present; seeding was skipped once DB had any items.
- **Change:** Seed now imports only missing asset images by checking existing `asset://` URIs and backfilling missing ones.
- **Files:**
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/core/data/ScreenshotRepository.kt`
- **Tests:** Not run.

## 2026-02-20 — Avoid seeding duplicate assets (HEIC + JPG)
- **Observation:** Same image appeared twice after adding JPG conversions of HEIC assets.
- **Change:** During asset seeding, de-duplicate by basename and prefer non-HEIC/HEIF when both exist.
- **Files:**
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/core/data/ScreenshotRepository.kt`
- **Tests:** Not run.

## 2026-02-20 — Add tag match display + compact result table
- **Observation:** Result diagnostics were hard to scan; wanted tag/category match visibility and shorter labels.
- **Change:** Display Tag match (Y/N) and compact two-row table layout; use Y/N for keyword matches; remove semantic average line.
- **Files:**
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/home/HomeScreen.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/search/SearchScreen.kt`
- **Tests:** Not run.

## 2026-02-20 — Settings screen + ModuleConfig persistence
- **Observation:** Package 5 requires settings toggles persisted in ObjectBox.
- **Change:** Added SettingsActivity/Screen with ModuleConfig toggles; persisted via SettingsRepository. Added Settings entry in Home search controls.
- **Files:**
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/core/data/SettingsRepository.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/settings/SettingsActivity.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/settings/SettingsScreen.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/settings/SettingsViewModel.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/home/HomeScreen.kt`
  - `android-kotlin/app/src/main/AndroidManifest.xml`
- **Tests:** Not run.

## 2026-02-20 — Package 5 indexing controls + checkpoints
- **Observation:** Need pause/resume-safe indexing and re-index/clear actions for Package 5.
- **Change:** Added indexing checkpoints via `lastCompletedStage`, re-index/clear DB actions (confirm dialogs), and paused indicator in status line.
- **Files:**
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/core/model/IndexingStage.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/core/model/Screenshot.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/core/data/ScreenshotRepository.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/settings/SettingsScreen.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/settings/SettingsViewModel.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/home/HomeViewModel.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/home/HomeScreen.kt`
- **Tests:** `./gradlew :app:test`, `./gradlew :app:connectedAndroidTest` (captioning test skipped).

## 2026-02-21 — Package 6 Gemma captioning controls
- **Observation:** Package 6 requires explicit Gemma model management and user-triggered actions.
- **Change:** Enabled Gemma captioning toggle, added model status + install instructions, and added warm-up/release actions. Added instrumented tests for missing-model and warm-up paths.
- **Additional:** Removed unused Image Embeddings toggle from Settings/ModuleConfig.
- **Files:**
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/infra/captioning/GemmaCaptioner.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/settings/SettingsScreen.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ui/settings/SettingsViewModel.kt`
  - `android-kotlin/app/src/main/java/com/screenshotsearcher/ScreenshotSearcherApp.kt`
  - `android-kotlin/app/src/androidTest/java/com/screenshotsearcher/GemmaCaptionerInstrumentedTest.kt`
- **Tests:** `./gradlew :app:test`, `./gradlew :app:connectedAndroidTest` (captioning + Gemma tests run on device with model installed).
