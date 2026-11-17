package app.tateyomi.ui.library

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tateyomi.data.BookSummary
import app.tateyomi.data.LibraryFolder
import app.tateyomi.data.LibrarySort
import app.tateyomi.ui.ImportUiState
import app.tateyomi.ui.LibraryFilter
import app.tateyomi.ui.UNFILED_FOLDER_ID
import app.tateyomi.ui.components.rememberBitmap
import app.tateyomi.ui.theme.Paper
import app.tateyomi.ui.theme.Vermilion
import java.io.File

@Composable
fun LibraryScreen(
    books: List<BookSummary>,
    folders: List<LibraryFolder>,
    filter: LibraryFilter,
    ready: Boolean,
    importState: ImportUiState?,
    coverFile: (BookSummary) -> File?,
    onImport: (List<Uri>) -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    onDeleteBook: (String) -> Unit,
    onDeleteBooks: (Set<String>) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameFolder: (String, String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onMoveBook: (String, String?) -> Unit,
    onMoveBooks: (Set<String>, String?) -> Unit,
    onCreateFolderAndMove: (String, String) -> Unit,
    onCreateFolderAndMoveBooks: (Set<String>, String) -> Unit,
    onUpdateBook: (String, String, String, List<String>) -> Unit,
    onUpdateTags: (Set<String>, Set<String>, Set<String>) -> Unit,
    onResetProgress: (String) -> Unit,
    onSetCover: (String, Uri) -> Unit,
    onResetCover: (String) -> Unit,
    onExportBook: (String, Uri) -> Unit,
    onFilterChange: (LibraryFilter) -> Unit,
) {
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> if (uris.isNotEmpty()) onImport(uris) }

    val query = filter.query
    val folderFilter = filter.folderId
    val selectedTags = filter.tags
    var menuBook by remember { mutableStateOf<BookSummary?>(null) }
    var moveTarget by remember { mutableStateOf<BookSummary?>(null) }
    var editTarget by remember { mutableStateOf<BookSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<BookSummary?>(null) }
    var resetTarget by remember { mutableStateOf<BookSummary?>(null) }
    var folderManagerOpen by remember { mutableStateOf(false) }
    var exportTarget by remember { mutableStateOf<BookSummary?>(null) }
    var coverTarget by remember { mutableStateOf<String?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var batchMoveOpen by remember { mutableStateOf(false) }
    var batchTagsOpen by remember { mutableStateOf(false) }
    var batchDeleteOpen by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val target = exportTarget
        exportTarget = null
        if (uri != null && target != null) onExportBook(target.id, uri)
    }
    val coverLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        val target = coverTarget
        coverTarget = null
        if (uri != null && target != null) onSetCover(target, uri)
    }

    LaunchedEffect(folders, folderFilter) {
        val current = folderFilter
        if (current != null && current != UNFILED_FOLDER_ID &&
            folders.none { it.id == current }
        ) {
            onFilterChange(filter.copy(folderId = null))
        }
    }

    val folderScoped = remember(books, folderFilter) {
        when (folderFilter) {
            null -> books
            UNFILED_FOLDER_ID -> books.filter { it.folderId == null }
            else -> books.filter { it.folderId == folderFilter }
        }
    }
    val allTags = remember(folderScoped) {
        folderScoped.flatMap { it.tags }.distinct().sorted()
    }
    val sortedFolderBooks = remember(folderScoped, filter.sort) {
        sortLibraryBooks(folderScoped, filter.sort)
    }
    val visibleBooks = remember(sortedFolderBooks, selectedTags, query) {
        val search = query.trim()
        sortedFolderBooks.filter { book ->
            val tagsMatch = selectedTags.isEmpty() || selectedTags.all { it in book.tags }
            val queryMatch = search.isBlank() ||
                book.title.contains(search, ignoreCase = true) ||
                book.author.contains(search, ignoreCase = true) ||
                book.tags.any { it.contains(search, ignoreCase = true) }
            tagsMatch && queryMatch
        }
    }
    LaunchedEffect(folderFilter, allTags) {
        if (selectedTags.isNotEmpty()) {
            val kept = selectedTags.intersect(allTags.toSet())
            if (kept != selectedTags) {
                onFilterChange(filter.copy(tags = kept))
            }
        }
    }
    LaunchedEffect(folderFilter, query, selectedTags) {
        if (selectionMode) selectedIds = emptySet()
    }
    val hideRecent = query.isNotBlank() || selectedTags.isNotEmpty() || selectionMode
    val recent = remember(folderScoped, hideRecent) {
        if (hideRecent) {
            null
        } else {
            folderScoped.filter {
                it.lastOpenedAt != null && (it.progress?.overallFraction ?: 0f) < 0.995f
            }.maxByOrNull { it.lastOpenedAt ?: 0L }
        }
    }
    val sectionTitle = when {
        query.isNotBlank() -> "検索結果"
        folderFilter == UNFILED_FOLDER_ID -> "未分類"
        folderFilter != null -> folders.firstOrNull { it.id == folderFilter }?.name ?: "本棚"
        else -> "本棚"
    }
    val allVisibleSelected = visibleBooks.isNotEmpty() &&
        visibleBooks.all { it.id in selectedIds }
    val selectedBooks = books.filter { it.id in selectedIds }
    val selectedCommonFolder = selectedBooks.firstOrNull()?.folderId?.takeIf { folder ->
        selectedBooks.all { it.folderId == folder }
    }
    val selectedTagsForEditing = remember(selectedBooks) {
        selectedBooks.flatMap { it.tags }.distinct().sorted()
    }

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    fun toggleSelection(id: String) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    BackHandler(enabled = selectionMode && importState == null) {
        exitSelection()
    }
    BackHandler(enabled = importState != null) { }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            ),
    ) {
        when {
            !ready -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                )
            }
            books.isEmpty() -> Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                Box(Modifier.padding(horizontal = 20.dp)) {
                    LibraryHeader(
                        onImport = { importLauncher.launch(arrayOf("*/*")) },
                        onOpenStats = onOpenStats,
                        onOpenSettings = onOpenSettings,
                    )
                }
                EmptyLibrary(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onManageFolders = { folderManagerOpen = true },
                )
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 146.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 8.dp,
                    bottom = if (selectionMode) 104.dp else 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LibraryHeader(
                        onImport = { importLauncher.launch(arrayOf("*/*")) },
                        onOpenStats = onOpenStats,
                        onOpenSettings = onOpenSettings,
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SearchField(
                        query = query,
                        onQueryChange = { onFilterChange(filter.copy(query = it)) },
                        onClear = { onFilterChange(filter.copy(query = "")) },
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    FolderFilterRow(
                        folders = folders,
                        books = books,
                        selected = folderFilter,
                        onSelect = { onFilterChange(filter.copy(folderId = it)) },
                        onManage = { folderManagerOpen = true },
                    )
                }
                if (allTags.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        TagFilterRow(
                            tags = allTags,
                            selected = selectedTags,
                            onToggle = { tag ->
                                onFilterChange(
                                    filter.copy(
                                        tags = if (tag in selectedTags) {
                                            selectedTags - tag
                                        } else {
                                            selectedTags + tag
                                        },
                                    ),
                                )
                            },
                            onClear = { onFilterChange(filter.copy(tags = emptySet())) },
                        )
                    }
                }
                if (recent != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ContinueReadingCard(
                            book = recent,
                            coverFile = coverFile(recent),
                            onOpen = { onOpenBook(recent.id) },
                            onLongClick = { menuBook = recent },
                        )
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    if (selectionMode) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { exitSelection() }) {
                                Icon(Icons.Outlined.Close, contentDescription = "選択を終了")
                            }
                            Text(
                                text = "${selectedIds.size}冊を選択",
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp,
                            )
                            Spacer(Modifier.weight(1f))
                            TextButton(
                                onClick = {
                                    selectedIds = if (allVisibleSelected) {
                                        emptySet()
                                    } else {
                                        visibleBooks.map { it.id }.toSet()
                                    }
                                },
                                enabled = visibleBooks.isNotEmpty(),
                            ) {
                                Text(if (allVisibleSelected) "選択解除" else "すべて選択")
                            }
                        }
                    } else {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = sectionTitle,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 19.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${visibleBooks.size}冊",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(4.dp))
                            SortMenuButton(
                                current = filter.sort,
                                onSelect = { onFilterChange(filter.copy(sort = it)) },
                            )
                            TextButton(
                                onClick = {
                                    selectionMode = true
                                    selectedIds = emptySet()
                                },
                                enabled = visibleBooks.isNotEmpty(),
                            ) {
                                Text("選択")
                            }
                        }
                    }
                }
                if (visibleBooks.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        val onlyFolder = folderFilter != null &&
                            query.isBlank() && selectedTags.isEmpty()
                        FilteredEmpty(
                            title = when {
                                !onlyFolder -> "該当する本がありません。"
                                folderFilter == UNFILED_FOLDER_ID -> "未分類の本がありません。"
                                else -> "このフォルダに本がありません。"
                            },
                            body = if (onlyFolder) {
                                "本を移動すると、ここに表示されます。"
                            } else {
                                "検索語やタグを変えると、ここに表示されます。"
                            },
                            onClear = {
                                onFilterChange(
                                    filter.copy(query = "", folderId = null, tags = emptySet()),
                                )
                            },
                        )
                    }
                }
                items(visibleBooks, key = { it.id }) { book ->
                    BookCard(
                        book = book,
                        coverFile = coverFile(book),
                        selectionMode = selectionMode,
                        selected = book.id in selectedIds,
                        onClick = {
                            if (selectionMode) toggleSelection(book.id) else onOpenBook(book.id)
                        },
                        onLongClick = {
                            if (selectionMode) {
                                toggleSelection(book.id)
                            } else {
                                menuBook = book
                            }
                        },
                    )
                }
            }
        }

        if (selectionMode && selectedIds.isNotEmpty()) {
            SelectionActionBar(
                count = selectedIds.size,
                onMove = { batchMoveOpen = true },
                onTags = { batchTagsOpen = true },
                onDelete = { batchDeleteOpen = true },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        if (importState != null) {
            ImportOverlay(importState)
        }
    }

    menuBook?.let { book ->
        BookActionSheet(
            book = book,
            folderName = folders.firstOrNull { it.id == book.folderId }?.name,
            onDismiss = { menuBook = null },
            onSelect = {
                menuBook = null
                selectionMode = true
                selectedIds = setOf(book.id)
            },
            onMove = {
                menuBook = null
                moveTarget = book
            },
            onExport = {
                menuBook = null
                exportTarget = book
                exportLauncher.launch(exportFileName(book))
            },
            onEdit = {
                menuBook = null
                editTarget = book
            },
            onResetProgress = {
                menuBook = null
                resetTarget = book
            },
            onDelete = {
                menuBook = null
                deleteTarget = book
            },
        )
    }

    moveTarget?.let { book ->
        MoveBookDialog(
            book = book,
            folders = folders,
            onDismiss = { moveTarget = null },
            onConfirm = { folderId ->
                onMoveBook(book.id, folderId)
                moveTarget = null
            },
            onCreateAndMove = { name ->
                onCreateFolderAndMove(book.id, name)
                moveTarget = null
            },
        )
    }

    editTarget?.let { target ->
        val live = books.firstOrNull { it.id == target.id } ?: target
        EditBookDialog(
            book = live,
            coverFile = coverFile(live),
            onDismiss = { editTarget = null },
            onSave = { title, author, tags ->
                onUpdateBook(live.id, title, author, tags)
                editTarget = null
            },
            onPickCover = {
                coverTarget = live.id
                coverLauncher.launch("image/*")
            },
            onResetCover = { onResetCover(live.id) },
        )
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "本を削除",
            body = "『${target.title}』と、この本の読書進捗・統計を端末から削除します。",
            confirmLabel = "削除",
            danger = true,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                onDeleteBook(target.id)
                deleteTarget = null
            },
        )
    }

    resetTarget?.let { target ->
        ConfirmDialog(
            title = "読書進捗をリセット",
            body = "『${target.title}』の読書位置を先頭に戻します。",
            confirmLabel = "リセット",
            onDismiss = { resetTarget = null },
            onConfirm = {
                onResetProgress(target.id)
                resetTarget = null
            },
        )
    }

    if (batchMoveOpen) {
        MoveBooksDialog(
            count = selectedIds.size,
            currentFolderId = selectedCommonFolder,
            folders = folders,
            onDismiss = { batchMoveOpen = false },
            onConfirm = { folderId ->
                onMoveBooks(selectedIds, folderId)
                batchMoveOpen = false
                exitSelection()
            },
            onCreateAndMove = { name ->
                onCreateFolderAndMoveBooks(selectedIds, name)
                batchMoveOpen = false
                exitSelection()
            },
        )
    }

    if (batchTagsOpen) {
        EditTagsDialog(
            count = selectedIds.size,
            commonTags = selectedTagsForEditing,
            onDismiss = { batchTagsOpen = false },
            onApply = { add, remove ->
                onUpdateTags(selectedIds, add, remove)
                batchTagsOpen = false
                exitSelection()
            },
        )
    }

    if (batchDeleteOpen) {
        ConfirmDialog(
            title = "本を削除",
            body = "選択した${selectedIds.size}冊と、読書進捗・統計を端末から削除します。",
            confirmLabel = "削除",
            danger = true,
            onDismiss = { batchDeleteOpen = false },
            onConfirm = {
                onDeleteBooks(selectedIds)
                batchDeleteOpen = false
                exitSelection()
            },
        )
    }

    if (folderManagerOpen) {
        FolderManagerDialog(
            folders = folders,
            onDismiss = { folderManagerOpen = false },
            onCreate = onCreateFolder,
            onRename = onRenameFolder,
            onDelete = onDeleteFolder,
        )
    }
}

private fun exportFileName(book: BookSummary): String {
    val extension = book.sourceFile.substringAfterLast('.', "bin")
    val safeTitle = book.title
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
        .replace(Regex("[\\s\\u3000]+"), " ")
        .trim()
        .take(80)
        .ifBlank { "book" }
    return "$safeTitle.$extension"
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        placeholder = { Text("書名・著者・タグを検索", fontSize = 14.sp) },
        leadingIcon = {
            Icon(Icons.Outlined.Search, contentDescription = null)
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = onClear) {
                    Icon(Icons.Outlined.Close, contentDescription = "検索を消す")
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
private fun SortMenuButton(
    current: LibrarySort,
    onSelect: (LibrarySort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Icon(
                Icons.AutoMirrored.Outlined.Sort,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text("並び替え", fontSize = 13.sp)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            LibrarySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    leadingIcon = if (option == current) {
                        {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = "選択中",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun SelectionActionBar(
    count: Int,
    onMove: () -> Unit,
    onTags: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .shadow(8.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${count}冊",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, end = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onMove) {
                Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("移動")
            }
            TextButton(onClick = onTags) {
                Icon(Icons.Outlined.Sell, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("タグ")
            }
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("削除")
            }
        }
    }
}

@Composable
private fun FolderFilterRow(
    folders: List<LibraryFolder>,
    books: List<BookSummary>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onManage: () -> Unit,
) {
    val hasUnfiled = books.any { it.folderId == null }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("すべて") },
            )
        }
        items(folders, key = { it.id }) { folder ->
            FilterChip(
                selected = selected == folder.id,
                onClick = { onSelect(if (selected == folder.id) null else folder.id) },
                label = { Text(folder.name, maxLines = 1) },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
        if (hasUnfiled) {
            item {
                FilterChip(
                    selected = selected == UNFILED_FOLDER_ID,
                    onClick = {
                        onSelect(
                            if (selected == UNFILED_FOLDER_ID) null else UNFILED_FOLDER_ID,
                        )
                    },
                    label = { Text("未分類") },
                )
            }
        }
        item {
            TextButton(onClick = onManage) {
                Text("フォルダ管理")
            }
        }
    }
}

@Composable
private fun TagFilterRow(
    tags: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(tags, key = { it }) { tag ->
            FilterChip(
                selected = tag in selected,
                onClick = { onToggle(tag) },
                label = { Text(tag, maxLines = 1) },
            )
        }
        if (selected.isNotEmpty()) {
            item {
                TextButton(onClick = onClear) {
                    Text("解除")
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    onImport: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Vermilion),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "縦",
                    color = Paper,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 23.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "縦読み",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 29.sp,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = "TATEYOMI  /  VERTICAL READER",
                    fontSize = 8.sp,
                    letterSpacing = 1.4.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(
                onClick = onOpenSettings,
                colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = "設定を開く")
            }
            Spacer(Modifier.width(6.dp))
            FilledTonalIconButton(
                onClick = onOpenStats,
                colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(Icons.Outlined.BarChart, contentDescription = "読書統計を開く")
            }
            Spacer(Modifier.width(6.dp))
            FilledTonalIconButton(
                onClick = onImport,
                colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "本を追加")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueReadingCard(
    book: BookSummary,
    coverFile: File?,
    onOpen: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .shadow(3.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onLongClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(
                book = book,
                coverFile = coverFile,
                modifier = Modifier
                    .width(66.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(7.dp)),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "続きから読む",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = book.title,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.author.isNotBlank()) {
                    Text(
                        text = book.author,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth((book.progress?.overallFraction ?: 0f).coerceIn(0f, 1f))
                                .background(MaterialTheme.colorScheme.secondary),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "${book.percent}%",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCard(
    book: BookSummary,
    coverFile: File?,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(bottom = 2.dp),
        ) {
            BookCover(
                book = book,
                coverFile = coverFile,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .shadow(4.dp, RoundedCornerShape(7.dp))
                    .clip(RoundedCornerShape(7.dp)),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = book.title,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.author.isNotBlank()) {
                Text(
                    text = book.author,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (book.tags.isNotEmpty()) {
                Text(
                    text = book.tags.take(2).joinToString(" ") { "#$it" },
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth((book.progress?.overallFraction ?: 0f).coerceIn(0f, 1f))
                            .background(
                                if (book.percent >= 100) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.secondary,
                            ),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (book.percent >= 100) "読了" else "${book.percent}%",
                    fontSize = 10.sp,
                    color = if (book.percent >= 100) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        if (selectionMode) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp),
                shape = CircleShape,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                },
                contentColor = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                tonalElevation = 2.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (selected) {
                            Icons.Outlined.Check
                        } else {
                            Icons.Outlined.RadioButtonUnchecked
                        },
                        contentDescription = if (selected) "選択中" else "未選択",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        if (selected) {
            Box(
                Modifier
                    .matchParentSize()
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(9.dp),
                    ),
            )
        }
    }
}

@Composable
private fun BookCover(
    book: BookSummary,
    coverFile: File?,
    modifier: Modifier,
) {
    val bitmap = rememberBitmap(coverFile, 700)
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            PlaceholderCover(book.title)
        }
        Box(
            Modifier
                .fillMaxHeight()
                .width(4.dp)
                .background(Color.Black.copy(alpha = 0.12f)),
        )
    }
}

@Composable
private fun PlaceholderCover(title: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFF6EFDF), Color(0xFFE6D9C1)),
                ),
            ),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            title.ifBlank { "無題の本" }
                .chunked(8)
                .take(4)
                .reversed()
                .forEach { column ->
                    Column(Modifier.padding(start = 4.dp)) {
                        column.forEach { ch ->
                            if (ch.isWhitespace()) {
                                Spacer(Modifier.height(15.dp))
                            } else {
                                Text(
                                    text = ch.toString(),
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    lineHeight = 15.sp,
                                    color = Color(0xFF24211C),
                                )
                            }
                        }
                    }
                }
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
                .size(16.dp)
                .background(Vermilion, RoundedCornerShape(3.dp)),
        )
    }
}

@Composable
private fun EmptyLibrary(
    modifier: Modifier,
    onImport: () -> Unit,
    onManageFolders: () -> Unit,
) {
    Column(
        modifier
            .padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.AutoStories,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(26.dp))
        Text(
            text = "まだ本がありません。",
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "ファイルを追加するとここに配置されます。",
            fontSize = 13.sp,
            lineHeight = 21.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(26.dp))
        Button(
            onClick = onImport,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("本を追加", fontSize = 14.sp)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onManageFolders) {
            Text("フォルダ管理")
        }
    }
}

@Composable
private fun FilteredEmpty(
    title: String,
    body: String,
    onClear: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 36.dp, bottom = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onClear) {
            Text("絞り込みを解除")
        }
    }
}

@Composable
private fun ImportOverlay(state: ImportUiState) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.34f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Row(
                Modifier.padding(horizontal = 26.dp, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.secondary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = "読み込み中…",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                    Text(
                        text = "${state.current} / ${state.total}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
