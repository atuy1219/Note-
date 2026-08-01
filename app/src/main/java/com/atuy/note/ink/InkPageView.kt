package com.atuy.note.ink

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import androidx.input.motionprediction.MotionEventPredictor
import com.atuy.note.data.BrushSpec
import com.atuy.note.data.InkSample
import com.atuy.note.data.PageSession
import com.atuy.note.data.RuntimeStroke
import com.atuy.note.data.ToolMode
import com.atuy.note.data.toBrush
import com.atuy.note.data.toStoredStroke
import kotlin.math.min

class InkPageView(context: Context) : FrameLayout(context) {
    private val dryView = DryInkView(context)
    private val wetView = InProgressStrokesView(context)
    private val predictor = MotionEventPredictor.newInstance(this)
    private val pointerStrokes = mutableMapOf<Int, InProgressStrokeId>()
    private val strokeSamples = mutableMapOf<InProgressStrokeId, MutableList<InkSample>>()
    private val strokeBrushSpecs = mutableMapOf<InProgressStrokeId, BrushSpec>()
    private var page: PageSession? = null
    private var backgroundBitmap: Bitmap? = null
    private var toolProvider: () -> ToolMode = { ToolMode.PEN }
    private var brushProvider: () -> BrushSpec = { BrushSpec() }
    private var onStrokeAdded: (RuntimeStroke) -> Unit = {}
    private var onErase: (Float, Float, Float) -> Unit = { _, _, _ -> }
    private var onActivated: () -> Unit = {}

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
                    val samples = strokeSamples.remove(id).orEmpty()
                    val spec = strokeBrushSpecs.remove(id) ?: brushProvider()
                    val stored = stroke.toStoredStroke(spec, samples)
                    onStrokeAdded(RuntimeStroke(stored, stroke))
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
        toolProvider: () -> ToolMode,
        brushProvider: () -> BrushSpec,
        onStrokeAdded: (RuntimeStroke) -> Unit,
        onErase: (Float, Float, Float) -> Unit,
        onActivated: () -> Unit,
    ) {
        this.page = page
        this.backgroundBitmap = background
        this.toolProvider = toolProvider
        this.brushProvider = brushProvider
        this.onStrokeAdded = onStrokeAdded
        this.onErase = onErase
        this.onActivated = onActivated
        dryView.page = page
        dryView.backgroundBitmap = background
        dryView.invalidate()
    }

    private fun handleMotionEvent(event: MotionEvent): Boolean {
        val index = event.actionIndex.coerceAtLeast(0)
        val toolType = event.getToolType(index)
        val stylus = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
        if (!stylus) return false

        onActivated()
        predictor.record(event)
        val pointerId = event.getPointerId(index)
        val temporaryEraser = toolType == MotionEvent.TOOL_TYPE_ERASER ||
            event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0 ||
            event.buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY != 0
        val erasing = toolProvider() == ToolMode.ERASER || temporaryEraser
        val scale = contentScale()
        val worldX = event.getX(index) / scale
        val worldY = event.getY(index) / scale

        if (erasing) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
                onErase(worldX, worldY, 28f)
            }
            return true
        }

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                requestUnbufferedDispatch(event)
                val transform = Matrix().apply { setScale(1f / scale, 1f / scale) }
                val id = wetView.startStroke(
                    event = event,
                    pointerId = pointerId,
                    brush = brushProvider().toBrush(),
                    motionEventToWorldTransform = transform,
                    strokeToWorldTransform = Matrix(),
                )
                pointerStrokes[pointerId] = id
                strokeSamples[id] = mutableListOf(sample(event, index, scale))
                strokeBrushSpecs[id] = brushProvider()
                true
            }
            MotionEvent.ACTION_MOVE -> {
                for (pointerIndex in 0 until event.pointerCount) {
                    val id = pointerStrokes[event.getPointerId(pointerIndex)] ?: continue
                    val samples = strokeSamples[id] ?: continue
                    for (historyIndex in 0 until event.historySize) {
                        samples += InkSample(
                            x = event.getHistoricalX(pointerIndex, historyIndex) / scale,
                            y = event.getHistoricalY(pointerIndex, historyIndex) / scale,
                            pressure = event.getHistoricalPressure(pointerIndex, historyIndex),
                        )
                    }
                    samples += sample(event, pointerIndex, scale)
                    val predicted = predictor.predict()
                    try {
                        wetView.addToStroke(event, event.getPointerId(pointerIndex), id, predicted)
                    } finally {
                        predicted?.recycle()
                    }
                }
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = pointerStrokes.remove(pointerId) ?: return true
                strokeSamples[id]?.add(sample(event, index, scale))
                wetView.finishStroke(event, pointerId, id)
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                pointerStrokes.values.forEach { id -> wetView.cancelStroke(id, event) }
                pointerStrokes.clear()
                strokeSamples.clear()
                strokeBrushSpecs.clear()
                true
            }
            else -> true
        }
    }

    private fun sample(event: MotionEvent, pointerIndex: Int, scale: Float) = InkSample(
        x = event.getX(pointerIndex) / scale,
        y = event.getY(pointerIndex) / scale,
        pressure = event.getPressure(pointerIndex).coerceIn(0f, 1f),
    )

    private fun contentScale(): Float {
        val current = page ?: return 1f
        if (width == 0 || height == 0) return 1f
        return min(width / current.width, height / current.height).coerceAtLeast(0.0001f)
    }

    private inner class DryInkView(context: Context) : View(context) {
        private val renderer = CanvasStrokeRenderer.create()
        private val transform = Matrix()
        private val paperPaint = Paint().apply { color = Color.WHITE }
        var page: PageSession? = null
        var backgroundBitmap: Bitmap? = null

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paperPaint)
            backgroundBitmap?.let { bitmap ->
                canvas.drawBitmap(bitmap, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), null)
            }
            val current = page ?: return
            val scale = contentScale()
            transform.reset()
            transform.setScale(scale, scale)
            current.strokes.forEach { renderer.draw(canvas, it.stroke, transform) }
        }
    }
}
