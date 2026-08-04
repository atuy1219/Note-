from pathlib import Path

path = Path("app/src/main/java/com/atuy/note/ink/InkPageView.kt")
text = path.read_text()

replacements = [
    (
        "val index = pointerId?.let(event::findPointerIndex) ?: -1",
        "val index = pointerId?.let { event.findPointerIndex(it) } ?: -1",
    ),
    (
        "if (lassoOutline.size >= 3 && current.selectedStrokeIds.isNotEmpty()) {",
        "if (lassoOutline.size >= 3) {",
    ),
]

for old, new in replacements:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one match for {old!r}, found {count}")
    text = text.replace(old, new, 1)

path.write_text(text)
