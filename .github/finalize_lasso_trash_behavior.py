from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one match in {path} for {old[:160]!r}, found {count}")
    path.write_text(text.replace(old, new, 1))


view_model = Path("app/src/main/java/com/atuy/note/MainViewModel.kt")
replace_once(
    view_model,
    "    val childFolders: List<FolderRecord>\n        get() = if (showingTrash) {\n            library.folders.filter { it.trashedAt != null }.sortedByDescending { it.trashedAt }\n        } else {",
    "    val childFolders: List<FolderRecord>\n        get() = if (showingTrash) {\n            library.folders.filter { folder ->\n                folder.trashedAt != null && !hasTrashedAncestor(folder)\n            }.sortedByDescending { it.trashedAt }\n        } else {",
)
replace_once(
    view_model,
    "    val visibleNotes: List<NoteSummary>\n        get() = if (showingTrash) {\n            library.notes.filter { it.trashedAt != null }.sortedByDescending { it.trashedAt }\n        } else {",
    "    val visibleNotes: List<NoteSummary>\n        get() = if (showingTrash) {\n            val hidden = effectivelyTrashedFolderIds\n            library.notes.filter { note ->\n                note.trashedAt != null && note.folderId !in hidden\n            }.sortedByDescending { it.trashedAt }\n        } else {",
)
replace_once(
    view_model,
    "    val trashItemCount: Int\n        get() = library.notes.count { it.trashedAt != null } +\n            library.folders.count { it.trashedAt != null }\n\n    fun clearStatus()",
    "    val trashItemCount: Int\n        get() {\n            val hidden = effectivelyTrashedFolderIds\n            val noteCount = library.notes.count { note ->\n                note.trashedAt != null && note.folderId !in hidden\n            }\n            val folderCount = library.folders.count { folder ->\n                folder.trashedAt != null && !hasTrashedAncestor(folder)\n            }\n            return noteCount + folderCount\n        }\n\n    private fun hasTrashedAncestor(folder: FolderRecord): Boolean {\n        val byId = library.folders.associateBy { it.id }\n        val visited = mutableSetOf<String>()\n        var parentId = folder.parentId\n        while (parentId != null) {\n            if (!visited.add(parentId)) return true\n            val parent = byId[parentId] ?: return false\n            if (parent.trashedAt != null) return true\n            parentId = parent.parentId\n        }\n        return false\n    }\n\n    fun clearStatus()",
)

ink = Path("app/src/main/java/com/atuy/note/ink/InkPageView.kt")
old = """                    if (!selectedDragMoved && hypot(dx, dy) > SELECTED_DRAG_SLOP / viewport.zoom) {
                        selectedDragMoved = true
                        lassoOutline = emptyList()
                    }
                    onSelectedMove(dx, dy)
                    dryView.invalidate()"""
new = """                    if (!selectedDragMoved && hypot(dx, dy) > SELECTED_DRAG_SLOP / viewport.zoom) {
                        selectedDragMoved = true
                        lassoOutline = emptyList()
                    }
                    if (selectedDragMoved) onSelectedMove(dx, dy)
                    dryView.invalidate()"""
text = ink.read_text()
count = text.count(old)
if count != 1:
    raise SystemExit(f"Expected one normal lasso movement block, found {count}")
text = text.replace(old, new, 1)
old_hold = """                                if (!selectedDragMoved &&
                                    hypot(dx, dy) > SELECTED_DRAG_SLOP / viewport.zoom
                                ) {
                                    selectedDragMoved = true
                                    lassoOutline = emptyList()
                                }
                                onSelectedMove(dx, dy)
                                dryView.invalidate()"""
new_hold = """                                if (!selectedDragMoved &&
                                    hypot(dx, dy) > SELECTED_DRAG_SLOP / viewport.zoom
                                ) {
                                    selectedDragMoved = true
                                    lassoOutline = emptyList()
                                }
                                if (selectedDragMoved) onSelectedMove(dx, dy)
                                dryView.invalidate()"""
count = text.count(old_hold)
if count != 1:
    raise SystemExit(f"Expected one converted lasso movement block, found {count}")
ink.write_text(text.replace(old_hold, new_hold, 1))
