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
import android.view.ScaleGestureDetector
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
import com.atuy.note.data.PageSession
import com.atuy.note.data.RuntimeStroke
import com.atuy.note.data.ToolMode
import com.atuy.note.data.toBrush
import com.atuy.note.data.toRuntimeStroke
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class InkPageView(context: Context) : FrameLayout(context) {
    private val dryView = DryInkView(context)
    private val wetView = InProgressStrokesView(context)
    private val predictor = MotionEventPredictor.newInstance(this)
    private var scaleGestureInProgress = false
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                scaleGestureInProgress = true
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoomAt(
                    detector.scaleFactor.coerceIn(0.75f, 1.33f),
                    detector.focusX,
                    detector.focusY,
                )
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                scaleGestureInProgress = false
            }
        },
    )
    private sealed interface ViewportMutation {
        data class Zoom(val scaleFactor: Float, val focusX: Float, val focusY: Float) : ViewportMutation
        data class Pan(val dx: Float, val dy: Float) : ViewportMutation
    }

    private val pointerStrokes = mutableMapOf<Int, InProgressStrokeId>()
    private val pendingFinishedStrokeIds = mutableSetOf<InProgressStrokeId>()
    private val pendingViewportMutations = ArrayDeque<ViewportMutation>()
    private val strokeBrushSpecs = mutableMapOf<InProgressStrokeId, BrushSpec>()
    private val lassoStrokeIds = mutableSetOf<InProgressStrokeId>()
    private val pendingCircleLasso = mutableMapOf<String, Runnable>()
    private val lassoBrush = Brush.createWithColorIntArgb(
        StockBrushes.dashedLine(),
        0xFF1976D2.toInt(),
        3.2f,
        0.1f,
    )

    private var page: PageSession? = null
    private var boundPageId: String? = null
    private var boundContentVersion = -1
    private var toolProvider: () -> ToolMode = { ToolMode.PEN }
    private var brushProvider: () -> BrushSpec = { BrushSpec() }
    private var navigationGestureProvider: () -> NavigationGestureMode = { NavigationGestureMode.ONE_FINGER }
    private var circleToLassoEnabledProvider: () -> Boolean = { false }
    private var readOnlyProvider: () -> Boolean = { false }
    private var onNavigationPan: (Float, Float) -> Unit = { _, _ -> }
    private var onStrokeAdded: (RuntimeStroke) -> Unit = {}
    private var onEraseStart: () -> Unit = {}
    private var onErase: (Float, Float, Float) -> Unit = { _, _, _ -> }
    private var onEraseEnd: () -> Unit = {}
    private var onLassoFinished: (Stroke) -> Unit = {}
    private var onCircleHoldLasso: (String, Stroke) -> Unit = { _, _ -> }
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
    private val viewport = InkViewport()
    private var previousTouchCentroidX = 0f
    private var previousTouchCentroidY = 0f
    private var touchGestureActive = false

    init {
        setWillNotDraw(false)
        addView(dryView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(wetView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        isFocusableInTouchMode = true
        wetView.isClickable = false
        wetView.isFocusable = false
        // Ink receives the same raw MotionEvents as this exactly-overlaid view.
        wetView.motionEventToViewTransform = Matrix()
        scaleDetector.isQuickScaleEnabled = false
        post { wetView.eagerInit() }
        wetView.addFinishedStrokesListener(object : InProgressStrokesFinishedListener {
            override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
                strokes.forEach { (id, stroke) ->
                    if (lassoStrokeIds.remove(id)) {
                        onLassoFinished(stroke)
                    } else {
                        val spec = strokeBrushSpecs.remove(id) ?: brushProvider()
                        val runtime = stroke.toRuntimeStroke(spec)
                        onStrokeAdded(runtime)
                        if (circleToLassoEnabledProvider() && looksLikeClosedLoop(runtime)) {
                            val runnable = Runnable {
                                pendingCircleLasso.remove(runtime.stored.id)
                                onCircleHoldLasso(runtime.stored.id, stroke)
                            }
                            pendingCircleLasso[runtime.stored.id] = runnable
                            postDelayed(runnable, CIRCLE_TO_LASSO_DELAY_MS)
                        }
                    }
                }
                val finishedIds = strokes.keys.toSet()
                // Keep the wet copy until the frame in which the newly committed dry stroke is drawn.
                // Camera mutations are deferred during this handoff so both layers always share one view.
                dryView.postInvalidateOnAnimation()
                postOnAnimation {
                    wetView.removeFinishedStrokes(finishedIds)
                    pendingFinishedStrokeIds.removeAll(finishedIds)
                    flushPendingViewportMutations()
                    dryView.postInvalidateOnAnimation()
                }
            }
        })
    }

    fun bind(
        page: PageSession,
        contentVersion: Int = page.contentVersion,
        background: Bitmap?,
        imageBitmaps: Map<String, Bitmap>,
        toolProvider: () -> ToolMode,
        brushProvider: () -> BrushSpec,
        navigationGestureProvider: () -> NavigationGestureMode,
        circleToLassoEnabledProvider: () -> Boolean,
        readOnlyProvider: () -> Boolean = { false },
        onNavigationPan: (Float, Float) -> Unit,
        onStrokeAdded: (RuntimeStroke) -> Unit,
        onEraseStart: () -> Unit,
        onErase: (Float, Float, Float) -> Unit,
        onEraseEnd: () -> Unit,
        onLassoFinished: (Stroke) -> Unit,
        onCircleHoldLasso: (String, Stroke) -> Unit,
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
            cancelPendingCircleLasso()
            boundPageId = page.id
            boundContentVersion = -1
            viewport.reset()
        }
        this.page = page
        this.toolProvider = toolProvider
        this.brushProvider = brushProvider
        this.navigationGestureProvider = navigationGestureProvider
        this.circleToLassoEnabledProvider = circleToLassoEnabledProvider
        this.readOnlyProvider = readOnlyProvider
        this.onNavigationPan = onNavigationPan
        this.onStrokeAdded = onStrokeAdded
        this.onEraseStart = onEraseStart
        this.onErase = onErase
        this.onEraseEnd = onEraseEnd
        this.onLassoFinished = onLassoFinished
        this.onCircleHoldLasso = onCircleHoldLasso
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
        if (boundContentVersion != contentVersion) {
            boundContentVersion = contentVersion
            dryView.postInvalidateOnAnimation()
        } else {
            dryView.invalidate()
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN ||
            event.actionMasked == MotionEvent.ACTION_POINTER_DOWN
        ) {
            requestDisallowInterceptTouchEvent(true)
        }
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = handleMotionEvent(event)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clampViewport()
        updateViewportMatrices()
        dryView.postInvalidateOnAnimation()
    }

    private fun handleMotionEvent(event: MotionEvent): Boolean {
        if (event.pointerCount <= 0) return false
        val actionIndex = event.actionIndex.coerceIn(0, event.pointerCount - 1)
        val stylusIndex = (0 until event.pointerCount).firstOrNull { index ->
            val type = event.getToolType(index)
            type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER
        }
        val routedIndex = if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            stylusIndex ?: actionIndex
        } else {
            actionIndex
        }
        val actionToolType = event.getToolType(routedIndex)
        val actionIsStylus = actionToolType == MotionEvent.TOOL_TYPE_STYLUS ||
            actionToolType == MotionEvent.TOOL_TYPE_ERASER

        if (readOnlyProvider() && actionIsStylus) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN ||
                event.actionMasked == MotionEvent.ACTION_POINTER_DOWN
            ) {
                onActivated()
            }
            return true
        }

        if (!actionIsStylus) {
            if (stylusIndex != null) return true
            return handleTouchMotion(event)
        }

        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            cancelPendingCircleLasso()
            requestDisallowInterceptTouchEvent(true)
            onActivated()
        }

        if (toolProvider() == ToolMode.IMAGE) return handleImageMotion(event, routedIndex)
        if (toolProvider() == ToolMode.LASSO) return handleLassoMotion(event, routedIndex)

        val pointerId = event.getPointerId(routedIndex)
        val temporaryEraser = actionToolType == MotionEvent.TOOL_TYPE_ERASER ||
            event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0 ||
            event.buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY != 0
        val erasing = toolProvider() == ToolMode.ERASER || temporaryEraser
        val point = mapViewToWorld(event.getX(routedIndex), event.getY(routedIndex))

        if (erasing) {
            val radius = 28f / viewport.zoom
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

        return handleAuthoredStroke(event, routedIndex, brushProvider().toBrush(), false)
    }

    private fun handleTouchMotion(event: MotionEvent): Boolean {
        val touchIndices = (0 until event.pointerCount).filter {
            event.getToolType(it) == MotionEvent.TOOL_TYPE_FINGER
        }
        if (touchIndices.isEmpty()) return false

        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelPendingCircleLasso()
                requestDisallowInterceptTouchEvent(true)
                onActivated()
                touchGestureActive = true
                val centroid = touchCentroid(event, touchIndices)
                previousTouchCentroidX = centroid.x
                previousTouchCentroidY = centroid.y
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                requestDisallowInterceptTouchEvent(true)
                val centroid = touchCentroid(event, touchIndices)
                previousTouchCentroidX = centroid.x
                previousTouchCentroidY = centroid.y
                touchGestureActive = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!touchGestureActive) return true
                val centroid = touchCentroid(event, touchIndices)
                val dx = centroid.x - previousTouchCentroidX
                val dy = centroid.y - previousTouchCentroidY

                if (touchIndices.size >= 2) {
                    if (viewport.zoom > 1.001f || scaleDetector.isInProgress || scaleGestureInProgress) {
                        panViewport(dx, dy)
                    } else if (navigationGestureProvider() == NavigationGestureMode.TWO_FINGER) {
                        onNavigationPan(dx, dy)
                    }
                } else if (!scaleDetector.isInProgress && !scaleGestureInProgress) {
                    if (viewport.zoom > 1.001f) {
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
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touchGestureActive = false
                scaleGestureInProgress = false
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

    private fun zoomAt(scaleFactor: Float, focusX: Float, focusY: Float) {
        if (pendingFinishedStrokeIds.isNotEmpty()) {
            pendingViewportMutations.addLast(ViewportMutation.Zoom(scaleFactor, focusX, focusY))
            return
        }
        if (applyZoom(scaleFactor, focusX, focusY)) {
            updateViewportMatrices()
            dryView.postInvalidateOnAnimation()
        }
    }

    private fun panViewport(dx: Float, dy: Float) {
        if (pendingFinishedStrokeIds.isNotEmpty()) {
            pendingViewportMutations.addLast(ViewportMutation.Pan(dx, dy))
            return
        }
        if (applyPan(dx, dy)) {
            updateViewportMatrices()
            dryView.postInvalidateOnAnimation()
        }
    }

    private fun applyZoom(scaleFactor: Float, focusX: Float, focusY: Float): Boolean {
        val currentPage = page ?: return false
        if (width <= 0 || height <= 0) return false
        return viewport.zoomAt(
            scaleFactor = scaleFactor,
            focusX = focusX,
            focusY = focusY,
            pageWidth = currentPage.width,
            pageHeight = currentPage.height,
            viewWidth = width.toFloat(),
            viewHeight = height.toFloat(),
        )
    }

    private fun applyPan(dx: Float, dy: Float): Boolean {
        val currentPage = page ?: return false
        if (width <= 0 || height <= 0) return false
        viewport.panBy(
            dx = dx,
            dy = dy,
            pageWidth = currentPage.width,
            pageHeight = currentPage.height,
            viewWidth = width.toFloat(),
            viewHeight = height.toFloat(),
        )
        return true
    }

    private fun flushPendingViewportMutations() {
        if (pendingFinishedStrokeIds.isNotEmpty() || pendingViewportMutations.isEmpty()) return
        var changed = false
        while (pendingViewportMutations.isNotEmpty()) {
            when (val mutation = pendingViewportMutations.removeFirst()) {
                is ViewportMutation.Zoom -> {
                    changed = applyZoom(mutation.scaleFactor, mutation.focusX, mutation.focusY) || changed
                }
                is ViewportMutation.Pan -> {
                    changed = applyPan(mutation.dx, mutation.dy) || changed
                }
            }
        }
        if (changed) {
            updateViewportMatrices()
            dryView.postInvalidateOnAnimation()
        }
    }

    private fun clampViewport() {
        val currentPage = page ?: return
        if (width <= 0 || height <= 0) return
        viewport.clamp(
            pageWidth = currentPage.width,
            pageHeight = currentPage.height,
            viewWidth = width.toFloat(),
            viewHeight = height.toFloat(),
        )
    }

    private fun updateViewportMatrices() {
        val transform = currentViewportTransform() ?: return
        worldToView.setValues(
            floatArrayOf(
                transform.scale, 0f, transform.translateX,
                0f, transform.scale, transform.translateY,
                0f, 0f, 1f,
            ),
        )
        check(worldToView.invert(viewToWorld)) { "Ink viewport matrix must be invertible" }
    }

    private fun currentViewportTransform(): ViewportTransform? {
        val currentPage = page ?: return null
        if (width <= 0 || height <= 0) return null
        return viewport.transform(
            pageWidth = currentPage.width,
            pageHeight = currentPage.height,
            viewWidth = width.toFloat(),
            viewHeight = height.toFloat(),
        )
    }

    private fun mapViewToWorld(x: Float, y: Float): PointF {
        val point = currentViewportTransform()?.viewToWorld(x, y) ?: return PointF(x, y)
        return PointF(point.x, point.y)
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
        updateViewportMatrices()

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                requestUnbufferedDispatch(event)
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
                val predicted = predictor.predict()
                try {
                    for (index in 0 until event.pointerCount) {
                        val type = event.getToolType(index)
                        if (type != MotionEvent.TOOL_TYPE_STYLUS && type != MotionEvent.TOOL_TYPE_ERASER) continue
                        val currentPointerId = event.getPointerId(index)
                        val id = pointerStrokes[currentPointerId] ?: continue
                        wetView.addToStroke(event, currentPointerId, id, predicted)
                    }
                } finally {
                    predicted?.recycle()
                }
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                pointerStrokes.remove(pointerId)?.let { id ->
                    pendingFinishedStrokeIds += id
                    wetView.finishStroke(event, pointerId, id)
                }
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

    private fun cancelPendingCircleLasso() {
        pendingCircleLasso.values.forEach(::removeCallbacks)
        pendingCircleLasso.clear()
    }

    private fun looksLikeClosedLoop(runtime: RuntimeStroke): Boolean {
        val samples = runtime.samples
        if (samples.size < 12) return false
        val minX = samples.minOf { it.x }
        val maxX = samples.maxOf { it.x }
        val minY = samples.minOf { it.y }
        val maxY = samples.maxOf { it.y }
        val width = maxX - minX
        val height = maxY - minY
        val minimumDimension = min(width, height)
        if (minimumDimension < MIN_CIRCLE_DIAMETER) return false
        val first = samples.first()
        val last = samples.last()
        val closingDistance = hypot(last.x - first.x, last.y - first.y)
        if (closingDistance > max(MAX_CIRCLE_GAP, minimumDimension * MAX_CIRCLE_GAP_RATIO)) return false
        val pathLength = samples.zipWithNext().sumOf { (a, b) ->
            hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
        }.toFloat()
        return pathLength >= (width + height) * MIN_CIRCLE_PATH_RATIO
    }

    private fun finishEraserGesture() {
        if (!eraserGestureActive) return
        eraserGestureActive = false
        onEraseEnd()
    }

    private fun releaseParentIntercept() {
        requestDisallowInterceptTouchEvent(false)
    }

    private companion object {
        const val CIRCLE_TO_LASSO_DELAY_MS = 1_200L
        const val MIN_CIRCLE_DIAMETER = 72f
        const val MAX_CIRCLE_GAP = 28f
        const val MAX_CIRCLE_GAP_RATIO = 0.24f
        const val MIN_CIRCLE_PATH_RATIO = 1.15f
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
            backgroundBitmap?.takeUnless { it.isRecycled }?.let { bitmap ->
                canvas.drawBitmap(bitmap, null, RectF(0f, 0f, current.width, current.height), imagePaint)
            }
            current.images.forEach { image ->
                val bitmap = imageBitmaps[image.entryName]?.takeUnless { it.isRecycled } ?: return@forEach
                val rect = RectF(image.x, image.y, image.x + image.width, image.y + image.height)
                canvas.drawBitmap(bitmap, null, rect, imagePaint)
                if (toolProvider() == ToolMode.IMAGE && current.selectedImageId == image.id) {
                    val worldStroke = selectionPaint.strokeWidth / (currentViewportTransform()?.scale ?: 1f)
                    selectionPaint.strokeWidth = worldStroke
                    canvas.drawRect(rect, selectionPaint)
                    selectionPaint.strokeWidth = 3f
                }
            }
            // CanvasStrokeRenderer only uses this argument to understand screen-space scale; the
            // Canvas must already contain the same transform used for the PDF and page images.
            current.strokes.forEach { renderer.draw(canvas, it.stroke, transform) }
            canvas.restore()

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
