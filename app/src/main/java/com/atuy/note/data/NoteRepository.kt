package com.atuy.note.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min

class NoteRepository(private val context: Context) {
    private val root = File(context.filesDir, "notebooks").apply { mkdirs() }
    private val noteDir = File(root, "notes").apply { mkdirs() }
    private val thumbDir = File(root, "thumbnails").apply { mkdirs() }
    private val pdfCacheDir = File(context.cacheDir, "note-pdf").apply { mkdirs() }
    private val imageCacheDir = File(context.cacheDir, "note-images").apply { mkdirs() }
    private val libraryFile = File(root, "library.json")
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val ioMutex = Mutex()
    private val pdfMutex = Mutex()

    suspend fun loadLibrary(): LibraryIndex = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val stored = runCatching {
                if (libraryFile.isFile) json.decodeFromString<LibraryIndex>(libraryFile.readText()) else LibraryIndex()
            }.getOrDefault(LibraryIndex())
            reconcile(stored)
        }
    }

    suspend fun createFolder(name: String, parentId: String?, current: LibraryIndex): LibraryIndex = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val folder = FolderRecord(name = name.trim().ifBlank { "New folder" }, parentId = parentId)
            current.copy(folders = current.folders + folder).also(::writeLibrary)
        }
    }

    suspend fun createBlankNote(title: String, folderId: String?, current: LibraryIndex): Pair<LibraryIndex, NoteSummary> =
        withContext(Dispatchers.IO) {
            ioMutex.withLock {
                val document = NoteDocument(title = title.trim().ifBlank { "Untitled" }, folderId = folderId)
                val file = noteFile(document.id)
                writeArchive(file, document, null, emptyMap(), emptyMap())
                val summary = summaryFor(document, renderThumbnail(document, null, emptyMap(), emptyMap()))
                val next = current.copy(notes = current.notes.filterNot { it.id == summary.id } + summary)
                writeLibrary(next)
                next to summary
            }
        }

    suspend fun importPdf(uri: Uri, folderId: String?, current: LibraryIndex): Pair<LibraryIndex, NoteSummary> =
        withContext(Dispatchers.IO) {
            ioMutex.withLock {
                val title = displayName(uri).substringBeforeLast('.').ifBlank { "Imported PDF" }
                val id = UUID.randomUUID().toString()
                val pdfFile = File(pdfCacheDir, "$id.pdf")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(pdfFile).use { output -> input.copyTo(output) }
                } ?: error("Could not open PDF")

                val pages = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        (0 until renderer.pageCount).map { index ->
                            renderer.openPage(index).use { page ->
                                val h = (PAGE_WIDTH * page.height.toFloat() / page.width.toFloat()).coerceAtLeast(1f)
                                PageDocument(width = PAGE_WIDTH, height = h, pdfPageIndex = index)
                            }
                        }
                    }
                }
                val document = NoteDocument(
                    id = id,
                    title = title,
                    folderId = folderId,
                    sourcePdfEntry = "background/source.pdf",
                    pages = pages.ifEmpty { listOf(PageDocument()) },
                )
                val file = noteFile(id)
                writeArchive(file, document, pdfFile, emptyMap(), emptyMap())
                val thumb = renderThumbnail(document, pdfFile, emptyMap(), emptyMap())
                val summary = summaryFor(document, thumb)
                val next = current.copy(notes = current.notes.filterNot { it.id == id } + summary)
                writeLibrary(next)
                next to summary
            }
        }

    suspend fun importImage(session: NoteSession, page: PageSession, uri: Uri): ImportedPageImage =
        withContext(Dispatchers.IO) {
            val bitmap = decodeContentImage(uri, 4096) ?: error("Could not decode image")
            val id = UUID.randomUUID().toString()
            val entryName = "images/$id.png"
            val sessionDir = sessionImageDir(session.id)
            val outputFile = File(sessionDir, "$id.png")
            FileOutputStream(outputFile).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Could not store image" }
            }
            val maxWidth = page.width * 0.72f
            val maxHeight = page.height * 0.58f
            val fitScale = min(maxWidth / bitmap.width.toFloat(), maxHeight / bitmap.height.toFloat())
                .coerceAtMost(1f)
                .coerceAtLeast(0.01f)
            val placedWidth = bitmap.width * fitScale
            val placedHeight = bitmap.height * fitScale
            ImportedPageImage(
                image = PageImage(
                    id = id,
                    entryName = entryName,
                    x = (page.width - placedWidth) / 2f,
                    y = (page.height - placedHeight) / 2f,
                    width = placedWidth,
                    height = placedHeight,
                ),
                entryFile = outputFile,
                bitmap = bitmap,
            )
        }

    suspend fun loadSession(noteId: String): NoteSession = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val archive = noteFile(noteId)
            require(archive.isFile) { "Notebook not found: $noteId" }
            val extractedPdf = File(pdfCacheDir, "$noteId.pdf").apply { delete() }
            val sessionImages = sessionImageDir(noteId).apply {
                deleteRecursively()
                mkdirs()
            }
            val imageFiles = mutableMapOf<String, File>()
            val imageBitmaps = mutableMapOf<String, Bitmap>()
            val inkEntries = mutableMapOf<String, ByteArray>()
            var document: NoteDocument? = null
            ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when {
                        entry.name == "manifest.json" ->
                            document = json.decodeFromString(zip.readBytes().decodeToString())
                        entry.name == "background/source.pdf" ->
                            FileOutputStream(extractedPdf).use { zip.copyTo(it) }
                        entry.name.startsWith("images/") && !entry.isDirectory -> {
                            val file = cacheFileForEntry(sessionImages, entry.name)
                            FileOutputStream(file).use { zip.copyTo(it) }
                            imageFiles[entry.name] = file
                            BitmapFactory.decodeFile(file.absolutePath)?.let { imageBitmaps[entry.name] = it }
                        }
                        entry.name.startsWith("ink/") && !entry.isDirectory ->
                            inkEntries[entry.name] = zip.readBytes()
                    }
                    zip.closeEntry()
                }
            }
            val loaded = requireNotNull(document) { "Invalid .atnote: manifest.json missing" }
            NoteSession(
                document = loaded,
                archiveFile = archive,
                sourcePdfFile = extractedPdf.takeIf { it.isFile },
                imageFiles = imageFiles,
                imageBitmaps = imageBitmaps,
                inkEntries = inkEntries,
            )
        }
    }

    suspend fun saveSession(session: NoteSession, current: LibraryIndex): LibraryIndex = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val document = session.toDocument(nextRevision = true)
            val inkEntries = session.encodedInkEntries()
            writeArchive(session.archiveFile, document, session.sourcePdfFile, session.imageFiles, inkEntries)
            session.revision = document.revision
            session.updatedAt = document.updatedAt
            session.dirty = false
            val thumb = renderThumbnail(document, session.sourcePdfFile, session.imageFiles, inkEntries)
            val summary = summaryFor(document, thumb)
            current.copy(notes = current.notes.filterNot { it.id == session.id } + summary).also(::writeLibrary)
        }
    }

    suspend fun renderPdfPage(session: NoteSession, page: PageSession, targetWidth: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            val source = session.sourcePdfFile ?: return@withContext null
            val index = page.pdfPageIndex ?: return@withContext null
            pdfMutex.withLock {
                runCatching {
                    ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        PdfRenderer(descriptor).use { renderer ->
                            renderer.openPage(index).use { pdfPage ->
                                val width = targetWidth.coerceIn(360, 1600)
                                val height = (width * pdfPage.height.toFloat() / pdfPage.width.toFloat()).toInt().coerceAtLeast(1)
                                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                                    bitmap.eraseColor(Color.WHITE)
                                    pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                            }
                        }
                    }
                }.getOrNull()
            }
        }

    fun noteFiles(): List<File> =
        noteDir.listFiles { f -> f.extension == NOTE_EXTENSION.removePrefix(".") }?.toList().orEmpty()

    fun noteFileById(id: String): File = noteFile(id)

    fun readDocument(file: File): NoteDocument? = runCatching {
        var document: NoteDocument? = null
        ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zip ->
            while (document == null) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "manifest.json") {
                    document = json.decodeFromString<NoteDocument>(zip.readBytes().decodeToString())
                }
                zip.closeEntry()
            }
        }
        document
    }.getOrNull()

    fun replaceFromRemoteBlocking(tempFile: File) {
        val doc = requireNotNull(readDocument(tempFile)) { "Downloaded notebook is invalid" }
        tempFile.copyTo(noteFile(doc.id), overwrite = true)
    }

    fun replaceFromRemoteAsConflict(tempFile: File, folderId: String?) {
        var document: NoteDocument? = null
        val extractedPdf = File.createTempFile("note-conflict-", ".pdf", pdfCacheDir)
        val extractedImagesDir = File.createTempFile("note-conflict-images-", "", imageCacheDir).apply {
            delete()
            mkdirs()
        }
        val extractedImages = mutableMapOf<String, File>()
        val extractedInkEntries = mutableMapOf<String, ByteArray>()
        var hasPdf = false
        ZipInputStream(BufferedInputStream(FileInputStream(tempFile))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.name == "manifest.json" ->
                        document = json.decodeFromString(zip.readBytes().decodeToString())
                    entry.name == "background/source.pdf" -> {
                        FileOutputStream(extractedPdf).use { zip.copyTo(it) }
                        hasPdf = true
                    }
                    entry.name.startsWith("images/") && !entry.isDirectory -> {
                        val file = cacheFileForEntry(extractedImagesDir, entry.name)
                        FileOutputStream(file).use { zip.copyTo(it) }
                        extractedImages[entry.name] = file
                    }
                    entry.name.startsWith("ink/") && !entry.isDirectory ->
                        extractedInkEntries[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        val source = requireNotNull(document) { "Conflict notebook is invalid" }
        val id = UUID.randomUUID().toString()
        val conflict = source.copy(
            id = id,
            title = "${source.title} (conflict)",
            folderId = folderId,
            updatedAt = System.currentTimeMillis(),
            revision = source.revision + 1,
        )
        writeArchive(noteFile(id), conflict, extractedPdf.takeIf { hasPdf }, extractedImages, extractedInkEntries)
        extractedPdf.delete()
        extractedImagesDir.deleteRecursively()
    }

    suspend fun replaceFromRemote(tempFile: File) = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val doc = requireNotNull(readDocument(tempFile)) { "Downloaded notebook is invalid" }
            val target = noteFile(doc.id)
            tempFile.copyTo(target, overwrite = true)
        }
    }

    suspend fun rebuildLibrary(currentFolders: List<FolderRecord>): LibraryIndex = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val notes = noteFiles().mapNotNull { file ->
                readDocument(file)?.let { doc ->
                    val thumb = thumbnailFile(doc.id).takeIf { it.isFile }
                    summaryFor(doc, thumb)
                }
            }
            LibraryIndex(folders = currentFolders, notes = notes).also(::writeLibrary)
        }
    }

    private fun reconcile(index: LibraryIndex): LibraryIndex {
        val filesById = noteFiles().associateBy { it.nameWithoutExtension }
        val valid = index.notes.filter { filesById.containsKey(it.id) }.toMutableList()
        val known = valid.mapTo(mutableSetOf()) { it.id }
        for ((id, file) in filesById) {
            if (id in known) continue
            readDocument(file)?.let { doc -> valid += summaryFor(doc, thumbnailFile(doc.id).takeIf { it.isFile }) }
        }
        return index.copy(notes = valid).also(::writeLibrary)
    }

    private fun writeLibrary(index: LibraryIndex) {
        val temp = File(libraryFile.parentFile, "${libraryFile.name}.tmp")
        temp.writeText(json.encodeToString(index))
        if (!temp.renameTo(libraryFile)) {
            temp.copyTo(libraryFile, overwrite = true)
            temp.delete()
        }
    }

    private fun writeArchive(
        target: File,
        document: NoteDocument,
        pdfFile: File?,
        imageFiles: Map<String, File>,
        inkEntries: Map<String, ByteArray>,
    ) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(temp))).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(json.encodeToString(document).toByteArray())
            zip.closeEntry()
            if (pdfFile?.isFile == true) {
                zip.putNextEntry(ZipEntry("background/source.pdf"))
                FileInputStream(pdfFile).use { it.copyTo(zip) }
                zip.closeEntry()
            }
            val referencedImages = document.pages.flatMap { it.images }.map { it.entryName }.distinct()
            referencedImages.forEach { entryName ->
                val imageFile = imageFiles[entryName]
                if (imageFile?.isFile == true) {
                    zip.putNextEntry(ZipEntry(entryName))
                    FileInputStream(imageFile).use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            val referencedInk = document.pages.flatMap { it.strokes }
                .map { it.inkEntry }
                .filter { it.isNotBlank() }
                .distinct()
            referencedInk.forEach { entryName ->
                val encoded = inkEntries[entryName] ?: return@forEach
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(encoded)
                zip.closeEntry()
            }
        }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    private fun renderThumbnail(
        document: NoteDocument,
        pdfFile: File?,
        imageFiles: Map<String, File>,
        inkEntries: Map<String, ByteArray>,
    ): File {
        val width = 480
        val first = document.pages.firstOrNull() ?: PageDocument()
        val height = (width * first.height / first.width).toInt().coerceAtLeast(1)
        val bitmap = if (pdfFile?.isFile == true && first.pdfPageIndex != null) {
            runCatching {
                ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        renderer.openPage(first.pdfPageIndex).use { page ->
                            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                                it.eraseColor(Color.WHITE)
                                page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    }
                }
            }.getOrNull()
        } else null
        val outputBitmap = bitmap ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        val canvas = Canvas(outputBitmap)
        val sx = width / first.width
        val sy = height / first.height

        first.images.forEach { image ->
            val file = imageFiles[image.entryName] ?: return@forEach
            val imageBitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@forEach
            canvas.drawBitmap(
                imageBitmap,
                null,
                RectF(
                    image.x * sx,
                    image.y * sy,
                    (image.x + image.width) * sx,
                    (image.y + image.height) * sy,
                ),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
            imageBitmap.recycle()
        }

        first.strokes.forEach { stored ->
            val runtime = stored.toRuntimeOrNull(inkEntries[stored.inkEntry]) ?: return@forEach
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = stored.brush.colorArgb
                strokeWidth = stored.brush.size * sx
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            runtime.samples.zipWithNext().forEach { (a, b) ->
                canvas.drawLine(a.x * sx, a.y * sy, b.x * sx, b.y * sy, paint)
            }
        }
        val file = thumbnailFile(document.id)
        FileOutputStream(file).use { outputBitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
        outputBitmap.recycle()
        return file
    }

    private fun decodeContentImage(uri: Uri, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / sampleSize > maxDimension) sampleSize *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun sessionImageDir(noteId: String) = File(imageCacheDir, noteId).apply { mkdirs() }

    private fun cacheFileForEntry(directory: File, entryName: String): File {
        val safeName = entryName.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(directory, safeName.ifBlank { UUID.randomUUID().toString() })
    }

    private fun summaryFor(document: NoteDocument, thumbnail: File?): NoteSummary = NoteSummary(
        id = document.id,
        title = document.title,
        folderId = document.folderId,
        updatedAt = document.updatedAt,
        revision = document.revision,
        pageCount = document.pages.size,
        thumbnailPath = thumbnail?.absolutePath,
    )

    private fun noteFile(id: String) = File(noteDir, "$id$NOTE_EXTENSION")
    private fun thumbnailFile(id: String) = File(thumbDir, "$id.png")

    private fun displayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0) ?: "Imported PDF"
        }
        return uri.lastPathSegment ?: "Imported PDF"
    }
}
