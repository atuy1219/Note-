from pathlib import Path
import re


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one match in {path} for {old[:120]!r}, found {count}")
    path.write_text(text.replace(old, new, 1))


def replace_regex_once(path: Path, pattern: str, replacement: str) -> None:
    text = path.read_text()
    next_text, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"Expected one regex match in {path} for {pattern[:120]!r}, found {count}")
    path.write_text(next_text)


models = Path("app/src/main/java/com/atuy/note/data/Models.kt")
replace_once(
    models,
    "    val parentId: String? = null,\n    val createdAt: Long = System.currentTimeMillis(),",
    "    val parentId: String? = null,\n    val trashedAt: Long? = null,\n    val createdAt: Long = System.currentTimeMillis(),",
)
replace_once(
    models,
    "    val folderId: String? = null,\n    val updatedAt: Long,\n    val revision: Long,",
    "    val folderId: String? = null,\n    val trashedAt: Long? = null,\n    val updatedAt: Long,\n    val revision: Long,",
)
replace_once(
    models,
    "    val folderId: String? = null,\n    val createdAt: Long = System.currentTimeMillis(),\n    val updatedAt: Long = createdAt,",
    "    val folderId: String? = null,\n    val trashedAt: Long? = null,\n    val createdAt: Long = System.currentTimeMillis(),\n    val updatedAt: Long = createdAt,",
)
replace_once(
    models,
    "    var folderId by mutableStateOf(document.folderId)\n    var updatedAt by mutableStateOf(document.updatedAt)",
    "    var folderId by mutableStateOf(document.folderId)\n    var trashedAt by mutableStateOf(document.trashedAt)\n    var updatedAt by mutableStateOf(document.updatedAt)",
)
replace_once(
    models,
    "            folderId = folderId,\n            createdAt = createdAt,",
    "            folderId = folderId,\n            trashedAt = trashedAt,\n            createdAt = createdAt,",
)

repository = Path("app/src/main/java/com/atuy/note/data/NoteRepository.kt")
replace_once(
    repository,
    "    suspend fun deleteNote(noteId: String, current: LibraryIndex): LibraryIndex = withContext(Dispatchers.IO) {\n        ioMutex.withLock {\n            noteFile(noteId).delete()\n            thumbnailFile(noteId).delete()\n            File(pdfCacheDir, \"$noteId.pdf\").delete()\n            sessionImageDir(noteId).deleteRecursively()\n            current.copy(notes = current.notes.filterNot { it.id == noteId }).also(::writeLibrary)\n        }\n    }\n",
    "    suspend fun deleteNote(noteId: String, current: LibraryIndex): LibraryIndex = withContext(Dispatchers.IO) {\n        ioMutex.withLock {\n            deleteNoteFiles(noteId)\n            current.copy(notes = current.notes.filterNot { it.id == noteId }).also(::writeLibrary)\n        }\n    }\n\n    suspend fun renameNote(noteId: String, name: String, current: LibraryIndex): LibraryIndex =\n        updateNoteMetadata(noteId, current) { document ->\n            document.copy(\n                title = name.trim().ifBlank { \"Untitled\" },\n                updatedAt = System.currentTimeMillis(),\n                revision = document.revision + 1,\n            )\n        }\n\n    suspend fun moveNote(noteId: String, folderId: String?, current: LibraryIndex): LibraryIndex =\n        updateNoteMetadata(noteId, current) { document ->\n            document.copy(\n                folderId = folderId,\n                updatedAt = System.currentTimeMillis(),\n                revision = document.revision + 1,\n            )\n        }\n\n    suspend fun trashNote(noteId: String, current: LibraryIndex): LibraryIndex =\n        updateNoteMetadata(noteId, current) { document ->\n            document.copy(\n                trashedAt = System.currentTimeMillis(),\n                updatedAt = System.currentTimeMillis(),\n                revision = document.revision + 1,\n            )\n        }\n\n    suspend fun restoreNote(noteId: String, current: LibraryIndex): LibraryIndex {\n        val summary = current.notes.firstOrNull { it.id == noteId } ?: return current\n        val restoredFolderId = summary.folderId?.takeIf { folderId ->\n            current.folders.any { it.id == folderId && it.trashedAt == null }\n        }\n        return updateNoteMetadata(noteId, current) { document ->\n            document.copy(\n                folderId = restoredFolderId,\n                trashedAt = null,\n                updatedAt = System.currentTimeMillis(),\n                revision = document.revision + 1,\n            )\n        }\n    }\n\n    suspend fun renameFolder(folderId: String, name: String, current: LibraryIndex): LibraryIndex =\n        withContext(Dispatchers.IO) {\n            ioMutex.withLock {\n                val normalized = name.trim().ifBlank { \"New folder\" }\n                current.copy(\n                    folders = current.folders.map { folder ->\n                        if (folder.id == folderId) {\n                            folder.copy(name = normalized, updatedAt = System.currentTimeMillis())\n                        } else folder\n                    },\n                ).also(::writeLibrary)\n            }\n        }\n\n    suspend fun moveFolder(folderId: String, parentId: String?, current: LibraryIndex): LibraryIndex =\n        withContext(Dispatchers.IO) {\n            ioMutex.withLock {\n                val forbidden = descendantFolderIds(folderId, current.folders) + folderId\n                require(parentId !in forbidden) { \"A folder cannot be moved into itself or a descendant\" }\n                current.copy(\n                    folders = current.folders.map { folder ->\n                        if (folder.id == folderId) {\n                            folder.copy(parentId = parentId, updatedAt = System.currentTimeMillis())\n                        } else folder\n                    },\n                ).also(::writeLibrary)\n            }\n        }\n\n    suspend fun trashFolder(folderId: String, current: LibraryIndex): LibraryIndex =\n        withContext(Dispatchers.IO) {\n            ioMutex.withLock {\n                current.copy(\n                    folders = current.folders.map { folder ->\n                        if (folder.id == folderId) {\n                            folder.copy(trashedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())\n                        } else folder\n                    },\n                ).also(::writeLibrary)\n            }\n        }\n\n    suspend fun restoreFolder(folderId: String, current: LibraryIndex): LibraryIndex =\n        withContext(Dispatchers.IO) {\n            ioMutex.withLock {\n                val target = current.folders.firstOrNull { it.id == folderId } ?: return@withLock current\n                val restoredParentId = target.parentId?.takeIf { parentId ->\n                    current.folders.any { it.id == parentId && it.trashedAt == null }\n                }\n                current.copy(\n                    folders = current.folders.map { folder ->\n                        if (folder.id == folderId) {\n                            folder.copy(\n                                parentId = restoredParentId,\n                                trashedAt = null,\n                                updatedAt = System.currentTimeMillis(),\n                            )\n                        } else folder\n                    },\n                ).also(::writeLibrary)\n            }\n        }\n\n    suspend fun deleteFolder(folderId: String, current: LibraryIndex): LibraryIndex =\n        withContext(Dispatchers.IO) {\n            ioMutex.withLock {\n                val folderIds = descendantFolderIds(folderId, current.folders) + folderId\n                val noteIds = current.notes.filter { it.folderId in folderIds }.map { it.id }.toSet()\n                noteIds.forEach(::deleteNoteFiles)\n                current.copy(\n                    folders = current.folders.filterNot { it.id in folderIds },\n                    notes = current.notes.filterNot { it.id in noteIds },\n                ).also(::writeLibrary)\n            }\n        }\n\n    suspend fun emptyTrash(current: LibraryIndex): LibraryIndex = withContext(Dispatchers.IO) {\n        ioMutex.withLock {\n            val directlyTrashedFolders = current.folders.filter { it.trashedAt != null }.map { it.id }\n            val folderIds = directlyTrashedFolders.flatMapTo(mutableSetOf()) { rootId ->\n                descendantFolderIds(rootId, current.folders) + rootId\n            }\n            val noteIds = current.notes.filter { note ->\n                note.trashedAt != null || note.folderId in folderIds\n            }.map { it.id }.toSet()\n            noteIds.forEach(::deleteNoteFiles)\n            current.copy(\n                folders = current.folders.filterNot { it.id in folderIds },\n                notes = current.notes.filterNot { it.id in noteIds },\n            ).also(::writeLibrary)\n        }\n    }\n",
)
replace_once(
    repository,
    "    private fun writeLibrary(index: LibraryIndex) {",
    "    private suspend fun updateNoteMetadata(\n        noteId: String,\n        current: LibraryIndex,\n        transform: (NoteDocument) -> NoteDocument,\n    ): LibraryIndex = withContext(Dispatchers.IO) {\n        ioMutex.withLock {\n            val updated = rewriteManifest(noteId, transform)\n            val summary = summaryFor(updated, thumbnailFile(noteId).takeIf { it.isFile })\n            current.copy(\n                notes = current.notes.filterNot { it.id == noteId } + summary,\n            ).also(::writeLibrary)\n        }\n    }\n\n    private fun rewriteManifest(\n        noteId: String,\n        transform: (NoteDocument) -> NoteDocument,\n    ): NoteDocument {\n        val source = noteFile(noteId)\n        require(source.isFile) { \"Notebook not found: $noteId\" }\n        val temp = File(source.parentFile, \"${source.name}.metadata.tmp\")\n        var updatedDocument: NoteDocument? = null\n        ZipInputStream(BufferedInputStream(FileInputStream(source))).use { input ->\n            ZipOutputStream(BufferedOutputStream(FileOutputStream(temp))).use { output ->\n                while (true) {\n                    val entry = input.nextEntry ?: break\n                    val bytes = input.readBytes()\n                    output.putNextEntry(ZipEntry(entry.name).apply { time = entry.time })\n                    if (entry.name == \"manifest.json\") {\n                        val currentDocument = json.decodeFromString<NoteDocument>(bytes.decodeToString())\n                        val nextDocument = transform(currentDocument)\n                        updatedDocument = nextDocument\n                        output.write(json.encodeToString(nextDocument).toByteArray())\n                    } else {\n                        output.write(bytes)\n                    }\n                    output.closeEntry()\n                    input.closeEntry()\n                }\n            }\n        }\n        val updated = requireNotNull(updatedDocument) { \"Invalid .atnote: manifest.json missing\" }\n        if (!temp.renameTo(source)) {\n            temp.copyTo(source, overwrite = true)\n            temp.delete()\n        }\n        return updated\n    }\n\n    private fun descendantFolderIds(rootId: String, folders: List<FolderRecord>): Set<String> {\n        val descendants = mutableSetOf<String>()\n        val pending = ArrayDeque<String>()\n        pending.add(rootId)\n        while (pending.isNotEmpty()) {\n            val parent = pending.removeFirst()\n            folders.filter { it.parentId == parent }.forEach { child ->\n                if (descendants.add(child.id)) pending.add(child.id)\n            }\n        }\n        return descendants\n    }\n\n    private fun deleteNoteFiles(noteId: String) {\n        noteFile(noteId).delete()\n        thumbnailFile(noteId).delete()\n        File(pdfCacheDir, \"$noteId.pdf\").delete()\n        sessionImageDir(noteId).deleteRecursively()\n    }\n\n    private fun writeLibrary(index: LibraryIndex) {",
)
replace_once(
    repository,
    "        folderId = document.folderId,\n        updatedAt = document.updatedAt,",
    "        folderId = document.folderId,\n        trashedAt = document.trashedAt,\n        updatedAt = document.updatedAt,",
)

view_model = Path("app/src/main/java/com/atuy/note/MainViewModel.kt")
replace_once(
    view_model,
    "    var currentFolderId by mutableStateOf<String?>(null)\n        private set\n    var toolMode",
    "    var currentFolderId by mutableStateOf<String?>(null)\n        private set\n    var showingTrash by mutableStateOf(false)\n        private set\n    var toolMode",
)
replace_regex_once(
    view_model,
    r"    val childFolders: List<FolderRecord>\n        get\(\) = .*?\n\n    val visibleNotes: List<NoteSummary>\n        get\(\) = .*?\n\n    fun clearStatus",
    "    private val effectivelyTrashedFolderIds: Set<String>\n        get() {\n            val trashed = library.folders.filter { it.trashedAt != null }.mapTo(mutableSetOf()) { it.id }\n            var changed = true\n            while (changed) {\n                changed = false\n                library.folders.forEach { folder ->\n                    if (folder.parentId in trashed && trashed.add(folder.id)) changed = true\n                }\n            }\n            return trashed\n        }\n\n    val activeFolders: List<FolderRecord>\n        get() {\n            val hidden = effectivelyTrashedFolderIds\n            return library.folders.filter { it.id !in hidden }.sortedBy { it.name.lowercase() }\n        }\n\n    val childFolders: List<FolderRecord>\n        get() = if (showingTrash) {\n            library.folders.filter { it.trashedAt != null }.sortedByDescending { it.trashedAt }\n        } else {\n            val hidden = effectivelyTrashedFolderIds\n            library.folders.filter { it.parentId == currentFolderId && it.id !in hidden }\n                .sortedBy { it.name.lowercase() }\n        }\n\n    val visibleNotes: List<NoteSummary>\n        get() = if (showingTrash) {\n            library.notes.filter { it.trashedAt != null }.sortedByDescending { it.trashedAt }\n        } else {\n            val hidden = effectivelyTrashedFolderIds\n            library.notes.filter { note ->\n                note.trashedAt == null && note.folderId == currentFolderId && note.folderId !in hidden\n            }.sortedByDescending { it.updatedAt }\n        }\n\n    val trashItemCount: Int\n        get() = library.notes.count { it.trashedAt != null } +\n            library.folders.count { it.trashedAt != null }\n\n    fun clearStatus",
)
replace_once(
    view_model,
    "    fun enterFolder(id: String?) { currentFolderId = id }\n    fun navigateUpFolder() { currentFolderId = currentFolder?.parentId }",
    "    fun enterFolder(id: String?) {\n        showingTrash = false\n        currentFolderId = id\n    }\n\n    fun showDocuments() {\n        showingTrash = false\n        currentFolderId = null\n    }\n\n    fun showTrash() {\n        showingTrash = true\n        currentFolderId = null\n    }\n\n    fun navigateUpFolder() { currentFolderId = currentFolder?.parentId }",
)
replace_once(
    view_model,
    "    fun showLibrary() { activeNoteId = null }",
    "    fun showLibrary() {\n        activeNoteId = null\n        showingTrash = false\n    }",
)
replace_once(
    view_model,
    "    fun setTool(mode: ToolMode) {",
    "    fun renameLibraryNote(noteId: String, name: String) {\n        viewModelScope.launch {\n            runBusy {\n                val session = openTabs.firstOrNull { it.id == noteId }\n                if (session?.dirty == true) saveNow(session)\n                saveJobs.remove(noteId)?.cancel()\n                library = repository.renameNote(noteId, name, library)\n                session?.let {\n                    it.title = name.trim().ifBlank { \"Untitled\" }\n                    it.updatedAt = System.currentTimeMillis()\n                    it.revision += 1\n                    it.dirty = false\n                }\n            }\n        }\n    }\n\n    fun moveLibraryNote(noteId: String, folderId: String?) {\n        viewModelScope.launch {\n            runBusy {\n                val session = openTabs.firstOrNull { it.id == noteId }\n                if (session?.dirty == true) saveNow(session)\n                saveJobs.remove(noteId)?.cancel()\n                library = repository.moveNote(noteId, folderId, library)\n                session?.let {\n                    it.folderId = folderId\n                    it.updatedAt = System.currentTimeMillis()\n                    it.revision += 1\n                    it.dirty = false\n                }\n            }\n        }\n    }\n\n    fun trashLibraryNote(noteId: String) {\n        viewModelScope.launch {\n            runBusy {\n                val session = openTabs.firstOrNull { it.id == noteId }\n                if (session?.dirty == true) saveNow(session)\n                saveJobs.remove(noteId)?.cancel()\n                library = repository.trashNote(noteId, library)\n                openTabs.removeAll { it.id == noteId }\n                if (activeNoteId == noteId) activeNoteId = null\n            }\n        }\n    }\n\n    fun restoreLibraryNote(noteId: String) {\n        viewModelScope.launch { runBusy { library = repository.restoreNote(noteId, library) } }\n    }\n\n    fun deleteLibraryNote(noteId: String) {\n        viewModelScope.launch {\n            runBusy {\n                saveJobs.remove(noteId)?.cancel()\n                library = repository.deleteNote(noteId, library)\n                openTabs.removeAll { it.id == noteId }\n                if (activeNoteId == noteId) activeNoteId = null\n            }\n        }\n    }\n\n    fun renameLibraryFolder(folderId: String, name: String) {\n        viewModelScope.launch { runBusy { library = repository.renameFolder(folderId, name, library) } }\n    }\n\n    fun moveLibraryFolder(folderId: String, parentId: String?) {\n        viewModelScope.launch { runBusy { library = repository.moveFolder(folderId, parentId, library) } }\n    }\n\n    fun trashLibraryFolder(folderId: String) {\n        viewModelScope.launch {\n            runBusy {\n                library = repository.trashFolder(folderId, library)\n                if (currentFolderId == folderId) currentFolderId = null\n            }\n        }\n    }\n\n    fun restoreLibraryFolder(folderId: String) {\n        viewModelScope.launch { runBusy { library = repository.restoreFolder(folderId, library) } }\n    }\n\n    fun deleteLibraryFolder(folderId: String) {\n        viewModelScope.launch {\n            runBusy {\n                val folderIds = descendantFolderIds(folderId) + folderId\n                val noteIds = library.notes.filter { it.folderId in folderIds }.map { it.id }.toSet()\n                noteIds.forEach { saveJobs.remove(it)?.cancel() }\n                openTabs.removeAll { it.id in noteIds }\n                if (activeNoteId in noteIds) activeNoteId = null\n                library = repository.deleteFolder(folderId, library)\n                if (currentFolderId in folderIds) currentFolderId = null\n            }\n        }\n    }\n\n    fun emptyTrash() {\n        viewModelScope.launch {\n            runBusy {\n                val trashedFolders = library.folders.filter { it.trashedAt != null }.map { it.id }\n                val folderIds = trashedFolders.flatMapTo(mutableSetOf()) { descendantFolderIds(it) + it }\n                val noteIds = library.notes.filter { it.trashedAt != null || it.folderId in folderIds }\n                    .map { it.id }.toSet()\n                noteIds.forEach { saveJobs.remove(it)?.cancel() }\n                openTabs.removeAll { it.id in noteIds }\n                if (activeNoteId in noteIds) activeNoteId = null\n                library = repository.emptyTrash(library)\n            }\n        }\n    }\n\n    fun moveTargetsForFolder(folderId: String): List<FolderRecord> {\n        val forbidden = descendantFolderIds(folderId) + folderId\n        return activeFolders.filter { it.id !in forbidden }\n    }\n\n    private fun descendantFolderIds(folderId: String): Set<String> {\n        val result = mutableSetOf<String>()\n        val pending = ArrayDeque<String>()\n        pending.add(folderId)\n        while (pending.isNotEmpty()) {\n            val parent = pending.removeFirst()\n            library.folders.filter { it.parentId == parent }.forEach { child ->\n                if (result.add(child.id)) pending.add(child.id)\n            }\n        }\n        return result\n    }\n\n    fun setTool(mode: ToolMode) {",
)

ink = Path("app/src/main/java/com/atuy/note/ink/InkPageView.kt")
replace_once(
    ink,
    "    private var circleHoldGestureActive = false\n    private var circleHoldCancelled = false\n    private var lassoOutline: List<PointF> = emptyList()",
    "    private var circleHoldGestureActive = false\n    private var circleHoldCancelled = false\n    private var circleHoldConverted = false\n    private var lassoOutline: List<PointF> = emptyList()",
)
replace_once(
    ink,
    "    private var onCircleHoldLasso: (String, Stroke) -> Unit = { _, _ -> }\n    private var onSelectedTransformStart",
    "    private var onCircleCandidateReady: () -> Unit = {}\n    private var onCircleHoldLasso: (String, Stroke) -> Unit = { _, _ -> }\n    private var onSelectedTransformStart",
)
replace_once(
    ink,
    "    private var selectedDragActive = false\n    private var selectedDragStartX = 0f",
    "    private var selectedDragActive = false\n    private var selectedDragMoved = false\n    private var selectedDragStartX = 0f",
)
replace_once(
    ink,
    "                        circleLassoCandidate = if (\n                            circleToLassoEnabledProvider() && looksLikeClosedLoop(runtime)\n                        ) {\n                            CircleLassoCandidate(runtime.stored.id, stroke, runtime.samples)\n                        } else {\n                            null\n                        }",
    "                        val candidate = if (\n                            circleToLassoEnabledProvider() && looksLikeClosedLoop(runtime)\n                        ) {\n                            CircleLassoCandidate(runtime.stored.id, stroke, runtime.samples)\n                        } else {\n                            null\n                        }\n                        circleLassoCandidate = candidate\n                        if (candidate != null) onCircleCandidateReady()",
)
replace_once(
    ink,
    "        onLassoFinished: (Stroke) -> Unit,\n        onCircleHoldLasso: (String, Stroke) -> Unit,",
    "        onLassoFinished: (Stroke) -> Unit,\n        onCircleCandidateReady: () -> Unit = {},\n        onCircleHoldLasso: (String, Stroke) -> Unit,",
)
replace_once(
    ink,
    "        this.onLassoFinished = onLassoFinished\n        this.onCircleHoldLasso = onCircleHoldLasso",
    "        this.onLassoFinished = onLassoFinished\n        this.onCircleCandidateReady = onCircleCandidateReady\n        this.onCircleHoldLasso = onCircleHoldLasso",
)
replace_once(
    ink,
    "                if (currentPage.isPointInsideStrokeSelection(point.x, point.y) && onSelectedTransformStart()) {\n                    lassoOutline = emptyList()\n                    selectedDragActive = true\n                    selectedDragStartX = point.x\n                    selectedDragStartY = point.y",
    "                if (currentPage.isPointInsideStrokeSelection(point.x, point.y) && onSelectedTransformStart()) {\n                    selectedDragActive = true\n                    selectedDragMoved = false\n                    selectedDragStartX = point.x\n                    selectedDragStartY = point.y",
)
replace_once(
    ink,
    "                if (selectedDragActive) {\n                    onSelectedMove(point.x - selectedDragStartX, point.y - selectedDragStartY)\n                    dryView.invalidate()",
    "                if (selectedDragActive) {\n                    val dx = point.x - selectedDragStartX\n                    val dy = point.y - selectedDragStartY\n                    if (!selectedDragMoved && hypot(dx, dy) > SELECTED_DRAG_SLOP / viewport.zoom) {\n                        selectedDragMoved = true\n                        lassoOutline = emptyList()\n                    }\n                    onSelectedMove(dx, dy)\n                    dryView.invalidate()",
)
replace_once(
    ink,
    "                    selectedDragActive = false\n                    onSelectedTransformEnd()",
    "                    selectedDragActive = false\n                    selectedDragMoved = false\n                    onSelectedTransformEnd()",
)
replace_once(
    ink,
    "                    selectedDragActive = false\n                    onSelectedTransformCancel()",
    "                    selectedDragActive = false\n                    selectedDragMoved = false\n                    onSelectedTransformCancel()",
)
replace_regex_once(
    ink,
    r"    private fun handleCircleLassoHold\(event: MotionEvent, pointerIndex: Int\): Boolean \{.*?\n    private fun looksLikeClosedLoop",
    '''    private fun handleCircleLassoHold(event: MotionEvent, pointerIndex: Int): Boolean {
        if (circleHoldGestureActive) {
            val pointerId = circleHoldPointerId
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val index = pointerId?.let { event.findPointerIndex(it) } ?: -1
                    if (index >= 0) {
                        val point = mapViewToWorld(event.getX(index), event.getY(index))
                        if (circleHoldConverted) {
                            if (selectedDragActive) {
                                val dx = point.x - selectedDragStartX
                                val dy = point.y - selectedDragStartY
                                if (!selectedDragMoved &&
                                    hypot(dx, dy) > SELECTED_DRAG_SLOP / viewport.zoom
                                ) {
                                    selectedDragMoved = true
                                    lassoOutline = emptyList()
                                }
                                onSelectedMove(dx, dy)
                                dryView.invalidate()
                            }
                        } else if (!circleHoldCancelled) {
                            val distance = hypot(point.x - circleHoldStartX, point.y - circleHoldStartY)
                            if (distance > CIRCLE_HOLD_SLOP / viewport.zoom) {
                                circleHoldCancelled = true
                                circleHoldRunnable?.let(::removeCallbacks)
                                circleHoldRunnable = null
                            }
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    if (circleHoldConverted && selectedDragActive) onSelectedTransformEnd()
                    selectedDragActive = false
                    selectedDragMoved = false
                    cancelPendingCircleLasso()
                    releaseParentIntercept()
                    dryView.invalidate()
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (circleHoldConverted && selectedDragActive) onSelectedTransformCancel()
                    selectedDragActive = false
                    selectedDragMoved = false
                    cancelPendingCircleLasso()
                    releaseParentIntercept()
                    dryView.invalidate()
                }
            }
            return true
        }

        if (event.actionMasked != MotionEvent.ACTION_DOWN &&
            event.actionMasked != MotionEvent.ACTION_POINTER_DOWN
        ) return false
        if (!circleToLassoEnabledProvider() || toolProvider() != ToolMode.PEN) return false

        val candidate = circleLassoCandidate ?: return false
        val point = mapViewToWorld(event.getX(pointerIndex), event.getY(pointerIndex))
        if (!isPointOnClosedLoop(point.x, point.y, candidate.samples)) return false

        requestDisallowInterceptTouchEvent(true)
        onActivated()
        circleHoldGestureActive = true
        circleHoldCancelled = false
        circleHoldConverted = false
        circleHoldPointerId = event.getPointerId(pointerIndex)
        circleHoldStartX = point.x
        circleHoldStartY = point.y
        val runnable = Runnable {
            if (!circleHoldGestureActive || circleHoldCancelled) return@Runnable
            val current = circleLassoCandidate ?: return@Runnable
            circleLassoCandidate = null
            lassoOutline = current.samples.map { PointF(it.x, it.y) }
            onCircleHoldLasso(current.strokeId, current.stroke)
            circleHoldConverted = true
            selectedDragActive = onSelectedTransformStart()
            selectedDragMoved = false
            selectedDragStartX = circleHoldStartX
            selectedDragStartY = circleHoldStartY
            dryView.postInvalidateOnAnimation()
        }
        circleHoldRunnable = runnable
        postDelayed(runnable, CIRCLE_HOLD_DELAY_MS)
        return true
    }

    private fun cancelPendingCircleLasso() {
        circleHoldRunnable?.let(::removeCallbacks)
        circleHoldRunnable = null
        circleHoldPointerId = null
        circleHoldGestureActive = false
        circleHoldCancelled = false
        circleHoldConverted = false
    }

    private fun isPointOnClosedLoop(x: Float, y: Float, samples: List<InkSample>): Boolean {
        if (samples.size < 3) return false
        val tolerance = CIRCLE_HOLD_HIT_RADIUS / viewport.zoom
        val toleranceSquared = tolerance * tolerance
        val segments = samples.zipWithNext() + listOf(samples.last() to samples.first())
        return segments.any { (a, b) ->
            squaredDistancePointToSegment(x, y, a.x, a.y, b.x, b.y) <= toleranceSquared
        }
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
        if (denominator <= 0.000001f) {
            val pointDx = px - ax
            val pointDy = py - ay
            return pointDx * pointDx + pointDy * pointDy
        }
        val t = (((px - ax) * dx + (py - ay) * dy) / denominator).coerceIn(0f, 1f)
        val nearestX = ax + t * dx
        val nearestY = ay + t * dy
        val pointDx = px - nearestX
        val pointDy = py - nearestY
        return pointDx * pointDx + pointDy * pointDy
    }

    private fun looksLikeClosedLoop''',
)
replace_once(
    ink,
    "        const val CIRCLE_HOLD_SLOP = 18f\n        const val MIN_CIRCLE_DIAMETER",
    "        const val CIRCLE_HOLD_SLOP = 18f\n        const val CIRCLE_HOLD_HIT_RADIUS = 24f\n        const val SELECTED_DRAG_SLOP = 6f\n        const val MIN_CIRCLE_DIAMETER",
)

ui = Path("app/src/main/java/com/atuy/note/ui/EnhancedNoteApp.kt")
for old in (
    "import android.os.SystemClock\n",
    "import androidx.compose.ui.geometry.Offset\n",
    "import kotlinx.coroutines.Job\n",
    "import kotlinx.coroutines.coroutineScope\n",
    "import kotlinx.coroutines.delay\n",
    "import kotlinx.coroutines.launch\n",
):
    ui.write_text(ui.read_text().replace(old, ""))
replace_once(ui, "import androidx.compose.foundation.layout.height\n", "import androidx.compose.foundation.layout.height\nimport androidx.compose.foundation.layout.heightIn\n")
replace_once(ui, "import androidx.compose.material.icons.filled.DeleteOutline\n", "import androidx.compose.material.icons.filled.DeleteForever\nimport androidx.compose.material.icons.filled.DeleteOutline\n")
replace_once(ui, "import androidx.compose.material.icons.filled.Description\n", "import androidx.compose.material.icons.filled.Description\nimport androidx.compose.material.icons.filled.DriveFileMove\n")
replace_once(ui, "import androidx.compose.material.icons.filled.MoreHoriz\n", "import androidx.compose.material.icons.filled.MoreHoriz\nimport androidx.compose.material.icons.filled.MoreVert\n")
replace_once(ui, "import androidx.compose.material.icons.filled.PictureAsPdf\n", "import androidx.compose.material.icons.filled.PictureAsPdf\nimport androidx.compose.material.icons.filled.RestoreFromTrash\n")
replace_once(ui, "import androidx.compose.material3.CircularProgressIndicator\n", "import androidx.compose.material3.CircularProgressIndicator\nimport androidx.compose.material3.DropdownMenu\nimport androidx.compose.material3.DropdownMenuItem\n")

home_section = r'''@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    viewModel: MainViewModel,
    onImportPdf: () -> Unit,
    onSyncDrive: () -> Unit,
    onOpenSettings: () -> Unit,
    snackbar: SnackbarHostState,
) {
    var createNote by remember { mutableStateOf(false) }
    var createFolder by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<LibraryTarget?>(null) }
    var moveTarget by remember { mutableStateOf<LibraryTarget?>(null) }
    var trashTarget by remember { mutableStateOf<LibraryTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<LibraryTarget?>(null) }
    var emptyTrash by remember { mutableStateOf(false) }

    val normalizedQuery = query.trim()
    val folders = viewModel.childFolders.filter {
        normalizedQuery.isBlank() || it.name.contains(normalizedQuery, ignoreCase = true)
    }
    val notes = viewModel.visibleNotes.filter {
        normalizedQuery.isBlank() || it.title.contains(normalizedQuery, ignoreCase = true)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                when {
                                    viewModel.showingTrash -> "ゴミ箱"
                                    viewModel.currentFolder != null -> viewModel.currentFolder!!.name
                                    else -> "書類"
                                },
                            )
                            Text(
                                if (viewModel.showingTrash) {
                                    "${viewModel.trashItemCount}件"
                                } else {
                                    "${viewModel.visibleNotes.size}件のノート"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        if (!viewModel.showingTrash && viewModel.currentFolderId != null) {
                            IconButton(onClick = viewModel::navigateUpFolder) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "親フォルダー")
                            }
                        }
                    },
                    actions = {
                        if (viewModel.showingTrash) {
                            IconButton(
                                enabled = viewModel.trashItemCount > 0,
                                onClick = { emptyTrash = true },
                            ) {
                                Icon(Icons.Default.DeleteForever, "ゴミ箱を空にする")
                            }
                            IconButton(onClick = viewModel::showDocuments) {
                                Icon(Icons.Default.Home, "書類へ戻る")
                            }
                        } else {
                            IconButton(onClick = onSyncDrive) {
                                Icon(Icons.Default.CloudSync, "Google Driveと同期")
                            }
                            IconButton(onClick = onImportPdf) {
                                Icon(Icons.Default.PictureAsPdf, "PDFを読み込む")
                            }
                            IconButton(onClick = { createFolder = true }) {
                                Icon(Icons.Default.CreateNewFolder, "フォルダーを作成")
                            }
                            IconButton(onClick = { createNote = true }) {
                                Icon(Icons.Default.Add, "ノートを作成")
                            }
                            IconButton(onClick = viewModel::showTrash) {
                                Icon(Icons.Default.DeleteOutline, "ゴミ箱")
                            }
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, "設定")
                        }
                    },
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = {
                        Text(if (viewModel.showingTrash) "ゴミ箱を検索" else "ノートとフォルダーを検索")
                    },
                )
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            if (maxWidth >= 840.dp) {
                Row(Modifier.fillMaxSize()) {
                    HomeFolderSidebar(
                        folders = viewModel.activeFolders,
                        selected = viewModel.currentFolderId,
                        showingTrash = viewModel.showingTrash,
                        onDocuments = viewModel::showDocuments,
                        onTrash = viewModel::showTrash,
                        onSelect = viewModel::enterFolder,
                    )
                    VerticalDivider()
                    HomeGrid(
                        folders = folders,
                        notes = notes,
                        showingTrash = viewModel.showingTrash,
                        onFolder = { viewModel.enterFolder(it.id) },
                        onNote = { viewModel.openNote(it.id) },
                        onRename = { renameTarget = it },
                        onMove = { moveTarget = it },
                        onTrash = { trashTarget = it },
                        onRestore = { target ->
                            when (target) {
                                is LibraryTarget.Note -> viewModel.restoreLibraryNote(target.value.id)
                                is LibraryTarget.Folder -> viewModel.restoreLibraryFolder(target.value.id)
                            }
                        },
                        onDelete = { deleteTarget = it },
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                HomeGrid(
                    folders = folders,
                    notes = notes,
                    showingTrash = viewModel.showingTrash,
                    onFolder = { viewModel.enterFolder(it.id) },
                    onNote = { viewModel.openNote(it.id) },
                    onRename = { renameTarget = it },
                    onMove = { moveTarget = it },
                    onTrash = { trashTarget = it },
                    onRestore = { target ->
                        when (target) {
                            is LibraryTarget.Note -> viewModel.restoreLibraryNote(target.value.id)
                            is LibraryTarget.Folder -> viewModel.restoreLibraryFolder(target.value.id)
                        }
                    },
                    onDelete = { deleteTarget = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (createNote) {
        NameDialog("新しいノート", "名称未設定のノート", onDismiss = { createNote = false }) {
            createNote = false
            viewModel.createBlankNote(it)
        }
    }
    if (createFolder) {
        NameDialog("新しいフォルダー", "新しいフォルダー", onDismiss = { createFolder = false }) {
            createFolder = false
            viewModel.createFolder(it)
        }
    }

    renameTarget?.let { target ->
        NameDialog("名前を変更", target.label, onDismiss = { renameTarget = null }) { name ->
            renameTarget = null
            when (target) {
                is LibraryTarget.Note -> viewModel.renameLibraryNote(target.value.id, name)
                is LibraryTarget.Folder -> viewModel.renameLibraryFolder(target.value.id, name)
            }
        }
    }

    moveTarget?.let { target ->
        val destinations = when (target) {
            is LibraryTarget.Note -> viewModel.activeFolders
            is LibraryTarget.Folder -> viewModel.moveTargetsForFolder(target.value.id)
        }
        val currentDestination = when (target) {
            is LibraryTarget.Note -> target.value.folderId
            is LibraryTarget.Folder -> target.value.parentId
        }
        MoveDialog(
            itemName = target.label,
            folders = destinations,
            initialFolderId = currentDestination,
            onDismiss = { moveTarget = null },
        ) { folderId ->
            moveTarget = null
            when (target) {
                is LibraryTarget.Note -> viewModel.moveLibraryNote(target.value.id, folderId)
                is LibraryTarget.Folder -> viewModel.moveLibraryFolder(target.value.id, folderId)
            }
        }
    }

    trashTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { trashTarget = null },
            title = { Text("ゴミ箱へ移動") },
            text = {
                Text(
                    if (target is LibraryTarget.Folder) {
                        "「${target.label}」と中の項目をゴミ箱へ移動します。"
                    } else {
                        "「${target.label}」をゴミ箱へ移動します。"
                    },
                )
            },
            confirmButton = {
                Button(onClick = {
                    trashTarget = null
                    when (target) {
                        is LibraryTarget.Note -> viewModel.trashLibraryNote(target.value.id)
                        is LibraryTarget.Folder -> viewModel.trashLibraryFolder(target.value.id)
                    }
                }) { Text("移動") }
            },
            dismissButton = {
                TextButton(onClick = { trashTarget = null }) { Text("キャンセル") }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("完全に削除") },
            text = {
                Text(
                    if (target is LibraryTarget.Folder) {
                        "「${target.label}」と中の項目を完全に削除します。元に戻せません。"
                    } else {
                        "「${target.label}」を完全に削除します。元に戻せません。"
                    },
                )
            },
            confirmButton = {
                Button(onClick = {
                    deleteTarget = null
                    when (target) {
                        is LibraryTarget.Note -> viewModel.deleteLibraryNote(target.value.id)
                        is LibraryTarget.Folder -> viewModel.deleteLibraryFolder(target.value.id)
                    }
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("キャンセル") }
            },
        )
    }

    if (emptyTrash) {
        AlertDialog(
            onDismissRequest = { emptyTrash = false },
            title = { Text("ゴミ箱を空にする") },
            text = { Text("ゴミ箱内のすべての項目を完全に削除します。元に戻せません。") },
            confirmButton = {
                Button(onClick = {
                    emptyTrash = false
                    viewModel.emptyTrash()
                }) { Text("すべて削除") }
            },
            dismissButton = {
                TextButton(onClick = { emptyTrash = false }) { Text("キャンセル") }
            },
        )
    }
}

private sealed interface LibraryTarget {
    val label: String

    data class Note(val value: NoteSummary) : LibraryTarget {
        override val label: String get() = value.title
    }

    data class Folder(val value: FolderRecord) : LibraryTarget {
        override val label: String get() = value.name
    }
}

@Composable
private fun HomeFolderSidebar(
    folders: List<FolderRecord>,
    selected: String?,
    showingTrash: Boolean,
    onDocuments: () -> Unit,
    onTrash: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    Surface(
        modifier = Modifier.width(240.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        LazyColumn(
            Modifier.fillMaxSize().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                SidebarItem("書類", Icons.Default.Home, !showingTrash && selected == null, onClick = onDocuments)
            }
            item {
                SidebarItem("ゴミ箱", Icons.Default.DeleteOutline, showingTrash, onClick = onTrash)
            }
            items(
                folders.sortedWith(
                    compareBy<FolderRecord> { it.parentId != null }.thenBy { it.name.lowercase() },
                ),
                key = { it.id },
            ) { folder ->
                SidebarItem(
                    folder.name,
                    Icons.Default.Folder,
                    !showingTrash && selected == folder.id,
                    indent = folder.parentId != null,
                ) { onSelect(folder.id) }
            }
        }
    }
}

@Composable
private fun SidebarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    indent: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(
                start = if (indent) 28.dp else 12.dp,
                top = 10.dp,
                bottom = 10.dp,
                end = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HomeGrid(
    folders: List<FolderRecord>,
    notes: List<NoteSummary>,
    showingTrash: Boolean,
    onFolder: (FolderRecord) -> Unit,
    onNote: (NoteSummary) -> Unit,
    onRename: (LibraryTarget) -> Unit,
    onMove: (LibraryTarget) -> Unit,
    onTrash: (LibraryTarget) -> Unit,
    onRestore: (LibraryTarget) -> Unit,
    onDelete: (LibraryTarget) -> Unit,
    modifier: Modifier,
) {
    if (folders.isEmpty() && notes.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    if (showingTrash) Icons.Default.DeleteOutline else Icons.Default.Description,
                    null,
                    Modifier.size(52.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(if (showingTrash) "ゴミ箱は空です" else "このフォルダーにはノートがありません")
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(170.dp),
        modifier = modifier,
        contentPadding = PaddingValues(18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(folders, key = { "folder-${it.id}" }) { folder ->
            val target = LibraryTarget.Folder(folder)
            Card(
                onClick = { if (!showingTrash) onFolder(folder) },
                enabled = !showingTrash,
                modifier = Modifier.fillMaxWidth().height(210.dp),
            ) {
                Box(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            null,
                            Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            folder.name,
                            Modifier.padding(12.dp),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    LibraryItemMenu(
                        showingTrash = showingTrash,
                        onRename = { onRename(target) },
                        onMove = { onMove(target) },
                        onTrash = { onTrash(target) },
                        onRestore = { onRestore(target) },
                        onDelete = { onDelete(target) },
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
            }
        }
        items(notes, key = { it.id }) { note ->
            val target = LibraryTarget.Note(note)
            val bitmap = remember(note.thumbnailPath, note.updatedAt) {
                note.thumbnailPath?.let(BitmapFactory::decodeFile)
            }
            Card(
                onClick = { if (!showingTrash) onNote(note) },
                enabled = !showingTrash,
                modifier = Modifier.fillMaxWidth().height(250.dp),
            ) {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        Box(
                            Modifier.fillMaxWidth().weight(1f).background(Color.White),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Icon(
                                    Icons.Default.Description,
                                    null,
                                    Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                note.title,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${note.pageCount}ページ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    LibraryItemMenu(
                        showingTrash = showingTrash,
                        onRename = { onRename(target) },
                        onMove = { onMove(target) },
                        onTrash = { onTrash(target) },
                        onRestore = { onRestore(target) },
                        onDelete = { onDelete(target) },
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryItemMenu(
    showingTrash: Boolean,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, "項目メニュー")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (showingTrash) {
                DropdownMenuItem(
                    text = { Text("元に戻す") },
                    leadingIcon = { Icon(Icons.Default.RestoreFromTrash, null) },
                    onClick = {
                        expanded = false
                        onRestore()
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { Text("名前を変更") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = {
                        expanded = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text("移動") },
                    leadingIcon = { Icon(Icons.Default.DriveFileMove, null) },
                    onClick = {
                        expanded = false
                        onMove()
                    },
                )
                DropdownMenuItem(
                    text = { Text("ゴミ箱へ移動") },
                    leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                    onClick = {
                        expanded = false
                        onTrash()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("完全に削除") },
                leadingIcon = { Icon(Icons.Default.DeleteForever, null) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun MoveDialog(
    itemName: String,
    folders: List<FolderRecord>,
    initialFolderId: String?,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var selectedFolderId by remember(itemName, initialFolderId) { mutableStateOf(initialFolderId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移動先を選択") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MoveDestinationRow("書類", selectedFolderId == null) { selectedFolderId = null }
                folders.forEach { folder ->
                    MoveDestinationRow(folder.name, selectedFolderId == folder.id) {
                        selectedFolderId = folder.id
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedFolderId) }) { Text("移動") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}

@Composable
private fun MoveDestinationRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            if (selected) Icons.Default.CheckCircle else Icons.Default.Folder,
            null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private enum class EditorPanel'''
replace_regex_once(
    ui,
    r"@OptIn\(ExperimentalMaterial3Api::class\)\n@Composable\nprivate fun HomeScreen\(.*?\nprivate enum class EditorPanel",
    home_section,
)

toolbar_section = r'''@Composable
private fun EditorCommandBar(
    viewModel: MainViewModel,
    vertical: Boolean,
    readOnly: Boolean,
    showPages: Boolean,
    activePanel: EditorPanel?,
    onTogglePages: () -> Unit,
    onToggleReadOnly: () -> Unit,
    onPanel: (EditorPanel) -> Unit,
) {
    val details = CommandSpec(Icons.Default.MoreHoriz, "詳細", activePanel == EditorPanel.DETAILS) {
        onPanel(EditorPanel.DETAILS)
    }
    val share = CommandSpec(Icons.Default.Share, "共有", false) {
        viewModel.saveActive()
        viewModel.reportStatus("ノートを保存しました")
    }
    val addPage = CommandSpec(
        Icons.AutoMirrored.Filled.NoteAdd,
        "ページ追加",
        false,
        viewModel::addPage,
    )
    val pen = CommandSpec(
        Icons.Default.Brush,
        "ペン",
        !readOnly && viewModel.toolMode == ToolMode.PEN &&
            viewModel.brushSpec.kind != BrushKind.HIGHLIGHTER,
    ) {
        if (!readOnly) {
            if (viewModel.brushSpec.kind == BrushKind.HIGHLIGHTER) {
                viewModel.setBrushKind(BrushKind.PRESSURE_PEN)
            } else {
                viewModel.setTool(ToolMode.PEN)
            }
            onPanel(EditorPanel.PEN)
        }
    }
    val eraser = CommandSpec(
        Icons.Default.DeleteOutline,
        "消しゴム",
        !readOnly && viewModel.toolMode == ToolMode.ERASER,
    ) {
        if (!readOnly) {
            viewModel.setTool(ToolMode.ERASER)
            onPanel(EditorPanel.ERASER)
        }
    }
    val text = CommandSpec(Icons.Default.TextFields, "テキスト", activePanel == EditorPanel.TEXT) {
        onPanel(EditorPanel.TEXT)
    }
    val sticker = CommandSpec(
        Icons.Default.EmojiEmotions,
        "ステッカー",
        activePanel == EditorPanel.STICKER,
    ) { onPanel(EditorPanel.STICKER) }
    val lasso = CommandSpec(
        Icons.Default.Gesture,
        "投げ縄",
        !readOnly && viewModel.toolMode == ToolMode.LASSO,
    ) {
        if (!readOnly) {
            viewModel.setTool(ToolMode.LASSO)
            onPanel(EditorPanel.LASSO)
        }
    }
    val image = CommandSpec(
        Icons.Default.Image,
        "画像",
        !readOnly && viewModel.toolMode == ToolMode.IMAGE,
    ) {
        if (!readOnly) {
            viewModel.setTool(ToolMode.IMAGE)
            onPanel(EditorPanel.IMAGE)
        }
    }
    val shape = CommandSpec(Icons.Default.Category, "シェイプ", activePanel == EditorPanel.SHAPE) {
        onPanel(EditorPanel.SHAPE)
    }
    val sticky = CommandSpec(
        Icons.AutoMirrored.Filled.StickyNote2,
        "付箋",
        activePanel == EditorPanel.STICKY,
    ) { onPanel(EditorPanel.STICKY) }
    val pointer = CommandSpec(Icons.Default.NearMe, "ポインタ", activePanel == EditorPanel.POINTER) {
        onPanel(EditorPanel.POINTER)
    }
    val voice = CommandSpec(Icons.Default.Mic, "音声", activePanel == EditorPanel.VOICE) {
        onPanel(EditorPanel.VOICE)
    }
    val readOnlyCommand = CommandSpec(Icons.Default.Visibility, "閲覧専用", readOnly, onToggleReadOnly)
    val ai = CommandSpec(Icons.Default.AutoAwesome, "AI", activePanel == EditorPanel.AI) {
        onPanel(EditorPanel.AI)
    }
    val search = CommandSpec(Icons.Default.Search, "検索", activePanel == EditorPanel.SEARCH) {
        onPanel(EditorPanel.SEARCH)
    }
    val pages = CommandSpec(Icons.Default.Menu, "ページ一覧", showPages, onTogglePages)

    val detailBlock = listOf(details, share, addPage)
    val toolsBeforeLasso = listOf(pen, eraser, text, sticker)
    val toolsAfterLasso = listOf(image, shape, sticky, pointer, voice)
    val pageBlock = listOf(readOnlyCommand, ai, search, pages)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        BoxWithConstraints {
            if (vertical) {
                if (maxHeight >= 760.dp) {
                    Column(
                        Modifier.width(64.dp).fillMaxHeight().padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CommandColumn(detailBlock)
                        Spacer(Modifier.weight(1f))
                        CommandColumn(toolsBeforeLasso)
                        CommandButton(lasso)
                        CommandColumn(toolsAfterLasso)
                        Spacer(Modifier.weight(1f))
                        CommandColumn(pageBlock)
                    }
                } else {
                    Column(
                        Modifier.width(64.dp).fillMaxHeight().verticalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CommandColumn(detailBlock)
                        Spacer(Modifier.height(14.dp))
                        CommandColumn(toolsBeforeLasso)
                        CommandButton(lasso)
                        CommandColumn(toolsAfterLasso)
                        Spacer(Modifier.height(14.dp))
                        CommandColumn(pageBlock)
                    }
                }
            } else {
                if (maxWidth >= 1120.dp) {
                    Row(
                        Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CommandRow(detailBlock)
                        Spacer(Modifier.weight(1f))
                        CommandRow(toolsBeforeLasso)
                        CommandButton(lasso)
                        CommandRow(toolsAfterLasso)
                        Spacer(Modifier.weight(1f))
                        CommandRow(pageBlock)
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().height(62.dp).horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CommandRow(detailBlock)
                        Spacer(Modifier.width(14.dp))
                        CommandRow(toolsBeforeLasso)
                        CommandButton(lasso)
                        CommandRow(toolsAfterLasso)
                        Spacer(Modifier.width(14.dp))
                        CommandRow(pageBlock)
                    }
                }
            }
        }
    }
}

private data class CommandSpec(
    val icon: ImageVector,
    val description: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun CommandRow(commands: List<CommandSpec>) {
    commands.forEach { CommandButton(it) }
}

@Composable
private fun CommandColumn(commands: List<CommandSpec>) {
    commands.forEach { CommandButton(it) }
}

@Composable
private fun CommandButton(spec: CommandSpec) {
    Surface(
        modifier = Modifier.padding(2.dp),
        shape = CircleShape,
        color = if (spec.selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
    ) {
        IconButton(onClick = spec.onClick) {
            Icon(spec.icon, spec.description)
        }
    }
}

@Composable
private fun EditorDetailPanel'''
replace_regex_once(
    ui,
    r"@Composable\nprivate fun EditorCommandBar\(.*?\n@Composable\nprivate fun EditorDetailPanel",
    toolbar_section,
)
replace_once(
    ui,
    "            \"投げ縄はスタイラスで自由な形に囲めます。線を離すと始点と終点を結んで選択範囲を閉じます。\",",
    "            \"通常の投げ縄は投げ縄ツールで囲みます。ペンの囲みを変換する場合は、閉じた線を描いてペンを離し、その線上を長押ししてください。長押ししたまま動かすと選択範囲を移動でき、囲み線は移動開始時に消えます。\",",
)
replace_once(
    ui,
    "            Text(\"囲ってペンを離した後、囲みの内側をもう一度長押し\", Modifier.padding(horizontal = 8.dp).weight(1f))",
    "            Text(\"ペンで囲む → 離す → 囲み線上を長押し\", Modifier.padding(horizontal = 8.dp).weight(1f))",
)
replace_once(
    ui,
    "    var circleHoldQualified by remember(page.id) { mutableStateOf(false) }\n\n    Card(",
    "    Card(",
)
replace_regex_once(
    ui,
    r"        Box\(\n            Modifier\.fillMaxWidth\(\)\n                \.aspectRatio\(page\.width / page\.height\)\n                \.circleHoldQualifier\(.*?\n                \),\n        \) \{",
    "        Box(\n            Modifier.fillMaxWidth().aspectRatio(page.width / page.height),\n        ) {",
)
replace_once(
    ui,
    "                        circleToLassoEnabledProvider = { false },\n                        readOnlyProvider = { readOnly },",
    "                        circleToLassoEnabledProvider = { viewModel.circleToLassoEnabled },\n                        readOnlyProvider = { readOnly },",
)
replace_regex_once(
    ui,
    r"                        onStrokeAdded = \{ runtime ->\n                            viewModel\.addStroke\(page, runtime\)\n                            if \(circleHoldQualified\) \{.*?\n                            \}\n                        \},",
    "                        onStrokeAdded = { runtime -> viewModel.addStroke(page, runtime) },",
)
replace_once(
    ui,
    "                        onLassoFinished = { viewModel.selectWithLasso(page, it) },\n                        onCircleHoldLasso = { _, _ -> },",
    "                        onLassoFinished = { viewModel.selectWithLasso(page, it) },\n                        onCircleCandidateReady = {\n                            viewModel.reportStatus(\"囲み線を長押しすると投げ縄に変換できます\")\n                        },\n                        onCircleHoldLasso = { strokeId, stroke ->\n                            viewModel.convertCircleStrokeToLasso(page, strokeId, stroke)\n                        },",
)
replace_regex_once(
    ui,
    r"\nprivate fun Modifier\.circleHoldQualifier\(.*?\n@Composable\nprivate fun ColorSwatch",
    "\n@Composable\nprivate fun ColorSwatch",
)
ui.write_text(ui.read_text().replace("private const val CIRCLE_HOLD_DELAY_MS = 700L\n", ""))
