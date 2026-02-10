# SPECS.md — Screenshot / Image Searcher (Android Kotlin)

> **Version**: 0.7-draft
> **Scope**: Android-only for now. Design portable for future iOS port.
> **Status**: PHASE A — awaiting review.
> **Last updated**: 2026-02-10 — versions pinned from Maven Central / Google Maven.

---

## 1. Goals

- **Showcase ObjectBox** as the core persistence + vector similarity search engine for on-device AI apps.
- **Offline-first, privacy-first**: all inference and storage happens on-device. No network calls for OCR/embeddings/labeling/LLM inference; optional model download only via explicit user action.
- **Developer-facing demo**: clean, readable code; toggleable modules so devs can compare search approaches.
- **Compare search modalities**: OCR keyword search vs. semantic text search vs. image similarity vs. label/category filtering vs. optional LLM-assisted keywords/summaries.
- **Reproducible**: pinned versions, deterministic builds, documented setup.
- **Design**: Sleek, stylish,clean, reduced, rounded corners, soft gradients if possible. Only use the colors specified: background: light grey (#F7F7F7); text colour: typically dark grey (\#393939) or for highlighting reasons teal (\#17A6A6); if you need an extra colour you can use the dark blue (\#2A3850); the general highlight colour is the teal: \#17A6A6 - use to highlight things, to guide the eye, but don't overuse; as needed for lines etc.: dark grey (\#393939), Icons should be plain / stylized / no shadow

## 2. Non-Goals

- No fine-tuning of any model.
- No cloud inference or analytics.
- No network calls for OCR/embeddings/labeling/LLM inference. Model download is allowed only via explicit user action (a "Download" button) and must be optional.
- No ObjectBox Sync implementation (design sync-ready, don't implement).
- No iOS/Swift code in this phase (future work).
- No CI/CD setup.

---

## 3. Core User Stories

| ID | Story | Acceptance |
|----|-------|------------|
| US-1 | As a developer, I can import local device images/screenshots into the app and see them indexed. | Images appear in the list with status "indexed" after pipeline completes. |
| US-2 | As a developer, I can search for images / Screenshots by keyword over OCR-extracted text. | Typing "password" returns screenshots / images containing that word (linked to the original asset) |
| US-3 | As a developer, I can search semantically by typing a keyword or a natural-language query. | Typing "login screen" returns relevant screenshots even if "login screen" doesn't appear verbatim in OCR text. |
| US-4 | As a developer, I can search by image similarity (query-by-example). | Selecting a screenshot returns visually similar screenshots ranked by similarity score. |
| US-5 | As a developer, I can filter results by auto-generated category/tag labels. | Tapping a tag like "text" or "screenshot" or "fruits" or "animals" narrows results. |
| US-5b | As a developer, when I type a query, results are ranked by combined signals (OCR + semantic + tags), and items matching multiple signals appear higher. | Unified is the default search mode; multi-signal matches rank higher than single-signal matches; each result shows "Matched by" badges and scores. |
| US-6 | As a developer, I can inspect a screenshot's full metadata: OCR text, tags, embeddings info, timestamps, URI. | Detail screen shows all enrichment data with collapsible sections. |
| US-7 | As a developer, I can toggle individual pipeline modules on/off from a settings screen. | Disabling "Text Embeddings" removes semantic search; re-enabling re-indexes. |
| US-8 | As a developer, I can see indexing progress (queued/indexed/failed counts, last run, failures) and pause/resume/cancel indexing. | Status bar updates in real-time; pause stops the worker; resume continues from where it left off. |
| US-9 | As a developer, the app works immediately after install with a bundled sample dataset. | First launch shows pre-indexed sample screenshots without requiring device permissions. |
| US-10 | (Optional) As a developer, I can enable a small on-device LLM to generate keyword suggestions or short summaries from OCR text. | LLM module toggle appears in settings; when enabled, detail screen shows LLM-generated summary/keywords. |

---

## 4. Architecture Overview

### 4.1 High-Level Diagram

```
┌─────────────────────────────────────────────────────────┐
│                        UI Layer                         │
│  HomeScreen · SearchScreen · DetailScreen · Settings    │
│         (Jetpack Compose + ViewModels)                  │
└────────────────────────┬────────────────────────────────┘
                         │ StateFlow / events
┌────────────────────────▼────────────────────────────────┐
│                   ViewModel Layer                        │
│  SearchViewModel · IndexViewModel · SettingsViewModel   │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│                   Core / Domain Layer                    │
│  IngestionPipeline · SearchEngine · ModuleRegistry      │
│  (Pure Kotlin — testable without Android framework)     │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│                  Infrastructure Layer                    │
│  ObjectBox (persistence + vector index)                  │
│  ML Kit (OCR, Image Labeling)                           │
│  MediaPipe (Text Embedder, Image Embedder, LLM)         │
│  WorkManager (background scheduling)                    │
└─────────────────────────────────────────────────────────┘
```

### 4.2 Ingestion Pipeline

The pipeline processes one image at a time, sequentially, in a single background worker thread. No parallel GPU/accelerator requests.

```
Import (URI)
  → Preprocess (decode, apply EXIF rotation, generate thumbnail, extract basic metadata)
  → [if enabled] OCR (ML Kit Text Recognition → raw text)
  → [if enabled] Text Embedding (MediaPipe Text Embedder → float[] vector)
  → [if enabled] Image Embedding (MediaPipe Image Embedder → float[] vector)
  → [if enabled] Labeling (ML Kit Image Labeling → tags/categories)
  → [if enabled] LLM Summary (MediaPipe LLM Inference → keywords/summary)
  → Persist (write Screenshot entity + related data to ObjectBox)
  → Update indexing status
```

**Pipeline characteristics:**
- Each stage is a standalone component with a clear interface (input → output).
- Stages are conditionally executed based on module toggle state (read from ObjectBox at pipeline start, not mid-run).
- If a stage fails, the error is recorded on the Screenshot entity (`indexingStatus = FAILED`, `indexingError = "..."`) and the pipeline continues to the next image.
- The pipeline is cancellable: between each image, check for cancellation signal.
- The pipeline is resumable: on resume, query for `indexingStatus = QUEUED` entities and continue.

**Image orientation:**
- The Preprocess stage must normalize orientation (apply EXIF rotation) before OCR and embedding inference.
- The normalized bitmap is used for thumbnail generation, OCR, and all embedder inputs.
- The stored thumbnail reflects the corrected orientation; the original URI remains unchanged.

**Atomic persistence:**
- Each processed Screenshot is persisted in ObjectBox in a single transaction at the end of its pipeline run.
- This ensures OCR text, labels, and embeddings are committed together so search indexes are never out of sync with metadata.

**Pause/Resume checkpointing:**
- The pipeline writes `lastCompletedStage` on the Screenshot entity after each stage completes successfully (values as per canonical enum in §4.7 entity table).
- On pause/cancel, the worker stops after finishing the current stage boundary (no mid-stage interruption).
- On resume, the worker restarts from the next stage for items with `indexingStatus = QUEUED` and a non-null `lastCompletedStage`.
- This prevents inconsistent partial data and makes demo behavior deterministic.

### 4.3 Background Processing Model

- **WorkManager** with a single `CoroutineWorker` for the ingestion pipeline.
- The worker is enqueued as a **unique work** (`ExistingWorkPolicy.KEEP`) so only one instance runs at a time.
- Pause = cancel the current WorkManager work + mark in-progress item as QUEUED (with `lastCompletedStage` preserved).
- Resume = re-enqueue the worker; it picks up from `lastCompletedStage` for partially-processed items.
- Cancel = cancel work + mark all QUEUED items as CANCELLED.

**Paused vs. idle UI distinction:** "Paused" means "not running with queued work remaining." The UI must display a paused state distinct from idle (e.g., "Paused — 58 remaining") even though the DB status for those items stays `QUEUED`. To survive process death, a `userPaused: Boolean` is persisted in an `AppState` entity in ObjectBox (not as a new indexing status). The derived UI state is: `isPaused = userPaused && !isRunning && queued > 0`. On app restart, this correctly distinguishes "user explicitly paused" from "worker hasn't been scheduled yet."

**No parallel accelerator usage:**
- Exactly one ingestion worker runs at a time (unique WorkManager chain).
- The worker processes exactly one asset at a time (no batching; no concurrent embedder/OCR calls).
- Do not enqueue secondary workers for embeddings/labels. All stages run sequentially within the same worker.

### 4.4 UI Notification Mechanism

- **Kotlin `StateFlow`** for all reactive state (no RxJava, no LiveData).
- `IndexingStatusFlow`: emits `IndexingState(queued: Int, indexed: Int, failed: Int, isRunning: Boolean, isPaused: Boolean, lastRunTimestamp: Long?)`.
- ViewModels expose `StateFlow` to Compose UI.
- The ingestion worker updates ObjectBox counts; a lightweight `Flow` derived from ObjectBox queries (or a simple `MutableStateFlow` updated by the worker via a shared singleton) propagates changes to the UI.

### 4.5 Separation of Core Logic vs. GUI

```
core/
  model/          — entity definitions, enums, value objects
  pipeline/       — IngestionPipeline, individual stage interfaces + implementations
  search/         — SearchEngine (keyword, semantic, image-sim, filter)
  module/         — ModuleRegistry, ModuleConfig

ui/
  home/           — HomeScreen + ViewModel
  search/         — SearchScreen + ViewModel
  detail/         — DetailScreen + ViewModel
  settings/       — SettingsScreen + ViewModel
  indexing/       — IndexingStatusBar + ViewModel

infra/
  objectbox/      — ObjectBox setup, Box providers
  mlkit/          — OCR wrapper, Image Labeling wrapper
  mediapipe/      — Text Embedder wrapper, Image Embedder wrapper, LLM wrapper
  worker/         — WorkManager worker
```

The `core/` and `infra/` layers are testable without the UI. The `infra/` wrappers expose clean Kotlin interfaces that `core/pipeline/` consumes.

### 4.6 Component Test Plan

**Rule: NO MOCKS. Use real components and real ObjectBox (in-memory or temp directory).**

| Component | Test Type | What's Verified |
|-----------|-----------|-----------------|
| ObjectBox entities | JVM unit test (ObjectBox in-memory) | CRUD, vector index creation, query correctness |
| OCR stage | Android instrumented test | Real ML Kit on a fixture image → expected text substring; EXIF-rotated fixture (90°) also passes |
| Text Embedder stage | Android instrumented test | Real MediaPipe on fixture text → vector of correct dimension, normalized |
| Image Embedder stage | Android instrumented test | Real MediaPipe on fixture image → vector of correct dimension, normalized |
| Image Labeling stage | Android instrumented test | Real ML Kit on fixture image → expected label(s) present |
| LLM stage (optional) | Android instrumented test | Real MediaPipe LLM on fixture text → non-empty output |
| Ingestion Pipeline | Android instrumented test | Full pipeline on fixture image → Screenshot entity persisted with all fields populated |
| Search Engine (keyword) | JVM unit test | Pre-populated ObjectBox → keyword query returns expected results |
| Search Engine (semantic) | Android instrumented test | Pre-populated ObjectBox with embeddings → nearest-neighbor query returns expected ranking |
| Search Engine (image sim) | Android instrumented test | Pre-populated ObjectBox → image similarity query returns expected ranking |
| Module toggles | JVM unit test | Disable a module → pipeline skips that stage → entity fields are null for that module |
| Indexing status | JVM unit test | Pipeline processes N images → status counts match (queued/indexed/failed) |
| Pause state | JVM unit test | Pause produces `isPaused=true` and `isRunning=false` while `queued > 0`; idle has both false |

**Integration tests** (combine multiple real components):
- Import 3 fixture images → run full pipeline → verify all entities in ObjectBox → run each search type → verify results.
- Toggle off text embeddings → re-index → verify semantic search returns empty, keyword search still works.
- Pause mid-indexing → verify partial results → resume → verify completion.

### 4.7 Data Model Overview

#### Screenshot Entity (primary)

| Field | Type | Notes |
|-------|------|-------|
| `id` | `Long` | ObjectBox auto-ID |
| `originalUri` | `String` | Content URI or file path to original image |
| `thumbnailBytes` | `ByteArray` | Compressed JPEG thumbnail (~200×200, quality 70) |
| `width` | `Int` | Original image width |
| `height` | `Int` | Original image height |
| `dateTaken` | `Long?` | EXIF or file-system timestamp (epoch ms) |
| `dateImported` | `Long` | When the app imported it (epoch ms) |
| `sourceApp` | `String?` | Package name if detectable from URI |
| `ocrText` | `String?` | Full OCR-extracted text (null if OCR disabled) |
| `textEmbedding` | `FloatArray?` | 512-dim vector from text embedder (null if disabled or no OCR text) |
| `imageEmbedding` | `FloatArray?` | 1024-dim vector from image embedder (null if disabled) |
| `labels` | `MutableList<String>` | Auto-generated labels from ML Kit Image Labeling |
| `labelConfidences` | `FloatArray?` | Parallel array of confidence scores for `labels` |
| `llmSummary` | `String?` | LLM-generated summary/keywords (null if LLM disabled) |
| `indexingStatus` | `String` | Enum-as-string: `QUEUED`, `PROCESSING`, `INDEXED`, `FAILED`, `CANCELLED` |
| `lastCompletedStage` | `String?` | Last successfully completed pipeline stage. Canonical enum values (single source of truth): `PREPROCESS_DONE`, `OCR_DONE`, `TEXT_EMBED_DONE`, `IMAGE_EMBED_DONE`, `LABELING_DONE`, `LLM_DONE`. Null if no stage completed yet. Used for pause/resume checkpointing. |
| `indexingError` | `String?` | Error message if FAILED |
| `enabledModulesAtIndex` | `String?` | Comma-separated list of modules active when indexed (for re-index detection) |

#### ModuleConfig Entity

| Field | Type | Notes |
|-------|------|-------|
| `id` | `Long` | ObjectBox auto-ID |
| `moduleId` | `String` | Unique key: `ocr`, `text_embedding`, `image_embedding`, `labeling`, `llm` |
| `enabled` | `Boolean` | Current toggle state |
| `lastUpdated` | `Long` | Timestamp of last toggle change |

#### AppState Entity (singleton — persisted)

| Field | Type | Notes |
|-------|------|-------|
| `id` | `Long` | ObjectBox auto-ID (always ID=1, singleton pattern) |
| `userPaused` | `Boolean` | True when user explicitly paused indexing; persists across process death |
| `lastIndexingRunTimestamp` | `Long?` | When the last indexing run completed (epoch ms) |

#### IndexingState (not persisted — derived / in-memory)

This is the same `IndexingState` class emitted by `IndexingStatusFlow` in §4.4.

| Field | Type | Notes |
|-------|------|-------|
| `queued` | `Int` | Count of QUEUED entities |
| `indexed` | `Int` | Count of INDEXED entities |
| `failed` | `Int` | Count of FAILED entities |
| `isRunning` | `Boolean` | Whether the worker is currently active |
| `isPaused` | `Boolean` | Derived: `userPaused && !isRunning && queued > 0` (see AppState entity) |
| `lastRunTimestamp` | `Long?` | When the last indexing run completed (from AppState) |

#### Sync-Readiness Notes

- All entities use ObjectBox `@Id` (Long) which is compatible with ObjectBox Sync.
- `originalUri` is device-local; a future sync layer would need to map URIs or sync thumbnail bytes.
- `textEmbedding` and `imageEmbedding` are stored as `FloatArray` which ObjectBox Sync handles natively.
- No cross-device unique ID is needed now, but adding a `uuid: String` field later is straightforward.

---

## 5. Embedding Dimensions

### 5.1 Text Embeddings

- **Model**: Universal Sentence Encoder (USE) via MediaPipe Text Embedder
- **File**: `universal_sentence_encoder.tflite`
- **Dimension**: **512**
- **License**: Apache 2.0

The ObjectBox `@HnswIndex` on `textEmbedding` must specify `dimensions = 512`. Any vector written to this field must be exactly 512 floats. The MediaPipe Text Embedder is configured with `l2Normalize = true`, producing unit-length vectors.

### 5.2 Image Embeddings

- **Model**: MobileNet V3 Small via MediaPipe Image Embedder
- **File**: `mobilenet_v3_small.tflite`
- **Dimension**: **1024**
- **Size**: ~10 MB
- **License**: Apache 2.0

The ObjectBox `@HnswIndex` on `imageEmbedding` must specify `dimensions = 1024`. The MediaPipe Image Embedder is configured with `l2Normalize = true`.

**Why MobileNet V3 Small over Large**: smaller download (~10 MB vs ~15 MB), faster inference (3.9 ms vs 9.8 ms on Pixel 6), and 1024 dimensions is sufficient for a demo. If quality is noticeably worse during experiments, we can evaluate switching to Large (1280 dims) — this would require a one-line dimension change in the entity annotation + re-index.

### 5.3 Dimension Mismatch Prevention

- The entity annotation hardcodes the dimension: `@HnswIndex(dimensions = 512)` / `@HnswIndex(dimensions = 1024)`.
- The MediaPipe embedder wrapper validates output dimension at initialization (assert `result.embeddingResult().embeddings()[0].floatEmbedding().length == EXPECTED_DIM`).
- A constants file (`EmbeddingConstants.kt`) defines `TEXT_EMBEDDING_DIM = 512` and `IMAGE_EMBEDDING_DIM = 1024`, referenced by both the entity and the embedder wrapper.
- If the model changes, the constant changes → entity annotation changes → ObjectBox regenerates schema → re-index is required. This is a deliberate, traceable change.

---

## 6. Vector Metric Configuration

### 6.1 Distance Type

| Embedding | ObjectBox `distanceType` | Justification |
|-----------|--------------------------|---------------|
| Text (USE, 512-dim) | `VectorDistanceType.COSINE` | USE embeddings represent semantic direction; cosine similarity measures angular distance, which is the standard metric for sentence embeddings. With l2-normalized vectors, cosine distance and dot-product distance are equivalent, but `COSINE` is more semantically clear. |
| Image (MobileNet V3, 1024-dim) | `VectorDistanceType.COSINE` | MobileNet feature vectors encode visual similarity as direction in embedding space. Cosine is the standard metric for image retrieval with CNN features. |

### 6.2 L2 Normalization Enforcement

Both MediaPipe embedders are configured with `l2Normalize = true`:

```kotlin
// Text Embedder configuration
val textOptions = TextEmbedderOptions.builder()
    .setBaseOptions(baseOptions)
    .setL2Normalize(true)   // ← enforced
    .build()

// Image Embedder configuration
val imageOptions = ImageEmbedderOptions.builder()
    .setBaseOptions(baseOptions)
    .setL2Normalize(true)   // ← enforced
    .build()
```

**Enforcement strategy**:
- The wrapper classes (`TextEmbedderWrapper`, `ImageEmbedderWrapper`) are the only code that creates embedder instances.
- `l2Normalize = true` is hardcoded in the wrapper, not configurable externally.
- A unit test verifies that the output vector has unit length (L2 norm ≈ 1.0 ± 0.001).

**Breaking change guard**: If `l2Normalize` cannot be enabled for a chosen embedder/model (e.g., the option is unsupported or produces incorrect results), the vector distance metric must be re-evaluated — `COSINE` may no longer be valid. This is a breaking change and requires explicit approval before implementation.

---

## 7. Storage Strategy

### 7.1 What's Stored in ObjectBox

- **Thumbnail**: compressed JPEG `ByteArray`, ~200×200 pixels, quality 70%.
  - Estimated size: 5–15 KB per thumbnail.
  - Used for: search result list display, detail screen preview.
- **Original URI**: `String` pointing to the original file on device (content:// or file://).
  - The full-resolution image is loaded on demand from this URI (e.g., for the detail screen full-size view).
- **Embeddings**: `FloatArray` — 512 floats (text) + 1024 floats (image) = ~6 KB per screenshot.
- **OCR text**: `String` — variable size, typically 0–2 KB for a screenshot.
- **Labels + metadata**: small strings and primitives.

**Total per screenshot**: ~10–25 KB in ObjectBox. For 10,000 screenshots, the DB would be ~100–250 MB, which is manageable for on-device.

### 7.2 What's NOT Stored in ObjectBox

- Full-resolution image pixels. The original file is accessed via URI when needed.
- Model files (stored in app assets or on-device files directory).

### 7.3 Thumbnail Generation

```kotlin
fun generateThumbnail(bitmap: Bitmap): ByteArray {
    val maxDim = 200
    val scale = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
    val scaled = Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * scale).toInt(),
        (bitmap.height * scale).toInt(),
        true  // bilinear filtering
    )
    val stream = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 70, stream)
    return stream.toByteArray()
}
```

Thumbnail dimensions and quality are defined as constants (`THUMBNAIL_MAX_DIM = 200`, `THUMBNAIL_QUALITY = 70`) so they can be tuned without code changes.

---

## 8. Ranking, Thresholds, and Result Explainability (Search Fusion)

### 8.1 Label Confidence Thresholds (ML Kit Image Labeling)

- ML Kit provides `confidence` per label as a float in **[0.0 .. 1.0]**.
- Display confidence in UI as: `confidencePct = round(confidence * 100)`.

**Thresholds (default):**
- **Display threshold:** show label chips only if `confidence >= 0.70`.
- **Max chips displayed:** show at most **5** label chips (highest confidence first).
- **Storage strategy for labels:**
  - Store the **top 10** labels per image (sorted by confidence).
  - During rendering/filtering, apply the display threshold (`>= 0.70`) and max-chips rule.
  - This keeps data available for experiments while keeping the UI clean.

### 8.2 Similarity Display Thresholds

Results below a minimum similarity score are hidden from results by default to reduce noise.

| Signal | Threshold | Behavior below threshold | Default |
|--------|-----------|--------------------------|---------|
| Semantic text search | `semanticScore >= 0.25` | Hide from results | Permissive; tune in EXPERIMENTS.md |
| Image similarity | `imageScore >= 0.30` | Hide from results | Permissive; tune in EXPERIMENTS.md |

**Score computation** (mirrors semanticScore):
- `imageScore = clamp(1.0 - cosineDistance, 0.0, 1.0)` (for image embedding cosine distance from ObjectBox vector search).

These defaults are intentionally permissive and will be tuned in EXPERIMENTS.md using the fixed sample dataset during Package 4/5.

**Interaction with Unified fusion (§8.3):** In Unified mode, thresholds apply per-modality during candidate generation. An item remains eligible for display if it passes **any** enabled modality threshold (e.g., a keyword hit keeps the item visible even if its semantic score is below 0.25). This preserves the "multi-signal wins" intent and prevents good multi-signal items from being hidden due to one weak modality score.

### 8.3 Unified Search and Ranking Across Signals

When the user types a query `q`, the app executes all enabled search modalities and merges results:

1. **Keyword search** over `ocrText` (if OCR module enabled)
2. **Semantic text search** via ObjectBox vector search on `textEmbedding` (if text embeddings enabled)
3. **Category/tag match** over stored labels (if labeling enabled)

All candidate results are **unioned by Screenshot ID**, then ranked with a transparent scoring model that prioritizes multi-signal matches:

**Final score:**

```
finalScore = coverageBoost + 0.8 * keywordScore + 1.0 * semanticScore + 0.6 * tagScore
```

Where:

- **`coverageBoost`** rewards items matching multiple modalities:
  - matches all 3 signals → `+3.0`
  - matches 2 signals → `+1.5`
  - matches 1 signal → `+0.0`

- **`keywordScore`**:
  - `1.0` if keyword match found in `ocrText`, else `0.0`
  - (Optional later: small bonus for multiple hits; cap total keywordScore at 1.0.)

- **`semanticScore`**:
  - Convert ObjectBox cosine distance to similarity for ranking/display:
    - `semanticScore = clamp(1.0 - cosineDistance, 0.0, 1.0)`
  - Requires `VectorDistanceType.COSINE` + l2-normalized embeddings.

- **`tagScore`**:
  - If query text matches a label, use the **max confidence** among matched labels:
    - `tagScore = max(confidence(label_i))` for labels that match `q`
  - Else `0.0`

**Guarantee:** Results matching **more modalities** (e.g., OCR + semantic + tag) must appear above results that match only one modality, even if one single-modality score is very high.

**Scope:** Fusion ranking applies to text queries (`q`) in Unified mode. Similar Image mode is ranked purely by image similarity score (optionally filtered by labels).

### 8.4 Result Explainability (Developer-Facing)

Each result card must display "Matched by" badges showing which signals contributed, e.g.:
- `OCR` (and a short highlighted snippet)
- `Semantic` (similarity score 0..1)
- `Tag: <label>` (confidence %)

This makes ranking behavior inspectable and supports the "learning demo" goal.

---

## 9. Proposed Tech Stack


### 9.1 Stack 1 — Default (Android)

All versions verified against Maven Central / Google Maven as of 2026-02-10.

| Component | Choice | Artifact | Version | Notes |
|-----------|--------|----------|---------|-------|
| Language | Kotlin | `org.jetbrains.kotlin:kotlin-stdlib` | **2.3.0** | Latest stable |
| Min SDK | 26 (Android 8.0) | — | — | See rationale below |
| Target SDK | 35 (Android 15) | — | — | Play compliance |
| Compile SDK | 35 | — | — | |
| Build — AGP | Android Gradle Plugin | `com.android.application` | **9.0.0** | |
| Build — Gradle | Gradle wrapper | — | **9.1.0** | Pinned in `gradle-wrapper.properties` |
| Build — JDK | Temurin | — | **17** | `jvmToolchain(17)` |
| Persistence + Vector Search | ObjectBox | `io.objectbox:objectbox-android` | **5.1.0** | `@HnswIndex`, `VectorDistanceType.COSINE` |
| ObjectBox Gradle Plugin | | `io.objectbox:objectbox-gradle-plugin` | **5.1.0** | Must match runtime version |
| OCR | ML Kit Text Recognition (bundled) | `com.google.mlkit:text-recognition` | **16.0.1** | Latin script; on-device |
| Text Embeddings | MediaPipe Text Embedder | `com.google.mediapipe:tasks-text` | **0.10.32** | USE model, 512-dim |
| Image Embeddings | MediaPipe Image Embedder | `com.google.mediapipe:tasks-vision` | **0.10.32** | MobileNet V3 Small, 1024-dim |
| Image Labeling | ML Kit Image Labeling (bundled) | `com.google.mlkit:image-labeling` | **17.0.9** | 400+ labels, on-device |
| Optional LLM | MediaPipe LLM Inference | `com.google.mediapipe:tasks-genai` | **0.10.32** | Gemma 3 270M-IT |
| UI | Jetpack Compose + Material 3 | `androidx.compose:compose-bom` | **2026.01.01** | |
| Background Work | WorkManager | `androidx.work:work-runtime-ktx` | **2.11.1** | Single CoroutineWorker |
| Reactive State | Kotlin StateFlow / Flow | (stdlib) | — | No RxJava |
| Image Loading | Coil | `io.coil-kt.coil3:coil-compose` | **3.3.0** | Compose-native |
| Navigation | Compose Navigation | (via Compose BOM) | — | |

**minSdk rationale**: We keep minSdk 26 for the first Android-only release to reduce edge cases and simplify testing, while still supporting modern on-device ML + background ingestion.

### 9.2 MediaPipe Version Compatibility Note

MediaPipe tasks are published under a shared version scheme. All three artifacts (`tasks-text`, `tasks-vision`, `tasks-genai`) are pinned to **0.10.32** — the highest version available on Google Maven for all three as of 2026-02-10. This avoids transitive dependency conflicts and the R8 minification bugs reported in 0.10.20–0.10.21.

**Reproducibility constraint**: Before locking TODO, verify the chosen MediaPipe Tasks version exists for `tasks-text`, `tasks-vision`, and `tasks-genai`, and that Gradle resolves exactly one MediaPipe version across all configurations.

**Verification step (Package 0)**: After scaffolding the Gradle project, run `./gradlew :app:dependencies | grep mediapipe` to confirm only one MediaPipe version resolves. If conflicts appear, add a resolution strategy in `build.gradle.kts`.

Version catalog entry (single source of truth):
```toml
[versions]
mediapipe = "0.10.32"

[libraries]
mediapipe-tasks-text = { module = "com.google.mediapipe:tasks-text", version.ref = "mediapipe" }
mediapipe-tasks-vision = { module = "com.google.mediapipe:tasks-vision", version.ref = "mediapipe" }
mediapipe-tasks-genai = { module = "com.google.mediapipe:tasks-genai", version.ref = "mediapipe" }
```

### 9.3 LLM Model Distribution

The app is fully functional without the LLM module. When the LLM toggle is enabled:
- If the Gemma 3 270M model file is not present on device, the UI shows a **"Model not installed"** state with an explicit **"Download Model"** button (user-triggered download, not automatic).
- A convenience script `scripts/download-gemma-model.sh` is provided for developers to pre-download the model during setup.
- The model file is stored in the app's internal files directory, not bundled in the APK.
- Model license: Gemma Terms of Use (not Apache 2.0) — developers must accept Google's terms from Kaggle before downloading.
- **Warm up** (optional action): pre-load the LLM interpreter/session so the first inference is fast. Triggered from Settings or automatically when the LLM toggle is enabled.
- **Release model** (explicit action): close the interpreter/session, free native buffers, drop references. Invoked when the LLM toggle is disabled, when the app goes to background, or when the user explicitly taps "Release" in Settings. Browsing thumbnails or non-LLM screens must not keep the LLM resident in memory.

### 9.4 Alternatives Considered (future work only)

| Building Block | Alternative | Why Deferred |
|----------------|-------------|--------------|
| Text Embeddings | ONNX Runtime + MiniLM | Lower-level API; MediaPipe is simpler for a demo. Can revisit if USE quality is insufficient. |
| Image Embeddings | MobileNet V3 Large (1280-dim) | Slower, larger. Only upgrade if Small quality is poor. |
| LLM | Gemma 2 2B | 8× larger; only consider if 270M output quality is useless. |
| UI | XML Views | Compose is the modern default; no reason to use XML for a new project. |

### 9.5 Known Warnings

- **ML Kit / targetSdk changes**: If targeting API 35 introduces behavior or Play policy issues, upgrade ML Kit to the next stable patch and record the change in EXPERIMENTS.md.
- **Privacy Sandbox SDK**: If the latest MediaPipe SDKs require it, set `android.experimental.privacysandbox.sdk.enable=true` in `gradle.properties`. Otherwise keep standard AGP 9.0 defaults. Verify during Package 0 dependency check.

---

## 10. UI Plan

### 10.1 Screens

All UI colors must be defined in a single theme file; do not hardcode colors in composables.

1. **Home Screen**
   - Grid/list of indexed screenshots (thumbnails).
   - Floating action button: "Import Images" (opens system picker).
   - Top bar: search icon → navigates to Search Screen.
   - Indexing status bar (sticky at bottom or top): shows counts + progress + Pause/Resume button.

2. **Search Screen**
   - Search bar at top with text input.
   - Search mode selector (chips/tabs): **"Unified"** (default) | "Keyword (OCR)" | "Semantic" | "Similar Image".
   - **Unified mode** runs OCR + semantic + tag search and fuses ranking per §8.3. Single-modality tabs are available for comparison (supports the "learning demo" goal).
   - For "Similar Image": tap a thumbnail in results or pick from gallery to use as query.
   - Filter chips below search bar: auto-populated from available labels/categories. Filter chips apply in Unified/Keyword/Semantic modes; Similar Image mode optionally supports label filters but defaults to off.
   - Results list: thumbnail + title/filename + matched signal:
     - Keyword: OCR text snippet with highlight.
     - Semantic: similarity score (0–1).
     - Image sim: similarity score (0–1).
   - Empty states with helpful messages per mode (e.g., "Enable Text Embeddings in Settings for semantic search").

3. **Detail Screen**
   - Full-size image preview (loaded from original URI).
   - Collapsible sections:
     - **OCR Text**: full extracted text, scrollable.
     - **Tags/Categories**: chips showing ML Kit labels + confidence %. Confidence is ML Kit label confidence only; LLM-generated keywords (if any) are shown separately without a confidence score.
     - **LLM Summary** (if enabled): keywords/summary text.
     - **Developer Info** (collapsed by default): embedding dimensions, indexing timestamp, enabled modules at index time, original URI, file metadata.

4. **Settings Screen**
   - Module toggles (switches):
     - OCR Ingestion + Keyword Search
     - Text Embeddings + Semantic Search
     - Image Embeddings + Image Similarity Search
     - Labeling/Categorization + Filters
     - (Optional) On-Device LLM
   - Each toggle shows: status (enabled/disabled), model name, approximate size.
   - "Re-index All" button (with confirmation dialog): clears existing index data and re-runs pipeline with current toggle settings.
   - "Clear Database" button (with confirmation dialog).
   - App info: version, ObjectBox version, model versions.

### 10.2 Navigation

```
Home ──→ Search ──→ Detail
  │                    ↑
  └──→ Settings        │
  └──→ Detail ─────────┘
```

Single-activity, Compose Navigation with `NavHost`. Back stack is standard Android.

### 10.3 Indexing Status Bar

Visible on Home Screen (and optionally Search Screen). Shows:
- "Indexing: 42/100 (3 failed)" or "Idle — 100 indexed"
- Progress bar (determinate when count is known).
- Pause / Resume button (icon toggle).
- Cancel button (stop icon, with confirmation).

---

## 11. Milestone / Package Plan

### Package 0 (v0.01) — Project Skeleton + ObjectBox
- Android project scaffold (Kotlin 2.3.0, AGP 9.0.0, Gradle 9.1.0, Kotlin DSL, version catalog, Compose BOM 2026.01.01).
- ObjectBox 5.1.0 integration: entity definitions, Box setup, in-memory test config.
- MediaPipe dependency compatibility check: `./gradlew :app:dependencies | grep mediapipe` confirms single version resolves.
- Bundled sample dataset (10–20 screenshots in `shared/samples/`, provided by human).
- Basic Home Screen showing imported thumbnails from ObjectBox.
- Import flow: system image picker → decode → thumbnail → persist.
- **Tests**: ObjectBox CRUD, entity schema validation, thumbnail generation.

### Package 1 (v0.02) — OCR + Keyword Search
- ML Kit Text Recognition integration (bundled, Latin).
- OCR pipeline stage: image → text.
- Keyword search via ObjectBox string query on `ocrText`.
- Search Screen with keyword mode.
- Detail Screen with OCR text section.
- **Tests**: OCR on fixture image, keyword search precision, empty-text handling.

### Package 2 (v0.03) — Text Embeddings + Semantic Search
- MediaPipe Text Embedder integration (USE, 512-dim).
- Text embedding pipeline stage: OCR text → float[512].
- ObjectBox HNSW vector index on `textEmbedding`.
- Semantic search: query text → embed → nearest-neighbor search.
- Search Screen with semantic mode.
- **Tests**: Embedding dimension validation, L2 norm check, semantic search ranking, empty-input handling.

### Package 3 (v0.04) — Image Embeddings + Image Similarity
- MediaPipe Image Embedder integration (MobileNet V3 Small, 1024-dim).
- Image embedding pipeline stage: bitmap → float[1024].
- ObjectBox HNSW vector index on `imageEmbedding`.
- Image similarity search: select image → embed → nearest-neighbor search.
- Search Screen with image similarity mode.
- **Tests**: Embedding dimension validation, L2 norm check, image similarity ranking, duplicate detection.

### Package 4 (v0.05) — Labeling + Filters
- ML Kit Image Labeling integration (bundled).
- Labeling pipeline stage: bitmap → labels + confidences.
- Filter chips on Search Screen.
- Detail Screen tags section.
- **Tests**: Labeling on fixture images, filter query correctness, empty-label handling.

### Package 5 (v0.06) — Settings + Module Toggles + Indexing Control
- Settings Screen with all toggles.
- ModuleConfig persistence in ObjectBox.
- Pipeline reads toggle state; stages skip if disabled.
- Re-index functionality.
- Indexing status bar with Pause/Resume/Cancel.
- Background worker (WorkManager) integration.
- **Tests**: Toggle on/off → pipeline behavior, pause/resume flow, status count accuracy.

### Package 6 (v0.07) — Optional LLM Module
- MediaPipe LLM Inference integration (Gemma 3 270M-IT).
- LLM pipeline stage: OCR text → summary/keywords.
- Detail Screen LLM summary section.
- Settings toggle for LLM module.
- "Model not installed" state + "Download Model" button (user-triggered).
- `scripts/download-gemma-model.sh` convenience script for developers.
- App is fully functional without the LLM model present.
- **Tests**: LLM output non-empty (when model present), toggle disable → no LLM field, model-not-present → graceful "not installed" state, performance/memory within bounds.

### Package 7 (v0.08) — Polish + Documentation
- UI polish: loading states, error states, empty states, transitions.
- Bundled sample dataset finalized (diverse screenshots).
- REPRODUCIBILITY.md finalized.
- EXPERIMENTS.md "Final Tech Stack" section completed.
- CHANGELOG.md up to date.
- README.md (no AI attribution).

### Future Packages (not in this phase)
- **PDF/Scan Notes**: render PDF pages → treat as images → OCR + embeddings.
- **iOS Port**: Swift + ObjectBox Swift + Core ML + SPM.
- **ObjectBox Sync**: sync indexed data across devices.

---

## 12. Acceptance Criteria Per Package

### Package 0 (v0.01)
- [ ] `./gradlew assembleDebug` succeeds.
- [ ] `./gradlew test` passes — ObjectBox CRUD tests green.
- [ ] App launches on emulator, shows sample thumbnails.
- [ ] Import from gallery persists a Screenshot entity with thumbnail bytes.

### Package 1 (v0.02)
- [ ] `./gradlew connectedAndroidTest` passes — OCR fixture test green.
- [ ] OCR fixture rotated 90° (EXIF) still produces correct text (orientation handled in Preprocess).
- [ ] Keyword search returns correct results for known fixture text.
- [ ] Detail screen shows OCR text for indexed screenshots.

### Package 2 (v0.03)
- [ ] `./gradlew connectedAndroidTest` passes — text embedding tests green.
- [ ] Semantic search returns relevant results for natural-language queries.
- [ ] Embedding vector is 512-dim and L2-normalized (test assertion).

### Package 3 (v0.04)
- [ ] `./gradlew connectedAndroidTest` passes — image embedding tests green.
- [ ] Image similarity search returns visually similar images ranked by score.
- [ ] Embedding vector is 1024-dim and L2-normalized (test assertion).

### Package 4 (v0.05)
- [ ] `./gradlew connectedAndroidTest` passes — labeling tests green.
- [ ] Only labels with confidence ≥ 0.70 are displayed; max 5 chips; chips sorted by confidence desc.
- [ ] Filter chips appear and narrow results correctly.
- [ ] Detail screen shows labels with confidence percentages.
- [ ] In Unified mode (text query), with OCR + semantic + labels enabled, items matching 2+ signals rank above items matching 1 signal (fusion ranking test).

### Package 5 (v0.06)
- [ ] All prior tests still pass (regression).
- [ ] Toggling a module off → that pipeline stage is skipped → corresponding fields are null.
- [ ] Pause → resume → indexing completes with correct counts.
- [ ] Re-index clears and re-processes all images.

### Package 6 (v0.07)
- [ ] LLM produces non-empty output for OCR text input.
- [ ] LLM toggle off → `llmSummary` is null → no crash.
- [ ] App does not OOM on a device with 4 GB RAM during LLM inference.
- [ ] LLM resources can be released after use; browsing thumbnails does not keep the LLM resident.

### Package 7 (v0.08)
- [ ] `./gradlew assembleDebug` + `./gradlew test` + `./gradlew connectedAndroidTest` all pass.
- [ ] Each result card shows "Matched by" badges (OCR + snippet, Semantic similarity, Tag + confidence).
- [ ] REPRODUCIBILITY.md allows a fresh checkout → build → run on a new machine.
- [ ] All doc files (SPECS, TODO, EXPERIMENTS, REPRODUCIBILITY, CHANGELOG) are up to date.

---

## 13. Future: iOS Port + Sync Readiness

### iOS Port Notes
- ObjectBox has a Swift binding; the data model (entities, vector indexes, distance types) translates directly.
- OCR: Apple Vision framework (on-device).
- Text Embeddings: Core ML with a converted USE model, or a NaturalLanguage framework embedding.
- Image Embeddings: Core ML with a converted MobileNet model.
- Labeling: Vision framework `VNClassifyImageRequest`.
- LLM: Core ML with a converted Gemma model, or Apple's on-device foundation models if available.
- UI: SwiftUI, mirroring the Compose screens.
- The `Screenshot` entity schema should be identical across platforms for future Sync compatibility.

### Sync Readiness
- All entities use `@Id` (Long) which ObjectBox Sync requires.
- No cross-device unique ID is needed yet. When Sync is added, ObjectBox provides its own sync IDs.
- `originalUri` is device-local and won't sync meaningfully; `thumbnailBytes` would serve as the portable visual reference.
- Embedding vectors sync as `FloatArray` — no special handling needed.
- `enabledModulesAtIndex` tracks which modules produced the data, preventing confusion when syncing between devices with different module configurations.

---

## 14. Open Questions for Review

1. **Sample dataset**: You said you'll provide 10–20 samples under `shared/samples/`. Should I wait for you to add them before Package 0, or scaffold the project first and add them later?
