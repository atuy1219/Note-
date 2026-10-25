package com.atuy.note.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkupClearTest {
    @Test
    fun clearingPlacedImagesKeepsPdfPageAndCanUndoAndRedo() {
        val source = PageDocument(
            pdfPageIndex = 42,
            images = listOf(
                PageImage(
                    id = "annotation",
                    entryName = "images/annotation.png",
                    x = 1f,
                    y = 2f,
                    width = 3f,
                    height = 4f,
                ),
            ),
        )
        val page = PageSession(source)
        assertTrue(page.clearMarkups())
        assertTrue(page.images.isEmpty())
        assertEquals(42, page.pdfPageIndex)
        assertTrue(page.canUndo)
        assertTrue(page.undo())
        assertEquals(1, page.images.size)
        assertTrue(page.redo())
        assertTrue(page.images.isEmpty())
        assertFalse(page.clearMarkups())
    }

    @Test
    fun emptyPdfHasNoClearAction() {
        val page = PageSession(PageDocument(pdfPageIndex = 0))
        assertFalse(page.clearMarkups())
        assertFalse(page.canUndo)
    }
}
