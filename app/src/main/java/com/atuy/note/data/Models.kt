package com.atuy.note.data

import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.storage.StrokeInputBatchSerialization
import androidx.ink.strokes.Stroke
import kotlinx.serialization.Serializable
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

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
    val formatVersion: Int = 1,
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

enum class ToolMode { PEN, ERASER }
enum class ScrollAxis { VERTICAL, HORIZONTAL }

data class RuntimeStroke(val stored: StoredStroke, val stroke: Stroke)

class PageSession(val source: PageDocument) {
    val id: String = source.id
    val width: Float = source.width
    val height: Float = source.height
    val pdfPageIndex: Int? = source.pdfPageIndex
    val strokes = mutableStateListOf<RuntimeStroke>()
    private val undoStack = ArrayDeque<InkOperation>()
    private val redoStack = ArrayDeque<InkOperation>()

    init {
        source.strokes.mapNotNullTo(strokes) { it.toRuntimeOrNull() }
    }

    fun add(runtime: RuntimeStroke, recordHistory: Boolean = true) {
        strokes += runtime
        if (recordHistory) {
            undoStack.addLast(InkOperation.Add(runtime))
            redoStack.clear()
        }
    }

    fun eraseAt(x: Float, y: Float, radius: Float): Boolean {
        val index = strokes.indexOfLast { runtime ->
            runtime.stored.samples.any { sample ->
                val dx = sample.x - x
                val dy = sample.y - y
                dx * dx + dy * dy <= radius * radius
            }
        }
        if (index < 0) return false
        val removed = strokes.removeAt(index)
        undoStack.addLast(InkOperation.Remove(index, removed))
        redoStack.clear()
        return true
    }

    fun undo(): Boolean {
        val op = undoStack.removeLastOrNull() ?: return false
        when (op) {
            is InkOperation.Add -> strokes.remove(op.stroke)
            is InkOperation.Remove -> strokes.add(op.index.coerceIn(0, strokes.size), op.stroke)
        }
        redoStack.addLast(op)
        return true
    }

    fun redo(): Boolean {
        val op = redoStack.removeLastOrNull() ?: return false
        when (op) {
            is InkOperation.Add -> strokes.add(op.stroke)
            is InkOperation.Remove -> strokes.remove(op.stroke)
        }
        undoStack.addLast(op)
        return true
    }

    fun toDocument(): PageDocument = PageDocument(
        id = id,
        width = width,
        height = height,
        pdfPageIndex = pdfPageIndex,
        strokes = strokes.map { it.stored },
    )
}

private sealed interface InkOperation {
    data class Add(val stroke: RuntimeStroke) : InkOperation
    data class Remove(val index: Int, val stroke: RuntimeStroke) : InkOperation
}

class NoteSession(
    document: NoteDocument,
    val archiveFile: File,
    val sourcePdfFile: File?,
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
