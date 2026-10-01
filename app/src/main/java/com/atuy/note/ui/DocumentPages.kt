package com.atuy.note.ui

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.atuy.note.data.NavigationGestureMode
import com.atuy.note.data.NoteSession
import com.atuy.note.data.PageSession
import com.atuy.note.data.ScrollAxis
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun DocumentPages(
    session: NoteSession,
    scrollAxis: ScrollAxis,
    navigationMode: NavigationGestureMode,
    readOnly: Boolean,
    modifier: Modifier,
    onVisiblePage: (Int) -> Unit,
    navigationBlocked: () -> Boolean,
    pageContent: @Composable (Int, PageSession, Dp) -> Unit,
) {
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    val verticalState = rememberLazyListState(session.activePageIndex)
    val horizontalState = rememberLazyListState(session.activePageIndex)
    val horizontalPan = rememberScrollState()
    val verticalPan = rememberScrollState()
    val vertical = scrollAxis == ScrollAxis.VERTICAL
    val mainState = if (vertical) verticalState else horizontalState
    val crossState = if (vertical) horizontalPan else verticalPan
    var pendingCrossScroll by remember { mutableStateOf<Float?>(null) }
    var pendingZoomAnchor by remember { mutableStateOf<DocumentZoomAnchor?>(null) }
    var flingJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val decay = rememberSplineBasedDecay<Float>()
    val density = LocalDensity.current
    val blockedProvider by rememberUpdatedState(navigationBlocked)
    var lastNavigationVersion by rememberSaveable { mutableIntStateOf(session.pageNavigationVersion) }
    var lastScrollAxis by rememberSaveable { mutableStateOf(scrollAxis) }

    LaunchedEffect(session.pageNavigationVersion, scrollAxis) {
        if (lastNavigationVersion != session.pageNavigationVersion || lastScrollAxis != scrollAxis) {
            flingJob?.cancel()
            val target = session.activePageIndex.coerceIn(0, session.pages.lastIndex)
            lastNavigationVersion = session.pageNavigationVersion
            lastScrollAxis = scrollAxis
            mainState.animateScrollToItem(target)
        }
    }
    val visibleCallback by rememberUpdatedState(onVisiblePage)
    LaunchedEffect(mainState) {
        snapshotFlow {
            val layout = mainState.layoutInfo
            layout.visibleItemsInfo.maxByOrNull { item ->
                (minOf(item.offset + item.size, layout.viewportEndOffset) -
                    maxOf(item.offset, layout.viewportStartOffset)).coerceAtLeast(0)
            }?.index
        }.collect { index -> index?.let(visibleCallback) }
    }

    BoxWithConstraints(modifier) {
        val baseWidth = (maxWidth - DOCUMENT_PAGE_GAP * 2).coerceIn(1.dp, 900.dp)
        val pageWidth = baseWidth * zoom
        val pageWidthPx = with(density) { pageWidth.toPx() }
        val baseWidthPx = with(density) { baseWidth.toPx() }
        val viewportCross = with(density) { if (vertical) maxWidth.toPx() else maxHeight.toPx() }
        val viewportCenter = Offset(with(density) { maxWidth.toPx() } / 2f, with(density) { maxHeight.toPx() } / 2f)
        val gapPx = with(density) { DOCUMENT_PAGE_GAP.toPx() }
        val tallestRatio = session.pages.maxOf { it.height / it.width }
        fun crossContentSize(width: Float): Float = maxOf(
            if (vertical) width else width * tallestRatio + gapPx * 2,
            viewportCross,
        )
        val contentSize = crossContentSize(pageWidthPx)
        fun pan(delta: Offset): Offset {
            val main = mainState.dispatchRawDelta(if (vertical) -delta.y else -delta.x)
            val cross = crossState.dispatchRawDelta(if (vertical) -delta.x else -delta.y)
            return if (vertical) Offset(-cross, -main) else Offset(-main, -cross)
        }

        val transformCallback by rememberUpdatedState<(Offset, Offset, Float) -> Unit> { focus, delta, factor ->
            val nextZoom = resolveDocumentZoom(zoom, factor)
            if (!zoomChanged(zoom, nextZoom)) {
                pan(delta)
            } else {
                val previousFocus = focus - delta
                val focusMain = if (vertical) focus.y else focus.x
                val previousMain = if (vertical) previousFocus.y else previousFocus.x
                val layout = mainState.layoutInfo
                val padding = layout.beforeContentPadding.toFloat()
                val item = layout.visibleItemsInfo.minByOrNull {
                    distanceToPage(previousMain - padding, it.offset.toFloat(), it.size.toFloat())
                }
                if (item != null) {
                    val pageIndex = pendingZoomAnchor?.page?.pageIndex ?: item.index
                    val page = session.pages[pageIndex]
                    val oldWidth = if (vertical) item.size * page.width / page.height else item.size.toFloat()
                    val nextWidth = baseWidthPx * nextZoom
                    val nextMainSize = if (vertical) nextWidth * page.height / page.width else nextWidth
                    val oldCrossSize = if (vertical) oldWidth else oldWidth * page.height / page.width
                    val newCrossSize = if (vertical) nextWidth else nextWidth * page.height / page.width
                    val anchor = pendingZoomAnchor ?: DocumentZoomAnchor(
                        pageZoomAnchor(item.index, item.offset.toFloat(), item.size.toFloat(), previousMain, padding),
                        crossZoomFraction(oldCrossSize, crossContentSize(oldWidth), crossState.value.toFloat(),
                            if (vertical) previousFocus.x else previousFocus.y),
                    )
                    pendingZoomAnchor = anchor
                    pendingCrossScroll = anchor.crossScroll(
                        newCrossSize, crossContentSize(nextWidth), if (vertical) focus.x else focus.y,
                    )
                    zoom = nextZoom
                    mainState.requestScrollToItem(pageIndex, anchor.page.scrollOffset(nextMainSize, focusMain, padding))
                }
            }
        }
        val startCallback by rememberUpdatedState<() -> Unit> {
            flingJob?.cancel()
            scope.launch { mainState.stopScroll(); crossState.stopScroll() }
        }
        val finishCallback by rememberUpdatedState<(Velocity) -> Unit> { velocity ->
            flingJob = scope.launch {
                var previousX = 0f
                var previousY = 0f
                launch {
                    AnimationState(0f, velocity.x).animateDecay(decay) {
                        val delta = value - previousX
                        previousX = value
                        if (abs(pan(Offset(delta, 0f)).x) < abs(delta) - 0.5f) cancelAnimation()
                    }
                }
                launch {
                    AnimationState(0f, velocity.y).animateDecay(decay) {
                        val delta = value - previousY
                        previousY = value
                        if (abs(pan(Offset(0f, delta)).y) < abs(delta) - 0.5f) cancelAnimation()
                    }
                }
            }
        }
        val navigationModifier = Modifier.documentNavigationGesture(
            navigationMode, readOnly, { startCallback() },
            { focus, delta, factor -> transformCallback(focus, delta, factor) },
            { finishCallback(it) },
            { blockedProvider() },
        )
        val crossLayoutModifier = Modifier.onGloballyPositioned {
            pendingCrossScroll?.let { target ->
                crossState.dispatchRawDelta(target - crossState.value)
                pendingCrossScroll = null
                pendingZoomAnchor = null
            }
        }

        Box(Modifier.fillMaxSize().then(navigationModifier)) {
            if (vertical) {
                Box(Modifier.fillMaxSize().horizontalScroll(crossState, enabled = false)) {
                    Box(
                        Modifier.width(with(density) { contentSize.toDp() }).fillMaxHeight()
                            .then(crossLayoutModifier),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        LazyColumn(
                            Modifier.width(pageWidth).fillMaxHeight(),
                            state = mainState,
                            userScrollEnabled = false,
                            contentPadding = PaddingValues(vertical = DOCUMENT_PAGE_GAP),
                            verticalArrangement = Arrangement.spacedBy(DOCUMENT_PAGE_GAP),
                        ) {
                            itemsIndexed(session.pages, key = { _, page -> page.id }) { index, page ->
                                pageContent(index, page, pageWidth)
                            }
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxSize().verticalScroll(crossState, enabled = false)) {
                    LazyRow(
                        Modifier.fillMaxWidth().height(with(density) { contentSize.toDp() })
                            .then(crossLayoutModifier),
                        state = mainState,
                        userScrollEnabled = false,
                        contentPadding = PaddingValues(horizontal = DOCUMENT_PAGE_GAP),
                        horizontalArrangement = Arrangement.spacedBy(DOCUMENT_PAGE_GAP),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        itemsIndexed(session.pages, key = { _, page -> page.id }) { index, page ->
                            pageContent(index, page, pageWidth)
                        }
                    }
                }
            }
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            ) {
                TextButton(
                    onClick = {
                        startCallback()
                        transformCallback(
                            viewportCenter,
                            Offset.Zero, 1f / zoom,
                        )
                    },
                    enabled = !blockedProvider(),
                    modifier = Modifier.semantics { contentDescription = "拡大率。タップして100%に戻す" },
                ) {
                    Text("${(zoom * 100).roundToInt()}%")
                }
            }
        }
    }
}

private fun Modifier.documentNavigationGesture(
    mode: NavigationGestureMode,
    readOnly: Boolean,
    onStart: () -> Unit,
    onTransform: (Offset, Offset, Float) -> Unit,
    onFinish: (Velocity) -> Unit,
    blocked: () -> Boolean,
): Modifier = pointerInput(mode, readOnly) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onStart()
        var stylusSeen = down.type != PointerType.Touch
        var pastSlop = false
        var pinched = false
        var accumulatedPan = Offset.Zero
        var accumulatedZoom = 1f
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            stylusSeen = stylusSeen || event.changes.any { it.type != PointerType.Touch }
            val touchCount = event.changes.count { it.pressed && it.type == PointerType.Touch }
            if (!stylusSeen && !blocked() && touchCount > 0) {
                val allowPan = touchCount >= 2 || readOnly || mode == NavigationGestureMode.ONE_FINGER
                if (allowPan) {
                    val delta = event.calculatePan()
                    val factor = if (touchCount >= 2) event.calculateZoom() else 1f
                    val focus = event.calculateCentroid()
                    if (focus != Offset.Unspecified && factor.isFinite() && factor > 0f) {
                        tracker.addPosition(event.changes.first().uptimeMillis, focus)
                        accumulatedPan += delta
                        accumulatedZoom *= factor
                        pinched = pinched || touchCount >= 2
                        if (!pastSlop) {
                            val zoomMotion = abs(1f - accumulatedZoom) * event.calculateCentroidSize(useCurrent = false)
                            pastSlop = accumulatedPan.getDistance() > viewConfiguration.touchSlop ||
                                zoomMotion > viewConfiguration.touchSlop
                        }
                        if (pastSlop) {
                            onTransform(focus, delta, factor)
                            event.changes.filter { it.type == PointerType.Touch }.forEach { it.consume() }
                        }
                    }
                }
            }
            val pressed = event.changes.any { it.pressed }
            if (!pressed && pastSlop && !stylusSeen && !pinched) {
                event.changes.firstOrNull { it.previousPressed }?.let {
                    tracker.addPosition(it.uptimeMillis, it.position)
                }
            }
        } while (pressed)
        if (pastSlop && !pinched && !stylusSeen) {
            onFinish(tracker.calculateVelocity())
        }
    }
}

private val DOCUMENT_PAGE_GAP = 14.dp
