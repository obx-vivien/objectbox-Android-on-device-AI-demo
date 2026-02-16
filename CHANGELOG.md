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
- Add MediaPipe Image Embedder (MobileNet V3 Small, 1024-dim) with ObjectBox HNSW (COSINE) for image similarity search.
- Store image embeddings on Screenshot and expose Similar Image mode with query-by-example.
- Add instrumented tests for embedding dimension, L2 norm, and similarity ranking.

## v0.04.1
- Work around MediaPipe JNI conflict by splitting into `textOnly` and `visionOnly` flavors.
- Scope `tasks-text` and `tasks-vision` dependencies per flavor; move embedder wrappers/tests to flavor source sets.
- `:app:connectedAndroidTest` now runs both flavor test APKs and passes on device/emulator.
