from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match in {path} for {old!r}, found {count}")
    path.write_text(text.replace(old, new, 1))


enhanced = Path("app/src/main/java/com/atuy/note/ui/EnhancedNoteApp.kt")
replace_once(
    enhanced,
    "import androidx.compose.material.icons.automirrored.filled.ArrowBack\n",
    "import androidx.compose.material.icons.automirrored.filled.ArrowBack\n"
    "import androidx.compose.material.icons.automirrored.filled.ArrowForward\n"
    "import androidx.compose.material.icons.automirrored.filled.NoteAdd\n"
    "import androidx.compose.material.icons.automirrored.filled.StickyNote2\n",
)
for old in (
    "import androidx.compose.material.icons.filled.ArrowForward\n",
    "import androidx.compose.material.icons.filled.NoteAdd\n",
    "import androidx.compose.material.icons.filled.StickyNote2\n",
):
    replace_once(enhanced, old, "")
replace_once(
    enhanced,
    "Icons.Default.StickyNote2",
    "Icons.AutoMirrored.Filled.StickyNote2",
)
replace_once(
    enhanced,
    "Icons.Default.NoteAdd",
    "Icons.AutoMirrored.Filled.NoteAdd",
)
replace_once(
    enhanced,
    "Icons.Default.ArrowForward",
    "Icons.AutoMirrored.Filled.ArrowForward",
)

note_app = Path("app/src/main/java/com/atuy/note/ui/NoteApp.kt")
replace_once(
    note_app,
    "import androidx.compose.material3.ScrollableTabRow\n",
    "import androidx.compose.material3.PrimaryScrollableTabRow\n",
)
replace_once(
    note_app,
    "                ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 8.dp) {",
    "                PrimaryScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 8.dp) {",
)
