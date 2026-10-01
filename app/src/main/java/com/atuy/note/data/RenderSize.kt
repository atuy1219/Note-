package com.atuy.note.data

import kotlin.math.sqrt

internal data class RenderSize(val width: Int, val height: Int)

internal fun boundedRenderSize(requestedWidth: Int, pageWidth: Float, pageHeight: Float): RenderSize {
    require(pageWidth.isFinite() && pageWidth > 0f && pageHeight.isFinite() && pageHeight > 0f)
    val aspect = pageHeight.toDouble() / pageWidth
    val width = minOf(
        requestedWidth.coerceIn(1, 2400).toDouble(),
        4096.0 / aspect,
        sqrt(8_000_000.0 / aspect),
    ).toInt().coerceAtLeast(1)
    val height = (width * aspect).toInt().coerceIn(1, 4096)
    return RenderSize(width, height)
}
