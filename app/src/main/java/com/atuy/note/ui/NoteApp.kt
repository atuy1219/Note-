package com.atuy.note.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.atuy.note.MainViewModel
import com.atuy.note.data.FolderRecord
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
            EditorScreen(viewModel, snackbar)
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
private fun FolderSidebar(
    folders: List<FolderRecord>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.width(248.dp).fillMaxHeight().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            SidebarRow("All local notes", Icons.Default.Home, selected == null) { onSelect(null) }
        }
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
private fun SidebarRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
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
    val bitmap = remember(note.thumbnailPath, note.updatedAt) {
        note.thumbnailPath?.let { BitmapFactory.decodeFile(it) }
    }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().height(236.dp)) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxWidth().weight(1f).background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
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
private fun EditorScreen(viewModel: MainViewModel, snackbar: SnackbarHostState) {
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
            InkToolbar(viewModel)
            HorizontalDivider()
            Pages(viewModel, active, Modifier.weight(1f))
        }
    }
}

@Composable
private fun InkToolbar(viewModel: MainViewModel) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalButton(onClick = { viewModel.setTool(ToolMode.PEN) }) {
            Icon(Icons.Default.Brush, null)
            Spacer(Modifier.width(6.dp))
            Text(if (viewModel.toolMode == ToolMode.PEN) "Pen" else "Use pen")
        }
        FilledTonalButton(onClick = { viewModel.setTool(ToolMode.ERASER) }) {
            Icon(Icons.Default.DeleteOutline, null)
            Spacer(Modifier.width(6.dp))
            Text(if (viewModel.toolMode == ToolMode.ERASER) "Eraser" else "Erase")
        }
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
        when (viewModel.scrollAxis) {
            ScrollAxis.VERTICAL -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                itemsIndexed(session.pages, key = { _, page -> page.id }) { index, page ->
                    NotePage(viewModel, session, page, index, Modifier.fillMaxWidth().widthIn(max = 900.dp))
                }
            }
            ScrollAxis.HORIZONTAL -> LazyRow(
                Modifier.fillMaxSize(),
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
                        background = background,
                        toolProvider = { viewModel.toolMode },
                        brushProvider = { viewModel.brushSpec },
                        onStrokeAdded = { viewModel.addStroke(page, it) },
                        onErase = { x, y, radius -> viewModel.eraseAt(page, x, y, radius) },
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
