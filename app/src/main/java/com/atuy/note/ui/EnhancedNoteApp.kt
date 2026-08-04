package com.atuy.note.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
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
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

@Composable
fun EnhancedNoteApp(
    viewModel: MainViewModel,
    uiPreferences: UiPreferencesState,
    onImportPdf: () -> Unit,
    onImportImage: () -> Unit,
    onSyncDrive: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    var showHomeSettings by rememberSaveable { mutableStateOf(false) }
    val message = viewModel.statusMessage

    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.clearStatus()
        }
    }

    BackHandler(enabled = viewModel.activeSession == null && showHomeSettings) {
        showHomeSettings = false
    }

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        AppFrame(
            viewModel = viewModel,
            uiPreferences = uiPreferences,
        ) {
            when {
                viewModel.activeSession != null -> EditorWorkspace(
                    viewModel = viewModel,
                    uiPreferences = uiPreferences,
                    onImportImage = onImportImage,
                    snackbar = snackbar,
                )
                showHomeSettings -> HomeSettingsScreen(
                    viewModel = viewModel,
                    uiPreferences = uiPreferences,
                    onBack = { showHomeSettings = false },
                )
                else -> HomeScreen(
                    viewModel = viewModel,
                    onImportPdf = onImportPdf,
                    onSyncDrive = onSyncDrive,
                    onOpenSettings = { showHomeSettings = true },
                    snackbar = snackbar,
                )
            }
        }

        if (viewModel.busy) {
            Box(
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                ) {
                    Row(
                        Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp)
                        Text("処理中…")
                    }
                }
            }
        }
    }
}

@Composable
private fun AppFrame(
    viewModel: MainViewModel,
    uiPreferences: UiPreferencesState,
    content: @Composable () -> Unit,
) {
    when (uiPreferences.tabLayoutMode) {
        TabLayoutMode.HORIZONTAL -> Column(Modifier.fillMaxSize()) {
            HorizontalAppTabs(viewModel)
            HorizontalDivider()
            Box(Modifier.weight(1f)) { content() }
        }
        TabLayoutMode.VERTICAL -> Row(Modifier.fillMaxSize()) {
            VerticalAppTabs(viewModel)
            VerticalDivider()
            Box(Modifier.weight(1f)) { content() }
        }
    }
}

@Composable
private fun HorizontalAppTabs(viewModel: MainViewModel) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        Row(
            Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 6.dp, top = 4.dp, end = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            AppTab(
                selected = viewModel.activeSession == null,
                icon = Icons.Default.Home,
                title = "ホーム",
                onClick = viewModel::showLibrary,
            )
            viewModel.openTabs.forEach { tab ->
                AppTab(
                    selected = viewModel.activeSession?.id == tab.id,
                    title = tab.title,
                    onClick = { viewModel.activateTab(tab.id) },
                    onClose = { viewModel.closeTab(tab.id) },
                )
            }
            IconButton(
                onClick = { viewModel.createBlankNote("名称未設定のノート") },
                modifier = Modifier.size(42.dp),
            ) {
                Icon(Icons.Default.Add, "新しいノート")
            }
        }
    }
}

@Composable
private fun AppTab(
    selected: Boolean,
    title: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    onClose: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.widthIn(min = 92.dp, max = 250.dp),
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
    ) {
        Row(
            Modifier.clickable(onClick = onClick)
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(18.dp))
            }
            Text(
                title,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
            )
            if (onClose != null) {
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "タブを閉じる", Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun VerticalAppTabs(viewModel: MainViewModel) {
    Surface(
        modifier = Modifier.width(228.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        LazyColumn(
            Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                VerticalAppTab(
                    selected = viewModel.activeSession == null,
                    title = "ホーム",
                    icon = Icons.Default.Home,
                    onClick = viewModel::showLibrary,
                )
            }
            items(viewModel.openTabs, key = { it.id }) { tab ->
                VerticalAppTab(
                    selected = viewModel.activeSession?.id == tab.id,
                    title = tab.title,
                    onClick = { viewModel.activateTab(tab.id) },
                    onClose = { viewModel.closeTab(tab.id) },
                )
            }
            item {
                IconButton(onClick = { viewModel.createBlankNote("名称未設定のノート") }) {
                    Icon(Icons.Default.Add, "新しいノート")
                }
            }
        }
    }
}

@Composable
private fun VerticalAppTab(
    selected: Boolean,
    title: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    onClose: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(start = 12.dp, top = 7.dp, bottom = 7.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(18.dp))
        Text(title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (onClose != null) {
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "タブを閉じる", Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    viewModel: MainViewModel,
    onImportPdf: () -> Unit,
    onSyncDrive: () -> Unit,
    onOpenSettings: () -> Unit,
    snackbar: SnackbarHostState,
) {
    var createNote by remember { mutableStateOf(false) }
    var createFolder by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<LibraryTarget?>(null) }
    var moveTarget by remember { mutableStateOf<LibraryTarget?>(null) }
    var trashTarget by remember { mutableStateOf<LibraryTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<LibraryTarget?>(null) }
    var emptyTrash by remember { mutableStateOf(false) }

    val normalizedQuery = query.trim()
    val folders = viewModel.childFolders.filter {
        normalizedQuery.isBlank() || it.name.contains(normalizedQuery, ignoreCase = true)
    }
    val notes = viewModel.visibleNotes.filter {
        normalizedQuery.isBlank() || it.title.contains(normalizedQuery, ignoreCase = true)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                when {
                                    viewModel.showingTrash -> "ゴミ箱"
                                    viewModel.currentFolder != null -> viewModel.currentFolder!!.name
                                    else -> "書類"
                                },
                            )
                            Text(
                                if (viewModel.showingTrash) {
                                    "${viewModel.trashItemCount}件"
                                } else {
                                    "${viewModel.visibleNotes.size}件のノート"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        if (!viewModel.showingTrash && viewModel.currentFolderId != null) {
                            IconButton(onClick = viewModel::navigateUpFolder) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "親フォルダー")
                            }
                        }
                    },
                    actions = {
                        if (viewModel.showingTrash) {
                            IconButton(
                                enabled = viewModel.trashItemCount > 0,
                                onClick = { emptyTrash = true },
                            ) {
                                Icon(Icons.Default.DeleteForever, "ゴミ箱を空にする")
                            }
                            IconButton(onClick = viewModel::showDocuments) {
                                Icon(Icons.Default.Home, "書類へ戻る")
                            }
                        } else {
                            IconButton(onClick = onSyncDrive) {
                                Icon(Icons.Default.CloudSync, "Google Driveと同期")
                            }
                            IconButton(onClick = onImportPdf) {
                                Icon(Icons.Default.PictureAsPdf, "PDFを読み込む")
                            }
                            IconButton(onClick = { createFolder = true }) {
                                Icon(Icons.Default.CreateNewFolder, "フォルダーを作成")
                            }
                            IconButton(onClick = { createNote = true }) {
                                Icon(Icons.Default.Add, "ノートを作成")
                            }
                            IconButton(onClick = viewModel::showTrash) {
                                Icon(Icons.Default.DeleteOutline, "ゴミ箱")
                            }
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, "設定")
                        }
                    },
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = {
                        Text(if (viewModel.showingTrash) "ゴミ箱を検索" else "ノートとフォルダーを検索")
                    },
                )
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            if (maxWidth >= 840.dp) {
                Row(Modifier.fillMaxSize()) {
                    HomeFolderSidebar(
                        folders = viewModel.activeFolders,
                        selected = viewModel.currentFolderId,
                        showingTrash = viewModel.showingTrash,
                        onDocuments = viewModel::showDocuments,
                        onTrash = viewModel::showTrash,
                        onSelect = viewModel::enterFolder,
                    )
                    VerticalDivider()
                    HomeGrid(
                        folders = folders,
                        notes = notes,
                        showingTrash = viewModel.showingTrash,
                        onFolder = { viewModel.enterFolder(it.id) },
                        onNote = { viewModel.openNote(it.id) },
                        onRename = { renameTarget = it },
                        onMove = { moveTarget = it },
                        onTrash = { trashTarget = it },
                        onRestore = { target ->
                            when (target) {
                                is LibraryTarget.Note -> viewModel.restoreLibraryNote(target.value.id)
                                is LibraryTarget.Folder -> viewModel.restoreLibraryFolder(target.value.id)
                            }
                        },
                        onDelete = { deleteTarget = it },
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                HomeGrid(
                    folders = folders,
                    notes = notes,
                    showingTrash = viewModel.showingTrash,
                    onFolder = { viewModel.enterFolder(it.id) },
                    onNote = { viewModel.openNote(it.id) },
                    onRename = { renameTarget = it },
                    onMove = { moveTarget = it },
                    onTrash = { trashTarget = it },
                    onRestore = { target ->
                        when (target) {
                            is LibraryTarget.Note -> viewModel.restoreLibraryNote(target.value.id)
                            is LibraryTarget.Folder -> viewModel.restoreLibraryFolder(target.value.id)
                        }
                    },
                    onDelete = { deleteTarget = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (createNote) {
        NameDialog("新しいノート", "名称未設定のノート", onDismiss = { createNote = false }) {
            createNote = false
            viewModel.createBlankNote(it)
        }
    }
    if (createFolder) {
        NameDialog("新しいフォルダー", "新しいフォルダー", onDismiss = { createFolder = false }) {
            createFolder = false
            viewModel.createFolder(it)
        }
    }

    renameTarget?.let { target ->
        NameDialog("名前を変更", target.label, onDismiss = { renameTarget = null }) { name ->
            renameTarget = null
            when (target) {
                is LibraryTarget.Note -> viewModel.renameLibraryNote(target.value.id, name)
                is LibraryTarget.Folder -> viewModel.renameLibraryFolder(target.value.id, name)
            }
        }
    }

    moveTarget?.let { target ->
        val destinations = when (target) {
            is LibraryTarget.Note -> viewModel.activeFolders
            is LibraryTarget.Folder -> viewModel.moveTargetsForFolder(target.value.id)
        }
        val currentDestination = when (target) {
            is LibraryTarget.Note -> target.value.folderId
            is LibraryTarget.Folder -> target.value.parentId
        }
        MoveDialog(
            itemName = target.label,
            folders = destinations,
            initialFolderId = currentDestination,
            onDismiss = { moveTarget = null },
        ) { folderId ->
            moveTarget = null
            when (target) {
                is LibraryTarget.Note -> viewModel.moveLibraryNote(target.value.id, folderId)
                is LibraryTarget.Folder -> viewModel.moveLibraryFolder(target.value.id, folderId)
            }
        }
    }

    trashTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { trashTarget = null },
            title = { Text("ゴミ箱へ移動") },
            text = {
                Text(
                    if (target is LibraryTarget.Folder) {
                        "「${target.label}」と中の項目をゴミ箱へ移動します。"
                    } else {
                        "「${target.label}」をゴミ箱へ移動します。"
                    },
                )
            },
            confirmButton = {
                Button(onClick = {
                    trashTarget = null
                    when (target) {
                        is LibraryTarget.Note -> viewModel.trashLibraryNote(target.value.id)
                        is LibraryTarget.Folder -> viewModel.trashLibraryFolder(target.value.id)
                    }
                }) { Text("移動") }
            },
            dismissButton = {
                TextButton(onClick = { trashTarget = null }) { Text("キャンセル") }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("完全に削除") },
            text = {
                Text(
                    if (target is LibraryTarget.Folder) {
                        "「${target.label}」と中の項目を完全に削除します。元に戻せません。"
                    } else {
                        "「${target.label}」を完全に削除します。元に戻せません。"
                    },
                )
            },
            confirmButton = {
                Button(onClick = {
                    deleteTarget = null
                    when (target) {
                        is LibraryTarget.Note -> viewModel.deleteLibraryNote(target.value.id)
                        is LibraryTarget.Folder -> viewModel.deleteLibraryFolder(target.value.id)
                    }
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("キャンセル") }
            },
        )
    }

    if (emptyTrash) {
        AlertDialog(
            onDismissRequest = { emptyTrash = false },
            title = { Text("ゴミ箱を空にする") },
            text = { Text("ゴミ箱内のすべての項目を完全に削除します。元に戻せません。") },
            confirmButton = {
                Button(onClick = {
                    emptyTrash = false
                    viewModel.emptyTrash()
                }) { Text("すべて削除") }
            },
            dismissButton = {
                TextButton(onClick = { emptyTrash = false }) { Text("キャンセル") }
            },
        )
    }
}

private sealed interface LibraryTarget {
    val label: String

    data class Note(val value: NoteSummary) : LibraryTarget {
        override val label: String get() = value.title
    }

    data class Folder(val value: FolderRecord) : LibraryTarget {
        override val label: String get() = value.name
    }
}

@Composable
private fun HomeFolderSidebar(
    folders: List<FolderRecord>,
    selected: String?,
    showingTrash: Boolean,
    onDocuments: () -> Unit,
    onTrash: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    Surface(
        modifier = Modifier.width(240.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        LazyColumn(
            Modifier.fillMaxSize().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                SidebarItem("書類", Icons.Default.Home, !showingTrash && selected == null, onClick = onDocuments)
            }
            item {
                SidebarItem("ゴミ箱", Icons.Default.DeleteOutline, showingTrash, onClick = onTrash)
            }
            items(
                folders.sortedWith(
                    compareBy<FolderRecord> { it.parentId != null }.thenBy { it.name.lowercase() },
                ),
                key = { it.id },
            ) { folder ->
                SidebarItem(
                    folder.name,
                    Icons.Default.Folder,
                    !showingTrash && selected == folder.id,
                    indent = folder.parentId != null,
                ) { onSelect(folder.id) }
            }
        }
    }
}

@Composable
private fun SidebarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    indent: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(
                start = if (indent) 28.dp else 12.dp,
                top = 10.dp,
                bottom = 10.dp,
                end = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HomeGrid(
    folders: List<FolderRecord>,
    notes: List<NoteSummary>,
    showingTrash: Boolean,
    onFolder: (FolderRecord) -> Unit,
    onNote: (NoteSummary) -> Unit,
    onRename: (LibraryTarget) -> Unit,
    onMove: (LibraryTarget) -> Unit,
    onTrash: (LibraryTarget) -> Unit,
    onRestore: (LibraryTarget) -> Unit,
    onDelete: (LibraryTarget) -> Unit,
    modifier: Modifier,
) {
    if (folders.isEmpty() && notes.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    if (showingTrash) Icons.Default.DeleteOutline else Icons.Default.Description,
                    null,
                    Modifier.size(52.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(if (showingTrash) "ゴミ箱は空です" else "このフォルダーにはノートがありません")
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(170.dp),
        modifier = modifier,
        contentPadding = PaddingValues(18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(folders, key = { "folder-${it.id}" }) { folder ->
            val target = LibraryTarget.Folder(folder)
            Card(
                onClick = { if (!showingTrash) onFolder(folder) },
                modifier = Modifier.fillMaxWidth().height(210.dp),
            ) {
                Box(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            null,
                            Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            folder.name,
                            Modifier.padding(12.dp),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    LibraryItemMenu(
                        showingTrash = showingTrash,
                        onRename = { onRename(target) },
                        onMove = { onMove(target) },
                        onTrash = { onTrash(target) },
                        onRestore = { onRestore(target) },
                        onDelete = { onDelete(target) },
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
            }
        }
        items(notes, key = { it.id }) { note ->
            val target = LibraryTarget.Note(note)
            val bitmap = remember(note.thumbnailPath, note.updatedAt) {
                note.thumbnailPath?.let(BitmapFactory::decodeFile)
            }
            Card(
                onClick = { if (!showingTrash) onNote(note) },
                modifier = Modifier.fillMaxWidth().height(250.dp),
            ) {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        Box(
                            Modifier.fillMaxWidth().weight(1f).background(Color.White),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Icon(
                                    Icons.Default.Description,
                                    null,
                                    Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                note.title,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${note.pageCount}ページ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    LibraryItemMenu(
                        showingTrash = showingTrash,
                        onRename = { onRename(target) },
                        onMove = { onMove(target) },
                        onTrash = { onTrash(target) },
                        onRestore = { onRestore(target) },
                        onDelete = { onDelete(target) },
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryItemMenu(
    showingTrash: Boolean,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, "項目メニュー")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (showingTrash) {
                DropdownMenuItem(
                    text = { Text("元に戻す") },
                    leadingIcon = { Icon(Icons.Default.RestoreFromTrash, null) },
                    onClick = {
                        expanded = false
                        onRestore()
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { Text("名前を変更") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = {
                        expanded = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text("移動") },
                    leadingIcon = { Icon(Icons.Default.DriveFileMove, null) },
                    onClick = {
                        expanded = false
                        onMove()
                    },
                )
                DropdownMenuItem(
                    text = { Text("ゴミ箱へ移動") },
                    leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                    onClick = {
                        expanded = false
                        onTrash()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("完全に削除") },
                leadingIcon = { Icon(Icons.Default.DeleteForever, null) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun MoveDialog(
    itemName: String,
    folders: List<FolderRecord>,
    initialFolderId: String?,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var selectedFolderId by remember(itemName, initialFolderId) { mutableStateOf(initialFolderId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移動先を選択") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MoveDestinationRow("書類", selectedFolderId == null) { selectedFolderId = null }
                folders.forEach { folder ->
                    MoveDestinationRow(folder.name, selectedFolderId == folder.id) {
                        selectedFolderId = folder.id
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedFolderId) }) { Text("移動") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}

@Composable
private fun MoveDestinationRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            if (selected) Icons.Default.CheckCircle else Icons.Default.Folder,
            null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private enum class EditorPanel {
    SEARCH,
    AI,
    PEN,
    ERASER,
    LASSO,
    TEXT,
    STICKER,
    IMAGE,
    SHAPE,
    STICKY,
    POINTER,
    VOICE,
    DETAILS,
}

@Composable
private fun EditorWorkspace(
    viewModel: MainViewModel,
    uiPreferences: UiPreferencesState,
    onImportImage: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val session = viewModel.activeSession ?: return
    var showPages by rememberSaveable(session.id) { mutableStateOf(false) }
    var readOnly by rememberSaveable(session.id) { mutableStateOf(false) }
    var panel by rememberSaveable(session.id) { mutableStateOf<EditorPanel?>(null) }
    var renameNote by remember(session.id) { mutableStateOf(false) }
    var deleteNote by remember(session.id) { mutableStateOf(false) }

    BackHandler {
        when {
            panel != null -> panel = null
            showPages -> showPages = false
            else -> viewModel.showLibrary()
        }
    }

    val toolbar: @Composable (Boolean) -> Unit = { vertical ->
        EditorCommandBar(
            viewModel = viewModel,
            vertical = vertical,
            readOnly = readOnly,
            showPages = showPages,
            activePanel = panel,
            onTogglePages = { showPages = !showPages },
            onToggleReadOnly = {
                readOnly = !readOnly
                panel = null
            },
            onPanel = { target ->
                panel = if (panel == target) null else target
            },
        )
    }

    val body: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                if (showPages) {
                    PageRail(
                        viewModel = viewModel,
                        session = session,
                        onClose = { showPages = false },
                    )
                    VerticalDivider()
                }
                SharedZoomPages(
                    viewModel = viewModel,
                    session = session,
                    readOnly = readOnly,
                    modifier = Modifier.weight(1f),
                )
            }

            panel?.let { activePanel ->
                EditorDetailPanel(
                    panel = activePanel,
                    viewModel = viewModel,
                    uiPreferences = uiPreferences,
                    onImportImage = onImportImage,
                    onDismiss = { panel = null },
                    onRename = { renameNote = true },
                    onDelete = { deleteNote = true },
                    modifier = detailPanelModifier(uiPreferences.toolbarDock),
                )
            }

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        }
    }

    when (uiPreferences.toolbarDock) {
        ToolbarDock.TOP -> Column(Modifier.fillMaxSize()) {
            toolbar(false)
            HorizontalDivider()
            Box(Modifier.weight(1f)) { body() }
        }
        ToolbarDock.BOTTOM -> Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { body() }
            HorizontalDivider()
            toolbar(false)
        }
        ToolbarDock.LEFT -> Row(Modifier.fillMaxSize()) {
            toolbar(true)
            VerticalDivider()
            Box(Modifier.weight(1f)) { body() }
        }
        ToolbarDock.RIGHT -> Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { body() }
            VerticalDivider()
            toolbar(true)
        }
    }

    if (renameNote) {
        NameDialog("ノート名を変更", session.title, onDismiss = { renameNote = false }) {
            renameNote = false
            viewModel.renameActiveNote(it)
        }
    }
    if (deleteNote) {
        AlertDialog(
            onDismissRequest = { deleteNote = false },
            title = { Text("ノートを削除") },
            text = { Text("「${session.title}」を端末から完全に削除します。") },
            confirmButton = {
                Button(onClick = {
                    deleteNote = false
                    viewModel.deleteActiveNote()
                }) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteNote = false }) { Text("キャンセル") }
            },
        )
    }
}

private fun BoxScope.detailPanelModifier(dock: ToolbarDock): Modifier = when (dock) {
    ToolbarDock.TOP -> Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
    ToolbarDock.BOTTOM -> Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
    ToolbarDock.LEFT -> Modifier.align(Alignment.CenterStart).padding(start = 10.dp)
    ToolbarDock.RIGHT -> Modifier.align(Alignment.CenterEnd).padding(end = 10.dp)
}

@Composable
private fun EditorCommandBar(
    viewModel: MainViewModel,
    vertical: Boolean,
    readOnly: Boolean,
    showPages: Boolean,
    activePanel: EditorPanel?,
    onTogglePages: () -> Unit,
    onToggleReadOnly: () -> Unit,
    onPanel: (EditorPanel) -> Unit,
) {
    val details = CommandSpec(Icons.Default.MoreHoriz, "詳細", activePanel == EditorPanel.DETAILS) {
        onPanel(EditorPanel.DETAILS)
    }
    val share = CommandSpec(Icons.Default.Share, "共有", false) {
        viewModel.saveActive()
        viewModel.reportStatus("ノートを保存しました")
    }
    val addPage = CommandSpec(
        Icons.AutoMirrored.Filled.NoteAdd,
        "ページ追加",
        false,
        viewModel::addPage,
    )
    val pen = CommandSpec(
        Icons.Default.Brush,
        "ペン",
        !readOnly && viewModel.toolMode == ToolMode.PEN &&
            viewModel.brushSpec.kind != BrushKind.HIGHLIGHTER,
    ) {
        if (!readOnly) {
            if (viewModel.brushSpec.kind == BrushKind.HIGHLIGHTER) {
                viewModel.setBrushKind(BrushKind.PRESSURE_PEN)
            } else {
                viewModel.setTool(ToolMode.PEN)
            }
            onPanel(EditorPanel.PEN)
        }
    }
    val eraser = CommandSpec(
        Icons.Default.DeleteOutline,
        "消しゴム",
        !readOnly && viewModel.toolMode == ToolMode.ERASER,
    ) {
        if (!readOnly) {
            viewModel.setTool(ToolMode.ERASER)
            onPanel(EditorPanel.ERASER)
        }
    }
    val text = CommandSpec(Icons.Default.TextFields, "テキスト", activePanel == EditorPanel.TEXT) {
        onPanel(EditorPanel.TEXT)
    }
    val sticker = CommandSpec(
        Icons.Default.EmojiEmotions,
        "ステッカー",
        activePanel == EditorPanel.STICKER,
    ) { onPanel(EditorPanel.STICKER) }
    val lasso = CommandSpec(
        Icons.Default.Gesture,
        "投げ縄",
        !readOnly && viewModel.toolMode == ToolMode.LASSO,
    ) {
        if (!readOnly) {
            viewModel.setTool(ToolMode.LASSO)
            onPanel(EditorPanel.LASSO)
        }
    }
    val image = CommandSpec(
        Icons.Default.Image,
        "画像",
        !readOnly && viewModel.toolMode == ToolMode.IMAGE,
    ) {
        if (!readOnly) {
            viewModel.setTool(ToolMode.IMAGE)
            onPanel(EditorPanel.IMAGE)
        }
    }
    val shape = CommandSpec(Icons.Default.Category, "シェイプ", activePanel == EditorPanel.SHAPE) {
        onPanel(EditorPanel.SHAPE)
    }
    val sticky = CommandSpec(
        Icons.AutoMirrored.Filled.StickyNote2,
        "付箋",
        activePanel == EditorPanel.STICKY,
    ) { onPanel(EditorPanel.STICKY) }
    val pointer = CommandSpec(Icons.Default.NearMe, "ポインタ", activePanel == EditorPanel.POINTER) {
        onPanel(EditorPanel.POINTER)
    }
    val voice = CommandSpec(Icons.Default.Mic, "音声", activePanel == EditorPanel.VOICE) {
        onPanel(EditorPanel.VOICE)
    }
    val readOnlyCommand = CommandSpec(Icons.Default.Visibility, "閲覧専用", readOnly, onToggleReadOnly)
    val ai = CommandSpec(Icons.Default.AutoAwesome, "AI", activePanel == EditorPanel.AI) {
        onPanel(EditorPanel.AI)
    }
    val search = CommandSpec(Icons.Default.Search, "検索", activePanel == EditorPanel.SEARCH) {
        onPanel(EditorPanel.SEARCH)
    }
    val pages = CommandSpec(Icons.Default.Menu, "ページ一覧", showPages, onTogglePages)

    val detailBlock = listOf(addPage, share, details)
    val toolsBeforeLasso = listOf(pen, eraser, text, sticker)
    val toolsAfterLasso = listOf(image, shape, sticky, pointer, voice)
    val pageBlock = listOf(pages, search, ai, readOnlyCommand)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        BoxWithConstraints {
            if (vertical) {
                if (maxHeight >= 1040.dp) {
                    val beforeHeight = COMMAND_EXTENT * toolsBeforeLasso.size.toFloat()
                    val afterHeight = COMMAND_EXTENT * toolsAfterLasso.size.toFloat()
                    Box(Modifier.width(COMMAND_EXTENT).fillMaxHeight().padding(vertical = 6.dp)) {
                        CommandColumn(detailBlock, Modifier.align(Alignment.TopCenter))
                        CommandColumn(pageBlock, Modifier.align(Alignment.BottomCenter))
                        CommandColumn(
                            toolsBeforeLasso,
                            Modifier.align(Alignment.Center).offset(
                                y = -(COMMAND_EXTENT / 2f + beforeHeight / 2f),
                            ),
                        )
                        CommandButton(lasso, Modifier.align(Alignment.Center))
                        CommandColumn(
                            toolsAfterLasso,
                            Modifier.align(Alignment.Center).offset(
                                y = COMMAND_EXTENT / 2f + afterHeight / 2f,
                            ),
                        )
                    }
                } else {
                    Column(
                        Modifier.width(COMMAND_EXTENT).fillMaxHeight().verticalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CommandColumn(detailBlock)
                        Spacer(Modifier.height(14.dp))
                        CommandColumn(toolsBeforeLasso)
                        CommandButton(lasso)
                        CommandColumn(toolsAfterLasso)
                        Spacer(Modifier.height(14.dp))
                        CommandColumn(pageBlock)
                    }
                }
            } else {
                if (maxWidth >= 1040.dp) {
                    val beforeWidth = COMMAND_EXTENT * toolsBeforeLasso.size.toFloat()
                    val afterWidth = COMMAND_EXTENT * toolsAfterLasso.size.toFloat()
                    Box(Modifier.fillMaxWidth().height(COMMAND_EXTENT).padding(horizontal = 8.dp)) {
                        CommandRow(detailBlock, Modifier.align(Alignment.CenterStart))
                        CommandRow(pageBlock, Modifier.align(Alignment.CenterEnd))
                        CommandRow(
                            toolsBeforeLasso,
                            Modifier.align(Alignment.Center).offset(
                                x = -(COMMAND_EXTENT / 2f + beforeWidth / 2f),
                            ),
                        )
                        CommandButton(lasso, Modifier.align(Alignment.Center))
                        CommandRow(
                            toolsAfterLasso,
                            Modifier.align(Alignment.Center).offset(
                                x = COMMAND_EXTENT / 2f + afterWidth / 2f,
                            ),
                        )
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().height(COMMAND_EXTENT)
                            .horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CommandRow(detailBlock)
                        Spacer(Modifier.width(14.dp))
                        CommandRow(toolsBeforeLasso)
                        CommandButton(lasso)
                        CommandRow(toolsAfterLasso)
                        Spacer(Modifier.width(14.dp))
                        CommandRow(pageBlock)
                    }
                }
            }
        }
    }
}

private data class CommandSpec(
    val icon: ImageVector,
    val description: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

private val COMMAND_EXTENT = 52.dp

@Composable
private fun CommandRow(
    commands: List<CommandSpec>,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        commands.forEach { CommandButton(it) }
    }
}

@Composable
private fun CommandColumn(
    commands: List<CommandSpec>,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        commands.forEach { CommandButton(it) }
    }
}

@Composable
private fun CommandButton(
    spec: CommandSpec,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(COMMAND_EXTENT).padding(2.dp),
        shape = CircleShape,
        color = if (spec.selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
    ) {
        IconButton(onClick = spec.onClick) {
            Icon(spec.icon, spec.description)
        }
    }
}

@Composable
private fun EditorDetailPanel(
    panel: EditorPanel,
    viewModel: MainViewModel,
    uiPreferences: UiPreferencesState,
    onImportImage: () -> Unit,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier.widthIn(max = if (panel == EditorPanel.DETAILS) 360.dp else 520.dp)
            .zIndex(20f),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
    ) {
        Column(
            Modifier.padding(14.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    panelTitle(panel),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "閉じる")
                }
            }
            HorizontalDivider()
            when (panel) {
                EditorPanel.SEARCH -> SearchPanel()
                EditorPanel.AI -> AiPanel(viewModel)
                EditorPanel.PEN -> PenPanel(viewModel)
                EditorPanel.ERASER -> Text(
                    "線単位で消去します。スタイラスの消しゴムボタンも利用できます。",
                )
                EditorPanel.LASSO -> LassoPanel(viewModel)
                EditorPanel.TEXT -> FeaturePanel(
                    "テキスト",
                    "テキストボックス配置用のツール領域です。現行のインクデータ形式を壊さないよう、入力UIを先行配置しています。",
                )
                EditorPanel.STICKER -> FeaturePanel(
                    "ステッカー",
                    "ステッカー選択パネルです。画像素材の追加先として利用します。",
                )
                EditorPanel.IMAGE -> ImagePanel(viewModel, onImportImage)
                EditorPanel.SHAPE -> FeaturePanel(
                    "シェイプ",
                    "線、四角、円、三角などの図形ツールを配置する領域です。",
                )
                EditorPanel.STICKY -> FeaturePanel(
                    "付箋",
                    "ページ上へ付箋を追加するためのツール領域です。",
                )
                EditorPanel.POINTER -> FeaturePanel(
                    "ポインタ",
                    "発表時に一時表示するポインタ用のツール領域です。",
                )
                EditorPanel.VOICE -> FeaturePanel(
                    "音声",
                    "録音と再生操作を配置するためのツール領域です。",
                )
                EditorPanel.DETAILS -> DetailsPanel(
                    viewModel = viewModel,
                    uiPreferences = uiPreferences,
                    onRename = onRename,
                    onDelete = onDelete,
                )
            }
        }
    }
}

private fun panelTitle(panel: EditorPanel): String = when (panel) {
    EditorPanel.SEARCH -> "検索"
    EditorPanel.AI -> "AI"
    EditorPanel.PEN -> "ペン"
    EditorPanel.ERASER -> "消しゴム"
    EditorPanel.LASSO -> "投げ縄"
    EditorPanel.TEXT -> "テキスト"
    EditorPanel.STICKER -> "ステッカー"
    EditorPanel.IMAGE -> "画像"
    EditorPanel.SHAPE -> "シェイプ"
    EditorPanel.STICKY -> "付箋"
    EditorPanel.POINTER -> "ポインタ"
    EditorPanel.VOICE -> "音声"
    EditorPanel.DETAILS -> "詳細"
}

@Composable
private fun SearchPanel() {
    var query by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("ページ内を検索") },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "PDFのテキスト検索に対応するための検索UIです。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AiPanel(viewModel: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("ノートの要約、質問、説明を行うAI機能の入口です。")
        Button(
            onClick = { viewModel.reportStatus("AI機能の接続先は未設定です") },
        ) {
            Icon(Icons.Default.AutoAwesome, null)
            Spacer(Modifier.width(8.dp))
            Text("AIを開く")
        }
    }
}

@Composable
private fun FeaturePanel(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PenPanel(viewModel: MainViewModel) {
    var showCustomEditor by remember { mutableStateOf(false) }
    val colors = listOf(
        0xFF111111.toInt() to Color(0xFF111111),
        0xFF1565C0.toInt() to Color(0xFF1565C0),
        0xFFC62828.toInt() to Color(0xFFC62828),
        0xFF2E7D32.toInt() to Color(0xFF2E7D32),
        0xFF6A1B9A.toInt() to Color(0xFF6A1B9A),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ToolChoice(
                selected = viewModel.brushSpec.kind == BrushKind.PRESSURE_PEN,
                icon = Icons.Default.Edit,
                label = "筆圧ペン",
            ) { viewModel.setBrushKind(BrushKind.PRESSURE_PEN) }
            ToolChoice(
                selected = viewModel.brushSpec.kind == BrushKind.MARKER,
                icon = Icons.Default.BorderColor,
                label = "マーカー",
            ) { viewModel.setBrushKind(BrushKind.MARKER) }
            ToolChoice(
                selected = viewModel.brushSpec.kind == BrushKind.HIGHLIGHTER,
                icon = Icons.Default.Highlight,
                label = "蛍光ペン",
            ) { viewModel.setBrushKind(BrushKind.HIGHLIGHTER) }
            ToolChoice(
                selected = viewModel.brushSpec.kind == BrushKind.CUSTOM,
                icon = Icons.Default.Tune,
                label = "カスタム",
            ) {
                viewModel.setBrushKind(BrushKind.CUSTOM)
                showCustomEditor = true
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            colors.forEach { (argb, color) ->
                ColorSwatch(
                    color = color,
                    selected = (viewModel.brushSpec.colorArgb and 0x00FFFFFF) ==
                        (argb and 0x00FFFFFF),
                ) {
                    viewModel.updateBrush(colorArgb = argb)
                }
            }
        }
        Column {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("太さ")
                Text("${"%.1f".format(viewModel.brushSpec.size)} pt")
            }
            Slider(
                value = viewModel.brushSpec.size,
                onValueChange = { viewModel.updateBrush(size = it) },
                valueRange = if (viewModel.brushSpec.kind == BrushKind.HIGHLIGHTER) {
                    8f..48f
                } else {
                    1f..24f
                },
            )
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
private fun ToolChoice(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
    )
}

@Composable
private fun LassoPanel(viewModel: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "通常の投げ縄は投げ縄ツールで囲みます。ペンの囲みを変換する場合は、閉じた線を描いてペンを離し、その線上を長押ししてください。長押ししたまま動かすと選択範囲を移動でき、囲み線は移動開始時に消えます。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            Text("ペンで囲む → 離す → 囲み線上を長押し", Modifier.padding(horizontal = 8.dp).weight(1f))
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
                IconButton(onClick = viewModel::applyCurrentBrushToSelected) {
                    Icon(Icons.Default.Brush, "現在のブラシを適用")
                }
                IconButton(onClick = { viewModel.scaleSelectedStrokes(0.85f) }) {
                    Icon(Icons.Default.ZoomOut, "縮小")
                }
                IconButton(onClick = { viewModel.scaleSelectedStrokes(1.15f) }) {
                    Icon(Icons.Default.ZoomIn, "拡大")
                }
                IconButton(onClick = viewModel::deleteSelectedStrokes) {
                    Icon(Icons.Default.DeleteOutline, "削除")
                }
            }
        }
    }
}

@Composable
private fun ImagePanel(viewModel: MainViewModel, onImportImage: () -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Button(onClick = onImportImage) {
            Icon(Icons.Default.AddPhotoAlternate, null)
            Spacer(Modifier.width(8.dp))
            Text("画像を追加")
        }
        if (viewModel.activePage?.selectedImageId != null) {
            IconButton(onClick = { viewModel.scaleSelectedImage(0.85f) }) {
                Icon(Icons.Default.ZoomOut, "画像を縮小")
            }
            IconButton(onClick = { viewModel.scaleSelectedImage(1.15f) }) {
                Icon(Icons.Default.ZoomIn, "画像を拡大")
            }
            IconButton(onClick = viewModel::deleteSelectedImage) {
                Icon(Icons.Default.DeleteOutline, "画像を削除")
            }
        }
    }
}

@Composable
private fun DetailsPanel(
    viewModel: MainViewModel,
    uiPreferences: UiPreferencesState,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("ページ", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("スクロール方向", Modifier.weight(1f))
            FilterChip(
                selected = viewModel.scrollAxis == ScrollAxis.VERTICAL,
                onClick = {
                    if (viewModel.scrollAxis != ScrollAxis.VERTICAL) viewModel.toggleScrollAxis()
                },
                label = { Text("縦") },
            )
            Spacer(Modifier.width(6.dp))
            FilterChip(
                selected = viewModel.scrollAxis == ScrollAxis.HORIZONTAL,
                onClick = {
                    if (viewModel.scrollAxis != ScrollAxis.HORIZONTAL) viewModel.toggleScrollAxis()
                },
                label = { Text("横") },
            )
        }
        HorizontalDivider()
        Text("ツールバー位置", style = MaterialTheme.typography.titleSmall)
        DockChoices(uiPreferences)
        HorizontalDivider()
        Button(onClick = onRename, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Edit, null)
            Spacer(Modifier.width(8.dp))
            Text("ノート名を変更")
        }
        TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Text("ノートを削除", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun DockChoices(uiPreferences: UiPreferencesState) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DockChoice(Icons.Default.ArrowUpward, "上", ToolbarDock.TOP, uiPreferences)
        DockChoice(Icons.Default.ArrowDownward, "下", ToolbarDock.BOTTOM, uiPreferences)
        DockChoice(Icons.AutoMirrored.Filled.ArrowBack, "左", ToolbarDock.LEFT, uiPreferences)
        DockChoice(Icons.AutoMirrored.Filled.ArrowForward, "右", ToolbarDock.RIGHT, uiPreferences)
    }
}

@Composable
private fun DockChoice(
    icon: ImageVector,
    label: String,
    dock: ToolbarDock,
    uiPreferences: UiPreferencesState,
) {
    FilterChip(
        selected = uiPreferences.toolbarDock == dock,
        onClick = { uiPreferences.updateToolbarDock(dock) },
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(17.dp)) },
    )
}

@Composable
private fun PageRail(
    viewModel: MainViewModel,
    session: NoteSession,
    onClose: () -> Unit,
) {
    Surface(
        modifier = Modifier.width(300.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "ページ一覧",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = viewModel::addPage) {
                    Icon(Icons.Default.Add, "ページを追加")
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, "閉じる")
                }
            }
            HorizontalDivider()
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(session.pages, key = { _, page -> page.id }) { index, page ->
                    val preview by produceState<Bitmap?>(
                        initialValue = null,
                        session.id,
                        page.id,
                        page.contentVersion,
                    ) {
                        value = viewModel.renderPagePreview(session, page, 320)
                    }
                    Column {
                        Card(
                            onClick = { viewModel.activatePage(index) },
                            border = if (index == session.activePageIndex) {
                                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                null
                            },
                        ) {
                            Box(
                                Modifier.fillMaxWidth()
                                    .aspectRatio(page.width / page.height)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (preview != null) {
                                    Image(
                                        bitmap = preview!!.asImageBitmap(),
                                        contentDescription = "ページ${index + 1}",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit,
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${index + 1}", Modifier.weight(1f))
                            IconButton(
                                onClick = { viewModel.duplicatePage(index) },
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(Icons.Default.ContentCopy, "複製", Modifier.size(16.dp))
                            }
                            IconButton(
                                enabled = session.pages.size > 1,
                                onClick = { viewModel.deletePage(index) },
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(Icons.Default.DeleteOutline, "削除", Modifier.size(16.dp))
                            }
                        }
                    }
                }
                item {
                    Card(
                        onClick = viewModel::addPage,
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.72f),
                    ) {
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(Icons.Default.Add, null)
                            Text("ページを追加", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeSettingsScreen(
    viewModel: MainViewModel,
    uiPreferences: UiPreferencesState,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "ホームに戻る")
                    }
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
                                onClick = { uiPreferences.updateThemeMode(mode) },
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
                            onClick = {
                                uiPreferences.updateTabLayoutMode(TabLayoutMode.HORIZONTAL)
                            },
                            label = { Text("横タブ") },
                        )
                        FilterChip(
                            selected = uiPreferences.tabLayoutMode == TabLayoutMode.VERTICAL,
                            onClick = {
                                uiPreferences.updateTabLayoutMode(TabLayoutMode.VERTICAL)
                            },
                            label = { Text("縦タブ") },
                        )
                    }
                }
            }
            item {
                SettingsGroup("ツールバー位置") {
                    DockChoices(uiPreferences)
                    Text(
                        "ツールバーは上・下・左・右のいずれかに固定されます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SettingsGroup("ページ移動に使う指") {
                    ChoiceRow {
                        FilterChip(
                            selected = viewModel.navigationGestureMode ==
                                NavigationGestureMode.ONE_FINGER,
                            onClick = {
                                viewModel.setNavigationGestureMode(
                                    NavigationGestureMode.ONE_FINGER,
                                )
                            },
                            label = { Text("1本") },
                        )
                        FilterChip(
                            selected = viewModel.navigationGestureMode ==
                                NavigationGestureMode.TWO_FINGER,
                            onClick = {
                                viewModel.setNavigationGestureMode(
                                    NavigationGestureMode.TWO_FINGER,
                                )
                            },
                            label = { Text("2本") },
                        )
                    }
                }
            }
            item {
                SettingsGroup("ページ方向") {
                    ChoiceRow {
                        FilterChip(
                            selected = viewModel.scrollAxis == ScrollAxis.VERTICAL,
                            onClick = {
                                if (viewModel.scrollAxis != ScrollAxis.VERTICAL) {
                                    viewModel.toggleScrollAxis()
                                }
                            },
                            label = { Text("縦") },
                        )
                        FilterChip(
                            selected = viewModel.scrollAxis == ScrollAxis.HORIZONTAL,
                            onClick = {
                                if (viewModel.scrollAxis != ScrollAxis.HORIZONTAL) {
                                    viewModel.toggleScrollAxis()
                                }
                            },
                            label = { Text("横") },
                        )
                    }
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
private fun SharedZoomPages(
    viewModel: MainViewModel,
    session: NoteSession,
    readOnly: Boolean,
    modifier: Modifier,
) {
    var zoom by rememberSaveable(session.id) { mutableFloatStateOf(1f) }
    val zoomModifier = Modifier.sharedPageZoomGesture { factor ->
        zoom = (zoom * factor).coerceIn(0.55f, 3f)
    }

    BoxWithConstraints(modifier.then(zoomModifier)) {
        val baseWidth = (maxWidth - 28.dp).coerceAtMost(900.dp).coerceAtLeast(240.dp)
        val pageWidth = baseWidth * zoom
        when (viewModel.scrollAxis) {
            ScrollAxis.VERTICAL -> VerticalPages(
                viewModel = viewModel,
                session = session,
                pageWidth = pageWidth,
                viewportWidth = maxWidth,
                readOnly = readOnly,
            )
            ScrollAxis.HORIZONTAL -> HorizontalPages(
                viewModel = viewModel,
                session = session,
                pageWidth = pageWidth,
                viewportHeight = maxHeight,
                readOnly = readOnly,
            )
        }
    }
}

@Composable
private fun VerticalPages(
    viewModel: MainViewModel,
    session: NoteSession,
    pageWidth: androidx.compose.ui.unit.Dp,
    viewportWidth: androidx.compose.ui.unit.Dp,
    readOnly: Boolean,
) {
    val verticalState = rememberLazyListState()
    val horizontalState = rememberScrollState()
    LaunchedEffect(session.activePageIndex, session.pages.size) {
        if (session.pages.isNotEmpty()) {
            verticalState.animateScrollToItem(
                session.activePageIndex.coerceIn(0, session.pages.lastIndex),
            )
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
                contentPadding = PaddingValues(vertical = PAGE_GAP),
                verticalArrangement = Arrangement.spacedBy(PAGE_GAP),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                itemsIndexed(session.pages, key = { _, page -> page.id }) { index, page ->
                    ZoomPage(
                        viewModel = viewModel,
                        session = session,
                        page = page,
                        index = index,
                        readOnly = readOnly,
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
    readOnly: Boolean,
) {
    val horizontalState = rememberLazyListState()
    val verticalState = rememberScrollState()
    val tallestPage = session.pages.maxOfOrNull {
        pageWidth * (it.height / it.width)
    } ?: viewportHeight
    val contentHeight = maxOf(tallestPage + PAGE_GAP * 2, viewportHeight)

    LaunchedEffect(session.activePageIndex, session.pages.size) {
        if (session.pages.isNotEmpty()) {
            horizontalState.animateScrollToItem(
                session.activePageIndex.coerceIn(0, session.pages.lastIndex),
            )
        }
    }

    Box(Modifier.fillMaxSize().verticalScroll(verticalState)) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().height(contentHeight),
            state = horizontalState,
            userScrollEnabled = false,
            contentPadding = PaddingValues(horizontal = PAGE_GAP),
            horizontalArrangement = Arrangement.spacedBy(PAGE_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(session.pages, key = { _, page -> page.id }) { index, page ->
                ZoomPage(
                    viewModel = viewModel,
                    session = session,
                    page = page,
                    index = index,
                    readOnly = readOnly,
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
    readOnly: Boolean,
    modifier: Modifier,
    onNavigationPan: (Float, Float) -> Unit,
) {
    val background by produceState<Bitmap?>(initialValue = null, session.id, page.id) {
        value = viewModel.renderPdfPage(session, page, 1200)
    }
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = androidx.compose.ui.graphics.RectangleShape,
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(page.width / page.height),
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
                        readOnlyProvider = { readOnly },
                        onNavigationPan = onNavigationPan,
                        onStrokeAdded = { runtime -> viewModel.addStroke(page, runtime) },
                        onEraseStart = { viewModel.beginErase(page) },
                        onErase = { x, y, radius -> viewModel.eraseAt(page, x, y, radius) },
                        onEraseEnd = { viewModel.endErase(page) },
                        onLassoFinished = { viewModel.selectWithLasso(page, it) },
                        onCircleCandidateReady = {
                            viewModel.reportStatus("囲み線を長押しすると投げ縄に変換できます")
                        },
                        onCircleHoldLasso = { strokeId, stroke ->
                            viewModel.convertCircleStrokeToLasso(page, strokeId, stroke)
                        },
                        onSelectedTransformStart = {
                            viewModel.beginSelectedStrokeTransform(page)
                        },
                        onSelectedMove = { dx, dy ->
                            viewModel.moveSelectedStrokes(page, dx, dy)
                        },
                        onSelectedTransformEnd = {
                            viewModel.endSelectedStrokeTransform(page)
                        },
                        onSelectedTransformCancel = {
                            viewModel.cancelSelectedStrokeTransform(page)
                        },
                        onImageSelected = { viewModel.selectImage(page, it) },
                        onImageTransformStart = {
                            viewModel.beginImageTransform(page, it)
                        },
                        onImageMove = { id, x, y -> viewModel.moveImage(page, id, x, y) },
                        onImageTransformEnd = { viewModel.endImageTransform(page) },
                        onImageTransformCancel = { viewModel.cancelImageTransform(page) },
                        onActivated = { viewModel.activatePage(index) },
                    )
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (readOnly) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Default.Visibility, null, Modifier.size(16.dp))
                        Text("閲覧専用", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

private fun Modifier.sharedPageZoomGesture(
    onZoom: (Float) -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var wasZooming = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val touchCount = event.changes.count {
                it.pressed && it.type == PointerType.Touch
            }
            if (touchCount >= 2) {
                val factor = event.calculateZoom()
                if (factor.isFinite() && factor > 0f) {
                    onZoom(factor.coerceIn(0.8f, 1.25f))
                }
                event.changes.forEach { it.consume() }
                wasZooming = true
            } else if (wasZooming) {
                event.changes.forEach { it.consume() }
            }
            if (event.changes.none { it.pressed }) break
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.size(if (selected) 38.dp else 34.dp),
        shape = CircleShape,
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize().background(color))
    }
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
                BrushSlider("横幅", value.scaleX, 0.15f..2.5f) {
                    value = value.copy(scaleX = it)
                }
                BrushSlider("縦幅", value.scaleY, 0.15f..2.5f) {
                    value = value.copy(scaleY = it)
                }
                BrushSlider("角の丸さ", value.cornerRounding, 0f..1f) {
                    value = value.copy(cornerRounding = it)
                }
                BrushSlider("傾斜", value.slantDegrees, -75f..75f, "°") {
                    value = value.copy(slantDegrees = it)
                }
                BrushSlider("回転", value.rotationDegrees, -180f..180f, "°") {
                    value = value.copy(rotationDegrees = it)
                }
                BrushSlider(
                    "平滑化",
                    value.smoothingWindowMillis.toFloat(),
                    0f..80f,
                    "ms",
                ) {
                    value = value.copy(smoothingWindowMillis = it.toLong())
                }
                BrushSlider(
                    "補間周波数",
                    value.upsamplingFrequencyHz.toFloat(),
                    30f..240f,
                    "Hz",
                ) {
                    value = value.copy(upsamplingFrequencyHz = it.toInt())
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(value) }) { Text("適用") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
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
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                "${"%.1f".format(value)}$suffix",
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
        )
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
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(value) }) { Text("決定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}

private val PAGE_GAP = 10.dp
