from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


ui_path = Path("app/src/main/java/com/atuy/note/ui/EnhancedNoteApp.kt")
ui = ui_path.read_text()
ui = replace_once(
    ui,
    "import androidx.compose.foundation.layout.padding\n",
    "import androidx.compose.foundation.layout.padding\nimport androidx.compose.foundation.layout.statusBarsPadding\n",
    "status bar import",
)
ui = replace_once(
    ui,
    "    Box(Modifier.fillMaxSize()) {\n        AppFrame(",
    "    Box(Modifier.fillMaxSize().statusBarsPadding()) {\n        AppFrame(",
    "root status bar inset",
)
ui = replace_once(
    ui,
    '        CommandSpec(Icons.Default.Visibility, "閲覧専用", readOnly, onToggleReadOnly),',
    '''        CommandSpec(
            Icons.Default.Visibility,
            "閲覧専用",
            readOnly,
            onToggleReadOnly,
            gapAfter = true,
        ),''',
    "read-only group gap",
)
ui = replace_once(
    ui,
    '''        CommandSpec(Icons.Default.Mic, "音声", activePanel == EditorPanel.VOICE) {
            onPanel(EditorPanel.VOICE)
        },''',
    '''        CommandSpec(
            icon = Icons.Default.Mic,
            description = "音声",
            selected = activePanel == EditorPanel.VOICE,
            onClick = { onPanel(EditorPanel.VOICE) },
            gapAfter = true,
        ),''',
    "voice group gap",
)
ui = replace_once(
    ui,
    '''            LazyColumn(
                modifier = Modifier.width(64.dp).fillMaxHeight(),
                contentPadding = PaddingValues(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(buttons) { spec ->
                    CommandButton(spec)
                }
            }''',
    '''            LazyColumn(
                modifier = Modifier.width(64.dp).fillMaxHeight(),
                contentPadding = PaddingValues(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(buttons) { spec ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CommandButton(spec)
                        if (spec.gapAfter) Spacer(Modifier.height(14.dp))
                    }
                }
            }''',
    "vertical command spacing",
)
ui = replace_once(
    ui,
    '''            LazyRow(
                modifier = Modifier.fillMaxWidth().height(62.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(buttons) { spec ->
                    CommandButton(spec)
                }
            }''',
    '''            LazyRow(
                modifier = Modifier.fillMaxWidth().height(62.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(buttons) { spec ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CommandButton(spec)
                        if (spec.gapAfter) Spacer(Modifier.width(14.dp))
                    }
                }
            }''',
    "horizontal command spacing",
)
ui = replace_once(
    ui,
    '''private data class CommandSpec(
    val icon: ImageVector,
    val description: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)''',
    '''private data class CommandSpec(
    val icon: ImageVector,
    val description: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val gapAfter: Boolean = false,
)''',
    "command spec gap flag",
)
ui = replace_once(
    ui,
    '            Text("囲んだ後、ペンを離さず静止", Modifier.padding(horizontal = 8.dp).weight(1f))',
    '            Text("囲ってペンを離した後、囲みの内側をもう一度長押し", Modifier.padding(horizontal = 8.dp).weight(1f))',
    "circle lasso help",
)
ui = replace_once(
    ui,
    '''@Composable
private fun LassoPanel(viewModel: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(''',
    '''@Composable
private fun LassoPanel(viewModel: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "投げ縄はスタイラスで自由な形に囲めます。線を離すと始点と終点を結んで選択範囲を閉じます。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(''',
    "freehand lasso help",
)
ui_path.write_text(ui)

activity_path = Path("app/src/main/java/com/atuy/note/MainActivity.kt")
activity = activity_path.read_text()
activity = replace_once(
    activity,
    "import androidx.activity.result.IntentSenderRequest\n",
    "import androidx.activity.result.IntentSenderRequest\nimport androidx.activity.result.PickVisualMediaRequest\n",
    "photo picker request import",
)
activity = replace_once(
    activity,
    '''    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importImage)
    }''',
    '''    private val imagePicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::importImage)
    }''',
    "photo picker contract",
)
activity = replace_once(
    activity,
    '                    onImportImage = { imagePicker.launch(arrayOf("image/*")) },',
    '''                    onImportImage = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },''',
    "photo picker launch",
)
activity_path.write_text(activity)

ink_path = Path("app/src/main/java/com/atuy/note/ink/InkPageView.kt")
ink = ink_path.read_text()
ink = replace_once(
    ink,
    "import android.graphics.Paint\n",
    "import android.graphics.Paint\nimport android.graphics.Path\n",
    "path import",
)
ink = replace_once(
    ink,
    "import com.atuy.note.data.BrushSpec\n",
    "import com.atuy.note.data.BrushSpec\nimport com.atuy.note.data.InkSample\n",
    "ink sample import",
)
ink = replace_once(
    ink,
    "    private val pendingCircleLasso = mutableMapOf<String, Runnable>()",
    '''    private data class CircleLassoCandidate(
        val strokeId: String,
        val stroke: Stroke,
        val samples: List<InkSample>,
    )

    private var circleLassoCandidate: CircleLassoCandidate? = null
    private var circleHoldRunnable: Runnable? = null
    private var circleHoldPointerId: Int? = null
    private var circleHoldStartX = 0f
    private var circleHoldStartY = 0f
    private var circleHoldGestureActive = false
    private var circleHoldCancelled = false
    private var lassoOutline: List<PointF> = emptyList()''',
    "circle lasso state",
)
ink = replace_once(
    ink,
    '''                    if (lassoStrokeIds.remove(id)) {
                        onLassoFinished(stroke)
                    } else {''',
    '''                    if (lassoStrokeIds.remove(id)) {
                        lassoOutline = stroke.toRuntimeStroke(BrushSpec()).samples.map { PointF(it.x, it.y) }
                        onLassoFinished(stroke)
                    } else {''',
    "freehand lasso outline capture",
)
ink = replace_once(
    ink,
    '''                        onStrokeAdded(runtime)
                        if (circleToLassoEnabledProvider() && looksLikeClosedLoop(runtime)) {
                            val runnable = Runnable {
                                pendingCircleLasso.remove(runtime.stored.id)
                                onCircleHoldLasso(runtime.stored.id, stroke)
                            }
                            pendingCircleLasso[runtime.stored.id] = runnable
                            postDelayed(runnable, CIRCLE_TO_LASSO_DELAY_MS)
                        }''',
    '''                        onStrokeAdded(runtime)
                        circleLassoCandidate = if (
                            circleToLassoEnabledProvider() && looksLikeClosedLoop(runtime)
                        ) {
                            CircleLassoCandidate(runtime.stored.id, stroke, runtime.samples)
                        } else {
                            null
                        }''',
    "defer circle lasso conversion",
)
ink = replace_once(
    ink,
    '''        if (boundPageId != page.id) {
            cancelPendingCircleLasso()
            boundPageId = page.id''',
    '''        if (boundPageId != page.id) {
            cancelPendingCircleLasso()
            circleLassoCandidate = null
            lassoOutline = emptyList()
            boundPageId = page.id''',
    "clear candidate on page change",
)
ink = replace_once(
    ink,
    '''        if (!actionIsStylus) {
            if (stylusIndex != null) return true''',
    '''        if (actionIsStylus && handleCircleLassoHold(event, routedIndex)) return true

        if (!actionIsStylus) {
            if (stylusIndex != null) return true''',
    "circle long press routing",
)
ink = replace_once(
    ink,
    '''                if (currentPage.isPointInsideStrokeSelection(point.x, point.y) && onSelectedTransformStart()) {
                    selectedDragActive = true''',
    '''                if (currentPage.isPointInsideStrokeSelection(point.x, point.y) && onSelectedTransformStart()) {
                    lassoOutline = emptyList()
                    selectedDragActive = true''',
    "clear outline before transform",
)
ink = replace_once(
    ink,
    '''    private fun cancelPendingCircleLasso() {
        pendingCircleLasso.values.forEach(::removeCallbacks)
        pendingCircleLasso.clear()
    }

    private fun looksLikeClosedLoop(runtime: RuntimeStroke): Boolean {''',
    '''    private fun handleCircleLassoHold(event: MotionEvent, pointerIndex: Int): Boolean {
        if (circleHoldGestureActive) {
            val pointerId = circleHoldPointerId
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val index = pointerId?.let(event::findPointerIndex) ?: -1
                    if (index >= 0 && !circleHoldCancelled) {
                        val point = mapViewToWorld(event.getX(index), event.getY(index))
                        val distance = hypot(point.x - circleHoldStartX, point.y - circleHoldStartY)
                        if (distance > CIRCLE_HOLD_SLOP / viewport.zoom) {
                            circleHoldCancelled = true
                            circleHoldRunnable?.let(::removeCallbacks)
                            circleHoldRunnable = null
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    cancelPendingCircleLasso()
                    releaseParentIntercept()
                }
            }
            return true
        }

        if (event.actionMasked != MotionEvent.ACTION_DOWN &&
            event.actionMasked != MotionEvent.ACTION_POINTER_DOWN
        ) return false
        if (!circleToLassoEnabledProvider() || toolProvider() != ToolMode.PEN) return false

        val candidate = circleLassoCandidate ?: return false
        val point = mapViewToWorld(event.getX(pointerIndex), event.getY(pointerIndex))
        if (!isPointInsideClosedLoop(point.x, point.y, candidate.samples)) return false

        requestDisallowInterceptTouchEvent(true)
        onActivated()
        circleHoldGestureActive = true
        circleHoldCancelled = false
        circleHoldPointerId = event.getPointerId(pointerIndex)
        circleHoldStartX = point.x
        circleHoldStartY = point.y
        val runnable = Runnable {
            if (!circleHoldGestureActive || circleHoldCancelled) return@Runnable
            val current = circleLassoCandidate ?: return@Runnable
            circleLassoCandidate = null
            lassoOutline = current.samples.map { PointF(it.x, it.y) }
            onCircleHoldLasso(current.strokeId, current.stroke)
            dryView.postInvalidateOnAnimation()
        }
        circleHoldRunnable = runnable
        postDelayed(runnable, CIRCLE_HOLD_DELAY_MS)
        return true
    }

    private fun cancelPendingCircleLasso() {
        circleHoldRunnable?.let(::removeCallbacks)
        circleHoldRunnable = null
        circleHoldPointerId = null
        circleHoldGestureActive = false
        circleHoldCancelled = false
    }

    private fun isPointInsideClosedLoop(x: Float, y: Float, samples: List<InkSample>): Boolean {
        if (samples.size < 3) return false
        var inside = false
        var previous = samples.last()
        samples.forEach { current ->
            val crosses = (current.y > y) != (previous.y > y) &&
                x < (previous.x - current.x) * (y - current.y) /
                (previous.y - current.y) + current.x
            if (crosses) inside = !inside
            previous = current
        }
        return inside
    }

    private fun looksLikeClosedLoop(runtime: RuntimeStroke): Boolean {''',
    "circle long press implementation",
)
ink = replace_once(
    ink,
    '''            if (toolProvider() == ToolMode.LASSO) {
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
            }''',
    '''            if (toolProvider() == ToolMode.LASSO) {
                if (lassoOutline.size >= 3 && current.selectedStrokeIds.isNotEmpty()) {
                    val selectionPath = Path().apply {
                        moveTo(lassoOutline.first().x, lassoOutline.first().y)
                        lassoOutline.drop(1).forEach { lineTo(it.x, it.y) }
                        close()
                    }
                    selectionPath.transform(transform)
                    canvas.drawPath(selectionPath, selectionPaint)
                } else {
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
            }''',
    "draw freehand selection outline",
)
ink = replace_once(
    ink,
    "        const val CIRCLE_TO_LASSO_DELAY_MS = 1_200L\n",
    "        const val CIRCLE_HOLD_DELAY_MS = 650L\n        const val CIRCLE_HOLD_SLOP = 18f\n",
    "circle hold constants",
)
ink_path.write_text(ink)
