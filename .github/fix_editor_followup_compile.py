from pathlib import Path


def replace_once(text: str, old: str, new: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one match for {old!r}, found {count}")
    return text.replace(old, new, 1)


ink_path = Path("app/src/main/java/com/atuy/note/ink/InkPageView.kt")
ink = ink_path.read_text()
ink = replace_once(
    ink,
    "val index = pointerId?.let(event::findPointerIndex) ?: -1",
    "val index = pointerId?.let { event.findPointerIndex(it) } ?: -1",
)
ink = replace_once(
    ink,
    "if (lassoOutline.size >= 3 && current.selectedStrokeIds.isNotEmpty()) {",
    "if (lassoOutline.size >= 3) {",
)
ink_path.write_text(ink)

ui_path = Path("app/src/main/java/com/atuy/note/ui/EnhancedNoteApp.kt")
ui = ui_path.read_text()
ui = replace_once(
    ui,
    '''private data class CommandSpec(
    val icon: ImageVector,
    val description: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val gapAfter: Boolean = false,
)''',
    '''private data class CommandSpec(
    val icon: ImageVector,
    val description: String,
    val selected: Boolean,
    val gapAfter: Boolean = false,
    val onClick: () -> Unit,
)''',
)
ui = replace_once(
    ui,
    'CommandSpec(Icons.Default.Menu, "ページ一覧", showPages, onTogglePages)',
    'CommandSpec(Icons.Default.Menu, "ページ一覧", showPages, onClick = onTogglePages)',
)
ui = replace_once(
    ui,
    '''        CommandSpec(
            Icons.Default.Visibility,
            "閲覧専用",
            readOnly,
            onToggleReadOnly,
            gapAfter = true,
        ),''',
    '''        CommandSpec(
            Icons.Default.Visibility,
            "閲覧専用",
            readOnly,
            gapAfter = true,
            onClick = onToggleReadOnly,
        ),''',
)
ui = replace_once(
    ui,
    'CommandSpec(Icons.Default.NoteAdd, "ページ追加", false, viewModel::addPage)',
    'CommandSpec(Icons.Default.NoteAdd, "ページ追加", false, onClick = viewModel::addPage)',
)
ui_path.write_text(ui)
