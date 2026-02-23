# ObjectBox Android On-Device AI Demo

A minimal Android demo that indexes screenshots and images **fully on-device** and lets you search them by OCR text, semantic meaning, and auto-generated tags. All inference runs locally; nothing leaves the device.

## What It Does
- Import images and screenshots, then index them on-device.
- Extract OCR text for keyword search.
- Generate semantic text embeddings for meaning-based search.
- Auto-label images with categories/tags.
- (Optional) Create short captions with Gemma to enrich search.

## ObjectBox Involvement
ObjectBox is the on-device database and vector search engine:
- Stores screenshot metadata, OCR text, labels, captions, and embeddings.
- Runs fast **HNSW vector search** on embeddings for semantic retrieval.
- Drives search results and filters via local queries only.

## Tech Stack (Android)
- **UI:** Jetpack Compose
- **Database & Vector Search:** ObjectBox (HNSW)
- **OCR:** ML Kit Text Recognition (on-device)
- **Text Embeddings:** MediaPipe Text Embedder (LiteRT)
- **Image Labeling:** ML Kit Image Labeling (on-device)
- **Captioning (optional):** MediaPipe LLM Inference with Gemma (local model)

## On-Device First
- No network calls for OCR, embeddings, labeling, or captioning.
- Models are stored locally (assets or `/data/local/tmp/llm/`).
- Search and ranking run entirely on-device.

## Run (quick)
```bash
cd android-kotlin
./gradlew :app:assembleDebug
```

## Model Notes
- Text embedder model asset required at:
  `android-kotlin/app/src/main/assets/models/text_embedder.tflite`
- Gemma model (optional) should be pushed to:
  `/data/local/tmp/llm/gemma-3n-E2B-it-int4.litertlm`

See `REPRODUCIBILITY.md` for full setup and device instructions.
