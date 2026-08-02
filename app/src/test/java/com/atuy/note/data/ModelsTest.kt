package com.atuy.note.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    @Test
    fun partialEraserSplitsLineIntoTwoFragments() {
        val samples = listOf(InkSample(0f, 0f), InkSample(100f, 0f))

        val fragments = splitSamplesOutsideCircle(samples, 50f, 0f, 10f)

        requireNotNull(fragments)
        assertEquals(2, fragments.size)
        assertTrue(fragments[0].last().x in 39.9f..40.1f)
        assertTrue(fragments[1].first().x in 59.9f..60.1f)
    }

    @Test
    fun partialEraserReturnsNullWhenUntouched() {
        val samples = listOf(InkSample(0f, 0f), InkSample(100f, 0f))

        assertNull(splitSamplesOutsideCircle(samples, 50f, 100f, 10f))
    }

    @Test
    fun partialEraserCanRemoveWholeLine() {
        val samples = listOf(InkSample(45f, 0f), InkSample(55f, 0f))

        assertEquals(emptyList<List<InkSample>>(), splitSamplesOutsideCircle(samples, 50f, 0f, 10f))
    }
}
