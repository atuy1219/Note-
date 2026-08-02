package com.atuy.note.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
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
