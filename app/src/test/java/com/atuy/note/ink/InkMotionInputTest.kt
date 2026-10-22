package com.atuy.note.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class InkMotionInputTest {
    private val tolerance = 1e-5f

    @Test
    fun mapsAndroidStylusAxesToInkAxes() {
        val halfPi = (PI / 2).toFloat()
        assertEquals((3 * PI / 2).toFloat(), motionOrientationToInkRadians(0f), tolerance)
        assertEquals(0f, motionOrientationToInkRadians(halfPi), tolerance)
        assertEquals(PI.toFloat(), motionOrientationToInkRadians(-halfPi), tolerance)
        assertEquals(halfPi, motionOrientationToInkRadians(PI.toFloat()), tolerance)
        assertEquals(halfPi, motionOrientationToInkRadians(-PI.toFloat()), tolerance)
    }

    @Test
    fun normalizesReportedNegativeOrientationAndWrapsTurns() {
        // Regression: IllegalArgumentException on -0.451 PI from MotionEvent.
        val actual = motionOrientationToInkRadians((-0.451 * PI).toFloat())
        assertEquals((1.049 * PI).toFloat(), actual, tolerance)
        for (angle in listOf(
            -1000f, -7f, -PI.toFloat(), -0.451f * PI.toFloat(),
            0f, PI.toFloat(), 10f, 1000f,
        )) {
            val normalized = motionOrientationToInkRadians(angle)
            assertTrue("angle=$angle gave $normalized", normalized >= 0f)
            assertTrue("angle=$angle gave $normalized", normalized < (2 * PI).toFloat())
        }
    }

    @Test
    fun rejectsNonfiniteSensorReadingsWithoutChangingOptionalFields() {
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(0f, motionOrientationToInkRadians(bad), 0f)
            assertEquals(0f, motionTiltToInkRadians(bad), 0f)
            assertEquals(1f, motionPressureToInk(bad), 0f)
        }
    }

    @Test
    fun clampsTiltAndPressureToInkAcceptedRange() {
        assertEquals(0f, motionTiltToInkRadians(-0.2f), 0f)
        assertEquals((PI / 2).toFloat(), motionTiltToInkRadians(9f), 0f)
        assertEquals(0f, motionPressureToInk(-3f), 0f)
        assertEquals(1f, motionPressureToInk(3f), 0f)
        assertEquals(0.45f, motionPressureToInk(0.45f), 0f)
    }
}
