# TODO.md — Screenshot / Image Searcher (Android Kotlin)

> **Scope:** PHASE B only — derived from SPECS.md.
>  **Hard rules:** Only this file is modified. No commits. Do not edit SPECS.md.

## Global Guardrails (apply to ALL packages)

- **Offline-only inference:** No network calls for OCR/embeddings/labeling/LLM inference. Model download is optional and must be explicit user action.
- **ObjectBox required:** All persistence is ObjectBox. All vector search is ObjectBox HNSW.
- **No full-res blobs in DB:** Store **only** `thumbnailBytes` (compressed JPEG) + `originalUri` string reference. Never store full-resolution image bytes.
- **Vector metric alignment:** Use `VectorDistanceType.COSINE` for text + image embeddings; enforce `l2Normalize=true` in MediaPipe; add **instrumented** tests that validate **L2 norm ~ 1.0 ± 0.001** before writing vectors (MediaPipe runs on-device).
- **No parallel accelerator usage:** Exactly one WorkManager worker; sequential per-image pipeline; no concurrent embedder/OCR calls.
- **No mocks in tests:** Use real ObjectBox (temp dir/in-memory where supported) + real ML Kit / MediaPipe for instrumentation tests.

## Notes / Risks (do NOT change SPECS; implement safely)

- **AGP 9 built-in Kotlin:** AGP 9 enables built-in Kotlin; the `org.jetbrains.kotlin.android` plugin may error (“no longer required”). Plan: migrate per Android’s “built-in Kotlin” steps in Package 0; if blocked, explicitly opt-out via `android.builtInKotlin=false` (temporary), record in EXPERIMENTS.md later (Phase C).
- **MediaPipe + R8:** Minification can strip required classes. Add **assembleRelease** smoke gates not only in Package 2, but also in Package 3 and Package 6 (where additional MediaPipe artifacts are introduced/activated).
- **Fusion ranking drift risk:** The scoring/threshold rules are in SPECS §8.2–§8.4. To avoid interpretation drift, the formula and thresholds are repeated in Packages 5–6 implementation steps.

------

## Package 0 — v0.01 Project Skeleton + ObjectBox + Sample Dataset

### Goals

- Scaffold Android project under `/android-kotlin/`.
- ObjectBox integrated and working.
- Home screen lists sample thumbnails from DB (pre-indexed data path).
- Import flow: system picker → preprocess → thumbnail → persist basic `Screenshot`.

### Steps

1. **Create project structure**
   - Create folders:
     - `/android-kotlin/` (Android app)
     - `/shared/` already exists (keep)
2. **Gradle + versions (pin exactly as SPECS)**
   - Gradle wrapper **9.1.0**
   - AGP **9.0.0**
   - Kotlin **2.3.0** (language level)
   - JDK toolchain **17**
   - Compose BOM **2026.01.01**
3. **AGP 9 built-in Kotlin migration (IMPORTANT)**
   - **Do NOT apply** `org.jetbrains.kotlin.android` plugin in module build files.
   - Remove any `android.kotlinOptions {}` DSL usage; migrate to the new recommended DSL if needed.
   - If `kapt` becomes necessary later (ObjectBox), follow the “legacy kapt” migration path (do not pre-add it unless needed).
   - If you cannot migrate immediately, explicitly opt out with `android.builtInKotlin=false` (temporary) and note it in “Notes/Risks” section of TODO (no SPECS edits).
4. **ObjectBox integration**
   - Add ObjectBox runtime and Gradle plugin (version **5.1.0**).
   - Define `Screenshot` + `ModuleConfig` (and `AppState` later in Package 5).
   - Ensure ObjectBox code generation works.
5. **Sample dataset**
   - Load images from `/shared/samples/` (if present) and persist them on first launch **without permissions**.
   - If samples are missing: app still launches with empty state.
6. **Home screen (Compose)**
   - Grid/list of thumbnails (from ObjectBox).
   - Floating action button: Import Images → system picker.
7. **Import flow (minimal)**
   - Pick image URI → decode → apply EXIF rotation → generate thumbnail → persist `Screenshot` with `indexingStatus=INDEXED` (for Package 0 only; real pipeline starts later).
   - **Storage guardrail:** persist only thumbnail bytes + URI; no full-res bytes.

### Expected files (high-level)

- `/android-kotlin/` with standard Gradle project layout
- Entity classes + ObjectBox setup under `/android-kotlin/app/src/main/java/...`
- Basic Compose UI screens

### Commands / Tests

- `./gradlew :android-kotlin:app:assembleDebug`
- `./gradlew :android-kotlin:app:test`
  - JVM unit tests:
    - ObjectBox CRUD test (temp directory store)
    - Thumbnail generation test

------

## Package 1 — v0.02 OCR + Keyword Search

### Goals

- ML Kit Text Recognition stage.
- Keyword search over `ocrText`.
- Detail screen shows OCR text.

### Steps

1. Add ML Kit text recognition dependency (bundled on-device).
2. Implement OCR stage wrapper (infra) and pipeline stage (core).
3. Update ingestion flow: import → preprocess → OCR → persist.
4. Keyword search: ObjectBox query over `ocrText` (case-insensitive).
5. Search screen: Keyword (OCR) mode.
6. Detail screen: OCR section.
7. **Storage guardrail restated:** Store OCR text as String; do not store full-res image bytes.

### Tests

- Instrumented test: OCR fixture image contains expected substring.
- Instrumented test: rotated EXIF fixture still works (preprocess orientation correct).
- JVM test: keyword search returns expected entity IDs.

### Commands

- `./gradlew :android-kotlin:app:connectedAndroidTest`

------

## Package 2 — v0.03 Text Embeddings + Semantic Search

### Goals

- MediaPipe Text Embedder (512-dim USE).
- ObjectBox HNSW index on `textEmbedding`.
- Semantic search mode.

### Steps

1. Add MediaPipe `tasks-text` dependency pinned to **0.10.32**.
2. Add/use model file (no auto-download; bundle if license permits, otherwise require local placement).
3. Implement `TextEmbedderWrapper` enforcing:
   - `l2Normalize = true`
   - output dim == 512
4. Pipeline stage: OCR text → embedding (skip if OCR null/empty).
5. Add `@HnswIndex(dimensions=512, distanceType=COSINE)` on `textEmbedding`.
6. Semantic search: query string → embed → nearest-neighbor vector search; compute `semanticScore = clamp(1 - cosineDistance, 0..1)`; apply threshold `>= 0.25`.
7. **Storage guardrail restated:** store FloatArray only; no image blobs.

### Tests

- Instrumented: embedding dim == 512.
- Instrumented: L2 norm ~ 1.0 ± 0.001.
- Instrumented: semantic ranking sanity with fixture dataset.

### Commands / Gates

- `./gradlew :android-kotlin:app:connectedAndroidTest`
- **Release minify smoke test (MediaPipe/R8 gate):**
   `./gradlew :android-kotlin:app:assembleRelease`
   If it fails/crashes, add `proguard-rules.pro` keep rules (starting point):
  - `-keep class com.google.mediapipe.tasks.** { *; }`
  - `-keep class com.google.protobuf.** { *; }`
  - `-dontwarn com.google.mediapipe.**`

------

## Package 3 — v0.04 ~~Image Embeddings + Image Similarity~~

### Goals

~~- MediaPipe Image Embedder (1024-dim MobileNet V3 Small).~~
~~- ObjectBox HNSW index on `imageEmbedding`.~~
~~- Similar Image search mode.~~

### Steps

~~1. Add MediaPipe `tasks-vision` pinned to **0.10.32**.~~
~~2. Implement `ImageEmbedderWrapper` enforcing:~~
~~   - `l2Normalize = true`~~
~~   - output dim == 1024~~
~~3. Pipeline stage: bitmap → embedding.~~
~~4. Add `@HnswIndex(dimensions=1024, distanceType=COSINE)` on `imageEmbedding`.~~
~~5. Similar image mode: choose a screenshot → vector search → `imageScore = clamp(1 - cosineDistance, 0..1)`; threshold `>= 0.30`.~~
~~6. Storage guardrail restated: store FloatArray only; never store full-res image bytes.~~

### Tests

~~- Instrumented: dim == 1024~~
~~- Instrumented: L2 norm ~ 1.0 ± 0.001~~
~~- Instrumented: similarity ranking with fixtures~~

### Commands / Gates

~~- `./gradlew :android-kotlin:app:connectedAndroidTest`~~
~~- Release minify smoke test (repeat gate):~~
~~   `./gradlew :android-kotlin:app:assembleRelease`~~

------

## Package 4 — v0.05 Labeling + Filters

### Goals

- ML Kit Image Labeling stage.
- Filter chips in Search UI.
- Detail screen shows tags with confidence.

### Steps

1. Add ML Kit image labeling dependency.
2. Implement labeling wrapper and pipeline stage → store top 10 labels + confidences.
3. UI rules (from SPECS):
   - Display labels only if confidence ≥ 0.70
   - Max 5 chips, sorted by confidence desc
4. Filter chips:
   - Populate from available labels in DB.
   - Apply filters in Unified/Keyword/Semantic.
5. Detail screen tags section.
6. **Storage guardrail restated:** store labels/confidences only; no image blobs.

### Tests

- Instrumented: expected label present on fixture images.
- JVM test: filter query correctness.

### Commands

- `./gradlew :android-kotlin:app:connectedAndroidTest`

------

## Package 4.1 — v0.05.1 Metadata + Derived Fields (Added Later)

### Goals

- Capture and persist image metadata (EXIF/MediaStore).
- Add derived description and dominant colors (on-device).
- Add metadata-based search filters.
- Use metadata for search, e.g. keyword as well as semantic.
- Detail screen shows metadata and description.

### Steps

1. **Metadata extraction (preprocess/import)**
   - Read metadata with priority:
     - EXIF for `dateTaken`, `orientation`, `width`, `height` if present.
     - MediaStore for `displayName`, `mimeType`, `sizeBytes`, `dateModified`, `dateTaken`, `album`, `durationMs`, `width`, `height`.
     - Decode bounds as fallback for dimensions.
   - Persist fields on `Screenshot`:
     - `displayName`, `mimeType`, `sizeBytes`, `width`, `height`, `orientation`, `dateTaken`, `dateModified`, `album`, `durationMs`.
2. **Derived fields**
   - `description`: on-device **image captioning model** (no network). Keep short (1–2 sentences).
   - `dominantColors`: compute top 3–5 colors from normalized bitmap; store as packed `IntArray` (0xRRGGBB).
3. **Search / filters**
   - Add metadata filters:
     - date range (taken/modified), name contains, size range, mime/type, dimensions, orientation, album, duration.
   - Integrate filters into Search UI (alongside label chips).
4. **Detail UI**
   - Add a “Metadata” section with all captured fields.
   - Show `description` below OCR/tags (if present).
   - Show color swatches for `dominantColors`.
5. **Storage guardrail restated**
   - Continue storing only thumbnail bytes + URI; no full-res bytes.

### Tests

- Instrumented: metadata extraction for a fixture asset (date/name/dimensions non-null where expected).
- Instrumented: dominant colors returns 3–5 values for a fixture.
- JVM: description generation non-empty when OCR or labels present.
- JVM: metadata filter query correctness (date range, name contains).

### Commands

- `./gradlew :android-kotlin:app:test`
- `./gradlew :android-kotlin:app:connectedAndroidTest`

------

## Package 5 — v0.06 Settings + Module Toggles + Indexing Control + Unified Fusion

### Goals

- Settings screen toggles persisted in ObjectBox (`ModuleConfig`).
- WorkManager sequential ingestion with pause/resume/cancel.
- Unified search fusion + “Matched by” badges (developer explainability).
- Persist `AppState.userPaused` to survive process death.

### Steps

1. Implement Settings screen:
   - toggles: OCR, text embeddings, image embeddings, labeling, LLM (optional toggle visible but disabled until Package 6)
   - Re-index all / Clear DB actions (confirm dialogs)
2. Persist toggles in ObjectBox (`ModuleConfig`).
3. Add `AppState` singleton entity:
   - `userPaused: Boolean`
   - `lastIndexingRunTimestamp: Long?`
4. WorkManager ingestion:
   - Unique work, `ExistingWorkPolicy.KEEP`
   - Sequential per-image pipeline
   - Pause: cancel work + set `userPaused=true`, keep items QUEUED
   - Resume: set `userPaused=false` + enqueue worker
   - Cancel: cancel work + mark QUEUED as CANCELLED
5. Pause/Resume checkpointing:
   - Write `lastCompletedStage` after each stage.
   - On resume, continue from next stage.
6. Indexing status bar:
   - Derived counts: queued/indexed/failed
   - `isPaused = userPaused && !isRunning && queued > 0`
7. **ObjectBox → Flow bridge**
   - Prefer ObjectBox coroutine/Flow support (`subscribe().toFlow()` or equivalent) to drive `StateFlow` updates.
8. **Unified search fusion (SPECS §8.2–§8.4)**
   - Candidate generation: union results from enabled modalities.
   - Threshold handling: per-modality thresholds during candidate generation; item remains visible if it passes **any** enabled modality.
   - Scoring (repeat here to avoid drift):
     - `finalScore = coverageBoost + 0.8*keywordScore + 1.0*semanticScore + 0.6*tagScore`
     - `coverageBoost`:
       - all 3 signals: +3.0
       - 2 signals: +1.5
       - 1 signal: +0.0
     - `keywordScore`: 1.0 if OCR contains query else 0.0
     - `semanticScore`: clamp(1 - cosineDistance, 0..1)
     - `tagScore`: max confidence among labels matching query else 0.0
   - Guarantee: multi-signal results rank above single-signal results.
9. “Matched by” badges on result cards:
   - OCR + snippet highlight
   - Semantic score
   - Tag + confidence

### Tests

- JVM: module toggles skip stages → fields null.
- JVM: pause state derived correctly (`isPaused` logic).
- Instrumented: work pause/resume continues and completes.
- JVM/instrumented: fusion ranking guarantees multi-signal wins.

### Commands

- `./gradlew :android-kotlin:app:test`
- `./gradlew :android-kotlin:app:connectedAndroidTest`

------

## Package 6 — v0.07 Optional LLM Module (MediaPipe GenAI)

### Goals

- MediaPipe `tasks-genai` pinned to **0.10.32**.
- Optional LLM stage produces summary/keywords from OCR text.
- Model not installed → explicit “Download Model” button (user-triggered).

### Steps

1. Add `tasks-genai` dependency pinned to 0.10.32.
2. Implement LLM wrapper with explicit load/release.
3. Settings:
   - Toggle enables LLM stage
   - If model missing → “Model not installed” + “Download Model”
   - Provide “Warm up” and “Release” actions
4. Pipeline stage: OCR text → llmSummary (store string).
5. **Storage guardrail restated:** store summary string only; do not store anything else large.
6. Memory/perf guard:
   - Ensure browsing thumbnails doesn’t keep LLM resident.
   - Release on toggle off and app background.

### Tests

- Instrumented: with model present → non-empty output.
- Instrumented: model missing → graceful UI state (no crash).
- Perf sanity: no OOM on 4 GB device target.

### Commands / Gates

- `./gradlew :android-kotlin:app:connectedAndroidTest`
- **Release minify smoke test (repeat gate):**
   `./gradlew :android-kotlin:app:assembleRelease`

------

## Package 7 — v0.08 Polish + Documentation

### Goals

- UI polish (loading/empty/error states).
- Docs finalized: REPRODUCIBILITY, EXPERIMENTS, CHANGELOG, README (no attribution).

### Steps

1. UI polish: empty states per mode, nicer transitions, error banners.
2. Dataset finalized.
3. Documentation completeness sweep.

### Commands

- `./gradlew :android-kotlin:app:assembleDebug`
- `./gradlew :android-kotlin:app:test`
- `./gradlew :android-kotlin:app:connectedAndroidTest`

------

## STOP

Reply with: **“TODO.md ready for approval.”**
 Do **not** implement Package 0 until you approve.

------

## 
