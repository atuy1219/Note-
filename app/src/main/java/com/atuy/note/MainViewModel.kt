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
import com.atuy.note.data.BrushSpec
import com.atuy.note.data.EraserMode
import com.atuy.note.data.FolderRecord
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
    var navigationGestureMode by mutableStateOf(
        enumPreference("navigation_gesture", NavigationGestureMode.ONE_FINGER),
    )
        private set
    var eraserMode by mutableStateOf(enumPreference("eraser_mode", EraserMode.PARTIAL))
        private set
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
            session.imageBitmaps.values.forEach { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
            openTabs.removeAt(index)
            if (activeNoteId == noteId) activeNoteId = openTabs.getOrNull((index - 1).coerceAtLeast(0))?.id
        }
    }

    fun showLibrary() { activeNoteId = null }

    fun setTool(mode: ToolMode) {
        toolMode = mode
        if (mode != ToolMode.IMAGE) activePage?.selectImage(null)
    }

    fun toggleEraser() {
        setTool(if (toolMode == ToolMode.ERASER) ToolMode.PEN else ToolMode.ERASER)
    }

    fun updateBrush(colorArgb: Int = brushSpec.colorArgb, size: Float = brushSpec.size) {
        brushSpec = brushSpec.copy(colorArgb = colorArgb, size = size)
        setTool(ToolMode.PEN)
    }

    fun toggleScrollAxis() {
        scrollAxis = if (scrollAxis == ScrollAxis.VERTICAL) ScrollAxis.HORIZONTAL else ScrollAxis.VERTICAL
    }

    fun setNavigationGestureMode(mode: NavigationGestureMode) {
        navigationGestureMode = mode
        preferences.edit().putString("navigation_gesture", mode.name).apply()
    }

    fun setEraserMode(mode: EraserMode) {
        eraserMode = mode
        preferences.edit().putString("eraser_mode", mode.name).apply()
        setTool(ToolMode.ERASER)
    }

    fun activatePage(index: Int) { activeSession?.activePageIndex = index.coerceAtLeast(0) }

    fun addPage() {
        val session = activeSession ?: return
        session.pages += PageSession(com.atuy.note.data.PageDocument())
        session.activePageIndex = session.pages.lastIndex
        markDirty(session)
    }

    fun addStroke(page: PageSession, runtime: RuntimeStroke) {
        page.add(runtime)
        markDirty()
    }

    fun beginErase(page: PageSession) { page.beginEraseGesture() }
    fun eraseAt(page: PageSession, x: Float, y: Float, radius: Float) { page.eraseAt(x, y, radius, eraserMode) }
    fun endErase(page: PageSession) { if (page.endEraseGesture()) markDirty() }

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
