# Architecture

## Layers

- `ui`: Material 3 library/editor UI and adaptive phone/tablet layout.
- `ink`: Android View bridge around `InProgressStrokesView` and `CanvasStrokeRenderer`.
- `data`: serializable document model, runtime sessions, archive repository and thumbnails.
- `sync`: Google Drive REST synchronization in `appDataFolder`.

## Rendering

`InkViewport` is the single owner of page fit, zoom, and pan. `DryInkView` applies its matrix once to the canvas and draws the optional PDF bitmap, page images, and completed Ink strokes in that same world coordinate system. The same complete transform is also supplied to `CanvasStrokeRenderer` for screen-space rendering quality; that argument describes the canvas transform but does not apply it. Stylus input remains as the raw view-space `MotionEvent`; the inverse viewport matrix is supplied to `InProgressStrokesView.startStroke`, as required by AndroidX Ink, so wet and dry coordinates do not diverge after zooming.

Finger gestures are consumed by `InkPageView` for pinch zoom and viewport pan, or forwarded as page-list navigation deltas at 1x. Completed strokes are added to the page model, the dry layer is invalidated, and their wet copies are removed within the same UI run loop. `PageSession.contentVersion` is observed directly by the `AndroidView` update block so the first stroke in a new note also schedules a dry redraw without requiring a tool change.

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
