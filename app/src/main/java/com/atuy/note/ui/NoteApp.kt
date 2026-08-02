package com.atuy.note.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.atuy.note.MainViewModel
import com.atuy.note.data.EraserMode
import com.atuy.note.data.FolderRecord
import com.atuy.note.data.NavigationGestureMode
import com.atuy.note.data.NoteSession
import com.atuy.note.data.NoteSummary
import com.atuy.note.data.PageSession
import com.atuy.note.data.ScrollAxis
import com.atuy.note.data.ToolMode
import com.atuy.note.ink.InkPageView

@Composable
fun NoteApp(
    viewModel: MainViewModel,
    onImportPdf: () -> Unit,
    onImportImage: () -> Unit,
    onSyncDrive: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val message = viewModel.statusMessage
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.clearStatus()
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (viewModel.activeSession == null) {
            LibraryScreen(viewModel, onImportPdf, onSyncDrive, snackbar)
        } else {
            EditorScreen(viewModel, onImportImage, snackbar)
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
private fun LibraryScreen(
    viewModel: MainViewModel,
    onImportPdf: () -> Unit,
    onSyncDrive: () -> Unit,
    snackbar: SnackbarHostState,
) {
    var createNote by remember { mutableStateOf(false) }
    var createFolder by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(viewModel.currentFolder?.name ?: "Note", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${viewModel.visibleNotes.size} notes",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    if (viewModel.currentFolderId != null) {
                        IconButton(onClick = viewModel::navigateUpFolder) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Parent folder")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSyncDrive) { Icon(Icons.Default.CloudSync, "Sync with Google Drive") }
                    IconButton(onClick = { createFolder = true }) { Icon(Icons.Default.CreateNewFolder, "New folder") }
                    IconButton(onClick = onImportPdf) { Icon(Icons.Default.PictureAsPdf, "Import PDF") }
                    IconButton(onClick = { createNote = true }) { Icon(Icons.Default.Add, "New note") }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            if (maxWidth >= 840.dp) {
                Row(Modifier.fillMaxSize()) {
                    FolderSidebar(
                        folders = viewModel.library.folders,
                        selected = viewModel.currentFolderId,
                        onSelect = viewModel::enterFolder,
                    )
                    VerticalDivider(Modifier.fillMaxHeight().width(1.dp))
                    LibraryGrid(
                        folders = viewModel.childFolders,
                        notes = viewModel.visibleNotes,
                        onFolder = { viewModel.enterFolder(it.id) },
                        onNote = { viewModel.openNote(it.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                LibraryGrid(
                    folders = viewModel.childFolders,
                    notes = viewModel.visibleNotes,
                    onFolder = { viewModel.enterFolder(it.id) },
                    onNote = { viewModel.openNote(it.id) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (createNote) {
        NameDialog("New note", "Untitled", onDismiss = { createNote = false }) {
            createNote = false
            viewModel.createBlankNote(it)
        }
    }
    if (createFolder) {
        NameDialog("New folder", "New folder", onDismiss = { createFolder = false }) {
            createFolder = false
            viewModel.createFolder(it)
        }
    }
}

@Composable
private fun FolderSidebar(folders: List<FolderRecord>, selected: String?, onSelect: (String?) -> Unit) {
    LazyColumn(
        modifier = Modifier.width(248.dp).fillMaxHeight().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item { SidebarRow("All local notes", Icons.Default.Home, selected == null) { onSelect(null) } }
        items(folders.sortedWith(compareBy<FolderRecord> { it.parentId != null }.thenBy { it.name.lowercase() })) { folder ->
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .clickable { onSelect(folder.id) }
                    .background(if (selected == folder.id) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .padding(start = if (folder.parentId == null) 12.dp else 30.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Default.Folder, null, Modifier.size(20.dp))
                Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SidebarRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp))
        Text(label)
    }
}

@Composable
private fun LibraryGrid(
    folders: List<FolderRecord>,
    notes: List<NoteSummary>,
    onFolder: (FolderRecord) -> Unit,
    onNote: (NoteSummary) -> Unit,
    modifier: Modifier,
) {
    if (folders.isEmpty() && notes.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Description, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("No notes in this folder", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(156.dp),
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(folders, key = { "folder-${it.id}" }) { folder -> FolderCard(folder) { onFolder(folder) } }
        items(notes, key = { it.id }) { note -> NoteCard(note) { onNote(note) } }
    }
}

@Composable
private fun FolderCard(folder: FolderRecord, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().height(176.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Folder, null, Modifier.size(58.dp), tint = MaterialTheme.colorScheme.primary)
                Text(folder.name, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun NoteCard(note: NoteSummary, onClick: () -> Unit) {
    val bitmap = remember(note.thumbnailPath, note.updatedAt) { note.thumbnailPath?.let { BitmapFactory.decodeFile(it) } }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().height(236.dp)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f).background(Color.White), contentAlignment = Alignment.Center) {
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(Icons.Default.Description, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.padding(12.dp)) {
                Text(note.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${note.pageCount} pages", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScreen(viewModel: MainViewModel, onImportImage: () -> Unit, snackbar: SnackbarHostState) {
    val active = viewModel.activeSession ?: return
    val selectedTab = viewModel.openTabs.indexOfFirst { it.id == active.id }.coerceAtLeast(0)
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(active.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = viewModel::showLibrary) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Library")
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::undo) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
                        IconButton(onClick = viewModel::redo) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") }
                        IconButton(onClick = viewModel::addPage) { Icon(Icons.Default.Add, "Add page") }
                        IconButton(onClick = viewModel::toggleScrollAxis) {
                            Icon(
                                if (viewModel.scrollAxis == ScrollAxis.VERTICAL) Icons.Default.SwapHoriz else Icons.Default.SwapVert,
                                "Change page direction",
                            )
                        }
                        IconButton(onClick = viewModel::saveActive) { Icon(Icons.Default.Save, "Save") }
                    },
                )
                ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 8.dp) {
                    viewModel.openTabs.forEach { tab ->
                        Tab(
                            selected = tab.id == active.id,
                            onClick = { viewModel.activateTab(tab.id) },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
                                    IconButton(onClick = { viewModel.closeTab(tab.id) }, modifier = Modifier.size(30.dp)) {
                                        Icon(Icons.Default.Close, "Close tab", Modifier.size(16.dp))
                                    }
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            InkToolbar(viewModel, onImportImage)
            HorizontalDivider()
            Pages(viewModel, active, Modifier.weight(1f))
        }
    }
}

@Composable
private fun InkToolbar(viewModel: MainViewModel, onImportImage: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = viewModel.toolMode == ToolMode.PEN,
            onClick = { viewModel.setTool(ToolMode.PEN) },
            label = { Text("Pen") },
            leadingIcon = { Icon(Icons.Default.Brush, null, Modifier.size(18.dp)) },
        )
        FilterChip(
            selected = viewModel.toolMode == ToolMode.ERASER,
            onClick = { viewModel.setTool(ToolMode.ERASER) },
            label = { Text("Eraser") },
            leadingIcon = { Icon(Icons.Default.DeleteOutline, null, Modifier.size(18.dp)) },
        )
        FilterChip(
            selected = viewModel.toolMode == ToolMode.LASSO,
            onClick = { viewModel.setTool(ToolMode.LASSO) },
            label = { Text("投げ縄") },
            leadingIcon = { Icon(Icons.Default.Gesture, null, Modifier.size(18.dp)) },
        )
        FilterChip(
            selected = viewModel.eraserMode == EraserMode.PARTIAL,
            onClick = { viewModel.setEraserMode(EraserMode.PARTIAL) },
            label = { Text("部分消し") },
        )
        FilterChip(
            selected = viewModel.eraserMode == EraserMode.WHOLE_STROKE,
            onClick = { viewModel.setEraserMode(EraserMode.WHOLE_STROKE) },
            label = { Text("線全体") },
        )
        if (viewModel.activePage?.selectedStrokeIds?.isNotEmpty() == true) {
            TextButton(onClick = { viewModel.scaleSelectedStrokes(0.85f) }) { Text("選択−") }
            TextButton(onClick = { viewModel.scaleSelectedStrokes(1.15f) }) { Text("選択＋") }
            IconButton(onClick = viewModel::deleteSelectedStrokes) {
                Icon(Icons.Default.DeleteOutline, "Delete selected strokes")
            }
        }
        FilledTonalButton(onClick = onImportImage) {
            Icon(Icons.Default.Image, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("画像追加")
        }
        FilterChip(
            selected = viewModel.toolMode == ToolMode.IMAGE,
            onClick = { viewModel.setTool(ToolMode.IMAGE) },
            label = { Text("画像移動") },
        )
        if (viewModel.activePage?.selectedImageId != null) {
            TextButton(onClick = { viewModel.scaleSelectedImage(0.85f) }) { Text("画像−") }
            TextButton(onClick = { viewModel.scaleSelectedImage(1.15f) }) { Text("画像＋") }
            IconButton(onClick = viewModel::deleteSelectedImage) {
                Icon(Icons.Default.DeleteOutline, "Delete selected image")
            }
        }
        VerticalDivider(Modifier.height(32.dp).width(1.dp))
        FilterChip(
            selected = viewModel.navigationGestureMode == NavigationGestureMode.ONE_FINGER,
            onClick = { viewModel.setNavigationGestureMode(NavigationGestureMode.ONE_FINGER) },
            label = { Text("指1本で移動") },
        )
        FilterChip(
            selected = viewModel.navigationGestureMode == NavigationGestureMode.TWO_FINGER,
            onClick = { viewModel.setNavigationGestureMode(NavigationGestureMode.TWO_FINGER) },
            label = { Text("指2本で移動") },
        )
        VerticalDivider(Modifier.height(32.dp).width(1.dp))
        listOf(
            0xFF111111.toInt() to Color(0xFF111111),
            0xFF1565C0.toInt() to Color(0xFF1565C0),
            0xFFC62828.toInt() to Color(0xFFC62828),
            0xFF2E7D32.toInt() to Color(0xFF2E7D32),
        ).forEach { (argb, color) ->
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(color)
                    .clickable { viewModel.updateBrush(colorArgb = argb) },
            )
        }
        listOf(3.2f, 5.5f, 9f).forEach { size ->
            TextButton(onClick = { viewModel.updateBrush(size = size) }) {
                Text("${size}pt", fontWeight = if (viewModel.brushSpec.size == size) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun Pages(viewModel: MainViewModel, session: NoteSession, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val horizontalPageWidth = if (maxWidth >= 840.dp) 760.dp else maxWidth - 24.dp
        val twoFinger = viewModel.navigationGestureMode == NavigationGestureMode.TWO_FINGER
        when (viewModel.scrollAxis) {
            ScrollAxis.VERTICAL -> {
                val state = rememberLazyListState()
                LazyColumn(
                    modifier = Modifier.fillMaxSize().twoFingerPan(twoFinger) { delta -> state.dispatchRawDelta(-delta.y) },
                    state = state,
                    userScrollEnabled = !twoFinger,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    itemsIndexed(session.pages, key = { _, page -> page.id }) { index, page ->
                        NotePage(viewModel, session, page, index, Modifier.fillMaxWidth().widthIn(max = 900.dp))
                    }
                }
            }
            ScrollAxis.HORIZONTAL -> {
                val state = rememberLazyListState()
                LazyRow(
                    modifier = Modifier.fillMaxSize().twoFingerPan(twoFinger) { delta -> state.dispatchRawDelta(-delta.x) },
                    state = state,
                    userScrollEnabled = !twoFinger,
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    itemsIndexed(session.pages, key = { _, page -> page.id }) { index, page ->
                        NotePage(viewModel, session, page, index, Modifier.width(horizontalPageWidth))
                    }
                }
            }
        }
    }
}

private fun Modifier.twoFingerPan(enabled: Boolean, onPan: (Offset) -> Unit): Modifier {
    if (!enabled) return this
    return pointerInput(enabled) {
        awaitEachGesture {
            var previousCentroid: Offset? = null
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val touches = event.changes.filter { it.pressed && it.type == PointerType.Touch }
                if (touches.size >= 2) {
                    val centroid = Offset(
                        x = touches.sumOf { it.position.x.toDouble() }.toFloat() / touches.size,
                        y = touches.sumOf { it.position.y.toDouble() }.toFloat() / touches.size,
                    )
                    previousCentroid?.let { previous -> onPan(centroid - previous) }
                    previousCentroid = centroid
                    touches.forEach { it.consume() }
                } else {
                    previousCentroid = null
                }
            } while (event.changes.any { it.pressed })
        }
    }
}

@Composable
private fun NotePage(
    viewModel: MainViewModel,
    session: NoteSession,
    page: PageSession,
    index: Int,
    modifier: Modifier,
) {
    val background by produceState<Bitmap?>(initialValue = null, session.id, page.id) {
        value = viewModel.renderPdfPage(session, page, 1200)
    }
    val redrawVersion = page.contentVersion
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Page ${index + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            AndroidView(
                factory = { context -> InkPageView(context) },
                update = { view ->
                    redrawVersion.hashCode()
                    view.bind(
                        page = page,
                        background = background,
                        imageBitmaps = session.imageBitmaps,
                        toolProvider = { viewModel.toolMode },
                        brushProvider = { viewModel.brushSpec },
                        onStrokeAdded = { viewModel.addStroke(page, it) },
                        onEraseStart = { viewModel.beginErase(page) },
                        onErase = { x, y, radius -> viewModel.eraseAt(page, x, y, radius) },
                        onEraseEnd = { viewModel.endErase(page) },
                        onLassoFinished = { viewModel.selectWithLasso(page, it) },
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
                modifier = Modifier.fillMaxWidth().aspectRatio(page.width / page.height),
            )
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
        confirmButton = { Button(onClick = { onConfirm(value) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
