package com.atuy.note.data

enum class NoteExportFormat(val mimeType: String, val extension: String) {
    PDF("application/pdf", "pdf"),
    PNG("image/png", "png"),
    PNG_ZIP("application/zip", "zip"),
}

data class NoteExportRequest(
    val snapshot: NoteSaveSnapshot,
    val format: NoteExportFormat,
    val pageIndex: Int,
)

internal fun exportFileName(title: String, format: NoteExportFormat, pageIndex: Int): String {
    val name = title.replace(Regex("[\\\\/\\p{Cntrl}:*?\"<>|]"), "_").trim().take(100)
        .ifBlank { "ノート" }
    val suffix = if (format == NoteExportFormat.PNG) "-${pageIndex + 1}" else ""
    return "$name$suffix.${format.extension}"
}
