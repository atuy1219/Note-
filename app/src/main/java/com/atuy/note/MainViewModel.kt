package com.atuy.note

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.ink.strokes.Stroke
import com.atuy.note.data.BrushKind
import com.atuy.note.data.BrushSpec
import com.atuy.note.data.CustomBrushSpec
import com.atuy.note.data.FolderRecord
import com.atuy.note.data.LassoCoverageMode
import com.atuy.note.data.LibraryIndex
import com.atuy.note.data.NavigationGestureMode
import com.atuy.note.data.NoteRepository
import com.atuy.note.data.NoteSession
import com.atuy.note.data.NoteSummary
import com.atuy.note.data.PageSession
import com.atuy.note.data.RuntimeStroke
import com.atuy.note.data.ScrollAxis
import com.atuy.note.data.ToolMode
import com.atuy.note.sync.DriveSyncManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = NoteRepository(application)
    private val driveSync = DriveSyncManager(application, repository)
    private val preferences = application.getSharedPreferences("editor_preferences", Context.MODE_PRIVATE)
    private val saveMutex = Mutex()
    private val saveJobs = mutableMapOf<String, Job>()

    var library by mutableStateOf(LibraryIndex())
        private set
    val openTabs = mutableStateListOf<NoteSession>()
    var activeNoteId by mutableStateOf<String?>(null)
        private set
    var currentFolderId by mutableStateOf<String?>(null)
        private set
    var toolMode by mutableStateOf(ToolMode.PEN)
        private set
    var brushSpec by mutableStateOf(BrushSpec())
        private set
    var scrollAxis by mutableStateOf(ScrollAxis.VERTICAL)
        private set

    private var navigationGestureModeState by mutableStateOf(
        enumPreference("navigation_gesture", NavigationGestureMode.ONE_FINGER),
    )
    val navigationGestureMode: NavigationGestureMode
        get() = navigationGestureModeState


    private var lassoCoverageModeState by mutableStateOf(
        enumPreference("lasso_coverage", LassoCoverageMode.HALF),
    )
    val lassoCoverageMode: LassoCoverageMode
        get() = lassoCoverageModeState

    private var circleToLassoEnabledState by mutableStateOf(
        preferences.getBoolean("circle_to_lasso", true),
    )
    val circleToLassoEnabled: Boolean
        get() = circleToLassoEnabledState

    var busy by mutableStateOf(false)
        private set
    var statusMessage by mutableStateOf<String?>(null)
        private set

    private var lastLenovoDoubleTapAt = 0L

    init {
        viewModelScope.launch {
            busy = true
            library = runCatching { repository.loadLibrary() }
                .onFailure { statusMessage = it.message ?: "Could not load notebooks" }
                .getOrDefault(LibraryIndex())
            busy = false
        }
    }

    val activeSession: NoteSession?
        get() = activeNoteId?.let { id -> openTabs.firstOrNull { it.id == id } }

    val activePage: PageSession?
        get() = activeSession?.let { session -> session.pages.getOrNull(session.activePageIndex) }

    val currentFolder: FolderRecord?
        get() = currentFolderId?.let { id -> library.folders.firstOrNull { it.id == id } }

    val childFolders: List<FolderRecord>
        get() = library.folders.filter { it.parentId == currentFolderId }.sortedBy { it.name.lowercase() }

    val visibleNotes: List<NoteSummary>
        get() = library.notes.filter { it.folderId == currentFolderId }.sortedByDescending { it.updatedAt }

    fun clearStatus() { statusMessage = null }
    fun reportStatus(message: String) { statusMessage = message }
    fun enterFolder(id: String?) { currentFolderId = id }
    fun navigateUpFolder() { currentFolderId = currentFolder?.parentId }

    fun createFolder(name: String) {
        viewModelScope.launch { runBusy { library = repository.createFolder(name, currentFolderId, library) } }
    }

    fun createBlankNote(title: String) {
        viewModelScope.launch {
            runBusy {
                val (next, summary) = repository.createBlankNote(title, currentFolderId, library)
                library = next
                openNoteNow(summary.id)
            }
        }
    }

    fun importPdf(uri: Uri) {
        viewModelScope.launch {
            runBusy {
                val (next, summary) = repository.importPdf(uri, currentFolderId, library)
                library = next
                openNoteNow(summary.id)
            }
        }
    }

    fun importImage(uri: Uri) {
        val session = activeSession ?: return
        val page = activePage ?: return
        viewModelScope.launch {
            runBusy {
                val imported = repository.importImage(session, page, uri)
                session.imageFiles[imported.image.entryName] = imported.entryFile
                session.imageBitmaps[imported.image.entryName] = imported.bitmap
                 page.addImage(imported.image)
                toolMode = ToolMode.IMAGE
                markDirty(session)
            }
        }
    }

    fun openNote(noteId: String) {
        openTabs.firstOrNull { it.id == noteId }?.let {
            activeNoteId = it.id
            return
        }
        viewModelScope.launch { runBusy { openNoteNow(noteId) } }
    }

    private suspend fun openNoteNow(noteId: String) {
        openTabs.firstOrNull { it.id == noteId }?.let {
            activeNoteId = it.id
            return
        }
        val session = repository.loadSession(noteId)
        openTabs += session
        activeNoteId = session.id
    }

    fun activateTab(noteId: String) {
        if (openTabs.any { it.id == noteId }) activeNoteId = noteId
    }

    fun closeTab(noteId: String) {
        viewModelScope.launch {
            val index = openTabs.indexOfFirst { it.id == noteId }
            if (index < 0) return@launch
            val session = openTabs[index]
            if (session.dirty) saveNow(session)
            // Do not recycle UI-visible bitmaps here. The outgoing AndroidView or a
            // hardware display list can still reference them for one or more frames.
            openTabs.removeAt(index)
            if (activeNoteId == noteId) activeNoteId = openTabs.getOrNull((index - 1).coerceAtLeast(0))?.id
        }
    }

    fun showLibrary() { activeNoteId = null }

    fun renameActiveNote(name: String) {
        val session = activeSession ?: return
        val normalized = name.trim().ifBlank { "Untitled" }
        if (session.title == normalized) return
        session.title = normalized
        markDirty(session)
    }

    fun deleteActiveNote() {
        val session = activeSession ?: return
        viewModelScope.launch {
            runBusy {
                saveJobs.remove(session.id)?.cancel()
                library = repository.deleteNote(session.id, library)
                // Bitmap lifetime follows the session references; explicit recycle can
                // race the final frame of the editor while it leaves composition.
                val index = openTabs.indexOfFirst { it.id == session.id }
                openTabs.removeAll { it.id == session.id }
                activeNoteId = openTabs.getOrNull(index.coerceAtMost(openTabs.lastIndex))?.id
            }
        }
    }

    fun setTool(mode: ToolMode) {
        toolMode = mode
        if (mode != ToolMode.IMAGE) activePage?.selectImage(null)
        if (mode != ToolMode.LASSO) activePage?.clearStrokeSelection()
    }

    fun toggleEraser() {
        setTool(if (toolMode == ToolMode.ERASER) ToolMode.PEN else ToolMode.ERASER)
    }

    fun updateBrush(colorArgb: Int = brushSpec.colorArgb, size: Float = brushSpec.size) {
        val resolvedColor = if (brushSpec.kind == BrushKind.HIGHLIGHTER) {
            (0x66 shl 24) or (colorArgb and 0x00FFFFFF)
        } else {
            (0xFF shl 24) or (colorArgb and 0x00FFFFFF)
        }
        brushSpec = brushSpec.copy(colorArgb = resolvedColor, size = size.coerceIn(0.5f, 96f))
        setTool(ToolMode.PEN)
    }

    fun setBrushKind(kind: BrushKind) {
        brushSpec = when (kind) {
            BrushKind.PRESSURE_PEN -> brushSpec.copy(
                kind = kind,
                colorArgb = (0xFF shl 24) or (brushSpec.colorArgb and 0x00FFFFFF),
                size = brushSpec.size.coerceIn(1.5f, 18f),
                custom = null,
            )
            BrushKind.MARKER -> brushSpec.copy(
                kind = kind,
                colorArgb = (0xFF shl 24) or (brushSpec.colorArgb and 0x00FFFFFF),
                size = brushSpec.size.coerceAtLeast(5f),
                custom = null,
            )
            BrushKind.HIGHLIGHTER -> brushSpec.copy(
                kind = kind,
                colorArgb = (0x66 shl 24) or (if ((brushSpec.colorArgb and 0x00FFFFFF) == 0x00111111) 0x00FFF176 else brushSpec.colorArgb and 0x00FFFFFF),
                size = brushSpec.size.coerceAtLeast(16f),
                custom = null,
            )
            BrushKind.CUSTOM -> brushSpec.copy(
                kind = kind,
                colorArgb = (0xFF shl 24) or (brushSpec.colorArgb and 0x00FFFFFF),
                custom = brushSpec.custom ?: CustomBrushSpec(),
            )
        }
        setTool(ToolMode.PEN)
    }

    fun updateCustomBrush(custom: CustomBrushSpec) {
        brushSpec = brushSpec.copy(kind = BrushKind.CUSTOM, custom = custom)
        setTool(ToolMode.PEN)
    }

    fun toggleScrollAxis() {
        scrollAxis = if (scrollAxis == ScrollAxis.VERTICAL) ScrollAxis.HORIZONTAL else ScrollAxis.VERTICAL
    }

    fun setNavigationGestureMode(mode: NavigationGestureMode) {
        navigationGestureModeState = mode
        preferences.edit().putString("navigation_gesture", mode.name).apply()
    }


    fun setLassoCoverageMode(mode: LassoCoverageMode) {
        lassoCoverageModeState = mode
        preferences.edit().putString("lasso_coverage", mode.name).apply()
        setTool(ToolMode.LASSO)
    }

    fun setCircleToLassoEnabled(enabled: Boolean) {
        circleToLassoEnabledState = enabled
        preferences.edit().putBoolean("circle_to_lasso", enabled).apply()
    }

    fun activatePage(index: Int) {
        val session = activeSession ?: return
        session.activePageIndex = index.coerceIn(0, session.pages.lastIndex.coerceAtLeast(0))
    }

    fun addPage() {
        val session = activeSession ?: return
        val insertAt = (session.activePageIndex + 1).coerceIn(0, session.pages.size)
        val template = activePage
        session.pages.add(
            insertAt,
            PageSession(
                com.atuy.note.data.PageDocument(
                    width = template?.width ?: com.atuy.note.data.PAGE_WIDTH,
                    height = template?.height ?: com.atuy.note.data.PAGE_HEIGHT,
                ),
            ),
        )
        session.activePageIndex = insertAt
        markDirty(session)
    }

    fun duplicatePage(index: Int) {
        val session = activeSession ?: return
        val source = session.pages.getOrNull(index) ?: return
        val insertAt = index + 1
        session.pages.add(insertAt, source.duplicate())
        session.activePageIndex = insertAt
        markDirty(session)
    }

    fun deletePage(index: Int) {
        val session = activeSession ?: return
        if (session.pages.size <= 1) {
            statusMessage = "最後のページは削除できません"
            return
        }
        if (index !in session.pages.indices) return
        session.pages.removeAt(index)
        session.activePageIndex = session.activePageIndex.coerceIn(0, session.pages.lastIndex)
        markDirty(session)
    }

    fun movePage(index: Int, delta: Int) {
        val session = activeSession ?: return
        val target = index + delta
        if (index !in session.pages.indices || target !in session.pages.indices) return
        val page = session.pages.removeAt(index)
        session.pages.add(target, page)
        session.activePageIndex = target
        markDirty(session)
    }

    fun addStroke(page: PageSession, runtime: RuntimeStroke) {
        page.add(runtime)
        markDirty()
    }

    fun beginErase(page: PageSession) { page.beginEraseGesture() }
    fun eraseAt(page: PageSession, x: Float, y: Float, radius: Float) { page.eraseAt(x, y, radius) }
    fun endErase(page: PageSession) { if (page.endEraseGesture()) markDirty() }

    fun selectWithLasso(page: PageSession, lasso: Stroke) {
        val count = page.selectWithLasso(lasso.inputs, lassoCoverageMode)
        statusMessage = if (count == 0) "選択なし" else "$count 本の線を選択"
    }

    fun convertCircleStrokeToLasso(page: PageSession, strokeId: String, lasso: Stroke) {
        if (!page.consumeStrokeForLasso(strokeId)) return
        val count = page.selectWithLasso(lasso.inputs, lassoCoverageMode)
        toolMode = ToolMode.LASSO
        markDirty()
        statusMessage = if (count == 0) "囲みを投げ縄に変換しました（選択なし）" else "$count 本の線を選択"
    }

    fun beginSelectedStrokeTransform(page: PageSession): Boolean = page.beginSelectedStrokeTransform()
    fun moveSelectedStrokes(page: PageSession, dx: Float, dy: Float) { page.transformSelectedStrokes(dx, dy) }
    fun endSelectedStrokeTransform(page: PageSession) { if (page.endSelectedStrokeTransform()) markDirty() }
    fun cancelSelectedStrokeTransform(page: PageSession) { page.cancelSelectedStrokeTransform() }

    fun scaleSelectedStrokes(factor: Float) {
        val page = activePage ?: return
        if (page.scaleSelectedStrokes(factor)) markDirty()
    }

    fun applyCurrentBrushToSelected() {
        val page = activePage ?: return
        if (page.restyleSelectedStrokes(
                colorArgb = brushSpec.colorArgb,
                size = brushSpec.size,
                kind = brushSpec.kind,
                custom = brushSpec.custom,
            )
        ) markDirty()
    }

    fun updateSelectedBrush(
        colorArgb: Int? = null,
        size: Float? = null,
        kind: BrushKind? = null,
    ) {
        val page = activePage ?: return
        val resolvedKind = kind
        val resolvedColor = colorArgb?.let { color ->
            if ((resolvedKind ?: brushSpec.kind) == BrushKind.HIGHLIGHTER) {
                (0x66 shl 24) or (color and 0x00FFFFFF)
            } else {
                (0xFF shl 24) or (color and 0x00FFFFFF)
            }
        }
        if (page.restyleSelectedStrokes(
                colorArgb = resolvedColor,
                size = size,
                kind = resolvedKind,
                custom = if (resolvedKind == BrushKind.CUSTOM) brushSpec.custom else null,
            )
        ) markDirty()
    }

    fun deleteSelectedStrokes() {
        val page = activePage ?: return
        if (page.deleteSelectedStrokes()) markDirty()
    }

    fun selectImage(page: PageSession, imageId: String?) {
        page.selectImage(imageId)
        if (imageId != null) toolMode = ToolMode.IMAGE
    }

    fun beginImageTransform(page: PageSession, imageId: String): Boolean = page.beginImageTransform(imageId)
    fun moveImage(page: PageSession, imageId: String, x: Float, y: Float) { page.moveImage(imageId, x, y) }
    fun endImageTransform(page: PageSession) { if (page.endImageTransform()) markDirty() }
    fun cancelImageTransform(page: PageSession) { page.cancelImageTransform() }

    fun scaleSelectedImage(factor: Float) {
        val page = activePage ?: return
        if (page.scaleSelectedImage(factor)) markDirty()
    }

    fun deleteSelectedImage() {
        val page = activePage ?: return
        if (page.deleteSelectedImage() != null) markDirty()
    }

    fun undo() { if (activePage?.undo() == true) markDirty() }
    fun redo() { if (activePage?.redo() == true) markDirty() }

    fun saveActive() {
        activeSession?.let { session -> viewModelScope.launch { saveNow(session) } }
    }

    suspend fun renderPdfPage(session: NoteSession, page: PageSession, targetWidth: Int): Bitmap? =
        repository.renderPdfPage(session, page, targetWidth)

    suspend fun renderPagePreview(session: NoteSession, page: PageSession, targetWidth: Int): Bitmap =
        repository.renderPagePreview(session, page, targetWidth)

    fun syncWithDrive(accessToken: String) {
        viewModelScope.launch {
            runBusy {
                openTabs.filter { it.dirty }.forEach { saveNow(it) }
                val result = driveSync.sync(accessToken, library.folders) { message ->
                    viewModelScope.launch { statusMessage = message }
                }
                library = repository.rebuildLibrary(result.folders)
                statusMessage = "Drive sync: ${result.uploaded} uploaded, ${result.downloaded} downloaded" +
                    if (result.conflicts > 0) ", ${result.conflicts} conflicts copied" else ""
            }
        }
    }

    fun handleStylusKey(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return false
        return when (event.keyCode) {
            601 -> { toggleEraser(); true }
            718 -> {
                val now = event.eventTime
                if (lastLenovoDoubleTapAt != 0L && now - lastLenovoDoubleTapAt <= 650L) {
                    lastLenovoDoubleTapAt = 0L
                    toggleEraser()
                } else {
                    lastLenovoDoubleTapAt = now
                }
                true
            }
            else -> false
        }
    }

    private fun markDirty() { activeSession?.let(::markDirty) }

    private fun markDirty(session: NoteSession) {
        session.dirty = true
        saveJobs.remove(session.id)?.cancel()
        saveJobs[session.id] = viewModelScope.launch {
            delay(900)
            saveNow(session)
        }
    }

    private suspend fun saveNow(session: NoteSession) {
        saveMutex.withLock {
            if (!session.dirty) return
            library = repository.saveSession(session, library)
        }
    }

    private suspend fun runBusy(block: suspend () -> Unit) {
        busy = true
        runCatching { block() }.onFailure { statusMessage = it.message ?: it.javaClass.simpleName }
        busy = false
    }

    private inline fun <reified T : Enum<T>> enumPreference(key: String, fallback: T): T =
        runCatching { enumValueOf<T>(preferences.getString(key, null) ?: fallback.name) }.getOrDefault(fallback)
}
