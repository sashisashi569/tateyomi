package app.tateyomi.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tateyomi.data.BuiltInFonts
import app.tateyomi.data.BuiltInPresets
import app.tateyomi.data.ManagedFont
import app.tateyomi.data.ReaderPreset
import app.tateyomi.data.ReaderSettings
import app.tateyomi.data.ReaderTheme
import app.tateyomi.data.SpreadMode
import app.tateyomi.data.effectiveFontId
import app.tateyomi.data.fontDisplayName
import app.tateyomi.reader.resolveFontSpec
import app.tateyomi.ui.theme.ReaderPalette
import app.tateyomi.ui.theme.readerPalette
import java.io.File
import kotlin.math.roundToInt

/**
 * The complete display settings editor. It is shared by the reader's bottom
 * sheet and the Settings screen so both places expose exactly the same
 * controls: font, writing direction, grid, theme and presets.
 *
 * The caller owns [settings] and decides when to persist it. The reader sheet
 * keeps a draft and applies it on close, while the Settings screen applies
 * every change immediately.
 */
@Composable
fun ReaderSettingsContent(
    settings: ReaderSettings,
    fonts: List<ManagedFont>,
    fontFileFor: (String) -> File?,
    presets: List<ReaderPreset>,
    palette: ReaderPalette,
    onChange: (ReaderSettings) -> Unit,
    onSavePreset: (String, ReaderSettings) -> Unit,
    onDeletePreset: (ReaderPreset) -> Unit,
    onExportPreset: (ReaderPreset, Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showFontDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var pendingOverwrite by remember { mutableStateOf<String?>(null) }
    var pendingExportPreset by remember { mutableStateOf<ReaderPreset?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val preset = pendingExportPreset
        pendingExportPreset = null
        if (uri != null && preset != null) onExportPreset(preset, uri)
    }
    val fontId = settings.effectiveFontId()
    val allFonts = remember(fonts) { BuiltInFonts.all + fonts }

    Column(modifier.fillMaxWidth()) {
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

        SectionLabel("フォント", palette)
        FontSelectionRow(
            fontId = fontId,
            fonts = allFonts,
            fontFileFor = fontFileFor,
            palette = palette,
            onClick = { showFontDialog = true },
        )
        Text(
            text = "フォントの追加・削除は「設定 > フォントマネージャー」から行えます。",
            fontSize = 10.sp,
            color = palette.muted,
            modifier = Modifier.padding(top = 6.dp),
        )

        Spacer(Modifier.height(18.dp))
        SectionLabel("組方向", palette)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = settings.vertical,
                onClick = {
                    onChange(
                        if (settings.charsPerLine < 30 || settings.linesPerPage < 20) {
                            settings.copy(
                                vertical = true,
                                charsPerLine = ReaderSettings.Bunko.charsPerLine,
                                linesPerPage = ReaderSettings.Bunko.linesPerPage,
                            )
                        } else {
                            settings.copy(vertical = true)
                        },
                    )
                },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) {
                Text("縦書き", fontSize = 14.sp)
            }
            SegmentedButton(
                selected = !settings.vertical,
                onClick = {
                    onChange(
                        if (settings.charsPerLine > 30 || settings.linesPerPage < 20) {
                            settings.copy(
                                vertical = false,
                                charsPerLine = ReaderSettings.Horizontal.charsPerLine,
                                linesPerPage = ReaderSettings.Horizontal.linesPerPage,
                            )
                        } else {
                            settings.copy(vertical = false)
                        },
                    )
                },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) {
                Text("横書き", fontSize = 14.sp)
            }
        }
        Text(
            text = if (settings.vertical) "右から左へ、上から下へ" else "左から右へ、上から下へ",
            fontSize = 11.sp,
            color = palette.muted,
            modifier = Modifier.padding(top = 6.dp),
        )

        Spacer(Modifier.height(18.dp))
        SectionLabel("見開き", palette)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SpreadMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = settings.spread == mode,
                    onClick = { onChange(settings.copy(spread = mode)) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = SpreadMode.entries.size,
                    ),
                ) {
                    Text(mode.label(), fontSize = 13.sp)
                }
            }
        }
        Text(
            text = when (settings.spread) {
                SpreadMode.AUTO -> "横向きのとき見開き、縦向きは単ページ"
                SpreadMode.SINGLE -> "常に1ページずつ表示"
                SpreadMode.SPREAD -> "常に2ページ見開きで表示"
            },
            fontSize = 11.sp,
            color = palette.muted,
            modifier = Modifier.padding(top = 6.dp),
        )
        SettingSlider(
            label = "ページ間余白",
            value = settings.spreadGapDp,
            range = ReaderSettings.MIN_SPREAD_GAP..ReaderSettings.MAX_SPREAD_GAP,
            display = { "${it}dp" },
            palette = palette,
            onChange = { onChange(settings.copy(spreadGapDp = it)) },
        )
        Text(
            text = "見開き表示のとき、左右ページの間に空ける余白です。負の値にするとページ同士を重ねます。",
            fontSize = 10.sp,
            color = palette.muted,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("表紙ページをスキップ", fontSize = 14.sp, color = palette.text)
                Text(
                    text = "先頭の表紙画像を読み飛ばします。",
                    fontSize = 10.sp,
                    color = palette.muted,
                )
            }
            Switch(
                checked = settings.skipCover,
                onCheckedChange = { onChange(settings.copy(skipCover = it)) },
            )
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("組式", palette)
        SettingSlider(
            label = "字数",
            value = settings.charsPerLine,
            range = ReaderSettings.MIN_CHARS..ReaderSettings.MAX_CHARS,
            display = { "${it}字" },
            palette = palette,
            onChange = { onChange(settings.copy(charsPerLine = it)) },
        )
        SettingSlider(
            label = "行数",
            value = settings.linesPerPage,
            range = ReaderSettings.MIN_LINES..ReaderSettings.MAX_LINES,
            display = { "${it}行" },
            palette = palette,
            onChange = { onChange(settings.copy(linesPerPage = it)) },
        )
        SettingSliderFloat(
            label = "行間",
            value = settings.lineSpacing,
            range = ReaderSettings.MIN_LINE_SPACING..ReaderSettings.MAX_LINE_SPACING,
            display = { "%.1f字".format(it) },
            palette = palette,
            onChange = { onChange(settings.copy(lineSpacing = it)) },
        )
        SettingSlider(
            label = "字下げ",
            value = settings.indent,
            range = 0..3,
            display = { "${it}字" },
            palette = palette,
            onChange = { onChange(settings.copy(indent = it)) },
        )
        SettingSlider(
            label = "余白",
            value = settings.marginDp,
            range = ReaderSettings.MIN_MARGIN..ReaderSettings.MAX_MARGIN,
            display = { "${it}dp" },
            palette = palette,
            onChange = { onChange(settings.copy(marginDp = it)) },
        )
        Text(
            text = "余白は文字サイズに比例して描画されます。",
            fontSize = 10.sp,
            color = palette.muted,
        )
        SettingSliderFloat(
            label = "ルビサイズ",
            value = settings.rubyScale,
            range = ReaderSettings.MIN_RUBY_SCALE..ReaderSettings.MAX_RUBY_SCALE,
            display = { "%.2f字".format(it) },
            palette = palette,
            onChange = { onChange(settings.copy(rubyScale = it)) },
        )
        Text(
            text = "ルビ2文字で本文1文字くらいが目安です。",
            fontSize = 10.sp,
            color = palette.muted,
        )

        Spacer(Modifier.height(14.dp))
        SectionLabel("紙面", palette)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReaderTheme.entries.forEach { theme ->
                val themePalette = readerPalette(theme)
                FilterChip(
                    selected = settings.theme == theme,
                    onClick = { onChange(settings.copy(theme = theme)) },
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
                    selected = settings == preset.settings,
                    onClick = { onChange(preset.settings) },
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
                        selected = settings == preset.settings,
                        onClick = { onChange(preset.settings) },
                        label = { Text(preset.name, fontSize = 13.sp) },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.Share,
                                    contentDescription = "プリセットを書き出す",
                                    tint = palette.muted,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable {
                                            pendingExportPreset = preset
                                            exportLauncher.launch(presetExportFileName(preset))
                                        },
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "プリセットを削除",
                                    tint = palette.muted,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { onDeletePreset(preset) },
                                )
                            }
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
                onClick = { onChange(ReaderSettings.Default) },
                colors = ButtonDefaults.textButtonColors(contentColor = palette.muted),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("初期設定", fontSize = 13.sp)
            }
        }
    }

    if (showFontDialog) {
        FontPickerDialog(
            selectedId = fontId,
            fonts = allFonts,
            fontFileFor = fontFileFor,
            palette = palette,
            onSelect = { font ->
                val builtInChoice = BuiltInFonts.choiceFor(font.id)
                onChange(
                    settings.copy(
                        fontId = font.id,
                        font = builtInChoice ?: settings.font,
                    ),
                )
                showFontDialog = false
            },
            onDismiss = { showFontDialog = false },
        )
    }

    if (showSaveDialog) {
        var name by remember { mutableStateOf("") }
        val trimmedName = name.trim()
        val builtInNameTaken = BuiltInPresets.any { it.name == trimmedName }
        val duplicateName = presets.any { it.name == trimmedName }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = palette.surface,
            titleContentColor = palette.text,
            textContentColor = palette.text,
            title = { Text("プリセットを保存", fontFamily = FontFamily.Serif) },
            text = {
                androidx.compose.foundation.layout.Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(24) },
                        label = { Text("プリセット名") },
                        placeholder = { Text("例: 通勤用") },
                        singleLine = true,
                    )
                    when {
                        builtInNameTaken -> Text(
                            text = "組み込みプリセットと同じ名前は使用できません。",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        duplicateName -> Text(
                            text = "同じ名前のプリセットがあります。保存時に上書きを確認します。",
                            fontSize = 10.sp,
                            color = palette.muted,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when {
                            trimmedName.isEmpty() || builtInNameTaken -> Unit
                            duplicateName -> pendingOverwrite = trimmedName
                            else -> {
                                onSavePreset(trimmedName, settings.normalized())
                                showSaveDialog = false
                            }
                        }
                    },
                    enabled = trimmedName.isNotEmpty() && !builtInNameTaken,
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

    pendingOverwrite?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingOverwrite = null },
            containerColor = palette.surface,
            titleContentColor = palette.text,
            textContentColor = palette.text,
            title = { Text("プリセットを上書き", fontFamily = FontFamily.Serif) },
            text = {
                Text(
                    "「$name」は既に保存されています。上書きしますか？",
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSavePreset(name, settings.normalized())
                        pendingOverwrite = null
                        showSaveDialog = false
                    },
                ) {
                    Text("上書き")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingOverwrite = null }) {
                    Text("キャンセル")
                }
            },
        )
    }
}

@Composable
private fun FontSelectionRow(
    fontId: String,
    fonts: List<ManagedFont>,
    fontFileFor: (String) -> File?,
    palette: ReaderPalette,
    onClick: () -> Unit,
) {
    val selected = BuiltInFonts.find(fontId) ?: fonts.firstOrNull { it.id == fontId }
    val spec = remember(fontId, fonts) {
        resolveFontSpec(fontId, fonts, fontFileFor)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.rule),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = fontDisplayName(fontId, fonts),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(7.dp))
                    Badge(if (selected?.builtIn != false) "端末" else "追加", palette)
                }
                FontSample(
                    font = spec,
                    color = palette.muted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .padding(top = 2.dp),
                    fontSize = 16.sp,
                )
            }
            Text(
                text = "変更",
                fontSize = 12.sp,
                color = palette.accent,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun FontPickerDialog(
    selectedId: String,
    fonts: List<ManagedFont>,
    fontFileFor: (String) -> File?,
    palette: ReaderPalette,
    onSelect: (ManagedFont) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        titleContentColor = palette.text,
        textContentColor = palette.text,
        title = { Text("フォント", fontFamily = FontFamily.Serif) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(fonts, key = { it.id }) { font ->
                    val selected = font.id == selectedId
                    val spec = remember(font.id, fonts) {
                        resolveFontSpec(font.id, fonts, fontFileFor)
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(font) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) {
                            palette.accent.copy(alpha = 0.08f)
                        } else {
                            palette.surface
                        },
                        border = BorderStroke(
                            1.dp,
                            if (selected) palette.accent.copy(alpha = 0.45f) else palette.rule,
                        ),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = font.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = palette.text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Badge(if (font.builtIn) "端末" else "追加", palette)
                                }
                                FontSample(
                                    font = spec,
                                    color = palette.muted,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(26.dp),
                                    fontSize = 15.sp,
                                )
                            }
                            if (selected) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = "選択中",
                                    tint = palette.accent,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        },
    )
}

@Composable
private fun Badge(text: String, palette: ReaderPalette) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = palette.rule.copy(alpha = 0.55f),
    ) {
        Text(
            text = text,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            color = palette.muted,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
        )
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

private fun ReaderTheme.label(): String = when (this) {
    ReaderTheme.PAPER -> "生成り"
    ReaderTheme.WHITE -> "白"
    ReaderTheme.DARK -> "夜"
}

private fun SpreadMode.label(): String = when (this) {
    SpreadMode.AUTO -> "自動"
    SpreadMode.SINGLE -> "単ページ"
    SpreadMode.SPREAD -> "見開き"
}

private fun presetExportFileName(preset: ReaderPreset): String {
    val safe = preset.name
        .replace(Regex("""[\\/:*?"<>|\s]+"""), "_")
        .trim('_')
        .take(40)
        .ifBlank { "preset" }
    return "tateyomi-preset-$safe.json"
}
