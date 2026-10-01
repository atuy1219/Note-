package com.atuy.note.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentZoomTest {
    @Test
    fun zoomKeepsTheTouchedPointOnALaterPage() {
        val anchor = pageZoomAnchor(7, -260f, 1000f, 354f, 14f)
        val offset = anchor.scrollOffset(2000f, 354f, 14f)

        assertEquals(7, anchor.pageIndex)
        assertEquals(0.6f, anchor.fraction, EPSILON)
        assertEquals(860, offset)
        assertEquals(354f, 14f - offset + anchor.fraction * 2000f, EPSILON)
    }

    @Test
    fun zoomAndPanPreserveThePointUnderTheMovingFingers() {
        val anchor = pageZoomAnchor(3, -120f, 800f, 294f, 14f)
        val offset = anchor.scrollOffset(1200f, 340f, 14f)

        assertEquals(0.5f, anchor.fraction, EPSILON)
        assertEquals(340f, 14f - offset + anchor.fraction * 1200f, EPSILON)
    }

    @Test
    fun crossAxisZoomIncludesTheCenterMarginOfASmallPage() {
        val anchor = DocumentZoomAnchor(PageZoomAnchor(0, 0f), crossZoomFraction(400f, 800f, 0f, 450f))
        val scroll = anchor.crossScroll(1200f, 1200f, 450f)

        assertEquals(300f, scroll, EPSILON)
        assertEquals(450f, 0.625f * 1200f - scroll, EPSILON)
    }

    @Test
    fun crossAxisZoomHandlesDifferentPdfPageHeights() {
        val anchor = DocumentZoomAnchor(PageZoomAnchor(0, 0f), crossZoomFraction(600f, 1000f, 120f, 400f))
        val scroll = anchor.crossScroll(1200f, 2000f, 430f)

        assertEquals(610f, scroll, EPSILON)
        assertEquals(430f, 400f + (320f / 600f) * 1200f - scroll, EPSILON)
    }

    @Test
    fun zoomLimitsAreTheSameForEveryPage() {
        assertEquals(MIN_DOCUMENT_ZOOM, resolveDocumentZoom(1f, 0.001f), EPSILON)
        assertEquals(MAX_DOCUMENT_ZOOM, resolveDocumentZoom(2f, 100f), EPSILON)
    }

    @Test
    fun invalidGestureFactorsDoNotCorruptTheViewport() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -1f).forEach { factor ->
            assertEquals(1.5f, resolveDocumentZoom(1.5f, factor), EPSILON)
        }
    }

    @Test
    fun focusInAGapUsesTheNearestPage() {
        assertEquals(8f, distanceToPage(108f, 0f, 100f), EPSILON)
        assertEquals(6f, distanceToPage(108f, 114f, 200f), EPSILON)
        assertEquals(0f, distanceToPage(150f, 114f, 200f), EPSILON)
    }

    @Test
    fun multipleZoomEventsBeforeLayoutKeepTheSameAnchor() {
        val anchor = DocumentZoomAnchor(pageZoomAnchor(2, -120f, 800f, 294f, 14f), 0.5f)
        val firstOffset = anchor.page.scrollOffset(1200f, 320f, 14f)
        val secondOffset = anchor.page.scrollOffset(1600f, 350f, 14f)
        assertEquals(320f, 14f - firstOffset + anchor.page.fraction * 1200f, EPSILON)
        assertEquals(350f, 14f - secondOffset + anchor.page.fraction * 1600f, EPSILON)
    }

    private companion object {
        const val EPSILON = 0.0001f
    }
}
