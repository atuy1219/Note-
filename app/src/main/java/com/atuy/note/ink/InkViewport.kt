package com.atuy.note.ink

import kotlin.math.abs
import kotlin.math.min

internal data class ViewportPoint(
    val x: Float,
    val y: Float,
)

internal data class ViewportTransform(
    val scale: Float,
    val translateX: Float,
    val translateY: Float,
) {
    fun worldToView(x: Float, y: Float): ViewportPoint = ViewportPoint(
        x = x * scale + translateX,
        y = y * scale + translateY,
    )

    fun viewToWorld(x: Float, y: Float): ViewportPoint = ViewportPoint(
        x = (x - translateX) / scale,
        y = (y - translateY) / scale,
    )
}

/**
 * Owns the page camera so PDF rendering, finished ink, and input inversion all use one transform.
 */
internal class InkViewport {
    var zoom: Float = MIN_ZOOM
        private set
    var panX: Float = 0f
        private set
    var panY: Float = 0f
        private set

    fun reset() {
        zoom = MIN_ZOOM
        panX = 0f
        panY = 0f
    }

    fun transform(
        pageWidth: Float,
        pageHeight: Float,
        viewWidth: Float,
        viewHeight: Float,
    ): ViewportTransform {
        val layout = layout(pageWidth, pageHeight, viewWidth, viewHeight)
        return ViewportTransform(
            scale = layout.fitScale * zoom,
            translateX = layout.baseOffsetX + panX,
            translateY = layout.baseOffsetY + panY,
        )
    }

    fun zoomAt(
        scaleFactor: Float,
        focusX: Float,
        focusY: Float,
        pageWidth: Float,
        pageHeight: Float,
        viewWidth: Float,
        viewHeight: Float,
    ): Boolean {
        val before = transform(pageWidth, pageHeight, viewWidth, viewHeight)
        val focusWorld = before.viewToWorld(focusX, focusY)
        val nextZoom = (zoom * scaleFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (abs(nextZoom - zoom) < ZOOM_EPSILON) return false

        zoom = nextZoom
        val layout = layout(pageWidth, pageHeight, viewWidth, viewHeight)
        val nextScale = layout.fitScale * zoom
        panX = focusX - layout.baseOffsetX - focusWorld.x * nextScale
        panY = focusY - layout.baseOffsetY - focusWorld.y * nextScale
        clamp(pageWidth, pageHeight, viewWidth, viewHeight)
        return true
    }

    fun panBy(
        dx: Float,
        dy: Float,
        pageWidth: Float,
        pageHeight: Float,
        viewWidth: Float,
        viewHeight: Float,
    ) {
        panX += dx
        panY += dy
        clamp(pageWidth, pageHeight, viewWidth, viewHeight)
    }

    fun clamp(
        pageWidth: Float,
        pageHeight: Float,
        viewWidth: Float,
        viewHeight: Float,
    ) {
        if (zoom <= MIN_ZOOM + ZOOM_RESET_EPSILON) {
            reset()
            return
        }

        val layout = layout(pageWidth, pageHeight, viewWidth, viewHeight)
        val scaledWidth = pageWidth * layout.fitScale * zoom
        val scaledHeight = pageHeight * layout.fitScale * zoom
        val targetX = layout.baseOffsetX + panX
        val targetY = layout.baseOffsetY + panY
        val clampedX = if (scaledWidth <= viewWidth) {
            (viewWidth - scaledWidth) / 2f
        } else {
            targetX.coerceIn(viewWidth - scaledWidth, 0f)
        }
        val clampedY = if (scaledHeight <= viewHeight) {
            (viewHeight - scaledHeight) / 2f
        } else {
            targetY.coerceIn(viewHeight - scaledHeight, 0f)
        }
        panX = clampedX - layout.baseOffsetX
        panY = clampedY - layout.baseOffsetY
    }

    private fun layout(
        pageWidth: Float,
        pageHeight: Float,
        viewWidth: Float,
        viewHeight: Float,
    ): BaseLayout {
        require(pageWidth > 0f && pageHeight > 0f) { "Page dimensions must be positive" }
        require(viewWidth > 0f && viewHeight > 0f) { "View dimensions must be positive" }
        val fitScale = min(viewWidth / pageWidth, viewHeight / pageHeight)
        return BaseLayout(
            fitScale = fitScale,
            baseOffsetX = (viewWidth - pageWidth * fitScale) / 2f,
            baseOffsetY = (viewHeight - pageHeight * fitScale) / 2f,
        )
    }

    private data class BaseLayout(
        val fitScale: Float,
        val baseOffsetX: Float,
        val baseOffsetY: Float,
    )

    private companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 5f
        const val ZOOM_EPSILON = 0.0001f
        const val ZOOM_RESET_EPSILON = 0.001f
    }
}
