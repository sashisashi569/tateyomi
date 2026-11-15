package app.tateyomi.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import app.tateyomi.data.BuiltInPresets
import app.tateyomi.data.FontChoice
import app.tateyomi.data.ReaderPreset
import app.tateyomi.data.ReaderSettings
import app.tateyomi.data.ReaderTheme
import app.tateyomi.reader.ReaderLayout
import app.tateyomi.ui.theme.ReaderPalette
import app.tateyomi.ui.theme.readerColorScheme
import app.tateyomi.ui.theme.readerPalette
import kotlin.math.roundToInt

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
        MaterialTheme(colorScheme = readerColorScheme(palette)) {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    settings: ReaderSettings,
    presets: List<ReaderPreset>,
    palette: ReaderPalette,
    onApply: (ReaderSettings) -> Unit,
    onSavePreset: (String, ReaderSettings) -> Unit,
    onDeletePreset: (ReaderPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember { mutableStateOf(settings) }
    var showSaveDialog by remember { mutableStateOf(false) }

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
        MaterialTheme(colorScheme = readerColorScheme(palette)) {
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
                            text = "組式設定",
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 22.sp,
                        )
                        Spacer(Modifier.weight(1f))
                    }
                    Text(
                        text = "文字サイズは字数・行数・行間から自動で決まります",
                        fontSize = 11.sp,
                        color = palette.muted,
                    )
                    Text(
                        text = "端末の表示領域に収まるよう自動縮小します",
                        fontSize = 10.sp,
                        color = palette.muted,
                    )
                    Spacer(Modifier.height(16.dp))

                SectionLabel("組方向", palette)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = draft.vertical,
                        onClick = {
                            draft = if (draft.charsPerLine < 30 || draft.linesPerPage < 20) {
                                draft.copy(
                                    vertical = true,
                                    charsPerLine = ReaderSettings.Bunko.charsPerLine,
                                    linesPerPage = ReaderSettings.Bunko.linesPerPage,
                                )
                            } else {
                                draft.copy(vertical = true)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) {
                        Text("縦書き", fontSize = 14.sp)
                    }
                    SegmentedButton(
                        selected = !draft.vertical,
                        onClick = {
                            draft = if (draft.charsPerLine > 30 || draft.linesPerPage < 20) {
                                draft.copy(
                                    vertical = false,
                                    charsPerLine = ReaderSettings.Horizontal.charsPerLine,
                                    linesPerPage = ReaderSettings.Horizontal.linesPerPage,
                                )
                            } else {
                                draft.copy(vertical = false)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) {
                        Text("横書き", fontSize = 14.sp)
                    }
                }
                Text(
                    text = if (draft.vertical) "右から左へ、上から下へ" else "左から右へ、上から下へ",
                    fontSize = 11.sp,
                    color = palette.muted,
                    modifier = Modifier.padding(top = 6.dp),
                )

                Spacer(Modifier.height(18.dp))
                SectionLabel("組式", palette)
                SettingSlider(
                    label = "字数",
                    value = draft.charsPerLine,
                    range = ReaderSettings.MIN_CHARS..ReaderSettings.MAX_CHARS,
                    display = { "${it}字" },
                    palette = palette,
                    onChange = { draft = draft.copy(charsPerLine = it) },
                )
                SettingSlider(
                    label = "行数",
                    value = draft.linesPerPage,
                    range = ReaderSettings.MIN_LINES..ReaderSettings.MAX_LINES,
                    display = { "${it}行" },
                    palette = palette,
                    onChange = { draft = draft.copy(linesPerPage = it) },
                )
                SettingSliderFloat(
                    label = "行間",
                    value = draft.lineSpacing,
                    range = ReaderSettings.MIN_LINE_SPACING..ReaderSettings.MAX_LINE_SPACING,
                    display = { "%.1f字".format(it) },
                    palette = palette,
                    onChange = { draft = draft.copy(lineSpacing = it) },
                )
                SettingSlider(
                    label = "字下げ",
                    value = draft.indent,
                    range = 0..3,
                    display = { "${it}字" },
                    palette = palette,
                    onChange = { draft = draft.copy(indent = it) },
                )
                SettingSlider(
                    label = "余白",
                    value = draft.marginDp,
                    range = ReaderSettings.MIN_MARGIN..ReaderSettings.MAX_MARGIN,
                    display = { "${it}dp" },
                    palette = palette,
                    onChange = { draft = draft.copy(marginDp = it) },
                )

                Spacer(Modifier.height(14.dp))
                SectionLabel("フォント", palette)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(FontChoice.entries) { font ->
                        FilterChip(
                            selected = draft.font == font,
                            onClick = { draft = draft.copy(font = font) },
                            label = {
                                Text(
                                    text = font.label(),
                                    fontFamily = font.previewFamily(),
                                    fontSize = 13.sp,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.accent.copy(alpha = 0.14f),
                                selectedLabelColor = palette.accent,
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                SectionLabel("紙面", palette)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderTheme.entries.forEach { theme ->
                        val themePalette = readerPalette(theme)
                        FilterChip(
                            selected = draft.theme == theme,
                            onClick = { draft = draft.copy(theme = theme) },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(12.dp)
                                            .background(themePalette.background, CircleShape),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(theme.label(), fontSize = 13.sp)
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.accent.copy(alpha = 0.14f),
                                selectedLabelColor = palette.accent,
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                SectionLabel("プリセット", palette)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(BuiltInPresets, key = { it.key }) { preset ->
                        FilterChip(
                            selected = draft == preset.settings,
                            onClick = { draft = preset.settings },
                            label = { Text(preset.name, fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.accent.copy(alpha = 0.14f),
                                selectedLabelColor = palette.accent,
                            ),
                        )
                    }
                }
                if (presets.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(presets, key = { it.id }) { preset ->
                            FilterChip(
                                selected = draft == preset.settings,
                                onClick = { draft = preset.settings },
                                label = { Text(preset.name, fontSize = 13.sp) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "プリセットを削除",
                                        tint = palette.muted,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { onDeletePreset(preset) },
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = palette.accent.copy(alpha = 0.14f),
                                    selectedLabelColor = palette.accent,
                                ),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showSaveDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.text),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("現在の設定を保存", fontSize = 13.sp)
                    }
                    TextButton(
                        onClick = { draft = ReaderSettings.Default },
                        colors = ButtonDefaults.textButtonColors(contentColor = palette.muted),
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("初期設定", fontSize = 13.sp)
                    }
                }
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

    if (showSaveDialog) {
        var name by remember { mutableStateOf("") }
        MaterialTheme(colorScheme = readerColorScheme(palette)) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                title = { Text("プリセットを保存", fontFamily = FontFamily.Serif) },
                text = {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(24) },
                        label = { Text("プリセット名") },
                        placeholder = { Text("例: 通勤用") },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onSavePreset(name, draft)
                            showSaveDialog = false
                        },
                        enabled = name.isNotBlank(),
                    ) {
                        Text("保存")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveDialog = false }) {
                        Text("キャンセル")
                    }
                },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, palette: ReaderPalette) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = palette.muted,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun SettingSlider(
    label: String,
    value: Int,
    range: IntRange,
    display: (Int) -> String,
    palette: ReaderPalette,
    onChange: (Int) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, color = palette.text)
            Spacer(Modifier.weight(1f))
            Text(
                display(local.roundToInt()),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = palette.accent,
            )
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = 0,
        )
    }
}

@Composable
private fun SettingSliderFloat(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: (Float) -> String,
    palette: ReaderPalette,
    onChange: (Float) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, color = palette.text)
            Spacer(Modifier.weight(1f))
            Text(
                display(local),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = palette.accent,
            )
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange((local * 10f).roundToInt() / 10f) },
            valueRange = range,
            steps = 0,
        )
    }
}

private fun FontChoice.label(): String = when (this) {
    FontChoice.MINCHO -> "明朝"
    FontChoice.GOTHIC -> "ゴシック"
    FontChoice.MONOSPACE -> "等幅"
}

private fun FontChoice.previewFamily(): FontFamily = when (this) {
    FontChoice.MINCHO -> FontFamily.Serif
    FontChoice.GOTHIC -> FontFamily.SansSerif
    FontChoice.MONOSPACE -> FontFamily.Monospace
}

private fun ReaderTheme.label(): String = when (this) {
    ReaderTheme.PAPER -> "生成り"
    ReaderTheme.WHITE -> "白"
    ReaderTheme.DARK -> "夜"
}
