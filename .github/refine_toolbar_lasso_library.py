from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one match in {path} for {old[:160]!r}, found {count}")
    path.write_text(text.replace(old, new, 1))


repository = Path("app/src/main/java/com/atuy/note/data/NoteRepository.kt")
replace_once(
    repository,
    "        val restoredFolderId = summary.folderId?.takeIf { folderId ->\n            current.folders.any { it.id == folderId && it.trashedAt == null }\n        }",
    "        val restoredFolderId = summary.folderId?.takeIf { folderId ->\n            isFolderActive(folderId, current.folders)\n        }",
)
replace_once(
    repository,
    "                val restoredParentId = target.parentId?.takeIf { parentId ->\n                    current.folders.any { it.id == parentId && it.trashedAt == null }\n                }",
    "                val restoredParentId = target.parentId?.takeIf { parentId ->\n                    isFolderActive(parentId, current.folders)\n                }",
)
replace_once(
    repository,
    "    private fun descendantFolderIds(rootId: String, folders: List<FolderRecord>): Set<String> {",
    "    private fun isFolderActive(folderId: String, folders: List<FolderRecord>): Boolean {\n        val byId = folders.associateBy { it.id }\n        val visited = mutableSetOf<String>()\n        var currentId: String? = folderId\n        while (currentId != null) {\n            if (!visited.add(currentId)) return false\n            val folder = byId[currentId] ?: return false\n            if (folder.trashedAt != null) return false\n            currentId = folder.parentId\n        }\n        return true\n    }\n\n    private fun descendantFolderIds(rootId: String, folders: List<FolderRecord>): Set<String> {",
)

view_model = Path("app/src/main/java/com/atuy/note/MainViewModel.kt")
replace_once(
    view_model,
    "    fun trashLibraryFolder(folderId: String) {\n        viewModelScope.launch {\n            runBusy {\n                library = repository.trashFolder(folderId, library)\n                if (currentFolderId == folderId) currentFolderId = null\n            }\n        }\n    }",
    "    fun trashLibraryFolder(folderId: String) {\n        viewModelScope.launch {\n            runBusy {\n                val folderIds = descendantFolderIds(folderId) + folderId\n                val noteIds = library.notes.filter { it.folderId in folderIds }.map { it.id }.toSet()\n                openTabs.filter { it.id in noteIds && it.dirty }.forEach { saveNow(it) }\n                noteIds.forEach { saveJobs.remove(it)?.cancel() }\n                library = repository.trashFolder(folderId, library)\n                openTabs.removeAll { it.id in noteIds }\n                if (activeNoteId in noteIds) activeNoteId = null\n                if (currentFolderId in folderIds) currentFolderId = null\n            }\n        }\n    }",
)

ui = Path("app/src/main/java/com/atuy/note/ui/EnhancedNoteApp.kt")
replace_once(
    ui,
    "import androidx.compose.foundation.layout.heightIn\n",
    "import androidx.compose.foundation.layout.heightIn\nimport androidx.compose.foundation.layout.offset\n",
)
replace_once(
    ui,
    "                enabled = !showingTrash,\n                modifier = Modifier.fillMaxWidth().height(210.dp),",
    "                modifier = Modifier.fillMaxWidth().height(210.dp),",
)
replace_once(
    ui,
    "                enabled = !showingTrash,\n                modifier = Modifier.fillMaxWidth().height(250.dp),",
    "                modifier = Modifier.fillMaxWidth().height(250.dp),",
)
replace_once(
    ui,
    "    val detailBlock = listOf(details, share, addPage)\n    val toolsBeforeLasso = listOf(pen, eraser, text, sticker)\n    val toolsAfterLasso = listOf(image, shape, sticky, pointer, voice)\n    val pageBlock = listOf(readOnlyCommand, ai, search, pages)",
    "    val detailBlock = listOf(addPage, share, details)\n    val toolsBeforeLasso = listOf(pen, eraser, text, sticker)\n    val toolsAfterLasso = listOf(image, shape, sticky, pointer, voice)\n    val pageBlock = listOf(pages, search, ai, readOnlyCommand)",
)
old_layout = """            if (vertical) {
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
            }"""
new_layout = """            if (vertical) {
                if (maxHeight >= 1040.dp) {
                    val beforeHeight = COMMAND_EXTENT * toolsBeforeLasso.size.toFloat()
                    val afterHeight = COMMAND_EXTENT * toolsAfterLasso.size.toFloat()
                    Box(Modifier.width(COMMAND_EXTENT).fillMaxHeight().padding(vertical = 6.dp)) {
                        CommandColumn(detailBlock, Modifier.align(Alignment.TopCenter))
                        CommandColumn(pageBlock, Modifier.align(Alignment.BottomCenter))
                        CommandColumn(
                            toolsBeforeLasso,
                            Modifier.align(Alignment.Center).offset(
                                y = -(COMMAND_EXTENT / 2f + beforeHeight / 2f),
                            ),
                        )
                        CommandButton(lasso, Modifier.align(Alignment.Center))
                        CommandColumn(
                            toolsAfterLasso,
                            Modifier.align(Alignment.Center).offset(
                                y = COMMAND_EXTENT / 2f + afterHeight / 2f,
                            ),
                        )
                    }
                } else {
                    Column(
                        Modifier.width(COMMAND_EXTENT).fillMaxHeight().verticalScroll(rememberScrollState())
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
                if (maxWidth >= 1040.dp) {
                    val beforeWidth = COMMAND_EXTENT * toolsBeforeLasso.size.toFloat()
                    val afterWidth = COMMAND_EXTENT * toolsAfterLasso.size.toFloat()
                    Box(Modifier.fillMaxWidth().height(COMMAND_EXTENT).padding(horizontal = 8.dp)) {
                        CommandRow(detailBlock, Modifier.align(Alignment.CenterStart))
                        CommandRow(pageBlock, Modifier.align(Alignment.CenterEnd))
                        CommandRow(
                            toolsBeforeLasso,
                            Modifier.align(Alignment.Center).offset(
                                x = -(COMMAND_EXTENT / 2f + beforeWidth / 2f),
                            ),
                        )
                        CommandButton(lasso, Modifier.align(Alignment.Center))
                        CommandRow(
                            toolsAfterLasso,
                            Modifier.align(Alignment.Center).offset(
                                x = COMMAND_EXTENT / 2f + afterWidth / 2f,
                            ),
                        )
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().height(COMMAND_EXTENT)
                            .horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
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
            }"""
replace_once(ui, old_layout, new_layout)
replace_once(
    ui,
    "@Composable\nprivate fun CommandRow(commands: List<CommandSpec>) {\n    commands.forEach { CommandButton(it) }\n}\n\n@Composable\nprivate fun CommandColumn(commands: List<CommandSpec>) {\n    commands.forEach { CommandButton(it) }\n}\n\n@Composable\nprivate fun CommandButton(spec: CommandSpec) {\n    Surface(\n        modifier = Modifier.padding(2.dp),",
    "private val COMMAND_EXTENT = 52.dp\n\n@Composable\nprivate fun CommandRow(\n    commands: List<CommandSpec>,\n    modifier: Modifier = Modifier,\n) {\n    Row(modifier, verticalAlignment = Alignment.CenterVertically) {\n        commands.forEach { CommandButton(it) }\n    }\n}\n\n@Composable\nprivate fun CommandColumn(\n    commands: List<CommandSpec>,\n    modifier: Modifier = Modifier,\n) {\n    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {\n        commands.forEach { CommandButton(it) }\n    }\n}\n\n@Composable\nprivate fun CommandButton(\n    spec: CommandSpec,\n    modifier: Modifier = Modifier,\n) {\n    Surface(\n        modifier = modifier.size(COMMAND_EXTENT).padding(2.dp),",
)
