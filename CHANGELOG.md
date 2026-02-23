# CHANGELOG

## v0.01
- Android project scaffold under `android-kotlin/` with ObjectBox 5.1.0.
- Home screen shows thumbnails from ObjectBox.
- Import flow: pick image → EXIF normalize → thumbnail → persist (thumbnail + URI only).
- Basic theme with restricted palette.
- JVM tests: ObjectBox CRUD + thumbnail size math.

## v0.02
- OCR stage with ML Kit Text Recognition (on-device).
- Keyword search over `ocrText`.
- Search screen (keyword mode) and detail screen OCR section.
- Instrumented OCR tests (bitmap + EXIF rotation) and keyword search JVM test.

## v0.02.1
- Fix androidTest compilation by removing `toUri()` usage in OCR instrumented test.

## v0.03
- Add MediaPipe Text Embedder (USE) with ObjectBox HNSW (COSINE) for semantic search.
- Store text embeddings on Screenshot and expose semantic search mode.
- Add instrumented tests for embedding dimension, L2 norm, and semantic ranking.
- **Correction:** USE TFLite model outputs 100-dim vectors, not 512 as SPECS assumed. Entity and HNSW index updated to 100-dim.

## v0.04
~~- Add MediaPipe Image Embedder (MobileNet V3 Small, 1024-dim) with ObjectBox HNSW (COSINE) for image similarity search.~~
~~- Store image embeddings on Screenshot and expose Similar Image mode with query-by-example.~~
~~- Add instrumented tests for embedding dimension, L2 norm, and similarity ranking.~~

## v0.04.1
~~- Work around MediaPipe JNI conflict by splitting into `textOnly` and `visionOnly` flavors.~~
~~- Scope `tasks-text` and `tasks-vision` dependencies per flavor; move embedder wrappers/tests to flavor source sets.~~
~~- `:app:connectedAndroidTest` now runs both flavor test APKs and passes on device/emulator.~~

## v0.04.2
- Remove image similarity embedding and related UI/tests; single build variant.

## v0.05
- Add ML Kit Image Labeling stage (top 10 labels with confidence).
- Store labels on `Screenshot`; display tags in Detail screen (confidence ≥ 0.70, max 5).
- Add filter chips in Search (Keyword/Semantic modes) driven by stored labels.
- Tests: instrumented labeling test with fixture asset and JVM label filter test.

## v0.05.1
- Capture metadata (EXIF/MediaStore/file fallback): name, mime/type, size, dates, dimensions, orientation, album, duration.
- Add Show-and-Tell image captioning for description (CNN encoder + LSTM decoder) and dominant colors.
- Add metadata filters in Search and metadata section in Detail.
- Store caption embedding (text embedder) alongside OCR embedding when available.
- Tests: metadata extraction + captioning instrumented tests and JVM metadata filter test.

## v0.05.2
- Move search controls to the top of the overview and add filter dropdown.
- Add a right-side scroll indicator for the screenshot grid.
- Use beam search for caption generation (beam size 3).

## v0.05.3
- Replace Show-and-Tell captioner with Gemma-3n (MediaPipe LLM Inference).
- Load Gemma-3n `.litertlm` from `/data/local/tmp/llm/` (not bundled in APK).

## v0.05.4
- Add description keyword + semantic search signals (caption text + caption embeddings).
- Show per-result search diagnostics (OCR/description keyword matches + semantic scores).
- Search on keyboard IME action and live result count.
- Sanitize Gemma captions to drop special tokens like `<end_of_turn>`.

## v0.05.5
- Simplify search to a single unified field (no keyword/semantic toggles).
- Search field layout updated to leave space for Filters button.
- Adjust ranking: keyword buckets (both > OCR > description); semantic avg used within buckets; semantic-only results shown only when no keyword matches and avg ≥ 0.80.
- Raise semantic threshold to 0.80.
- Add caption backfill to regenerate missing descriptions/embeddings on launch and after imports.
- Tidy per-result labels (shortened text; removed raw-distance display).

## v0.06
- Settings screen with module toggles persisted in ObjectBox.
- WorkManager sequential ingestion with pause/resume/cancel controls.
- Add AppState (userPaused + last indexing timestamp) to persist indexing state.
- Unified fusion ranking (coverage boost + keyword/semantic/tag scores) with per-modality thresholds.
- Indexing checkpoints per stage with `lastCompletedStage` to resume after interruption.
- Add re-index all / clear database actions (confirm dialogs).
- Indexing status line shows paused state when queue exists.
- Captioning instrumented test marked skipped until LLM model is present (Package 6).

## v0.07
- Enable Gemma captioning toggle in Settings with inline explanations.
- Show Gemma model status + install instructions, and add Warm up / Release actions.
- Add Gemma captioning instrumented tests for missing-model and warm-up paths.
- Remove unused Image Embeddings toggle.
