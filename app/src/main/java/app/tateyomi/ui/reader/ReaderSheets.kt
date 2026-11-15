package app.tateyomi.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tateyomi.data.ManagedFont
import app.tateyomi.data.ReaderPreset
import app.tateyomi.data.ReaderSettings
import app.tateyomi.reader.ReaderLayout
import app.tateyomi.ui.components.ReaderSettingsContent
import app.tateyomi.ui.theme.ReaderPalette
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TocSheet(
    layout: ReaderLayout,
    toc: List<app.tateyomi.data.TocEntry>,
    currentPage: Int,
    palette: ReaderPalette,
    onDismiss: () -> Unit,
    onSelectEntry: (app.tateyomi.data.TocEntry) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val entries = remember(layout, toc) {
        if (toc.isEmpty()) {
            layout.chapters.map { app.tateyomi.data.TocEntry(it.title, it.index, 0) }
        } else {
            toc.filter { it.chapterIndex in layout.chapters.indices }
        }
    }
    val activeIndex = remember(entries, currentPage) {
        entries.indexOfLast { layout.pageForToc(it) <= currentPage }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.surface,
        contentColor = palette.text,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = palette.rule) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "目次",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${entries.size}項目",
                    fontSize = 12.sp,
                    color = palette.muted,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "閉じる", tint = palette.muted)
                }
            }
            HorizontalDivider(color = palette.rule)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
            ) {
                itemsIndexed(
                    entries,
                    key = { _, entry -> "${entry.chapterIndex}:${entry.blockIndex}:${entry.title}" },
                ) { index, entry ->
                    val selected = index == activeIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (selected) palette.accent.copy(alpha = 0.08f) else Color.Transparent)
                            .clickable { onSelectEntry(entry) }
                            .padding(
                                start = 14.dp + (entry.level.coerceAtMost(3) * 16).dp,
                                end = 16.dp,
                                top = 12.dp,
                                bottom = 12.dp,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(26.dp)
                                .background(
                                    if (selected) palette.accent else Color.Transparent,
                                    CircleShape,
                                ),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = (index + 1).toString().padStart(2, '0'),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (selected) palette.accent else palette.muted,
                            modifier = Modifier.width(28.dp),
                        )
                        Text(
                            text = entry.title.ifBlank { "第${entry.chapterIndex + 1}章" },
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) palette.accent else palette.text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Text(
                                text = "現在地",
                                fontSize = 10.sp,
                                color = palette.accent,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    settings: ReaderSettings,
    fonts: List<ManagedFont>,
    fontFileFor: (String) -> File?,
    presets: List<ReaderPreset>,
    palette: ReaderPalette,
    onApply: (ReaderSettings) -> Unit,
    onSavePreset: (String, ReaderSettings) -> Unit,
    onDeletePreset: (ReaderPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember { mutableStateOf(settings) }

    fun close() {
        onApply(draft.normalized())
        onDismiss()
    }

    BackHandler {
        close()
    }

    ModalBottomSheet(
        onDismissRequest = { close() },
        sheetState = sheetState,
        containerColor = palette.surface,
        contentColor = palette.text,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = palette.rule) },
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(end = 44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "表示設定",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 22.sp,
                    )
                    Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                ReaderSettingsContent(
                    settings = draft,
                    fonts = fonts,
                    fontFileFor = fontFileFor,
                    presets = presets,
                    palette = palette,
                    onChange = { draft = it },
                    onSavePreset = onSavePreset,
                    onDeletePreset = onDeletePreset,
                )
                Spacer(Modifier.height(24.dp))
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                shape = CircleShape,
                color = palette.surface,
                shadowElevation = 4.dp,
            ) {
                IconButton(onClick = { close() }) {
                    Icon(Icons.Outlined.Close, contentDescription = "閉じる", tint = palette.muted)
                }
            }
        }
    }
}
