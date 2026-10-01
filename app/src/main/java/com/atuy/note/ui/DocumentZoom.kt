package com.atuy.note.ui

import kotlin.math.abs

internal const val MIN_DOCUMENT_ZOOM = 0.35f
internal const val MAX_DOCUMENT_ZOOM = 5f

internal fun resolveDocumentZoom(current: Float, factor: Float): Float {
    if (!factor.isFinite() || factor <= 0f) return current
    return (current * factor).coerceIn(MIN_DOCUMENT_ZOOM, MAX_DOCUMENT_ZOOM)
}

internal data class PageZoomAnchor(
    val pageIndex: Int,
    val fraction: Float,
) {
    fun scrollOffset(newPageSize: Float, focus: Float, contentPadding: Float): Int =
        kotlin.math.round(fraction * newPageSize - focus + contentPadding).toInt()
}

internal data class DocumentZoomAnchor(
    val page: PageZoomAnchor,
    val crossFraction: Float,
) {
    fun crossScroll(pageSize: Float, contentSize: Float, focus: Float): Float =
        (contentSize - pageSize) / 2f + crossFraction * pageSize - focus
}

internal fun pageZoomAnchor(
    pageIndex: Int,
    pageOffset: Float,
    pageSize: Float,
    focus: Float,
    contentPadding: Float,
): PageZoomAnchor = PageZoomAnchor(
    pageIndex,
    (focus - contentPadding - pageOffset) / pageSize.coerceAtLeast(1f),
)

internal fun crossZoomFraction(pageSize: Float, contentSize: Float, scroll: Float, focus: Float): Float =
    (scroll + focus - (contentSize - pageSize) / 2f) / pageSize.coerceAtLeast(1f)

internal fun distanceToPage(focus: Float, offset: Float, size: Float): Float = when {
    focus < offset -> offset - focus
    focus > offset + size -> focus - offset - size
    else -> 0f
}

internal fun zoomChanged(before: Float, after: Float): Boolean = abs(before - after) > 0.0001f
