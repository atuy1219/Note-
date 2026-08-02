package com.atuy.note.ink

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import androidx.input.motionprediction.MotionEventPredictor
import com.atuy.note.data.BrushSpec
import com.atuy.note.data.NavigationGestureMode
import com.atuy.note.data.PageImage
import com.atuy.note.data.PageSession
import com.atuy.note.data.RuntimeStroke
import com.atuy.note.data.ToolMode
import com.atuy.note.data.toBrush
import com.atuy.note.data.toRuntimeStroke
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

class InkPageView(context: Context) : FrameLayout(context) {
    private val dryView = DryInkView(context)
    private val wetView = InProgressStrokesView(context)
    private val predictor = MotionEventPredictor.newInstance(this)
    private val pointerStrokes = mutableMapOf<Int, InProgressStrokeId>()
    private val strokeBrushSpecs = mutableMapOf<InProgressStrokeId, BrushSpec>()
    private val lassoStrokeIds = mutableSetOf<InProgressStrokeId>()
    private val lassoBrush = Brush.createWithColorIntArgb(
        StockBrushes.dashedLine(),
        0xFF1976D2.toInt(),
        3.2f,
        0.1f,
    )

    private var page: PageSession? = null
    private var boundPageId: String? = null
    private var backgroundBitmap: Bitmap? = null
    private var imageBitmaps: Map<String, Bitmap> = emptyMap()
    private var toolProvider: () -> ToolMode = { ToolMode.PEN }
    private var brushProvider: () -> BrushSpec = { BrushSpec() }
    private var navigationGestureProvider: () -> NavigationGestureMode = { NavigationGestureMode.ONE_FINGER }
    private var onNavigationPan: (Float, Float) -> Unit = { _, _ -> }
    private var onStrokeAdded: (RuntimeStroke) -> Unit = {}
    private var onEraseStart: () -> Unit = {}
    private var onErase: (Float, Float, Float) -> Unit = { _, _, _ -> }
    private var onEraseEnd: () -> Unit = {}
    private var onLassoFinished: (Stroke) -> Unit = {}
    private var onSelectedTransformStart: () -> Boolean = { false }
    private var onSelectedMove: (Float, Float) -> Unit = { _, _ -> }
    private var onSelectedTransformEnd: () -> Unit = {}
    private var onSelectedTransformCancel: () -> Unit = {}
    private var onImageSelected: (String?) -> Unit = {}
    private var onImageTransformStart: (String) -> Boolean = { false }
    private var onImageMove: (String, Float, Float) -> Unit = { _, _, _ -> }
    private var onImageTransformEnd: () -> Unit = {}
    private var onImageTransformCancel: () -> Unit = {}
    private var onActivated: () -> Unit = {}

    private var draggingImageId: String? = null
    private var imageDragOffsetX = 0f
    private var imageDragOffsetY = 0f
    private var eraserGestureActive = false
    private var selectedDragActive = false
    private var selectedDragStartX = 0f
    private var selectedDragStartY = 0f

    private val worldToView = Matrix()
    private val viewToWorld = Matrix()
    private var viewportZoom = 1f
    private var viewportPanX = 0f
    private var viewportPanY = 0f
    private var previousTouchCentroidX = 0f
    private var previousTouchCentroidY = 0f
    private var previousTouchSpan = 0f
    private var touchGestureActive = false

    init {
        setWillNotDraw(false)
        addView(dryView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(wetView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        isFocusableInTouchMode = true
        wetView.isClickable = false
        wetView.isFocusable = false
        wetView.motionEventToViewTransform = Matrix()
        wetView.addFinishedStrokesListener(object : InProgressStrokesFinishedListener {
            override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
                strokes.forEach { (id, stroke) ->
                    if (lassoStrokeIds.remove(id)) {
                        onLassoFinished(stroke)
                    } else {
                        val spec = strokeBrushSpecs.remove(id) ?: brushProvider()
                        onStrokeAdded(stroke.toRuntimeStroke(spec))
                    }
                }
                wetView.removeFinishedStrokes(strokes.keys)
                dryView.invalidate()
            }
        })
        setOnTouchListener { _, event -> handleMotionEvent(event) }
    }

    fun bind(
        page: PageSession,
        background: Bitmap?,
        imageBitmaps: Map<String, Bitmap>,
        toolProvider: () -> ToolMode,
        brushProvider: () -> BrushSpec,
        navigationGestureProvider: () -> NavigationGestureMode,
        onNavigationPan: (Float, Float) -> Unit,
        onStrokeAdded: (RuntimeStroke) -> Unit,
        onEraseStart: () -> Unit,
        onErase: (Float, Float, Float) -> Unit,
        onEraseEnd: () -> Unit,
        onLassoFinished: (Stroke) -> Unit,
        onSelectedTransformStart: () -> Boolean,
        onSelectedMove: (Float, Float) -> Unit,
        onSelectedTransformEnd: () -> Unit,
        onSelectedTransformCancel: () -> Unit,
        onImageSelected: (String?) -> Unit,
        onImageTransformStart: (String) -> Boolean,
        onImageMove: (String, Float, Float) -> Unit,
        onImageTransformEnd: () -> Unit,
        onImageTransformCancel: () -> Unit,
        onActivated: () -> Unit,
    ) {
        if (boundPageId != page.id) {
            boundPageId = page.id
            viewportZoom = 1f
            viewportPanX = 0f
            viewportPanY = 0f
        }
        this.page = page
        this.backgroundBitmap = background
        this.imageBitmaps = imageBitmaps
        this.toolProvider = toolProvider
        this.brushProvider = brushProvider
        this.navigationGestureProvider = navigationGestureProvider
        this.onNavigationPan = onNavigationPan
        this.onStrokeAdded = onStrokeAdded
        this.onEraseStart = onEraseStart
        this.onErase = onErase
        this.onEraseEnd = onEraseEnd
        this.onLassoFinished = onLassoFinished
        this.onSelectedTransformStart = onSelectedTransformStart
        this.onSelectedMove = onSelectedMove
        this.onSelectedTransformEnd = onSelectedTransformEnd
        this.onSelectedTransformCancel = onSelectedTransformCancel
        this.onImageSelected = onImageSelected
        this.onImageTransformStart = onImageTransformStart
        this.onImageMove = onImageMove
        this.onImageTransformEnd = onImageTransformEnd
        this.onImageTransformCancel = onImageTransformCancel
        this.onActivated = onActivated
        dryView.page = page
        dryView.backgroundBitmap = background
        dryView.imageBitmaps = imageBitmaps
        dryView.toolProvider = toolProvider
        updateViewportMatrices()
        dryView.invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clampViewport()
        updateViewportMatrices()
        dryView.invalidate()
    }

    private fun handleMotionEvent(event: MotionEvent): Boolean {
        if (event.pointerCount <= 0) return false
        val actionIndex = event.actionIndex.coerceIn(0, event.pointerCount - 1)
        val actionToolType = event.getToolType(actionIndex)
        val actionIsStylus = actionToolType == MotionEvent.TOOL_TYPE_STYLUS ||
            actionToolType == MotionEvent.TOOL_TYPE_ERASER
        val hasStylus = (0 until event.pointerCount).any { index ->
            val type = event.getToolType(index)
            type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER
        }

        if (!actionIsStylus) {
            if (hasStylus) return true
            return handleTouchMotion(event)
        }

        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            requestDisallowInterceptTouchEvent(true)
            onActivated()
        }

        if (toolProvider() == ToolMode.IMAGE) return handleImageMotion(event, actionIndex)
        if (toolProvider() == ToolMode.LASSO) return handleLassoMotion(event, actionIndex)

        predictor.record(event)
        val pointerId = event.getPointerId(actionIndex)
        val temporaryEraser = actionToolType == MotionEvent.TOOL_TYPE_ERASER ||
            event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0 ||
            event.buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY != 0
        val erasing = toolProvider() == ToolMode.ERASER || temporaryEraser
        val point = mapViewToWorld(event.getX(actionIndex), event.getY(actionIndex))

        if (erasing) {
            val radius = 28f / viewportZoom.coerceAtLeast(1f)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    if (!eraserGestureActive) {
                        eraserGestureActive = true
                        onEraseStart()
                    }
                    onErase(point.x, point.y, radius)
                    dryView.invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    for (pointerIndex in 0 until event.pointerCount) {
                        val type = event.getToolType(pointerIndex)
                        if (type != MotionEvent.TOOL_TYPE_STYLUS && type != MotionEvent.TOOL_TYPE_ERASER) continue
                        val current = mapViewToWorld(event.getX(pointerIndex), event.getY(pointerIndex))
                        onErase(current.x, current.y, radius)
                    }
                    dryView.invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    onErase(point.x, point.y, radius)
                    finishEraserGesture()
                    releaseParentIntercept()
                    dryView.invalidate()
                }
                MotionEvent.ACTION_CANCEL -> {
                    finishEraserGesture()
                    releaseParentIntercept()
                    dryView.invalidate()
                }
            }
            return true
        }

        return handleAuthoredStroke(event, actionIndex, brushProvider().toBrush(), false)
    }

    private fun handleTouchMotion(event: MotionEvent): Boolean {
        val touchIndices = (0 until event.pointerCount).filter { event.getToolType(it) == MotionEvent.TOOL_TYPE_FINGER }
        if (touchIndices.isEmpty()) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestDisallowInterceptTouchEvent(true)
                onActivated()
                touchGestureActive = true
                val centroid = touchCentroid(event, touchIndices)
                previousTouchCentroidX = centroid.x
                previousTouchCentroidY = centroid.y
                previousTouchSpan = 0f
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                requestDisallowInterceptTouchEvent(true)
                val centroid = touchCentroid(event, touchIndices)
                previousTouchCentroidX = centroid.x
                previousTouchCentroidY = centroid.y
                previousTouchSpan = touchSpan(event, touchIndices, centroid)
                touchGestureActive = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!touchGestureActive) return true
                val centroid = touchCentroid(event, touchIndices)
                val dx = centroid.x - previousTouchCentroidX
                val dy = centroid.y - previousTouchCentroidY

                if (touchIndices.size >= 2) {
                    val span = touchSpan(event, touchIndices, centroid)
                    val scaleFactor = if (previousTouchSpan > 0.5f && span > 0.5f) {
                        (span / previousTouchSpan).coerceIn(0.75f, 1.33f)
                    } else {
                        1f
                    }
                    val zooming = abs(scaleFactor - 1f) > 0.002f
                    if (zooming) zoomAt(scaleFactor, centroid.x, centroid.y)
                    if (viewportZoom > 1.001f) {
                        panViewport(dx, dy)
                    } else if (!zooming) {
                        onNavigationPan(dx, dy)
                    }
                    previousTouchSpan = span
                } else {
                    previousTouchSpan = 0f
                    if (viewportZoom > 1.001f) {
                        if (navigationGestureProvider() == NavigationGestureMode.ONE_FINGER) {
                            panViewport(dx, dy)
                        }
                    } else if (navigationGestureProvider() == NavigationGestureMode.ONE_FINGER) {
                        onNavigationPan(dx, dy)
                    }
                }

                previousTouchCentroidX = centroid.x
                previousTouchCentroidY = centroid.y
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val remaining = touchIndices.filter { it != event.actionIndex }
                if (remaining.isNotEmpty()) {
                    val centroid = touchCentroid(event, remaining)
                    previousTouchCentroidX = centroid.x
                    previousTouchCentroidY = centroid.y
                    previousTouchSpan = if (remaining.size >= 2) touchSpan(event, remaining, centroid) else 0f
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touchGestureActive = false
                previousTouchSpan = 0f
                releaseParentIntercept()
                return true
            }
            else -> return true
        }
    }

    private fun touchCentroid(event: MotionEvent, indices: List<Int>): PointF {
        var x = 0f
        var y = 0f
        indices.forEach { index ->
            x += event.getX(index)
            y += event.getY(index)
        }
        return PointF(x / indices.size, y / indices.size)
    }

    private fun touchSpan(event: MotionEvent, indices: List<Int>, centroid: PointF): Float {
        if (indices.size < 2) return 0f
        var distance = 0f
        indices.forEach { index ->
            distance += hypot(event.getX(index) - centroid.x, event.getY(index) - centroid.y)
        }
        return distance / indices.size
    }

    private fun zoomAt(scaleFactor: Float, focusX: Float, focusY: Float) {
        val currentPage = page ?: return
        if (width <= 0 || height <= 0) return
        updateViewportMatrices()
        val focusWorld = mapViewToWorld(focusX, focusY)
        val nextZoom = (viewportZoom * scaleFactor).coerceIn(1f, 5f)
        if (abs(nextZoom - viewportZoom) < 0.0001f) return
        viewportZoom = nextZoom

        val baseScale = fitScale(currentPage)
        val baseOffsetX = (width - currentPage.width * baseScale) / 2f
        val baseOffsetY = (height - currentPage.height * baseScale) / 2f
        val nextScale = baseScale * viewportZoom
        viewportPanX = focusX - baseOffsetX - focusWorld.x * nextScale
        viewportPanY = focusY - baseOffsetY - focusWorld.y * nextScale
        clampViewport()
        updateViewportMatrices()
        dryView.invalidate()
    }

    private fun panViewport(dx: Float, dy: Float) {
        viewportPanX += dx
        viewportPanY += dy
        clampViewport()
        updateViewportMatrices()
        dryView.invalidate()
    }

    private fun clampViewport() {
        val currentPage = page ?: return
        if (width <= 0 || height <= 0) return
        val baseScale = fitScale(currentPage)
        val baseOffsetX = (width - currentPage.width * baseScale) / 2f
        val baseOffsetY = (height - currentPage.height * baseScale) / 2f
        val scaledWidth = currentPage.width * baseScale * viewportZoom
        val scaledHeight = currentPage.height * baseScale * viewportZoom

        val targetX = baseOffsetX + viewportPanX
        val targetY = baseOffsetY + viewportPanY
        val clampedX = if (scaledWidth <= width) {
            (width - scaledWidth) / 2f
        } else {
            targetX.coerceIn(width - scaledWidth, 0f)
        }
        val clampedY = if (scaledHeight <= height) {
            (height - scaledHeight) / 2f
        } else {
            targetY.coerceIn(height - scaledHeight, 0f)
        }
        viewportPanX = clampedX - baseOffsetX
        viewportPanY = clampedY - baseOffsetY
        if (viewportZoom <= 1.001f) {
            viewportZoom = 1f
            viewportPanX = 0f
            viewportPanY = 0f
        }
    }

    private fun updateViewportMatrices() {
        val currentPage = page ?: return
        if (width <= 0 || height <= 0) return
        val baseScale = fitScale(currentPage)
        val scale = baseScale * viewportZoom
        val tx = (width - currentPage.width * baseScale) / 2f + viewportPanX
        val ty = (height - currentPage.height * baseScale) / 2f + viewportPanY
        worldToView.setValues(
            floatArrayOf(
                scale, 0f, tx,
                0f, scale, ty,
                0f, 0f, 1f,
            ),
        )
        check(worldToView.invert(viewToWorld)) { "Ink viewport matrix must be invertible" }
    }

    private fun fitScale(currentPage: PageSession): Float =
        min(width / currentPage.width, height / currentPage.height).coerceAtLeast(0.0001f)

    private fun mapViewToWorld(x: Float, y: Float): PointF {
        updateViewportMatrices()
        val values = floatArrayOf(x, y)
        viewToWorld.mapPoints(values)
        return PointF(values[0], values[1])
    }

    private fun handleLassoMotion(event: MotionEvent, pointerIndex: Int): Boolean {
        val currentPage = page ?: return true
        val point = mapViewToWorld(event.getX(pointerIndex), event.getY(pointerIndex))

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (currentPage.isPointInsideStrokeSelection(point.x, point.y) && onSelectedTransformStart()) {
                    selectedDragActive = true
                    selectedDragStartX = point.x
                    selectedDragStartY = point.y
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (selectedDragActive) {
                    onSelectedMove(point.x - selectedDragStartX, point.y - selectedDragStartY)
                    dryView.invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (selectedDragActive) {
                    selectedDragActive = false
                    onSelectedTransformEnd()
                    releaseParentIntercept()
                    dryView.invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (selectedDragActive) {
                    selectedDragActive = false
                    onSelectedTransformCancel()
                    releaseParentIntercept()
                    dryView.invalidate()
                    return true
                }
            }
        }
        return handleAuthoredStroke(event, pointerIndex, lassoBrush, true)
    }

    private fun handleAuthoredStroke(
        event: MotionEvent,
        pointerIndex: Int,
        brush: Brush,
        lasso: Boolean,
    ): Boolean {
        predictor.record(event)
        val pointerId = event.getPointerId(pointerIndex)
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                requestUnbufferedDispatch(event)
                updateViewportMatrices()
                val id = wetView.startStroke(
                    event = event,
                    pointerId = pointerId,
                    brush = brush,
                    motionEventToWorldTransform = Matrix(viewToWorld),
                    strokeToWorldTransform = Matrix(),
                )
                pointerStrokes[pointerId] = id
                if (lasso) lassoStrokeIds += id else strokeBrushSpecs[id] = brushProvider()
                true
            }
            MotionEvent.ACTION_MOVE -> {
                for (index in 0 until event.pointerCount) {
                    val type = event.getToolType(index)
                    if (type != MotionEvent.TOOL_TYPE_STYLUS && type != MotionEvent.TOOL_TYPE_ERASER) continue
                    val currentPointerId = event.getPointerId(index)
                    val id = pointerStrokes[currentPointerId] ?: continue
                    val predicted = predictor.predict()
                    try {
                        wetView.addToStroke(event, currentPointerId, id, predicted)
                    } finally {
                        predicted?.recycle()
                    }
                }
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                pointerStrokes.remove(pointerId)?.let { id -> wetView.finishStroke(event, pointerId, id) }
                releaseParentIntercept()
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                pointerStrokes.values.forEach { id -> wetView.cancelStroke(id, event) }
                pointerStrokes.clear()
                strokeBrushSpecs.clear()
                lassoStrokeIds.clear()
                releaseParentIntercept()
                true
            }
            else -> true
        }
    }

    private fun handleImageMotion(event: MotionEvent, pointerIndex: Int): Boolean {
        val currentPage = page ?: return true
        val point = mapViewToWorld(event.getX(pointerIndex), event.getY(pointerIndex))
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val hit = currentPage.images.asReversed().firstOrNull { image ->
                    point.x >= image.x && point.x <= image.x + image.width &&
                        point.y >= image.y && point.y <= image.y + image.height
                }
                onImageSelected(hit?.id)
                dryView.invalidate()
                if (hit != null && onImageTransformStart(hit.id)) {
                    draggingImageId = hit.id
                    imageDragOffsetX = point.x - hit.x
                    imageDragOffsetY = point.y - hit.y
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val id = draggingImageId ?: return true
                onImageMove(id, point.x - imageDragOffsetX, point.y - imageDragOffsetY)
                dryView.invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (draggingImageId != null) onImageTransformEnd()
                draggingImageId = null
                releaseParentIntercept()
                dryView.invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (draggingImageId != null) onImageTransformCancel()
                draggingImageId = null
                releaseParentIntercept()
                dryView.invalidate()
                return true
            }
            else -> return true
        }
    }

    private fun finishEraserGesture() {
        if (!eraserGestureActive) return
        eraserGestureActive = false
        onEraseEnd()
    }

    private fun releaseParentIntercept() {
        requestDisallowInterceptTouchEvent(false)
    }

    private inner class DryInkView(context: Context) : View(context) {
        private val renderer = CanvasStrokeRenderer.create()
        private val transform = Matrix()
        private val paperPaint = Paint().apply { color = Color.WHITE }
        private val outsidePaint = Paint().apply { color = Color.rgb(238, 238, 238) }
        private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(25, 118, 210)
            style = Paint.Style.STROKE
            strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
        }
        var page: PageSession? = null
        var backgroundBitmap: Bitmap? = null
        var imageBitmaps: Map<String, Bitmap> = emptyMap()
        var toolProvider: () -> ToolMode = { ToolMode.PEN }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), outsidePaint)
            val current = page ?: return
            updateViewportMatrices()
            transform.set(worldToView)

            canvas.save()
            canvas.concat(transform)
            canvas.drawRect(0f, 0f, current.width, current.height, paperPaint)
            backgroundBitmap?.let { bitmap ->
                canvas.drawBitmap(bitmap, null, RectF(0f, 0f, current.width, current.height), imagePaint)
            }
            current.images.forEach { image ->
                val bitmap = imageBitmaps[image.entryName] ?: return@forEach
                val rect = RectF(image.x, image.y, image.x + image.width, image.y + image.height)
                canvas.drawBitmap(bitmap, null, rect, imagePaint)
                if (toolProvider() == ToolMode.IMAGE && current.selectedImageId == image.id) {
                    val worldStroke = selectionPaint.strokeWidth / (fitScale(current) * viewportZoom)
                    selectionPaint.strokeWidth = worldStroke
                    canvas.drawRect(rect, selectionPaint)
                    selectionPaint.strokeWidth = 3f
                }
            }
            canvas.restore()

            current.strokes.forEach { renderer.draw(canvas, it.stroke, transform) }

            if (toolProvider() == ToolMode.LASSO) {
                current.selectedStrokeBounds()?.let { bounds ->
                    val rect = RectF(bounds[0], bounds[1], bounds[2], bounds[3])
                    transform.mapRect(rect)
                    val padding = 10f
                    canvas.drawRect(
                        rect.left - padding,
                        rect.top - padding,
                        rect.right + padding,
                        rect.bottom + padding,
                        selectionPaint,
                    )
                }
            }
        }
    }
}
