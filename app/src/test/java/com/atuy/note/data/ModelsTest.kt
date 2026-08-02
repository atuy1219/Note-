package com.atuy.note.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun partialEraserPreservesExtendedStylusChannels() {
        val samples = listOf(
            InkSample(
                x = 0f,
                y = 0f,
                elapsedTimeMillis = 10L,
                strokeUnitLengthCm = 0.02f,
                pressure = 0.2f,
                tiltRadians = 0.1f,
                orientationRadians = 6.1f,
            ),
            InkSample(
                x = 100f,
                y = 0f,
                elapsedTimeMillis = 110L,
                strokeUnitLengthCm = 0.02f,
                pressure = 0.8f,
                tiltRadians = 0.5f,
                orientationRadians = 0.1f,
            ),
        )

        val fragments = requireNotNull(splitSamplesOutsideCircle(samples, 50f, 0f, 10f))
        val leftBoundary = fragments.first().last()
        val rightBoundary = fragments.last().first()

        assertTrue(leftBoundary.elapsedTimeMillis in 49L..51L)
        assertTrue(rightBoundary.elapsedTimeMillis in 69L..71L)
        assertNotNull(leftBoundary.strokeUnitLengthCm)
        assertNotNull(leftBoundary.pressure)
        assertNotNull(leftBoundary.tiltRadians)
        assertNotNull(leftBoundary.orientationRadians)
        assertTrue(leftBoundary.pressure!! in 0.43f..0.45f)
        assertTrue(rightBoundary.pressure!! in 0.55f..0.57f)
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
