package com.atuy.note.ink

import org.junit.Assert.assertEquals
import org.junit.Test

class InkViewportTest {
    @Test
    fun fitTransformCentersLetterboxedPage() {
        val transform = InkViewport().transform(
            pageWidth = 100f,
            pageHeight = 200f,
            viewWidth = 300f,
            viewHeight = 300f,
        )

        assertEquals(1.5f, transform.scale, TOLERANCE)
        assertEquals(75f, transform.translateX, TOLERANCE)
        assertEquals(0f, transform.translateY, TOLERANCE)
        assertPointEquals(ViewportPoint(75f, 0f), transform.worldToView(0f, 0f))
        assertPointEquals(ViewportPoint(225f, 300f), transform.worldToView(100f, 200f))
    }

    @Test
    fun zoomKeepsWorldPointUnderGestureFocus() {
        val viewport = InkViewport()
        val before = viewport.transform(100f, 200f, 200f, 400f)
        val focusX = 100f
        val focusY = 120f
        val anchoredWorldPoint = before.viewToWorld(focusX, focusY)

        viewport.zoomAt(
            scaleFactor = 2f,
            focusX = focusX,
            focusY = focusY,
            pageWidth = 100f,
            pageHeight = 200f,
            viewWidth = 200f,
            viewHeight = 400f,
        )

        val after = viewport.transform(100f, 200f, 200f, 400f)
        assertPointEquals(ViewportPoint(focusX, focusY), after.worldToView(anchoredWorldPoint.x, anchoredWorldPoint.y))
    }

    @Test
    fun transformRoundTripsInputAndRenderedCoordinatesAfterZoomAndPan() {
        val viewport = InkViewport()
        viewport.zoomAt(2f, 100f, 200f, 100f, 200f, 200f, 400f)
        viewport.panBy(24f, -36f, 100f, 200f, 200f, 400f)
        val transform = viewport.transform(100f, 200f, 200f, 400f)

        val viewPoint = ViewportPoint(82f, 247f)
        val worldPoint = transform.viewToWorld(viewPoint.x, viewPoint.y)

        assertPointEquals(viewPoint, transform.worldToView(worldPoint.x, worldPoint.y))
    }

    @Test
    fun panIsClampedToPageEdges() {
        val viewport = InkViewport()
        viewport.zoomAt(2f, 100f, 100f, 100f, 100f, 200f, 200f)

        viewport.panBy(1_000f, 1_000f, 100f, 100f, 200f, 200f)
        val topLeft = viewport.transform(100f, 100f, 200f, 200f)
        assertEquals(0f, topLeft.translateX, TOLERANCE)
        assertEquals(0f, topLeft.translateY, TOLERANCE)

        viewport.panBy(-1_000f, -1_000f, 100f, 100f, 200f, 200f)
        val bottomRight = viewport.transform(100f, 100f, 200f, 200f)
        assertEquals(-200f, bottomRight.translateX, TOLERANCE)
        assertEquals(-200f, bottomRight.translateY, TOLERANCE)
    }

    @Test
    fun returningToMinimumZoomRecentersPage() {
        val viewport = InkViewport()
        viewport.zoomAt(2f, 100f, 100f, 100f, 100f, 200f, 200f)
        viewport.panBy(-40f, -30f, 100f, 100f, 200f, 200f)

        viewport.zoomAt(0.1f, 80f, 90f, 100f, 100f, 200f, 200f)

        val transform = viewport.transform(100f, 100f, 200f, 200f)
        assertEquals(1f, viewport.zoom, TOLERANCE)
        assertEquals(2f, transform.scale, TOLERANCE)
        assertEquals(0f, transform.translateX, TOLERANCE)
        assertEquals(0f, transform.translateY, TOLERANCE)
    }

    private fun assertPointEquals(expected: ViewportPoint, actual: ViewportPoint) {
        assertEquals(expected.x, actual.x, TOLERANCE)
        assertEquals(expected.y, actual.y, TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
