package com.atuy.note.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.PrimaryScrollableTabRow
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
import androidx.compose.ui.window.Dialog
import com.atuy.note.MainViewModel
import com.atuy.note.data.BrushKind
import com.atuy.note.data.CustomBrushSpec
import com.atuy.note.data.FolderRecord
import com.atuy.note.data.LassoCoverageMode
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
    var query by remember { mutableStateOf("") }
    val normalizedQuery = query.trim()
    val folders = if (normalizedQuery.isBlank()) {
        viewModel.childFolders
    } else {
        viewModel.childFolders.filter { it.name.contains(normalizedQuery, ignoreCase = true) }
    }
    val notes = if (normalizedQuery.isBlank()) {
        viewModel.visibleNotes
    } else {
        viewModel.visibleNotes.filter { it.title.contains(normalizedQuery, ignoreCase = true) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
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
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("ノートとフォルダーを検索") },
                )
            }
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
                        folders = folders,
                        notes = notes,
                        onFolder = { viewModel.enterFolder(it.id) },
                        onNote = { viewModel.openNote(it.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                LibraryGrid(
                    folders = folders,
                    notes = notes,
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
    var showPages by remember(active.id) { mutableStateOf(false) }
    var showMenu by remember(active.id) { mutableStateOf(false) }
    var renameNote by remember(active.id) { mutableStateOf(false) }
    var deleteNote by remember(active.id) { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Library")
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::undo) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
                        IconButton(onClick = viewModel::redo) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") }
                        IconButton(onClick = { showPages = true }) { Icon(Icons.Default.GridView, "ページ一覧") }
                        IconButton(onClick = viewModel::addPage) { Icon(Icons.Default.Add, "Add page") }
                        IconButton(onClick = viewModel::toggleScrollAxis) {
                            Icon(
                                if (viewModel.scrollAxis == ScrollAxis.VERTICAL) Icons.Default.SwapHoriz else Icons.Default.SwapVert,
                                "Change page direction",
                            )
                        }
                        IconButton(onClick = viewModel::saveActive) { Icon(Icons.Default.Save, "Save") }
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
                PrimaryScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 8.dp) {
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
                    Tab(
                        selected = false,
                        onClick = { viewModel.createBlankNote("Untitled") },
                        text = { Icon(Icons.Default.Add, "新しいタブ") },
                    )
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
private fun InkToolbar(viewModel: MainViewModel, onImportImage: () -> Unit) {
    var showCustomEditor by remember { mutableStateOf(false) }
    val normalColors = listOf(
        0xFF111111.toInt() to Color(0xFF111111),
        0xFF1565C0.toInt() to Color(0xFF1565C0),
        0xFFC62828.toInt() to Color(0xFFC62828),
        0xFF2E7D32.toInt() to Color(0xFF2E7D32),
    )
    val highlighterColors = listOf(
        0x66FFF176 to Color(0x66FFF176),
        0x6681C784 to Color(0x6681C784),
        0x6664B5F6 to Color(0x6664B5F6),
        0x66F48FB1 to Color(0x66F48FB1),
    )
    val activeColors = if (viewModel.brushSpec.kind == BrushKind.HIGHLIGHTER) highlighterColors else normalColors
    val activeSizes = if (viewModel.brushSpec.kind == BrushKind.HIGHLIGHTER) listOf(12f, 18f, 26f) else listOf(3.2f, 5.5f, 9f)

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = viewModel.toolMode == ToolMode.PEN && viewModel.brushSpec.kind == BrushKind.PRESSURE_PEN,
            onClick = { viewModel.setBrushKind(BrushKind.PRESSURE_PEN) },
            label = { Text("ペン") },
            leadingIcon = { Icon(Icons.Default.Brush, null, Modifier.size(18.dp)) },
        )
        FilterChip(
            selected = viewModel.toolMode == ToolMode.PEN && viewModel.brushSpec.kind == BrushKind.MARKER,
            onClick = { viewModel.setBrushKind(BrushKind.MARKER) },
            label = { Text("マーカー") },
        )
        FilterChip(
            selected = viewModel.toolMode == ToolMode.PEN && viewModel.brushSpec.kind == BrushKind.HIGHLIGHTER,
            onClick = { viewModel.setBrushKind(BrushKind.HIGHLIGHTER) },
            label = { Text("蛍光ペン") },
        )
        FilterChip(
            selected = viewModel.toolMode == ToolMode.PEN && viewModel.brushSpec.kind == BrushKind.CUSTOM,
            onClick = {
                viewModel.setBrushKind(BrushKind.CUSTOM)
                showCustomEditor = true
            },
            label = { Text("カスタム") },
        )
        FilterChip(
            selected = viewModel.toolMode == ToolMode.ERASER,
            onClick = { viewModel.setTool(ToolMode.ERASER) },
            label = { Text("消しゴム") },
            leadingIcon = { Icon(Icons.Default.DeleteOutline, null, Modifier.size(18.dp)) },
        )
        FilterChip(
            selected = viewModel.toolMode == ToolMode.LASSO,
            onClick = { viewModel.setTool(ToolMode.LASSO) },
            label = { Text("投げ縄") },
            leadingIcon = { Icon(Icons.Default.Gesture, null, Modifier.size(18.dp)) },
        )


        if (viewModel.toolMode == ToolMode.LASSO) {
            VerticalDivider(Modifier.height(32.dp).width(1.dp))
            Text("選択率", style = MaterialTheme.typography.labelMedium)
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

        if (viewModel.activePage?.selectedStrokeIds?.isNotEmpty() == true) {
            VerticalDivider(Modifier.height(32.dp).width(1.dp))
            FilledTonalButton(onClick = viewModel::applyCurrentBrushToSelected) { Text("現在のブラシを適用") }
            TextButton(onClick = { viewModel.scaleSelectedStrokes(0.85f) }) { Text("選択−") }
            TextButton(onClick = { viewModel.scaleSelectedStrokes(1.15f) }) { Text("選択＋") }
            normalColors.forEach { (argb, color) ->
                ColorSwatch(color) { viewModel.updateSelectedBrush(colorArgb = argb) }
            }
            listOf(3.2f, 5.5f, 9f, 18f).forEach { size ->
                TextButton(onClick = { viewModel.updateSelectedBrush(size = size) }) { Text("${size}pt") }
            }
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
            selected = viewModel.circleToLassoEnabled,
            onClick = { viewModel.setCircleToLassoEnabled(!viewModel.circleToLassoEnabled) },
            label = { Text("囲み→投げ縄") },
            leadingIcon = { Icon(Icons.Default.Gesture, null, Modifier.size(18.dp)) },
        )

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
        activeColors.forEach { (argb, color) ->
            ColorSwatch(color) { viewModel.updateBrush(colorArgb = argb) }
        }
        activeSizes.forEach { size ->
            TextButton(onClick = { viewModel.updateBrush(size = size) }) {
                Text("${size}pt", fontWeight = if (viewModel.brushSpec.size == size) FontWeight.Bold else FontWeight.Normal)
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

@Composable
private fun ColorSwatch(color: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(color).clickable(onClick = onClick),
    )
}

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
                Text(
                    "筆圧特性はPressure Penを継承し、ここではペン先形状と入力平滑化を変更します。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
private fun Pages(viewModel: MainViewModel, session: NoteSession, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val horizontalPageWidth = if (maxWidth >= 840.dp) 760.dp else maxWidth - 24.dp
        when (viewModel.scrollAxis) {
            ScrollAxis.VERTICAL -> {
                val state = rememberLazyListState()
                LaunchedEffect(session.activePageIndex, session.pages.size) {
                    if (session.pages.isNotEmpty()) state.animateScrollToItem(session.activePageIndex.coerceIn(0, session.pages.lastIndex))
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = state,
                    userScrollEnabled = false,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    itemsIndexed(session.pages, key = { _, page -> page.id }) { index, page ->
                        NotePage(
                            viewModel = viewModel,
                            session = session,
                            page = page,
                            index = index,
                            modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
                            onNavigationPan = { _, dy -> state.dispatchRawDelta(-dy) },
                        )
                    }
                }
            }
            ScrollAxis.HORIZONTAL -> {
                val state = rememberLazyListState()
                LaunchedEffect(session.activePageIndex, session.pages.size) {
                    if (session.pages.isNotEmpty()) state.animateScrollToItem(session.activePageIndex.coerceIn(0, session.pages.lastIndex))
                }
                LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    state = state,
                    userScrollEnabled = false,
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    itemsIndexed(session.pages, key = { _, page -> page.id }) { index, page ->
                        NotePage(
                            viewModel = viewModel,
                            session = session,
                            page = page,
                            index = index,
                            modifier = Modifier.width(horizontalPageWidth),
                            onNavigationPan = { dx, _ -> state.dispatchRawDelta(-dx) },
                        )
                    }
                }
            }
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
    onNavigationPan: (Float, Float) -> Unit,
) {
    val background by produceState<Bitmap?>(initialValue = null, session.id, page.id) {
        value = viewModel.renderPdfPage(session, page, 1200)
    }
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
                    view.bind(
                        page = page,
                        contentVersion = page.contentVersion,
                        background = background,
                        imageBitmaps = session.imageBitmaps,
                        toolProvider = { viewModel.toolMode },
                        brushProvider = { viewModel.brushSpec },
                        navigationGestureProvider = { viewModel.navigationGestureMode },
                        circleToLassoEnabledProvider = { viewModel.circleToLassoEnabled },
                        onNavigationPan = onNavigationPan,
                        onStrokeAdded = { viewModel.addStroke(page, it) },
                        onEraseStart = { viewModel.beginErase(page) },
                        onErase = { x, y, radius -> viewModel.eraseAt(page, x, y, radius) },
                        onEraseEnd = { viewModel.endErase(page) },
                        onLassoFinished = { viewModel.selectWithLasso(page, it) },
                        onCircleHoldLasso = { strokeId, stroke -> viewModel.convertCircleStrokeToLasso(page, strokeId, stroke) },
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
                        // Compose and the hardware renderer may retain this Bitmap in a
                        // recorded display list after the composable leaves composition. Explicitly
                        // recycling it here can race the next draw; allow GC to reclaim it instead.
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
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                ) {
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
        confirmButton = { Button(onClick = { onConfirm(value) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
