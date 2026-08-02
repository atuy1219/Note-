package com.atuy.note.ink

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
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
import com.atuy.note.data.PageImage
import com.atuy.note.data.PageSession
import com.atuy.note.data.RuntimeStroke
import com.atuy.note.data.ToolMode
import com.atuy.note.data.toBrush
import com.atuy.note.data.toRuntimeStroke
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
    private var backgroundBitmap: Bitmap? = null
    private var imageBitmaps: Map<String, Bitmap> = emptyMap()
    private var toolProvider: () -> ToolMode = { ToolMode.PEN }
    private var brushProvider: () -> BrushSpec = { BrushSpec() }
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

    init {
        setWillNotDraw(false)
        addView(dryView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(wetView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        isFocusableInTouchMode = true
        wetView.isClickable = false
        wetView.isFocusable = false
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
        this.page = page
        this.backgroundBitmap = background
        this.imageBitmaps = imageBitmaps
        this.toolProvider = toolProvider
        this.brushProvider = brushProvider
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
        dryView.invalidate()
    }

    private fun handleMotionEvent(event: MotionEvent): Boolean {
        if (event.pointerCount <= 0) return false
        val index = event.actionIndex.coerceIn(0, event.pointerCount - 1)
        val toolType = event.getToolType(index)
        val stylus = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
        if (!stylus) return false

        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            requestDisallowInterceptTouchEvent(true)
            onActivated()
        }

        val scale = contentScale()
        if (toolProvider() == ToolMode.IMAGE) return handleImageMotion(event, index)
        if (toolProvider() == ToolMode.LASSO) return handleLassoMotion(event, index, scale)

        predictor.record(event)
        val pointerId = event.getPointerId(index)
        val temporaryEraser = toolType == MotionEvent.TOOL_TYPE_ERASER ||
            event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0 ||
            event.buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY != 0
        val erasing = toolProvider() == ToolMode.ERASER || temporaryEraser
        val worldX = event.getX(index) / scale
        val worldY = event.getY(index) / scale

        if (erasing) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    if (!eraserGestureActive) {
                        eraserGestureActive = true
                        onEraseStart()
                    }
                    onErase(worldX, worldY, 28f)
                    dryView.invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    for (pointerIndex in 0 until event.pointerCount) {
                        onErase(event.getX(pointerIndex) / scale, event.getY(pointerIndex) / scale, 28f)
                    }
                    dryView.invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    onErase(worldX, worldY, 28f)
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

        return handleAuthoredStroke(event, index, scale, brushProvider().toBrush(), false)
    }

    private fun handleLassoMotion(event: MotionEvent, pointerIndex: Int, scale: Float): Boolean {
        val currentPage = page ?: return true
        val worldX = event.getX(pointerIndex) / scale
        val worldY = event.getY(pointerIndex) / scale

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (currentPage.isPointInsideStrokeSelection(worldX, worldY) && onSelectedTransformStart()) {
                    selectedDragActive = true
                    selectedDragStartX = worldX
                    selectedDragStartY = worldY
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (selectedDragActive) {
                    onSelectedMove(worldX - selectedDragStartX, worldY - selectedDragStartY)
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
        return handleAuthoredStroke(event, pointerIndex, scale, lassoBrush, true)
    }

    private fun handleAuthoredStroke(
        event: MotionEvent,
        pointerIndex: Int,
        scale: Float,
        brush: Brush,
        lasso: Boolean,
    ): Boolean {
        predictor.record(event)
        val pointerId = event.getPointerId(pointerIndex)
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                requestUnbufferedDispatch(event)
                val transform = Matrix().apply { setScale(1f / scale, 1f / scale) }
                val id = wetView.startStroke(
                    event = event,
                    pointerId = pointerId,
                    brush = brush,
                    motionEventToWorldTransform = transform,
                    strokeToWorldTransform = Matrix(),
                )
                pointerStrokes[pointerId] = id
                if (lasso) lassoStrokeIds += id else strokeBrushSpecs[id] = brushProvider()
                true
            }
            MotionEvent.ACTION_MOVE -> {
                for (index in 0 until event.pointerCount) {
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
        val scale = contentScale()
        val worldX = event.getX(pointerIndex) / scale
        val worldY = event.getY(pointerIndex) / scale
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val hit = currentPage.images.asReversed().firstOrNull { image ->
                    worldX >= image.x && worldX <= image.x + image.width &&
                        worldY >= image.y && worldY <= image.y + image.height
                }
                onImageSelected(hit?.id)
                dryView.invalidate()
                if (hit != null && onImageTransformStart(hit.id)) {
                    draggingImageId = hit.id
                    imageDragOffsetX = worldX - hit.x
                    imageDragOffsetY = worldY - hit.y
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val id = draggingImageId ?: return true
                onImageMove(id, worldX - imageDragOffsetX, worldY - imageDragOffsetY)
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

    private fun contentScale(): Float {
        val current = page ?: return 1f
        if (width == 0 || height == 0) return 1f
        return min(width / current.width, height / current.height).coerceAtLeast(0.0001f)
    }

    private inner class DryInkView(context: Context) : View(context) {
        private val renderer = CanvasStrokeRenderer.create()
        private val transform = Matrix()
        private val paperPaint = Paint().apply { color = Color.WHITE }
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
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paperPaint)
            backgroundBitmap?.let { bitmap ->
                canvas.drawBitmap(bitmap, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), null)
            }
            val current = page ?: return
            val scale = contentScale()

            current.images.forEach { image ->
                val bitmap = imageBitmaps[image.entryName] ?: return@forEach
                val rect = imageRect(image, scale)
                canvas.drawBitmap(bitmap, null, rect, imagePaint)
                if (toolProvider() == ToolMode.IMAGE && current.selectedImageId == image.id) {
                    canvas.drawRect(rect, selectionPaint)
                }
            }

            transform.reset()
            transform.setScale(scale, scale)
            current.strokes.forEach { renderer.draw(canvas, it.stroke, transform) }

            if (toolProvider() == ToolMode.LASSO) {
                current.selectedStrokeBounds()?.let { bounds ->
                    val padding = 10f * scale
                    canvas.drawRect(
                        bounds[0] * scale - padding,
                        bounds[1] * scale - padding,
                        bounds[2] * scale + padding,
                        bounds[3] * scale + padding,
                        selectionPaint,
                    )
                }
            }
        }

        private fun imageRect(image: PageImage, scale: Float) = RectF(
            image.x * scale,
            image.y * scale,
            (image.x + image.width) * scale,
            (image.y + image.height) * scale,
        )
    }
}
