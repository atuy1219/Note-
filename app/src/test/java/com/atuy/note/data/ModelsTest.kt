package com.atuy.note.data

import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    @Test
    fun defaultPageHasUsableDimensions() {
        val page = PageDocument()
        assertTrue(page.width > 0f)
        assertTrue(page.height > 0f)
    }
}
