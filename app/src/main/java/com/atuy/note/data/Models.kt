package com.atuy.note.data

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.ink.brush.Brush
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.BrushTip
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.geometry.ImmutableAffineTransform
import androidx.ink.geometry.Intersection.intersects
import androidx.ink.strokes.createClosedShape
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import androidx.ink.strokes.StrokeInputBatch
import androidx.ink.storage.StrokeInputBatchSerialization
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.roundToLong
import kotlin.math.sqrt

const val NOTE_MIME_TYPE = "application/vnd.atuy.note+zip"
const val NOTE_EXTENSION = ".atnote"
const val PAGE_WIDTH = 1080f
const val PAGE_HEIGHT = 1528f

private val IDENTITY_TRANSFORM = ImmutableAffineTransform(1f, 0f, 0f, 0f, 1f, 0f)

@Serializable
data class LibraryIndex(
    val version: Int = 1,
    val folders: List<FolderRecord> = emptyList(),
    val notes: List<NoteSummary> = emptyList(),
)

@Serializable
data class FolderRecord(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val parentId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

@Serializable
data class FolderManifest(
    val version: Int = 1,
    val folders: List<FolderRecord> = emptyList(),
)

@Serializable
data class NoteSummary(
    val id: String,
    val title: String,
    val folderId: String? = null,
    val updatedAt: Long,
    val revision: Long,
    val pageCount: Int,
    val thumbnailPath: String? = null,
)

@Serializable
data class NoteDocument(
    val formatVersion: Int = 4,
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val folderId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val revision: Long = 0,
    val sourcePdfEntry: String? = null,
    val pages: List<PageDocument> = listOf(PageDocument()),
)

@Serializable
data class PageDocument(
    val id: String = UUID.randomUUID().toString(),
    val width: Float = PAGE_WIDTH,
    val height: Float = PAGE_HEIGHT,
    val pdfPageIndex: Int? = null,
    val strokes: List<StoredStroke> = emptyList(),
    val images: List<PageImage> = emptyList(),
)

@Serializable
data class PageImage(
    val id: String = UUID.randomUUID().toString(),
    val entryName: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class StoredStroke(
    val id: String = UUID.randomUUID().toString(),
    val brush: BrushSpec,
    val inkEntry: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val encodedInputs: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val samples: List<InkSample> = emptyList(),
)

@Serializable
data class InkSample(
    val x: Float,
    val y: Float,
    val elapsedTimeMillis: Long = 0L,
    val strokeUnitLengthCm: Float? = null,
    val pressure: Float? = 1f,
    val tiltRadians: Float? = null,
    val orientationRadians: Float? = null,
    val toolType: StoredInputToolType = StoredInputToolType.STYLUS,
)

@Serializable
enum class StoredInputToolType { STYLUS, TOUCH, MOUSE }

@Serializable
data class CustomBrushSpec(
    val scaleX: Float = 1f,
    val scaleY: Float = 0.48f,
    val cornerRounding: Float = 0.72f,
    val slantDegrees: Float = 0f,
    val rotationDegrees: Float = 0f,
    val smoothingWindowMillis: Long = 18L,
    val upsamplingFrequencyHz: Int = 120,
)

@Serializable
data class BrushSpec(
    val kind: BrushKind = BrushKind.PRESSURE_PEN,
    val colorArgb: Int = 0xFF111111.toInt(),
    val size: Float = 5.5f,
    val epsilon: Float = 0.1f,
    val custom: CustomBrushSpec? = null,
)

@Serializable
enum class BrushKind { PRESSURE_PEN, MARKER, HIGHLIGHTER, CUSTOM }

enum class ToolMode { PEN, ERASER, LASSO, IMAGE }
enum class ScrollAxis { VERTICAL, HORIZONTAL }
enum class NavigationGestureMode { ONE_FINGER, TWO_FINGER }
enum class EraserMode { WHOLE_STROKE, PARTIAL }
enum class LassoCoverageMode(val minimumCoverage: Float) {
    INTERSECTS(0f), QUARTER(0.25f), HALF(0.5f), ALMOST_ALL(0.9f)
}

data class RuntimeStroke(
    val stored: StoredStroke,
    val stroke: Stroke,
    val samples: List<InkSample> = stroke.inputs.toInkSamples(),
)

data class ImportedPageImage(
    val image: PageImage,
    val entryFile: File,
    val bitmap: Bitmap,
)

class PageSession(
    val source: PageDocument,
    inkEntries: Map<String, ByteArray> = emptyMap(),
) {
    val id: String = source.id
    val width: Float = source.width
    val height: Float = source.height
    val pdfPageIndex: Int? = source.pdfPageIndex
    val strokes = mutableStateListOf<RuntimeStroke>()
    val images = mutableStateListOf<PageImage>()
    val selectedStrokeIds = mutableStateListOf<String>()
    var selectedImageId by mutableStateOf<String?>(null)
    var contentVersion by mutableIntStateOf(0)
        private set

    private val undoStack = ArrayDeque<InkOperation>()
    private val redoStack = ArrayDeque<InkOperation>()
    private var eraseGestureBefore: List<RuntimeStroke>? = null
    private var imageTransformBefore: PageImage? = null
    private var selectedStrokeTransformBefore: List<RuntimeStroke>? = null

    init {
        source.strokes.mapNotNullTo(strokes) { stored ->
            stored.toRuntimeOrNull(inkEntries[stored.inkEntry])
        }
        images.addAll(source.images)
    }

    fun add(runtime: RuntimeStroke, recordHistory: Boolean = true) {
        strokes += runtime
        clearStrokeSelection()
        contentVersion++
        if (recordHistory) {
            undoStack.addLast(InkOperation.AddStroke(runtime))
            redoStack.clear()
        }
    }

    fun beginEraseGesture() {
        if (eraseGestureBefore == null) eraseGestureBefore = strokes.toList()
        clearStrokeSelection()
    }

    fun eraseAt(x: Float, y: Float, radius: Float, mode: EraserMode): Boolean = when (mode) {
        EraserMode.WHOLE_STROKE -> eraseWholeStrokes(x, y, radius)
        EraserMode.PARTIAL -> eraseStrokeParts(x, y, radius)
    }

    fun endEraseGesture(): Boolean {
        val before = eraseGestureBefore ?: return false
        eraseGestureBefore = null
        val after = strokes.toList()
        if (sameStrokeSequence(before, after)) return false
        undoStack.addLast(InkOperation.ReplaceStrokes(before, after))
        redoStack.clear()
        return true
    }

    fun selectWithLasso(lassoInputs: StrokeInputBatch, mode: LassoCoverageMode): Int {
        if (lassoInputs.size < 3) {
            clearStrokeSelection()
            return 0
        }
        val lassoShape = runCatching { lassoInputs.createClosedShape() }.getOrNull()
        if (lassoShape == null) {
            clearStrokeSelection()
            return 0
        }
        val selected = strokes.filter { runtime ->
            runCatching {
                if (mode == LassoCoverageMode.INTERSECTS) {
                    runtime.stroke.shape.intersects(lassoShape, IDENTITY_TRANSFORM, IDENTITY_TRANSFORM)
                } else {
                    runtime.stroke.shape.computeCoverage(lassoShape, IDENTITY_TRANSFORM) >= mode.minimumCoverage
                }
            }.getOrDefault(false)
        }.map { it.stored.id }
        selectedStrokeIds.clear()
        selectedStrokeIds.addAll(selected)
        selectedImageId = null
        contentVersion++
        return selected.size
    }

    fun clearStrokeSelection() {
        if (selectedStrokeIds.isEmpty()) return
        selectedStrokeIds.clear()
        contentVersion++
    }

    fun selectedStrokeBounds(): FloatArray? {
        val selected = strokes.filter { it.stored.id in selectedStrokeIds }
        if (selected.isEmpty()) return null
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        selected.forEach { runtime ->
            val box = runtime.stroke.shape.computeBoundingBox() ?: return@forEach
            minX = min(minX, box.xMin)
            minY = min(minY, box.yMin)
            maxX = max(maxX, box.xMax)
            maxY = max(maxY, box.yMax)
        }
        return if (minX.isFinite()) floatArrayOf(minX, minY, maxX, maxY) else null
    }

    fun isPointInsideStrokeSelection(x: Float, y: Float, padding: Float = 18f): Boolean {
        val bounds = selectedStrokeBounds() ?: return false
        return x >= bounds[0] - padding && x <= bounds[2] + padding &&
            y >= bounds[1] - padding && y <= bounds[3] + padding
    }

    fun beginSelectedStrokeTransform(): Boolean {
        if (selectedStrokeIds.isEmpty()) return false
        selectedStrokeTransformBefore = strokes.toList()
        return true
    }

    fun transformSelectedStrokes(dx: Float, dy: Float) {
        val before = selectedStrokeTransformBefore ?: return
        val selectedIds = selectedStrokeIds.toSet()
        val transformed = before.map { runtime ->
            if (runtime.stored.id in selectedIds) runtime.transformed(dx = dx, dy = dy) else runtime
        }
        replaceAllStrokes(transformed, preserveSelection = true)
    }

    fun endSelectedStrokeTransform(): Boolean {
        val before = selectedStrokeTransformBefore ?: return false
        selectedStrokeTransformBefore = null
        val after = strokes.toList()
        if (sameStrokeSequence(before, after)) return false
        undoStack.addLast(InkOperation.ReplaceStrokes(before, after))
        redoStack.clear()
        return true
    }

    fun cancelSelectedStrokeTransform() {
        val before = selectedStrokeTransformBefore ?: return
        selectedStrokeTransformBefore = null
        replaceAllStrokes(before, preserveSelection = true)
    }

    fun scaleSelectedStrokes(factor: Float): Boolean {
        if (selectedStrokeIds.isEmpty()) return false
        val bounds = selectedStrokeBounds() ?: return false
        val centerX = (bounds[0] + bounds[2]) / 2f
        val centerY = (bounds[1] + bounds[3]) / 2f
        val selectedIds = selectedStrokeIds.toSet()
        val before = strokes.toList()
        val after = before.map { runtime ->
            if (runtime.stored.id in selectedIds) {
                runtime.transformed(scale = factor.coerceIn(0.2f, 5f), centerX = centerX, centerY = centerY)
            } else runtime
        }
        if (sameStrokeSequence(before, after)) return false
        replaceAllStrokes(after, preserveSelection = true)
        undoStack.addLast(InkOperation.ReplaceStrokes(before, after))
        redoStack.clear()
        return true
    }

    fun restyleSelectedStrokes(
        colorArgb: Int? = null,
        size: Float? = null,
        kind: BrushKind? = null,
        custom: CustomBrushSpec? = null,
    ): Boolean {
        if (selectedStrokeIds.isEmpty()) return false
        val selectedIds = selectedStrokeIds.toSet()
        val before = strokes.toList()
        val after = before.map { runtime ->
            if (runtime.stored.id !in selectedIds) return@map runtime
            val previous = runtime.stored.brush
            val nextKind = kind ?: previous.kind
            val next = previous.copy(
                kind = nextKind,
                colorArgb = colorArgb ?: previous.colorArgb,
                size = size?.coerceIn(0.5f, 96f) ?: previous.size,
                custom = when (nextKind) {
                    BrushKind.CUSTOM -> custom ?: previous.custom ?: CustomBrushSpec()
                    else -> null
                },
            )
            runtime.withBrush(next)
        }
        if (sameStrokeSequence(before, after) && before.map { it.stored.brush } == after.map { it.stored.brush }) return false
        replaceAllStrokes(after, preserveSelection = true)
        undoStack.addLast(InkOperation.ReplaceStrokes(before, after))
        redoStack.clear()
        return true
    }

    fun deleteSelectedStrokes(): Boolean {
        if (selectedStrokeIds.isEmpty()) return false
        val ids = selectedStrokeIds.toSet()
        val before = strokes.toList()
        val after = before.filterNot { it.stored.id in ids }
        if (before.size == after.size) return false
        replaceAllStrokes(after)
        undoStack.addLast(InkOperation.ReplaceStrokes(before, after))
        redoStack.clear()
        return true
    }

    fun addImage(image: PageImage) {
        images += image
        contentVersion++
        selectedImageId = image.id
        clearStrokeSelection()
        undoStack.addLast(InkOperation.AddImage(image))
        redoStack.clear()
    }

    fun deleteSelectedImage(): PageImage? {
        val id = selectedImageId ?: return null
        val index = images.indexOfFirst { it.id == id }
        if (index < 0) return null
        val removed = images.removeAt(index)
        contentVersion++
        selectedImageId = null
        undoStack.addLast(InkOperation.RemoveImage(index, removed))
        redoStack.clear()
        return removed
    }

    fun scaleSelectedImage(factor: Float): Boolean {
        val id = selectedImageId ?: return false
        val index = images.indexOfFirst { it.id == id }
        if (index < 0) return false
        val before = images[index]
        val newWidth = (before.width * factor).coerceIn(48f, width)
        val newHeight = (before.height * factor).coerceIn(48f, height)
        val centerX = before.x + before.width / 2f
        val centerY = before.y + before.height / 2f
        val after = before.copy(
            x = (centerX - newWidth / 2f).coerceIn(0f, max(0f, width - newWidth)),
            y = (centerY - newHeight / 2f).coerceIn(0f, max(0f, height - newHeight)),
            width = newWidth,
            height = newHeight,
        )
        if (before == after) return false
        images[index] = after
        contentVersion++
        undoStack.addLast(InkOperation.TransformImage(before, after))
        redoStack.clear()
        return true
    }

    fun selectImage(id: String?) {
        selectedImageId = id?.takeIf { candidate -> images.any { it.id == candidate } }
        if (selectedImageId != null) clearStrokeSelection()
    }

    fun beginImageTransform(id: String): Boolean {
        val image = images.firstOrNull { it.id == id } ?: return false
        selectedImageId = id
        imageTransformBefore = image
        return true
    }

    fun moveImage(id: String, x: Float, y: Float): Boolean {
        val index = images.indexOfFirst { it.id == id }
        if (index < 0) return false
        val current = images[index]
        val next = current.copy(
            x = x.coerceIn(0f, max(0f, width - current.width)),
            y = y.coerceIn(0f, max(0f, height - current.height)),
        )
        if (next == current) return false
        images[index] = next
        contentVersion++
        return true
    }

    fun endImageTransform(): Boolean {
        val before = imageTransformBefore ?: return false
        imageTransformBefore = null
        val after = images.firstOrNull { it.id == before.id } ?: return false
        if (before == after) return false
        undoStack.addLast(InkOperation.TransformImage(before, after))
        redoStack.clear()
        return true
    }

    fun cancelImageTransform() {
        val before = imageTransformBefore ?: return
        imageTransformBefore = null
        replaceImage(before)
    }

    fun undo(): Boolean {
        val op = undoStack.removeLastOrNull() ?: return false
        when (op) {
            is InkOperation.AddStroke -> strokes.remove(op.stroke)
            is InkOperation.ReplaceStrokes -> replaceAllStrokes(op.before)
            is InkOperation.AddImage -> {
                images.removeAll { it.id == op.image.id }
                if (selectedImageId == op.image.id) selectedImageId = null
            }
            is InkOperation.RemoveImage -> images.add(op.index.coerceIn(0, images.size), op.image)
            is InkOperation.TransformImage -> replaceImage(op.before)
        }
        contentVersion++
        redoStack.addLast(op)
        return true
    }

    fun redo(): Boolean {
        val op = redoStack.removeLastOrNull() ?: return false
        when (op) {
            is InkOperation.AddStroke -> strokes.add(op.stroke)
            is InkOperation.ReplaceStrokes -> replaceAllStrokes(op.after)
            is InkOperation.AddImage -> images.add(op.image)
            is InkOperation.RemoveImage -> {
                images.removeAll { it.id == op.image.id }
                if (selectedImageId == op.image.id) selectedImageId = null
            }
            is InkOperation.TransformImage -> replaceImage(op.after)
        }
        contentVersion++
        undoStack.addLast(op)
        return true
    }

    fun toDocument(): PageDocument = PageDocument(
        id = id,
        width = width,
        height = height,
        pdfPageIndex = pdfPageIndex,
        strokes = strokes.map { it.stored },
        images = images.toList(),
    )

    fun encodedInkEntries(): Map<String, ByteArray> = strokes.associate { runtime ->
        runtime.stored.inkEntry to StrokeInputBatchSerialization.encode(runtime.stroke.inputs)
    }

    private fun eraseWholeStrokes(x: Float, y: Float, radius: Float): Boolean {
        val before = strokes.size
        strokes.removeAll { runtime -> polylineIntersectsCircle(runtime.samples, x, y, radius) }
        val changed = strokes.size != before
        if (changed) contentVersion++
        return changed
    }

    private fun eraseStrokeParts(x: Float, y: Float, radius: Float): Boolean {
        var changed = false
        val replacement = mutableListOf<RuntimeStroke>()
        for (runtime in strokes) {
            val split = splitSamplesOutsideCircle(runtime.samples, x, y, radius)
            if (split == null) {
                replacement += runtime
                continue
            }
            changed = true
            split.mapNotNullTo(replacement) { samples -> runtime.runtimeWithSamples(samples) }
        }
        if (changed) replaceAllStrokes(replacement)
        return changed
    }

    private fun replaceImage(image: PageImage) {
        val index = images.indexOfFirst { it.id == image.id }
        if (index >= 0) {
            images[index] = image
            contentVersion++
        }
    }

    private fun replaceAllStrokes(values: List<RuntimeStroke>, preserveSelection: Boolean = false) {
        strokes.clear()
        strokes.addAll(values)
        if (!preserveSelection) selectedStrokeIds.clear()
        else selectedStrokeIds.retainAll(values.mapTo(mutableSetOf()) { it.stored.id })
        contentVersion++
    }
}

private sealed interface InkOperation {
    data class AddStroke(val stroke: RuntimeStroke) : InkOperation
    data class ReplaceStrokes(val before: List<RuntimeStroke>, val after: List<RuntimeStroke>) : InkOperation
    data class AddImage(val image: PageImage) : InkOperation
    data class RemoveImage(val index: Int, val image: PageImage) : InkOperation
    data class TransformImage(val before: PageImage, val after: PageImage) : InkOperation
}

class NoteSession(
    document: NoteDocument,
    val archiveFile: File,
    val sourcePdfFile: File?,
    val imageFiles: MutableMap<String, File> = mutableMapOf(),
    val imageBitmaps: MutableMap<String, Bitmap> = mutableMapOf(),
    inkEntries: Map<String, ByteArray> = emptyMap(),
) {
    val id: String = document.id
    val createdAt: Long = document.createdAt
    var title by mutableStateOf(document.title)
    var folderId by mutableStateOf(document.folderId)
    var updatedAt by mutableStateOf(document.updatedAt)
    var revision by mutableStateOf(document.revision)
    var dirty by mutableStateOf(false)
    var activePageIndex by mutableIntStateOf(0)
    val pages = mutableStateListOf<PageSession>().apply {
        addAll(document.pages.map { PageSession(it, inkEntries) })
    }

    fun toDocument(nextRevision: Boolean): NoteDocument {
        val now = System.currentTimeMillis()
        val revisionValue = if (nextRevision) revision + 1 else revision
        return NoteDocument(
            formatVersion = 4,
            id = id,
            title = title,
            folderId = folderId,
            createdAt = createdAt,
            updatedAt = if (nextRevision) now else updatedAt,
            revision = revisionValue,
            sourcePdfEntry = sourcePdfFile?.let { "background/source.pdf" },
            pages = pages.map { it.toDocument() },
        )
    }

    fun encodedInkEntries(): Map<String, ByteArray> = buildMap {
        pages.forEach { putAll(it.encodedInkEntries()) }
    }
}

fun BrushSpec.toBrush(): Brush {
    val family = when (kind) {
        BrushKind.PRESSURE_PEN -> StockBrushes.pressurePen()
        BrushKind.MARKER -> StockBrushes.marker()
        BrushKind.HIGHLIGHTER -> StockBrushes.highlighter()
        BrushKind.CUSTOM -> buildCustomBrushFamily(custom ?: CustomBrushSpec())
    }
    val resolvedColor = if (kind == BrushKind.HIGHLIGHTER && (colorArgb ushr 24) == 0xFF) {
        (0x66 shl 24) or (colorArgb and 0x00FFFFFF)
    } else colorArgb
    return Brush.createWithColorIntArgb(family, resolvedColor, size, epsilon)
}

private fun buildCustomBrushFamily(spec: CustomBrushSpec): BrushFamily {
    val base = StockBrushes.pressurePen()
    val baseCoat = base.coats.first()
    val customTip = baseCoat.tip.copy(
        scaleX = spec.scaleX.coerceIn(0.08f, 4f),
        scaleY = spec.scaleY.coerceIn(0.08f, 4f),
        cornerRounding = spec.cornerRounding.coerceIn(0f, 1f),
        slantDegrees = spec.slantDegrees.coerceIn(-90f, 90f),
        rotationDegrees = spec.rotationDegrees,
    )
    val customCoat = baseCoat.copy(tip = customTip)
    return base.copy(
        coats = listOf(customCoat),
        inputModel = BrushFamily.InputModel.SlidingWindowModel(
            spec.smoothingWindowMillis.coerceIn(0L, 100L),
            spec.upsamplingFrequencyHz.coerceIn(30, 360),
        ),
        developerComment = "Note custom brush",
    )
}

fun Stroke.toRuntimeStroke(spec: BrushSpec): RuntimeStroke {
    val id = UUID.randomUUID().toString()
    val stored = StoredStroke(id = id, brush = spec, inkEntry = "ink/strokes/$id.bin")
    return RuntimeStroke(stored = stored, stroke = this)
}

fun StoredStroke.toRuntimeOrNull(officialBytes: ByteArray?): RuntimeStroke? = runCatching {
    val decoded = when {
        officialBytes != null -> StrokeInputBatchSerialization.decode(officialBytes)
        !encodedInputs.isNullOrBlank() -> {
            val legacyBytes = Base64.decode(encodedInputs, Base64.NO_WRAP)
            StrokeInputBatchSerialization.decode(legacyBytes)
        }
        else -> error("Missing Ink Storage entry for stroke $id")
    }
    val normalizedEntry = inkEntry.ifBlank { "ink/strokes/$id.bin" }
    val normalized = copy(
        inkEntry = normalizedEntry,
        encodedInputs = null,
        samples = emptyList(),
    )
    val runtimeSamples = if (samples.isNotEmpty()) samples else decoded.toInkSamples()
    RuntimeStroke(normalized, Stroke(brush.toBrush(), decoded), runtimeSamples)
}.getOrNull()

private fun RuntimeStroke.runtimeWithSamples(rawSamples: List<InkSample>): RuntimeStroke? = runCatching {
    val samples = sanitizeSamples(rawSamples)
    check(samples.size >= 2) { "Not enough stroke samples" }
    val batch = MutableStrokeInputBatch()
    samples.forEach { sample ->
        batch.add(
            type = sample.toolType.toInputToolType(),
            x = sample.x,
            y = sample.y,
            elapsedTimeMillis = sample.elapsedTimeMillis,
            strokeUnitLengthCm = sample.strokeUnitLengthCm ?: StrokeInput.NO_STROKE_UNIT_LENGTH,
            pressure = sample.pressure ?: StrokeInput.NO_PRESSURE,
            tiltRadians = sample.tiltRadians ?: StrokeInput.NO_TILT,
            orientationRadians = sample.orientationRadians ?: StrokeInput.NO_ORIENTATION,
        )
    }
    batch.setNoiseSeed(stroke.inputs.getNoiseSeed())
    val id = UUID.randomUUID().toString()
    val metadata = StoredStroke(id = id, brush = stored.brush, inkEntry = "ink/strokes/$id.bin")
    RuntimeStroke(metadata, Stroke(stored.brush.toBrush(), batch), samples)
}.getOrNull()

private fun RuntimeStroke.withBrush(spec: BrushSpec): RuntimeStroke {
    val metadata = stored.copy(brush = spec)
    return RuntimeStroke(metadata, Stroke(spec.toBrush(), stroke.inputs), samples)
}

private fun RuntimeStroke.transformed(
    dx: Float = 0f,
    dy: Float = 0f,
    scale: Float = 1f,
    centerX: Float = 0f,
    centerY: Float = 0f,
): RuntimeStroke {
    val batch = MutableStrokeInputBatch()
    for (index in 0 until stroke.inputs.size) {
        val input = stroke.inputs[index]
        val transformedX = centerX + (input.x - centerX) * scale + dx
        val transformedY = centerY + (input.y - centerY) * scale + dy
        batch.add(
            type = input.toolType,
            x = transformedX,
            y = transformedY,
            elapsedTimeMillis = input.elapsedTimeMillis,
            strokeUnitLengthCm = input.strokeUnitLengthCm,
            pressure = input.pressure,
            tiltRadians = input.tiltRadians,
            orientationRadians = input.orientationRadians,
        )
    }
    batch.setNoiseSeed(stroke.inputs.getNoiseSeed())
    val nextStroke = Stroke(stored.brush.toBrush(), batch)
    return RuntimeStroke(stored, nextStroke)
}

fun StrokeInputBatch.toInkSamples(): List<InkSample> = List(size) { index ->
    val input = this[index]
    InkSample(
        x = input.x,
        y = input.y,
        elapsedTimeMillis = input.elapsedTimeMillis,
        strokeUnitLengthCm = input.strokeUnitLengthCm.takeUnless { it == StrokeInput.NO_STROKE_UNIT_LENGTH },
        pressure = input.pressure.takeUnless { it == StrokeInput.NO_PRESSURE },
        tiltRadians = input.tiltRadians.takeUnless { it == StrokeInput.NO_TILT },
        orientationRadians = input.orientationRadians.takeUnless { it == StrokeInput.NO_ORIENTATION },
        toolType = input.toolType.toStoredInputToolType(),
    )
}

private fun InputToolType.toStoredInputToolType(): StoredInputToolType = when (this) {
    InputToolType.MOUSE -> StoredInputToolType.MOUSE
    InputToolType.TOUCH -> StoredInputToolType.TOUCH
    else -> StoredInputToolType.STYLUS
}

private fun StoredInputToolType.toInputToolType(): InputToolType = when (this) {
    StoredInputToolType.MOUSE -> InputToolType.MOUSE
    StoredInputToolType.TOUCH -> InputToolType.TOUCH
    StoredInputToolType.STYLUS -> InputToolType.STYLUS
}

private fun sanitizeSamples(samples: List<InkSample>): List<InkSample> {
    if (samples.isEmpty()) return emptyList()
    val output = ArrayList<InkSample>(samples.size)
    for (sample in samples) {
        val previous = output.lastOrNull()
        if (previous == null || abs(previous.x - sample.x) > 0.001f || abs(previous.y - sample.y) > 0.001f) {
            output += sample
        }
    }
    return output
}

private fun sameStrokeSequence(a: List<RuntimeStroke>, b: List<RuntimeStroke>): Boolean =
    a.size == b.size && a.indices.all { index ->
        a[index].stored.id == b[index].stored.id && a[index].samples == b[index].samples
    }

private fun polylineIntersectsCircle(samples: List<InkSample>, cx: Float, cy: Float, radius: Float): Boolean {
    if (samples.isEmpty()) return false
    if (samples.size == 1) return squaredDistance(samples[0].x, samples[0].y, cx, cy) <= radius * radius
    return samples.zipWithNext().any { (a, b) ->
        squaredDistancePointToSegment(cx, cy, a.x, a.y, b.x, b.y) <= radius * radius
    }
}

internal fun splitSamplesOutsideCircle(
    samples: List<InkSample>,
    cx: Float,
    cy: Float,
    radius: Float,
): List<List<InkSample>>? {
    if (!polylineIntersectsCircle(samples, cx, cy, radius)) return null
    if (samples.size < 2) return emptyList()

    val runs = mutableListOf<List<InkSample>>()
    var current: MutableList<InkSample>? = null

    fun finishCurrent() {
        val cleaned = current?.let(::sanitizeSamples).orEmpty()
        if (cleaned.size >= 2) runs += cleaned
        current = null
    }

    for ((a, b) in samples.zipWithNext()) {
        val cuts = buildList {
            add(0f)
            addAll(segmentCircleIntersections(a, b, cx, cy, radius))
            add(1f)
        }.sorted().fold(mutableListOf<Float>()) { acc, value ->
            if (acc.isEmpty() || abs(acc.last() - value) > 0.0001f) acc += value.coerceIn(0f, 1f)
            acc
        }

        for (index in 0 until cuts.lastIndex) {
            val t0 = cuts[index]
            val t1 = cuts[index + 1]
            if (t1 - t0 <= 0.0001f) continue
            val mid = interpolate(a, b, (t0 + t1) / 2f)
            val outside = squaredDistance(mid.x, mid.y, cx, cy) >= radius * radius
            if (outside) {
                val p0 = interpolate(a, b, t0)
                val p1 = interpolate(a, b, t1)
                val target = current ?: mutableListOf<InkSample>().also { current = it }
                if (target.isEmpty() || squaredDistance(target.last().x, target.last().y, p0.x, p0.y) > 0.0001f) {
                    target += p0
                }
                if (squaredDistance(target.last().x, target.last().y, p1.x, p1.y) > 0.0001f) {
                    target += p1
                }
            } else {
                finishCurrent()
            }
        }
    }
    finishCurrent()
    return runs
}

private fun segmentCircleIntersections(
    a: InkSample,
    b: InkSample,
    cx: Float,
    cy: Float,
    radius: Float,
): List<Float> {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val fx = a.x - cx
    val fy = a.y - cy
    val aa = dx * dx + dy * dy
    if (aa <= 0.000001f) return emptyList()
    val bb = 2f * (fx * dx + fy * dy)
    val cc = fx * fx + fy * fy - radius * radius
    val discriminant = bb * bb - 4f * aa * cc
    if (discriminant < 0f) return emptyList()
    val root = sqrt(discriminant)
    val t1 = (-bb - root) / (2f * aa)
    val t2 = (-bb + root) / (2f * aa)
    return listOf(t1, t2).filter { it > 0f && it < 1f }
}

private fun interpolate(a: InkSample, b: InkSample, t: Float) = InkSample(
    x = a.x + (b.x - a.x) * t,
    y = a.y + (b.y - a.y) * t,
    elapsedTimeMillis = a.elapsedTimeMillis + ((b.elapsedTimeMillis - a.elapsedTimeMillis) * t).roundToLong(),
    strokeUnitLengthCm = a.strokeUnitLengthCm ?: b.strokeUnitLengthCm,
    pressure = interpolateOptional(a.pressure, b.pressure, t),
    tiltRadians = interpolateOptional(a.tiltRadians, b.tiltRadians, t),
    orientationRadians = interpolateOrientation(a.orientationRadians, b.orientationRadians, t),
    toolType = a.toolType,
)

private fun interpolateOptional(a: Float?, b: Float?, t: Float): Float? =
    if (a != null && b != null) a + (b - a) * t else a ?: b

private fun interpolateOrientation(a: Float?, b: Float?, t: Float): Float? {
    if (a == null || b == null) return a ?: b
    val turn = (2.0 * PI).toFloat()
    var delta = (b - a) % turn
    val halfTurn = PI.toFloat()
    if (delta > halfTurn) delta -= turn
    if (delta < -halfTurn) delta += turn
    return ((a + delta * t) % turn + turn) % turn
}

private fun squaredDistancePointToSegment(
    px: Float,
    py: Float,
    ax: Float,
    ay: Float,
    bx: Float,
    by: Float,
): Float {
    val dx = bx - ax
    val dy = by - ay
    val denominator = dx * dx + dy * dy
    if (denominator <= 0.000001f) return squaredDistance(px, py, ax, ay)
    val t = (((px - ax) * dx + (py - ay) * dy) / denominator).coerceIn(0f, 1f)
    return squaredDistance(px, py, ax + t * dx, ay + t * dy)
}

private fun squaredDistance(ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = ax - bx
    val dy = ay - by
    return dx * dx + dy * dy
}
