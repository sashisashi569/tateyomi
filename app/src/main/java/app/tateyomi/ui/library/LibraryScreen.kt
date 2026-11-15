package app.tateyomi.ui.library

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import app.tateyomi.ui.ImportUiState
import app.tateyomi.ui.components.rememberBitmap
import app.tateyomi.ui.theme.Paper
import app.tateyomi.ui.theme.Vermilion
import java.io.File

@Composable
fun LibraryScreen(
    books: List<BookSummary>,
    ready: Boolean,
    importState: ImportUiState?,
    coverFile: (BookSummary) -> File?,
    onImport: (List<android.net.Uri>) -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    onDeleteBook: (String) -> Unit,
) {
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> if (uris.isNotEmpty()) onImport(uris) }

    var deleteTarget by remember { mutableStateOf<BookSummary?>(null) }
    val recent = remember(books) {
        books.filter {
            it.lastOpenedAt != null && (it.progress?.overallFraction ?: 0f) < 0.995f
        }.maxByOrNull { it.lastOpenedAt ?: 0L }
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
                )
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 146.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
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
                if (recent != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ContinueReadingCard(
                            book = recent,
                            coverFile = coverFile(recent),
                            onOpen = { onOpenBook(recent.id) },
                        )
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "本棚",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 19.sp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${books.size}冊",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "長押しで削除",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        )
                    }
                }
                items(books, key = { it.id }) { book ->
                    BookCard(
                        book = book,
                        coverFile = coverFile(book),
                        onClick = { onOpenBook(book.id) },
                        onLongClick = { deleteTarget = book },
                    )
                }
            }
        }

        if (importState != null) {
            ImportOverlay(importState)
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("本を削除", fontFamily = FontFamily.SansSerif) },
            text = {
                Text(
                    "『${target.title}』と、この本の読書進捗・統計を端末から削除します。",
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteBook(target.id)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("キャンセル")
                }
            },
        )
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
                    text = "TATEYOMI  /  VERTICAL EPUB READER",
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
                Icon(Icons.Outlined.Add, contentDescription = "EPUBを追加")
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
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .shadow(3.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onOpen),
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
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
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
private fun EmptyLibrary(modifier: Modifier, onImport: () -> Unit) {
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
            text = "EPUBファイルを追加するとここに配置されます。",
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
            Text("EPUBを追加", fontSize = 14.sp)
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
