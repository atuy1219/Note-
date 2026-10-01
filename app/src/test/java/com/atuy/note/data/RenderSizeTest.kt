package com.atuy.note.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderSizeTest {
    @Test
    fun normalPageKeepsRequestedWidthAndAspectRatio() {
        assertEquals(RenderSize(1200, 1800), boundedRenderSize(1200, 800f, 1200f))
    }

    @Test
    fun enlargedAndVeryTallPagesStayWithinTheMemoryBudget() {
        listOf(1f, 2f, 10f, 100_000f).forEach { aspect ->
            val size = boundedRenderSize(20_000, 1f, aspect)
            assertTrue(size.width in 1..2400)
            assertTrue(size.height in 1..4096)
            assertTrue(size.width.toLong() * size.height <= 8_000_000)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidPageDimensionsAreRejectedBeforeBitmapAllocation() {
        boundedRenderSize(1200, 0f, 100f)
    }
}
