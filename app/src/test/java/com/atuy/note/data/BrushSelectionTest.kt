package com.atuy.note.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class BrushSelectionTest {
    @Test
    fun highlighterReturnPreservesMarkerSettings() {
        val marker = BrushSpec(kind = BrushKind.MARKER, colorArgb = 0xFF1565C0.toInt(), size = 7f)
        val highlighter = BrushSpec(kind = BrushKind.HIGHLIGHTER, colorArgb = 0x66FFF176, size = 24f)
        val selection = BrushSelection().update(marker).update(highlighter)
        assertEquals(marker, selection.selectPen().currentBrush)
        assertEquals(highlighter, selection.selectPen().select(BrushKind.HIGHLIGHTER).currentBrush)
    }

    @Test
    fun restartRestoresSelectedKindAndIndependentBrushes() {
        val pen = BrushSpec(size = 3f)
        val highlighter = BrushSpec(kind = BrushKind.HIGHLIGHTER, size = 22f, colorArgb = 0x66C62828)
        val selection = BrushSelection().update(pen).update(highlighter)
        val restored = Json.decodeFromString<BrushSelection>(Json.encodeToString(selection))
        assertEquals(highlighter, restored.currentBrush)
        assertEquals(pen, restored.selectPen().currentBrush)
    }
}
