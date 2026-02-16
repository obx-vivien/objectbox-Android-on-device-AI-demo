# AGENTS.md — Screenshot / Image Searcher

Project-local constraints and working procedure for the “Screenshot / Image Searcher” demo.

## 0) Precedence
- This file extends `~/AGENTS_GLOBAL.md`.
- If there is a conflict, follow the strictest rule.

## 1) Non-negotiables
- ObjectBox is mandatory:
  - All persistence uses ObjectBox.
  - All vector similarity search uses ObjectBox vector search (vector indexes).
- Offline-only inference:
  - No network calls for OCR/embeddings/labeling/LLM inference.
  - Data stays on-device.
- No AI attribution anywhere (README/docs/UI/commits/etc.).

## 2) Workflow gates for this project
1) Create/iterate `SPECS.md` ONLY. Stop for review.
2) After approval, create `TODO.md` ONLY. Stop for approval.
3) After approval, implement exactly in TODO packages/milestones.
4) If blocked or tempted to deviate (swap frameworks/models, big refactor), stop and ask.

## 3) Required tracking files
- `EXPERIMENTS.md` (append-only):
  - Log every tool/model/framework tried, kept/discarded, and why.
  - Include a final “Final Tech Stack” section listing pinned versions.
- `REPRODUCIBILITY.md`:
  - Fresh macOS build/run steps for Android + iOS.
  - Explicit tool versions, model acquisition, and pitfalls.
- `CHANGELOG.md`:
  - v0.01, v0.02, … entries aligned to TODO packages (“what works” snapshots).
- This `AGENTS.md`:
  - Update only when the agent makes mistakes/struggles; add prevention rules.

## 4) Baseline tech stack (approved default)
### Database / vector search
- ObjectBox with vector indexes for similarity search.

## 5) Iteration and testing rules
- Work in tight loops: implement a minimal slice → build/test → smoke test → log → proceed.
- Keep the project buildable at all times; fix breakages immediately before continuing.
- Each module (OCR, text embeddings, image embeddings, tagging, optional LLM) must include:
  - unit tests for persistence round-trips (ObjectBox) and key transformations
  - at least one small fixture/golden test
- Module toggles must be “real” (disable compute + storage) and have tests verifying behavior.
- Experiment comparisons change one variable at a time; keep dataset and queries constant; log metrics and notes in `EXPERIMENTS.md`.


### OCR (text extraction)
- Android: Google ML Kit Text Recognition (on-device).
- iOS: Apple Vision text recognition (`VNRecognizeTextRequest`).

### Embeddings (semantic + image similarity)
- Text embeddings: MediaPipe Text Embedder running on LiteRT.
- Image embeddings: MediaPipe Image Embedder.

### Categorization / labeling
- Android: ML Kit Image Labeling (on-device).
- iOS: Apple Vision image classification (`VNClassifyImageRequest`) or closest native equivalent.

### Optional “AI feel” module (must be toggleable)
- Use MediaPipe LLM Inference API with a small Gemma model.
- Default target: Gemma 3 270M (or the smallest current Gemma that is MediaPipe/LiteRT compatible).
- If the chosen Gemma model/runtime path becomes blocked:
  - stop and ask before switching to a different model or inference stack.

## 5) Architecture expectations
- Data model must support:
  - Screenshot/Document entity with metadata (date/source/uri/path/ingestion status)
  - OCR text stored and searchable
  - Tags/categories stored
  - Vector embeddings stored (text embedding and image embedding), with ObjectBox vector indexes
- Modular pipeline:
  - Components can be enabled/disabled via in-app toggles (OCR, text embeddings, image embeddings, labeling, optional LLM).
- Developer-friendly diagnostics:
  - indexing progress, failure reporting, and basic performance timings (optional).

## 6) Version pinning and snapshots
- Pin build tooling and dependencies (Gradle wrapper, catalogs; iOS Package.resolved; explicit Xcode/Swift notes).
- Treat each TODO package as a snapshot boundary:
  - update `CHANGELOG.md`, `REPRODUCIBILITY.md`, `EXPERIMENTS.md`.

## 7) “Ask before changing” list (examples)
Must ask before:
- replacing MediaPipe/LiteRT with ONNX/TFLite alternatives
- swapping OCR engines
- changing UI frameworks
- changing repo layout or combining Android/iOS into a single codebase
- moving away from ObjectBox for storage or vector search
- adding cloud inference or network dependencies

