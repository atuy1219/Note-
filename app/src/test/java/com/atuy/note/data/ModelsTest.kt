package com.atuy.note.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModelsTest {
    @Test
    fun editingHistoryUpdatesUndoAndRedoAvailability() {
        val page = PageSession(PageDocument())
        assertFalse(page.canUndo)
        assertFalse(page.canRedo)
        page.addImage(PageImage(entryName = "images/test.png", x = 0f, y = 0f, width = 100f, height = 100f))
        assertTrue(page.canUndo)
        assertFalse(page.canRedo)
        assertTrue(page.undo())
        assertFalse(page.canUndo)
        assertTrue(page.canRedo)
        assertTrue(page.redo())
        assertTrue(page.canUndo)
        assertFalse(page.canRedo)
    }

    @Test
    fun touchingAPageDoesNotRequestNavigation() {
        val session = NoteSession(NoteDocument(title = "Test"), File("note.atnote"), null)
        session.activePageIndex = 0
        assertEquals(0, session.pageNavigationVersion)
        session.requestPageNavigation()
        assertEquals(1, session.pageNavigationVersion)
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingStrokeDataDoesNotSilentlyOpenAnEmptyPage() {
        PageSession(PageDocument(strokes = listOf(StoredStroke(brush = BrushSpec()))))
    }

    @Test
    fun saveSnapshotKeepsOriginalMetadataAndImageList() {
        val session = NoteSession(NoteDocument(title = "Before"), File("note.atnote"), null)
        session.markEdited()
        val snapshot = session.captureSaveSnapshot()

        session.title = "After"
        session.pages.first().addImage(PageImage(entryName = "images/new.png", x = 0f, y = 0f, width = 100f, height = 100f))
        session.markEdited()

        assertEquals("Before", snapshot.document.title)
        assertTrue(snapshot.document.pages.first().images.isEmpty())
        session.completeSave(snapshot)
        assertTrue(session.dirty)
        assertEquals(snapshot.document.revision, session.revision)
    }

    @Test
    fun completedSaveOnlyClearsDirtyForTheSavedGeneration() {
        val session = NoteSession(NoteDocument(title = "Test"), File("note.atnote"), null)
        session.markEdited()
        val snapshot = session.captureSaveSnapshot()
        session.completeSave(snapshot)
        assertFalse(session.dirty)
    }

    @Test
    fun defaultPageHasUsableDimensions() {
        val page = PageDocument()
        assertTrue(page.width > 0f)
        assertTrue(page.height > 0f)
    }

    @Test
    fun duplicatePageKeepsContentButUsesIndependentIds() {
        val original = PageSession(
            PageDocument(
                width = 800f,
                height = 1200f,
                pdfPageIndex = 3,
                images = listOf(
                    PageImage(entryName = "images/example.png", x = 10f, y = 20f, width = 100f, height = 80f),
                ),
            ),
        )

        val duplicate = original.duplicate()

        assertNotEquals(original.id, duplicate.id)
        assertEquals(original.width, duplicate.width)
        assertEquals(original.height, duplicate.height)
        assertEquals(original.pdfPageIndex, duplicate.pdfPageIndex)
        assertEquals(original.images.single().entryName, duplicate.images.single().entryName)
        assertNotEquals(original.images.single().id, duplicate.images.single().id)
    }
}
