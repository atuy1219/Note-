package com.atuy.note.ui

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.atuy.note.MainViewModel
import com.atuy.note.data.BrushKind
import com.atuy.note.data.CustomBrushSpec
import com.atuy.note.data.LassoCoverageMode
import com.atuy.note.data.NavigationGestureMode
import com.atuy.note.data.NoteSession
import com.atuy.note.data.PageSession
import com.atuy.note.data.ScrollAxis
import com.atuy.note.data.ToolMode
import com.atuy.note.ink.InkPageView
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun EnhancedNoteApp(
    viewModel: MainViewModel,
    uiPreferences: UiPreferencesState,
    onImportPdf: () -> Unit,
    onImportImage: () -> Unit,
    onSyncDrive: () -> Unit,
) {
    if (viewModel.activeSession == null) {
        NoteApp(
            viewModel = viewModel,
            onImportPdf = onImportPdf,
            onImportImage = onImportImage,
            onSyncDrive = onSyncDrive,
        )
        return
    }

    val snackbar = remember { SnackbarHostState() }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val message = viewModel.statusMessage
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.clearStatus()
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (showSettings) {
            EditorSettingsScreen(
                viewModel = viewModel,
                uiPreferences = uiPreferences,
                onBack = { showSettings = false },
            )
        } else {
            EnhancedEditorScreen(
                viewModel = viewModel,
                uiPreferences = uiPreferences,
                onImportImage = onImportImage,
                snackbar = snackbar,
                onOpenSettings = { showSettings = true },
            )
        }
        if (viewModel.busy) {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
                    Row(
                        Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp)
                        Text("Working…")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnhancedEditorScreen(
    viewModel: MainViewModel,
    uiPreferences: UiPreferencesState,
    onImportImage: () -> Unit,
    snackbar: SnackbarHostState,
    onOpenSettings: () -> Unit,
) {
    val active = viewModel.activeSession ?: return
    var showPages by remember(active.id) { mutableStateOf(false) }
    var showMenu by remember(active.id) { mutableStateOf(false) }
    var renameNote by remember(active.id) { mutableStateOf(false) }
    var deleteNote by remember(active.id) { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(active.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${active.pages.size}ページ • ${if (active.dirty) "保存中" else "保存済み"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = viewModel::showLibrary) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "ライブラリ")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::undo) { Icon(Icons.AutoMirrored.Filled.Undo, "元に戻す") }
                    IconButton(onClick = viewModel::redo) { Icon(Icons.AutoMirrored.Filled.Redo, "やり直す") }
                    IconButton(onClick = { showPages = true }) { Icon(Icons.Default.GridView, "ページ一覧") }
                    IconButton(onClick = viewModel::addPage) { Icon(Icons.Default.Add, "ページ追加") }
                    IconButton(onClick = viewModel::saveActive) { Icon(Icons.Default.Save, "保存") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "設定") }
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "その他") }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("名前を変更") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = {
                                    showMenu = false
                                    renameNote = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("ノートを削除") },
                                leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                                onClick = {
                                    showMenu = false
                                    deleteNote = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (uiPreferences.tabLayoutMode) {
            TabLayoutMode.HORIZONTAL -> Column(Modifier.fillMaxSize().padding(padding)) {
                HorizontalNoteTabs(viewModel, active)
                HorizontalDivider()
                EditorCanvas(viewModel, active, onImportImage, Modifier.weight(1f))
            }
            TabLayoutMode.VERTICAL -> Row(Modifier.fillMaxSize().padding(padding)) {
                VerticalNoteTabs(viewModel, active)
                VerticalDivider(Modifier.fillMaxHeight().width(1.dp))
                EditorCanvas(viewModel, active, onImportImage, Modifier.weight(1f))
            }
        }
    }

    if (showPages) {
        PageOverviewDialog(
            viewModel = viewModel,
            session = active,
            onDismiss = { showPages = false },
            onOpenPage = { index ->
                viewModel.activatePage(index)
                showPages = false
            },
        )
    }
    if (renameNote) {
        NameDialog("ノート名を変更", active.title, onDismiss = { renameNote = false }) {
            renameNote = false
            viewModel.renameActiveNote(it)
        }
    }
    if (deleteNote) {
        AlertDialog(
            onDismissRequest = { deleteNote = false },
            title = { Text("ノートを削除") },
            text = { Text("「${active.title}」を端末から完全に削除します。") },
            confirmButton = {
                Button(onClick = {
                    deleteNote = false
                    viewModel.deleteActiveNote()
                }) { Text("削除") }
            },
            dismissButton = { TextButton(onClick = { deleteNote = false }) { Text("キャンセル") } },
        )
    }
}

@Composable
private fun HorizontalNoteTabs(viewModel: MainViewModel, active: NoteSession) {
    val selectedTab = viewModel.openTabs.indexOfFirst { it.id == active.id }.coerceAtLeast(0)
    androidx.compose.material3.ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 8.dp) {
        viewModel.openTabs.forEach { tab ->
            Tab(
                selected = tab.id == active.id,
                onClick = { viewModel.activateTab(tab.id) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
                        IconButton(onClick = { viewModel.closeTab(tab.id) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Close, "タブを閉じる", Modifier.size(16.dp))
                        }
                    }
                },
            )
        }
        Tab(
            selected = false,
            onClick = { viewModel.createBlankNote("Untitled") },
            text = { Icon(Icons.Default.Add, "新しいタブ") },
        )
    }
}

@Composable
private fun VerticalNoteTabs(viewModel: MainViewModel, active: NoteSession) {
    Surface(modifier = Modifier.width(224.dp).fillMaxHeight(), tonalElevation = 1.dp) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(viewModel.openTabs, key = { _, tab -> tab.id }) { _, tab ->
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                        .background(
                            if (tab.id == active.id) MaterialTheme.colorScheme.secondaryContainer
                            else Color.Transparent,
                        )
                        .clickable { viewModel.activateTab(tab.id) }
                        .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        tab.title,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { viewModel.closeTab(tab.id) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Close, "タブを閉じる", Modifier.size(17.dp))
                    }
                }
            }
            item {
                IconButton(onClick = { viewModel.createBlankNote("Untitled") }) {
                    Icon(Icons.Default.Add, "新しいタブ")
                }
            }
        }
    }
}

@Composable
private fun EditorCanvas(
    viewModel: MainViewModel,
    session: NoteSession,
    onImportImage: () -> Unit,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    var toolbarX by rememberSaveable(session.id) { mutableFloatStateOf(with(density) { 12.dp.toPx() }) }
    var toolbarY by rememberSaveable(session.id) { mutableFloatStateOf(with(density) { 12.dp.toPx() }) }

    Box(modifier.fillMaxSize()) {
        SharedZoomPages(viewModel, session, Modifier.fillMaxSize())
        FloatingInkToolbar(
            viewModel = viewModel,
            onImportImage = onImportImage,
            onDrag = { delta ->
                toolbarX = (toolbarX + delta.x).coerceAtLeast(0f)
                toolbarY = (toolbarY + delta.y).coerceAtLeast(0f)
            },
            modifier = Modifier.offset { IntOffset(toolbarX.roundToInt(), toolbarY.roundToInt()) }
                .zIndex(10f),
        )
    }
}

private enum class ToolbarPanel { PEN, ERASER, HIGHLIGHTER, LASSO, IMAGE, COLOR, SIZE }

@Composable
private fun FloatingInkToolbar(
    viewModel: MainViewModel,
    onImportImage: () -> Unit,
    onDrag: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var panel by remember { mutableStateOf<ToolbarPanel?>(null) }
    var showCustomEditor by remember { mutableStateOf(false) }
    val normalColors = listOf(
        0xFF111111.toInt() to Color(0xFF111111),
        0xFF1565C0.toInt() to Color(0xFF1565C0),
        0xFFC62828.toInt() to Color(0xFFC62828),
        0xFF2E7D32.toInt() to Color(0xFF2E7D32),
        0xFF6A1B9A.toInt() to Color(0xFF6A1B9A),
    )
    val highlighterColors = listOf(
        0x66FFF176 to Color(0x66FFF176),
        0x6681C784 to Color(0x6681C784),
        0x6664B5F6 to Color(0x6664B5F6),
        0x66F48FB1 to Color(0x66F48FB1),
    )
    val colors = if (viewModel.brushSpec.kind == BrushKind.HIGHLIGHTER) highlighterColors else normalColors

    Surface(
        modifier = modifier.widthIn(max = 440.dp),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).size(width = 48.dp, height = 18.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.DragIndicator,
                    contentDescription = "ツールバーを移動",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ToolIconButton(
                    selected = viewModel.toolMode == ToolMode.PEN && viewModel.brushSpec.kind != BrushKind.HIGHLIGHTER,
                    icon = Icons.Default.Brush,
                    description = "ペン",
                ) {
                    if (viewModel.brushSpec.kind == BrushKind.HIGHLIGHTER) viewModel.setBrushKind(BrushKind.PRESSURE_PEN)
                    else viewModel.setTool(ToolMode.PEN)
                    panel = panel.toggle(ToolbarPanel.PEN)
                }
                ToolIconButton(
                    selected = viewModel.toolMode == ToolMode.ERASER,
                    icon = Icons.Default.DeleteOutline,
                    description = "消しゴム",
                ) {
                    viewModel.setTool(ToolMode.ERASER)
                    panel = panel.toggle(ToolbarPanel.ERASER)
                }
                ToolIconButton(
                    selected = viewModel.toolMode == ToolMode.PEN && viewModel.brushSpec.kind == BrushKind.HIGHLIGHTER,
                    icon = Icons.Default.Highlight,
                    description = "蛍光ペン",
                ) {
                    viewModel.setBrushKind(BrushKind.HIGHLIGHTER)
                    panel = panel.toggle(ToolbarPanel.HIGHLIGHTER)
                }
                ToolIconButton(
                    selected = viewModel.toolMode == ToolMode.LASSO,
                    icon = Icons.Default.Gesture,
                    description = "投げ縄",
                ) {
                    viewModel.setTool(ToolMode.LASSO)
                    panel = panel.toggle(ToolbarPanel.LASSO)
                }
                ToolIconButton(
                    selected = viewModel.toolMode == ToolMode.IMAGE,
                    icon = Icons.Default.Image,
                    description = "画像",
                ) {
                    viewModel.setTool(ToolMode.IMAGE)
                    panel = panel.toggle(ToolbarPanel.IMAGE)
                }
                ToolColorButton(
                    color = Color(viewModel.brushSpec.colorArgb),
                    selected = panel == ToolbarPanel.COLOR,
                ) { panel = panel.toggle(ToolbarPanel.COLOR) }
                ToolIconButton(
                    selected = panel == ToolbarPanel.SIZE,
                    icon = Icons.Default.LineWeight,
                    description = "太さ",
                ) { panel = panel.toggle(ToolbarPanel.SIZE) }
            }

            when (panel) {
                ToolbarPanel.PEN -> {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ToolIconButton(
                            selected = viewModel.brushSpec.kind == BrushKind.PRESSURE_PEN,
                            icon = Icons.Default.Edit,
                            description = "筆圧ペン",
                        ) { viewModel.setBrushKind(BrushKind.PRESSURE_PEN) }
                        ToolIconButton(
                            selected = viewModel.brushSpec.kind == BrushKind.MARKER,
                            icon = Icons.Default.BorderColor,
                            description = "マーカー",
                        ) { viewModel.setBrushKind(BrushKind.MARKER) }
                        ToolIconButton(
                            selected = viewModel.brushSpec.kind == BrushKind.CUSTOM,
                            icon = Icons.Default.Tune,
                            description = "カスタムペン",
                        ) {
                            viewModel.setBrushKind(BrushKind.CUSTOM)
                            showCustomEditor = true
                        }
                    }
                }
                ToolbarPanel.ERASER -> {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text("線単位で消去", style = MaterialTheme.typography.labelMedium)
                }
                ToolbarPanel.HIGHLIGHTER, ToolbarPanel.COLOR -> {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        colors.forEach { (argb, color) ->
                            ColorSwatch(color, selected = viewModel.brushSpec.colorArgb == argb) {
                                viewModel.updateBrush(colorArgb = argb)
                            }
                        }
                    }
                }
                ToolbarPanel.LASSO -> {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    LassoPanel(viewModel, normalColors)
                }
                ToolbarPanel.IMAGE -> {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onImportImage) { Icon(Icons.Default.AddPhotoAlternate, "画像を追加") }
                        if (viewModel.activePage?.selectedImageId != null) {
                            IconButton(onClick = { viewModel.scaleSelectedImage(0.85f) }) { Icon(Icons.Default.ZoomOut, "画像を縮小") }
                            IconButton(onClick = { viewModel.scaleSelectedImage(1.15f) }) { Icon(Icons.Default.ZoomIn, "画像を拡大") }
                            IconButton(onClick = viewModel::deleteSelectedImage) { Icon(Icons.Default.DeleteOutline, "画像を削除") }
                        }
                    }
                }
                ToolbarPanel.SIZE -> {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Column {
                        Slider(
                            value = viewModel.brushSpec.size,
                            onValueChange = { viewModel.updateBrush(size = it) },
                            valueRange = if (viewModel.brushSpec.kind == BrushKind.HIGHLIGHTER) 8f..48f else 1f..24f,
                        )
                        Text(
                            "${"%.1f".format(viewModel.brushSpec.size)} pt",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.align(Alignment.End),
                        )
                    }
                }
                null -> Unit
            }
        }
    }

    if (showCustomEditor) {
        CustomBrushDialog(
            initial = viewModel.brushSpec.custom ?: CustomBrushSpec(),
            onDismiss = { showCustomEditor = false },
            onApply = {
                showCustomEditor = false
                viewModel.updateCustomBrush(it)
            },
        )
    }
}

private fun ToolbarPanel?.toggle(target: ToolbarPanel): ToolbarPanel? = if (this == target) null else target

@Composable
private fun ToolIconButton(
    selected: Boolean,
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
    ) {
        IconButton(onClick = onClick) { Icon(icon, description) }
    }
}

@Composable
private fun ToolColorButton(color: Color, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
    ) {
        IconButton(onClick = onClick) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(color),
            )
        }
    }
}

@Composable
private fun LassoPanel(viewModel: MainViewModel, colors: List<Pair<Int, Color>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                LassoCoverageMode.INTERSECTS to "交差",
                LassoCoverageMode.QUARTER to "25%",
                LassoCoverageMode.HALF to "50%",
                LassoCoverageMode.ALMOST_ALL to "90%",
            ).forEach { (mode, label) ->
                FilterChip(
                    selected = viewModel.lassoCoverageMode == mode,
                    onClick = { viewModel.setLassoCoverageMode(mode) },
                    label = { Text(label) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Gesture, null)
            Text("囲んで長押し", Modifier.padding(horizontal = 8.dp).weight(1f))
            Switch(
                checked = viewModel.circleToLassoEnabled,
                onCheckedChange = viewModel::setCircleToLassoEnabled,
            )
        }
        if (viewModel.activePage?.selectedStrokeIds?.isNotEmpty() == true) {
            HorizontalDivider()
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::applyCurrentBrushToSelected) { Icon(Icons.Default.Brush, "現在のブラシを適用") }
                IconButton(onClick = { viewModel.scaleSelectedStrokes(0.85f) }) { Icon(Icons.Default.ZoomOut, "選択を縮小") }
                IconButton(onClick = { viewModel.scaleSelectedStrokes(1.15f) }) { Icon(Icons.Default.ZoomIn, "選択を拡大") }
                colors.forEach { (argb, color) ->
                    ColorSwatch(color, false) { viewModel.updateSelectedBrush(colorArgb = argb) }
                }
                IconButton(onClick = viewModel::deleteSelectedStrokes) { Icon(Icons.Default.DeleteOutline, "選択を削除") }
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(if (selected) 38.dp else 34.dp),
        shape = CircleShape,
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize().background(color))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorSettingsScreen(
    viewModel: MainViewModel,
    uiPreferences: UiPreferencesState,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "戻る") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                SettingsGroup("テーマ") {
                    ChoiceRow {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = uiPreferences.themeMode == mode,
                                onClick = { uiPreferences.setThemeMode(mode) },
                                label = {
                                    Text(
                                        when (mode) {
                                            ThemeMode.SYSTEM -> "システム"
                                            ThemeMode.LIGHT -> "ライト"
                                            ThemeMode.DARK -> "ダーク"
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }
            item {
                SettingsGroup("タブ") {
                    ChoiceRow {
                        FilterChip(
                            selected = uiPreferences.tabLayoutMode == TabLayoutMode.HORIZONTAL,
                            onClick = { uiPreferences.setTabLayoutMode(TabLayoutMode.HORIZONTAL) },
                            label = { Text("横タブ") },
                        )
                        FilterChip(
                            selected = uiPreferences.tabLayoutMode == TabLayoutMode.VERTICAL,
                            onClick = { uiPreferences.setTabLayoutMode(TabLayoutMode.VERTICAL) },
                            label = { Text("縦タブ") },
                        )
                    }
                }
            }
            item {
                SettingsGroup("ページ移動に使う指") {
                    ChoiceRow {
                        FilterChip(
                            selected = viewModel.navigationGestureMode == NavigationGestureMode.ONE_FINGER,
                            onClick = { viewModel.setNavigationGestureMode(NavigationGestureMode.ONE_FINGER) },
                            label = { Text("1本") },
                        )
                        FilterChip(
                            selected = viewModel.navigationGestureMode == NavigationGestureMode.TWO_FINGER,
                            onClick = { viewModel.setNavigationGestureMode(NavigationGestureMode.TWO_FINGER) },
                            label = { Text("2本") },
                        )
                    }
                    Text(
                        "ピンチ操作は常に2本指で、すべてのページへ同じ倍率を適用します。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SettingsGroup("ページ方向") {
                    ChoiceRow {
                        FilterChip(
                            selected = viewModel.scrollAxis == ScrollAxis.VERTICAL,
                            onClick = {
                                if (viewModel.scrollAxis != ScrollAxis.VERTICAL) viewModel.toggleScrollAxis()
                            },
                            label = { Text("縦") },
                        )
                        FilterChip(
                            selected = viewModel.scrollAxis == ScrollAxis.HORIZONTAL,
                            onClick = {
                                if (viewModel.scrollAxis != ScrollAxis.HORIZONTAL) viewModel.toggleScrollAxis()
                            },
                            label = { Text("横") },
                        )
                    }
                }
            }
            item {
                SettingsGroup("フローティングツールバー") {
                    Text(
                        "上部のドラッグハンドルを動かすと、ツールバーを任意の位置へ移動できます。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun ChoiceRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun SharedZoomPages(viewModel: MainViewModel, session: NoteSession, modifier: Modifier) {
    var zoom by rememberSaveable(session.id) { mutableFloatStateOf(1f) }
    val zoomModifier = Modifier.sharedPageZoomGesture { factor ->
        zoom = (zoom * factor).coerceIn(0.55f, 3f)
    }

    BoxWithConstraints(modifier.then(zoomModifier)) {
        val baseWidth = (maxWidth - 16.dp).coerceAtMost(900.dp).coerceAtLeast(240.dp)
        val pageWidth = baseWidth * zoom
        when (viewModel.scrollAxis) {
            ScrollAxis.VERTICAL -> VerticalPages(viewModel, session, pageWidth, maxWidth)
            ScrollAxis.HORIZONTAL -> HorizontalPages(viewModel, session, pageWidth, maxHeight)
        }
    }
}

@Composable
private fun VerticalPages(
    viewModel: MainViewModel,
    session: NoteSession,
    pageWidth: androidx.compose.ui.unit.Dp,
    viewportWidth: androidx.compose.ui.unit.Dp,
) {
    val verticalState = rememberLazyListState()
    val horizontalState = rememberScrollState()
    LaunchedEffect(session.activePageIndex, session.pages.size) {
        if (session.pages.isNotEmpty()) {
            verticalState.animateScrollToItem(session.activePageIndex.coerceIn(0, session.pages.lastIndex))
        }
    }
    Box(Modifier.fillMaxSize().horizontalScroll(horizontalState)) {
        Box(
            Modifier.width(maxOf(pageWidth, viewportWidth)).fillMaxHeight(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.width(pageWidth).fillMaxHeight(),
                state = verticalState,
                userScrollEnabled = false,
                contentPadding = PaddingValues(0.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                itemsIndexed(session.pages, key = { _, page -> page.id }) { index, page ->
                    ZoomPage(
                        viewModel = viewModel,
                        session = session,
                        page = page,
                        index = index,
                        modifier = Modifier.fillMaxWidth(),
                        onNavigationPan = { dx, dy ->
                            horizontalState.dispatchRawDelta(-dx)
                            verticalState.dispatchRawDelta(-dy)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HorizontalPages(
    viewModel: MainViewModel,
    session: NoteSession,
    pageWidth: androidx.compose.ui.unit.Dp,
    viewportHeight: androidx.compose.ui.unit.Dp,
) {
    val horizontalState = rememberLazyListState()
    val verticalState = rememberScrollState()
    val tallestPage = session.pages.maxOfOrNull { page -> pageWidth * (page.height / page.width) } ?: viewportHeight
    val contentHeight = maxOf(tallestPage, viewportHeight)
    LaunchedEffect(session.activePageIndex, session.pages.size) {
        if (session.pages.isNotEmpty()) {
            horizontalState.animateScrollToItem(session.activePageIndex.coerceIn(0, session.pages.lastIndex))
        }
    }
    Box(Modifier.fillMaxSize().verticalScroll(verticalState)) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().height(contentHeight),
            state = horizontalState,
            userScrollEnabled = false,
            contentPadding = PaddingValues(0.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(session.pages, key = { _, page -> page.id }) { index, page ->
                ZoomPage(
                    viewModel = viewModel,
                    session = session,
                    page = page,
                    index = index,
                    modifier = Modifier.width(pageWidth),
                    onNavigationPan = { dx, dy ->
                        horizontalState.dispatchRawDelta(-dx)
                        verticalState.dispatchRawDelta(-dy)
                    },
                )
            }
        }
    }
}

@Composable
private fun ZoomPage(
    viewModel: MainViewModel,
    session: NoteSession,
    page: PageSession,
    index: Int,
    modifier: Modifier,
    onNavigationPan: (Float, Float) -> Unit,
) {
    val background by produceState<Bitmap?>(initialValue = null, session.id, page.id) {
        value = viewModel.renderPdfPage(session, page, 1200)
    }
    var circleHoldQualified by remember(page.id) { mutableStateOf(false) }

    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = androidx.compose.ui.graphics.RectangleShape,
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(page.width / page.height)
                .circleHoldQualifier(
                    enabled = viewModel.circleToLassoEnabled && viewModel.toolMode == ToolMode.PEN,
                    onStrokeStart = { circleHoldQualified = false },
                    onQualified = { circleHoldQualified = true },
                ),
        ) {
            AndroidView(
                factory = { context -> InkPageView(context) },
                update = { view ->
                    view.bind(
                        page = page,
                        contentVersion = page.contentVersion,
                        background = background,
                        imageBitmaps = session.imageBitmaps,
                        toolProvider = { viewModel.toolMode },
                        brushProvider = { viewModel.brushSpec },
                        navigationGestureProvider = { viewModel.navigationGestureMode },
                        circleToLassoEnabledProvider = { false },
                        onNavigationPan = onNavigationPan,
                        onStrokeAdded = { runtime ->
                            viewModel.addStroke(page, runtime)
                            if (circleHoldQualified) {
                                circleHoldQualified = false
                                viewModel.convertCircleStrokeToLasso(page, runtime.stored.id, runtime.stroke)
                            }
                        },
                        onEraseStart = { viewModel.beginErase(page) },
                        onErase = { x, y, radius -> viewModel.eraseAt(page, x, y, radius) },
                        onEraseEnd = { viewModel.endErase(page) },
                        onLassoFinished = { viewModel.selectWithLasso(page, it) },
                        onCircleHoldLasso = { _, _ -> },
                        onSelectedTransformStart = { viewModel.beginSelectedStrokeTransform(page) },
                        onSelectedMove = { dx, dy -> viewModel.moveSelectedStrokes(page, dx, dy) },
                        onSelectedTransformEnd = { viewModel.endSelectedStrokeTransform(page) },
                        onSelectedTransformCancel = { viewModel.cancelSelectedStrokeTransform(page) },
                        onImageSelected = { viewModel.selectImage(page, it) },
                        onImageTransformStart = { viewModel.beginImageTransform(page, it) },
                        onImageMove = { id, x, y -> viewModel.moveImage(page, id, x, y) },
                        onImageTransformEnd = { viewModel.endImageTransform(page) },
                        onImageTransformCancel = { viewModel.cancelImageTransform(page) },
                        onActivated = { viewModel.activatePage(index) },
                    )
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun Modifier.sharedPageZoomGesture(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var wasZooming = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val touchCount = event.changes.count { it.pressed && it.type == PointerType.Touch }
            if (touchCount >= 2) {
                val factor = event.calculateZoom()
                if (factor.isFinite() && factor > 0f) onZoom(factor.coerceIn(0.8f, 1.25f))
                event.changes.forEach { it.consume() }
                wasZooming = true
            } else if (wasZooming) {
                event.changes.forEach { it.consume() }
            }
            if (event.changes.none { it.pressed }) break
        }
    }
}

private fun Modifier.circleHoldQualifier(
    enabled: Boolean,
    onStrokeStart: () -> Unit,
    onQualified: () -> Unit,
): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.type != PointerType.Stylus) return@awaitEachGesture
            onStrokeStart()
            val pointerId = down.id
            val points = mutableListOf(down.position)
            var active = true
            var qualified = false
            var holdJob: Job? = null
            var lastPoint = down.position
            var lastMovementAt = SystemClock.uptimeMillis()

            while (active) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                if (!change.pressed) {
                    active = false
                    break
                }
                val distance = hypot(
                    (change.position.x - lastPoint.x).toDouble(),
                    (change.position.y - lastPoint.y).toDouble(),
                ).toFloat()
                if (distance >= 1.5f) {
                    points += change.position
                    lastPoint = change.position
                    lastMovementAt = SystemClock.uptimeMillis()
                    if (holdJob != null) {
                        holdJob?.cancel()
                        holdJob = null
                    }
                }
                if (!qualified && holdJob == null && looksLikeClosedLoop(points)) {
                    holdJob = launch {
                        delay(CIRCLE_HOLD_DELAY_MS)
                        if (active && SystemClock.uptimeMillis() - lastMovementAt >= CIRCLE_HOLD_DELAY_MS - 40L) {
                            qualified = true
                            onQualified()
                        }
                    }
                }
            }
            holdJob?.cancel()
        }
    }
}

private fun looksLikeClosedLoop(points: List<Offset>): Boolean {
    if (points.size < 12) return false
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    val width = maxX - minX
    val height = maxY - minY
    val minimumDimension = min(width, height)
    if (minimumDimension < 48f) return false
    val first = points.first()
    val last = points.last()
    val closingDistance = hypot((last.x - first.x).toDouble(), (last.y - first.y).toDouble()).toFloat()
    if (closingDistance > max(24f, minimumDimension * 0.25f)) return false
    val pathLength = points.zipWithNext().sumOf { (a, b) ->
        hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
    }.toFloat()
    return pathLength >= (width + height) * 1.15f
}

private const val CIRCLE_HOLD_DELAY_MS = 700L

@Composable
private fun CustomBrushDialog(
    initial: CustomBrushSpec,
    onDismiss: () -> Unit,
    onApply: (CustomBrushSpec) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("カスタムブラシ") },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BrushSlider("横幅", value.scaleX, 0.15f..2.5f) { value = value.copy(scaleX = it) }
                BrushSlider("縦幅", value.scaleY, 0.15f..2.5f) { value = value.copy(scaleY = it) }
                BrushSlider("角の丸さ", value.cornerRounding, 0f..1f) { value = value.copy(cornerRounding = it) }
                BrushSlider("傾斜", value.slantDegrees, -75f..75f, "°") { value = value.copy(slantDegrees = it) }
                BrushSlider("回転", value.rotationDegrees, -180f..180f, "°") { value = value.copy(rotationDegrees = it) }
                BrushSlider("平滑化", value.smoothingWindowMillis.toFloat(), 0f..80f, "ms") {
                    value = value.copy(smoothingWindowMillis = it.toLong())
                }
                BrushSlider("補間周波数", value.upsamplingFrequencyHz.toFloat(), 30f..240f, "Hz") {
                    value = value.copy(upsamplingFrequencyHz = it.toInt())
                }
            }
        },
        confirmButton = { Button(onClick = { onApply(value) }) { Text("適用") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}

@Composable
private fun BrushSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    suffix: String = "",
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text("${"%.1f".format(value)}$suffix", style = MaterialTheme.typography.labelMedium)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun PageOverviewDialog(
    viewModel: MainViewModel,
    session: NoteSession,
    onDismiss: () -> Unit,
    onOpenPage: (Int) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 8.dp,
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("ページ一覧", style = MaterialTheme.typography.headlineSmall)
                        Text("${session.pages.size}ページ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row {
                        IconButton(onClick = viewModel::addPage) { Icon(Icons.Default.Add, "ページを追加") }
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "閉じる") }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(180.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(session.pages, key = { _, page -> page.id }) { index, page ->
                        val preview by produceState<Bitmap?>(initialValue = null, session.id, page.id, page.contentVersion) {
                            value = viewModel.renderPagePreview(session, page, 360)
                        }
                        Card(
                            onClick = { onOpenPage(index) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (index == session.activePageIndex) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                            ),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                                Box(
                                    Modifier.fillMaxWidth().aspectRatio(page.width / page.height).background(Color.White),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (preview != null) {
                                        androidx.compose.foundation.Image(
                                            bitmap = preview!!.asImageBitmap(),
                                            contentDescription = "Page ${index + 1}",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit,
                                        )
                                    } else {
                                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                                    }
                                }
                                Text("Page ${index + 1}", modifier = Modifier.padding(top = 8.dp), fontWeight = FontWeight.SemiBold)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    IconButton(enabled = index > 0, onClick = { viewModel.movePage(index, -1) }) {
                                        Icon(Icons.Default.KeyboardArrowUp, "前へ")
                                    }
                                    IconButton(enabled = index < session.pages.lastIndex, onClick = { viewModel.movePage(index, 1) }) {
                                        Icon(Icons.Default.KeyboardArrowDown, "後へ")
                                    }
                                    IconButton(onClick = { viewModel.duplicatePage(index) }) {
                                        Icon(Icons.Default.ContentCopy, "複製")
                                    }
                                    IconButton(enabled = session.pages.size > 1, onClick = { viewModel.deletePage(index) }) {
                                        Icon(Icons.Default.DeleteOutline, "削除")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true) },
        confirmButton = { Button(onClick = { onConfirm(value) }) { Text("変更") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}
