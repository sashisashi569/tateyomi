package app.tateyomi.ui.settings

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import app.tateyomi.data.AppSettings
import app.tateyomi.data.BuiltInFonts
import app.tateyomi.data.ManagedFont
import app.tateyomi.data.ReaderPreset
import app.tateyomi.data.ReaderSettings
import app.tateyomi.data.effectiveFontId
import app.tateyomi.reader.resolveFontSpec
import app.tateyomi.ui.components.FontSample
import app.tateyomi.ui.components.ReaderSettingsContent
import app.tateyomi.ui.theme.ReaderPalette
import java.io.File
import java.util.Locale

private const val PAGE_HOME = 0
private const val PAGE_DISPLAY = 1
private const val PAGE_FONTS = 2
private const val PAGE_ABOUT = 3

@Composable
fun SettingsScreen(
    settings: AppSettings,
    fontBusy: Boolean,
    fontFileFor: (String) -> File?,
    onBack: () -> Unit,
    onReaderSettingsChange: (app.tateyomi.data.ReaderSettings) -> Unit,
    onSavePreset: (String, app.tateyomi.data.ReaderSettings) -> Unit,
    onDeletePreset: (app.tateyomi.data.ReaderPreset) -> Unit,
    onAddFont: (Uri) -> Unit,
    onDeleteFont: (String) -> Unit,
) {
    var page by rememberSaveable { mutableIntStateOf(PAGE_HOME) }
    val title = when (page) {
        PAGE_DISPLAY -> "表示設定"
        PAGE_FONTS -> "フォントマネージャー"
        PAGE_ABOUT -> "アプリ情報"
        else -> "設定"
    }

    BackHandler(enabled = page != PAGE_HOME) {
        page = PAGE_HOME
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        SettingsTopBar(
            title = title,
            onBack = {
                if (page == PAGE_HOME) onBack() else page = PAGE_HOME
            },
        )
        when (page) {
            PAGE_DISPLAY -> DisplaySettingsPage(
                settings = settings,
                fontFileFor = fontFileFor,
                onReaderSettingsChange = onReaderSettingsChange,
                onSavePreset = onSavePreset,
                onDeletePreset = onDeletePreset,
            )
            PAGE_FONTS -> FontManagerPage(
                settings = settings,
                fontBusy = fontBusy,
                fontFileFor = fontFileFor,
                onAddFont = onAddFont,
                onDeleteFont = onDeleteFont,
            )
            PAGE_ABOUT -> AppInfoPage()
            else -> SettingsHome(
                onOpenDisplay = { page = PAGE_DISPLAY },
                onOpenFonts = { page = PAGE_FONTS },
                onOpenAbout = { page = PAGE_ABOUT },
            )
        }
    }
}

@Composable
private fun SettingsTopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "戻る")
        }
        Text(
            text = title,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun SettingsHome(
    onOpenDisplay: () -> Unit,
    onOpenFonts: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(
            text = "読書環境",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "表示・フォント・アプリの情報",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 22.dp),
        )

        SettingsMenuRow(
            icon = { Icon(Icons.Outlined.Palette, contentDescription = null) },
            title = "表示設定",
            subtitle = "組式・フォント・紙面テーマ",
            onClick = onOpenDisplay,
        )
        Spacer(Modifier.height(12.dp))
        SettingsMenuRow(
            icon = { Icon(Icons.Outlined.FontDownload, contentDescription = null) },
            title = "フォントマネージャー",
            subtitle = "フォントの追加・削除",
            onClick = onOpenFonts,
        )
        Spacer(Modifier.height(12.dp))
        SettingsMenuRow(
            icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
            title = "アプリ情報",
            subtitle = "バージョン・ライセンス",
            onClick = onOpenAbout,
        )
        Spacer(Modifier.height(28.dp))
        Text(
            text = "設定は端末内に保存されます。",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun SettingsMenuRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides
                        MaterialTheme.colorScheme.primary,
                ) {
                    icon()
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DisplaySettingsPage(
    settings: AppSettings,
    fontFileFor: (String) -> File?,
    onReaderSettingsChange: (ReaderSettings) -> Unit,
    onSavePreset: (String, ReaderSettings) -> Unit,
    onDeletePreset: (ReaderPreset) -> Unit,
) {
    val palette = materialReaderPalette()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(
            text = "読書画面に適用されます。リーダーの「表示」シートと同じ設定です。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 14.dp),
        )
        ReaderSettingsContent(
            settings = settings.reader,
            fonts = settings.fonts,
            fontFileFor = fontFileFor,
            presets = settings.presets,
            palette = palette,
            onChange = onReaderSettingsChange,
            onSavePreset = onSavePreset,
            onDeletePreset = onDeletePreset,
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun materialReaderPalette(): ReaderPalette {
    val scheme = MaterialTheme.colorScheme
    return ReaderPalette(
        background = scheme.background,
        surface = scheme.surface,
        text = scheme.onSurface,
        muted = scheme.onSurfaceVariant,
        accent = scheme.primary,
        rule = scheme.outlineVariant,
        isDark = scheme.background.luminance() < 0.5f,
    )
}

@Composable
private fun FontManagerPage(
    settings: AppSettings,
    fontBusy: Boolean,
    fontFileFor: (String) -> File?,
    onAddFont: (Uri) -> Unit,
    onDeleteFont: (String) -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onAddFont(uri)
    }
    var pendingDelete by remember { mutableStateOf<ManagedFont?>(null) }
    val fonts = BuiltInFonts.all + settings.fonts
    val activeId = settings.reader.effectiveFontId()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "フォント一覧",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    FilledTonalButton(
                        onClick = { launcher.launch(arrayOf("*/*")) },
                        enabled = !fontBusy,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        if (fontBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("追加中…", fontSize = 12.sp)
                        } else {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("追加", fontSize = 12.sp)
                        }
                    }
                }
                Text(
                    text = "TTF / OTF / TTC を追加できます。選択は「表示設定」で行います。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }
        }

        items(fonts, key = { it.id }) { font ->
            val spec = remember(font.id, settings.fonts) {
                resolveFontSpec(font.id, settings.fonts, fontFileFor)
            }
            val fileSize = if (font.builtIn) null else fontFileFor(font.id)?.length()
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = font.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(7.dp))
                            Badge(if (font.builtIn) "標準" else "追加")
                            if (font.id == activeId) {
                                Spacer(Modifier.width(6.dp))
                                Badge("使用中", accent = true)
                            }
                        }
                        FontSample(
                            font = spec,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .padding(top = 2.dp),
                            fontSize = 16.sp,
                        )
                        if (fileSize != null) {
                            Text(
                                text = formatFileSize(fileSize),
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (!font.builtIn) {
                        IconButton(onClick = { pendingDelete = font }) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "フォントを削除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
        }

        if (settings.fonts.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = "追加したフォントはありません。右上の「追加」からTTF / OTF / TTCを読み込めます。",
                        fontSize = 11.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    pendingDelete?.let { font ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("フォントを削除", fontFamily = FontFamily.Serif) },
            text = {
                Text(
                    "「${font.name}」を削除します。" +
                        if (font.id == activeId) "\n使用中のフォントのため、明朝体に戻ります。" else "",
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteFont(font.id)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("キャンセル")
                }
            },
        )
    }
}

@Composable
private fun AppInfoPage() {
    val context = LocalContext.current
    val packageInfo = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
    }
    val versionName = packageInfo?.versionName ?: "?"
    @Suppress("DEPRECATION")
    val versionCode = packageInfo?.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            it.longVersionCode
        } else {
            it.versionCode.toLong()
        }
    } ?: 0L

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 18.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            Row(
                Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .background(
                            MaterialTheme.colorScheme.secondary,
                            RoundedCornerShape(10.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "縦",
                        color = MaterialTheme.colorScheme.onSecondary,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = "縦読み",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 24.sp,
                    )
                    Text(
                        text = "TATEYOMI",
                        fontSize = 9.sp,
                        letterSpacing = 1.4.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "バージョン $versionName ($versionCode)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        InfoSection(title = "アプリについて") {
            InfoRow("パッケージ", context.packageName)
            InfoRow("対応形式", "EPUB 2 / EPUB 3（リフロー）")
            InfoRow("組方向", "縦書き / 横書き")
            InfoRow("フォント", "端末フォント + 追加フォント")
            InfoRow("保存先", "アプリ専用領域")
        }

        InfoSection(title = "プライバシー") {
            Text(
                text = "本棚・本文・読書進捗・設定・統計は端末内にのみ保存されます。" +
                    "ネットワーク通信は行いません。",
                fontSize = 12.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        InfoSection(title = "オープンソース") {
            Text(
                text = "Kotlin / Jetpack Compose / kotlinx.serialization / Jsoup",
                fontSize = 12.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "各ライブラリはそれぞれのライセンスに従います。",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        InfoSection(title = "テスト用データ") {
            Text(
                text = "アプリにEPUBは同梱されていません。UI検証用のEPUBは" +
                    "androidTest用アセットにのみ含まれ、リリースAPKには含まれません。",
                fontSize = 11.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun InfoSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(bottom = 14.dp)) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(92.dp),
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun Badge(text: String, accent: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (accent) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Text(
            text = text,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            color = if (accent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.JAPAN, "%.0f KB", kb)
    return String.format(Locale.JAPAN, "%.1f MB", kb / 1024.0)
}
