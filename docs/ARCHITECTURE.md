# Architecture

## Layers

- `ui`: Material 3 library/editor UI and adaptive phone/tablet layout.
- `ink`: Android View bridge around `InProgressStrokesView` and `CanvasStrokeRenderer`.
- `data`: serializable document model, runtime sessions, archive repository and thumbnails.
- `sync`: Google Drive REST synchronization in `appDataFolder`.

## Rendering

`DocumentPages` owns a single 35–500% zoom and the page-list/cross-axis scroll state. Pinch gestures capture the page and fractional point under the fingers, request the matching lazy-list offset during remeasure, and apply the cross-axis correction after placement. An anchor is retained across input events until layout catches up. Fixed page gaps and the centered margins of differently sized PDF pages are included in the calculation.

The active UI binds `InkPageView` with external navigation, so it cannot apply a second page-local zoom. `InkViewport` supplies the fit transform for PDF, images, completed ink, and input inversion. The legacy UI may still use its local camera. `CanvasStrokeRenderer` receives the same transform already applied to the Canvas; its transform argument describes rendering scale and does not apply that transform a second time.

Finger navigation waits for touch slop, supports the selected one/two-finger mode, and adds inertia to single-finger pans. Gestures containing a stylus do not move the camera. Navigation also waits for the completed-stroke handoff to finish. Undo/redo availability is observable, and cancellations close pending input/transform gestures.

Visible-page tracking changes the active editing page without requesting a scroll. Explicit page selection and page operations increment a separate navigation generation. Saveable UI state is scoped by note ID and removed when a tab closes, preserving zoom and scroll positions when switching tabs.

PDF rendering is debounced and uses resolution buckets with dimension and pixel budgets. Previews and library thumbnails use the Ink renderer, including pressure, custom tips, highlighter opacity, and single-point strokes.

## Persistence

Every save writes a temporary ZIP and atomically replaces the target `.atnote`. A note revision is incremented only after a successful save. Embedded source PDFs are streamed into the ZIP instead of being represented as page screenshots.

## Synchronization

Drive files use `appProperties` for `noteId`, revision and SHA-256. The hidden app-data space prevents user edits outside the application. Remote and local folder records merge by stable folder ID and latest `updatedAt`. Equal-revision hash mismatches create a conflict copy rather than discarding either side.

## `.atnote` format v3

Version 3 stores each completed stroke as the gzip-compressed Protocol Buffers payload produced by `androidx.ink.storage.StrokeInputBatchSerialization`. The JSON manifest contains only stroke IDs, brush metadata, and an `ink/strokes/<id>.bin` entry reference. Version 2 Base64-in-JSON notes remain readable and migrate on the next save.

Lasso input uses the AndroidX Ink dashed-line stock brush. Its input batch is closed with `MeshCreation.createClosedShape`, then intersected with each stroke mesh. Selected strokes can be moved, scaled, deleted, undone, and redone; transformations rebuild editable Ink input batches rather than rasterizing them.


## Ink editing and brush model (v4)

- Selection bounds come from each rendered `PartitionedMesh.computeBoundingBox()`, so brush width and tip geometry are included.
- Lasso selection supports intersection and 25%, 50%, or 90% mesh-coverage thresholds using `PartitionedMesh.computeCoverage()`.
- `BrushKind.HIGHLIGHTER` uses AndroidX Ink `StockBrushes.highlighter()` with translucent ARGB colors.
- `BrushKind.CUSTOM` stores a parameterized tip shape and smoothing model in `BrushSpec.CustomBrushSpec`; it inherits pressure behavior from the stock pressure pen.
- Stroke transformations and partial erasure preserve tool type, elapsed time, physical stroke-unit length, pressure, tilt, orientation, and noise seed.
- Version 3 notes remain readable because all new brush and input fields have backward-compatible defaults; the next save writes version 4.
