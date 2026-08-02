package com.atuy.note.data

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.storage.StrokeInputBatchSerialization
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import kotlinx.serialization.Serializable
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

const val NOTE_MIME_TYPE = "application/vnd.atuy.note+zip"
const val NOTE_EXTENSION = ".atnote"
const val PAGE_WIDTH = 1080f
const val PAGE_HEIGHT = 1528f

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
    val formatVersion: Int = 2,
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

@Serializable
data class StoredStroke(
    val id: String = UUID.randomUUID().toString(),
    val brush: BrushSpec,
    val encodedInputs: String,
    val samples: List<InkSample>,
)

@Serializable
data class InkSample(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f,
)

@Serializable
data class BrushSpec(
    val kind: BrushKind = BrushKind.PRESSURE_PEN,
    val colorArgb: Int = 0xFF111111.toInt(),
    val size: Float = 5.5f,
    val epsilon: Float = 0.1f,
)

@Serializable
enum class BrushKind { PRESSURE_PEN, MARKER }

enum class ToolMode { PEN, ERASER, IMAGE }
enum class ScrollAxis { VERTICAL, HORIZONTAL }
enum class NavigationGestureMode { ONE_FINGER, TWO_FINGER }
enum class EraserMode { WHOLE_STROKE, PARTIAL }

data class RuntimeStroke(val stored: StoredStroke, val stroke: Stroke)

data class ImportedPageImage(
    val image: PageImage,
    val entryFile: File,
    val bitmap: Bitmap,
)

class PageSession(val source: PageDocument) {
    val id: String = source.id
    val width: Float = source.width
    val height: Float = source.height
    val pdfPageIndex: Int? = source.pdfPageIndex
    val strokes = mutableStateListOf<RuntimeStroke>()
    val images = mutableStateListOf<PageImage>()
    var selectedImageId by mutableStateOf<String?>(null)
    var contentVersion by mutableIntStateOf(0)
        private set
    private val undoStack = ArrayDeque<InkOperation>()
    private val redoStack = ArrayDeque<InkOperation>()
    private var eraseGestureBefore: List<RuntimeStroke>? = null
    private var imageTransformBefore: PageImage? = null

    init {
        source.strokes.mapNotNullTo(strokes) { it.toRuntimeOrNull() }
        images.addAll(source.images)
    }

    fun add(runtime: RuntimeStroke, recordHistory: Boolean = true) {
        strokes += runtime
        contentVersion++
        if (recordHistory) {
            undoStack.addLast(InkOperation.AddStroke(runtime))
            redoStack.clear()
        }
    }

    fun beginEraseGesture() {
        if (eraseGestureBefore == null) eraseGestureBefore = strokes.toList()
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

    fun addImage(image: PageImage) {
        images += image
        contentVersion++
        selectedImageId = image.id
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

    private fun eraseWholeStrokes(x: Float, y: Float, radius: Float): Boolean {
        val before = strokes.size
        strokes.removeAll { runtime -> polylineIntersectsCircle(runtime.stored.samples, x, y, radius) }
        val changed = strokes.size != before
        if (changed) contentVersion++
        return changed
    }

    private fun eraseStrokeParts(x: Float, y: Float, radius: Float): Boolean {
        var changed = false
        val replacement = mutableListOf<RuntimeStroke>()
        for (runtime in strokes) {
            val split = splitSamplesOutsideCircle(runtime.stored.samples, x, y, radius)
            if (split == null) {
                replacement += runtime
                continue
            }
            changed = true
            split.mapNotNullTo(replacement) { samples -> runtime.stored.runtimeWithSamples(samples) }
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

    private fun replaceAllStrokes(values: List<RuntimeStroke>) {
        strokes.clear()
        strokes.addAll(values)
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
        addAll(document.pages.map(::PageSession))
    }

    fun toDocument(nextRevision: Boolean): NoteDocument {
        val now = System.currentTimeMillis()
        val revisionValue = if (nextRevision) revision + 1 else revision
        return NoteDocument(
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
}

fun BrushSpec.toBrush(): Brush {
    val family = when (kind) {
        BrushKind.PRESSURE_PEN -> StockBrushes.pressurePen()
        BrushKind.MARKER -> StockBrushes.marker()
    }
    return Brush.createWithColorIntArgb(family, colorArgb, size, epsilon)
}

fun Stroke.toStoredStroke(spec: BrushSpec, samples: List<InkSample>): StoredStroke {
    val output = ByteArrayOutputStream()
    StrokeInputBatchSerialization.encode(inputs, output)
    return StoredStroke(
        brush = spec,
        encodedInputs = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP),
        samples = samples,
    )
}

fun StoredStroke.toRuntimeOrNull(): RuntimeStroke? = runCatching {
    val decoded = Base64.decode(encodedInputs, Base64.NO_WRAP)
    val inputs = StrokeInputBatchSerialization.decode(ByteArrayInputStream(decoded))
    RuntimeStroke(this, Stroke(brush.toBrush(), inputs))
}.getOrNull()

private fun StoredStroke.runtimeWithSamples(rawSamples: List<InkSample>): RuntimeStroke? = runCatching {
    val samples = sanitizeSamples(rawSamples)
    check(samples.size >= 2) { "Not enough stroke samples" }
    val batch = MutableStrokeInputBatch()
    samples.forEachIndexed { index, sample ->
        batch.add(
            type = InputToolType.STYLUS,
            x = sample.x,
            y = sample.y,
            elapsedTimeMillis = index * 8L,
            pressure = sample.pressure.coerceIn(0f, 1f),
        )
    }
    val stroke = Stroke(brush.toBrush(), batch)
    RuntimeStroke(stroke.toStoredStroke(brush, samples), stroke)
}.getOrNull()

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
    a.size == b.size && a.indices.all { index -> a[index].stored.id == b[index].stored.id }

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
    pressure = a.pressure + (b.pressure - a.pressure) * t,
)

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
