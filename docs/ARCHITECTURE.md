# Architecture

## Layers

- `ui`: Material 3 library/editor UI and adaptive phone/tablet layout.
- `ink`: Android View bridge around `InProgressStrokesView` and `CanvasStrokeRenderer`.
- `data`: serializable document model, runtime sessions, archive repository and thumbnails.
- `sync`: Google Drive REST synchronization in `appDataFolder`.

## Rendering

Finger gestures are left to Compose's page scrollers. Stylus and eraser events are consumed by `InkPageView`. Wet ink is rendered by AndroidX Ink's `InProgressStrokesView`; completed strokes are handed to `DryInkView`, which redraws them with `CanvasStrokeRenderer` over the optional PDF bitmap.

## Persistence

Every save writes a temporary ZIP and atomically replaces the target `.atnote`. A note revision is incremented only after a successful save. Embedded source PDFs are streamed into the ZIP instead of being represented as page screenshots.

## Synchronization

Drive files use `appProperties` for `noteId`, revision and SHA-256. The hidden app-data space prevents user edits outside the application. Remote and local folder records merge by stable folder ID and latest `updatedAt`. Equal-revision hash mismatches create a conflict copy rather than discarding either side.

## `.atnote` format v3

Version 3 stores each completed stroke as the gzip-compressed Protocol Buffers payload produced by `androidx.ink.storage.StrokeInputBatchSerialization`. The JSON manifest contains only stroke IDs, brush metadata, and an `ink/strokes/<id>.bin` entry reference. Version 2 Base64-in-JSON notes remain readable and migrate on the next save.

Lasso input uses the AndroidX Ink dashed-line stock brush. Its input batch is closed with `MeshCreation.createClosedShape`, then intersected with each stroke mesh. Selected strokes can be moved, scaled, deleted, undone, and redone; transformations rebuild editable Ink input batches rather than rasterizing them.
