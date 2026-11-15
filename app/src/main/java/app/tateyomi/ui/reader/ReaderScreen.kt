package app.tateyomi.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Toc
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tateyomi.data.AppSettings
import app.tateyomi.data.ReaderPreset
import app.tateyomi.data.ReaderSettings
import app.tateyomi.data.effectiveFontId
import app.tateyomi.data.ReadingProgress
import app.tateyomi.reader.ReaderFontSpec
import app.tateyomi.reader.ReaderLayout
import app.tateyomi.reader.ReaderLayoutEngine
import app.tateyomi.reader.resolveFontSpec
import app.tateyomi.reader.computeReaderMetrics
import app.tateyomi.ui.LoadedBook
import app.tateyomi.ui.MainViewModel
import app.tateyomi.ui.ReaderUiState
import app.tateyomi.ui.theme.ReaderPalette
import app.tateyomi.ui.theme.readerPalette
import app.tateyomi.util.formatPercent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun ReaderScreen(
    viewModel: MainViewModel,
    state: ReaderUiState,
    appSettings: AppSettings,
) {
    val palette = readerPalette(appSettings.reader.theme)
    KeepScreenAwake()

    when (state) {
        is ReaderUiState.Loading -> ReaderLoading(palette, onBack = viewModel::closeBook)
        is ReaderUiState.Error -> ReaderError(
            palette = palette,
            message = state.message,
            onRetry = { viewModel.retryOpen(state.bookId) },
            onBack = viewModel::closeBook,
        )
        is ReaderUiState.Ready -> {
            val readerSettings = appSettings.reader
            val fontId = readerSettings.effectiveFontId()
            val fontSpec = remember(fontId, appSettings.fonts) {
                resolveFontSpec(fontId, appSettings.fonts, viewModel::fontFile)
            }
            ReaderPager(
                viewModel = viewModel,
                book = state.book,
                settings = readerSettings,
                fontSpec = fontSpec,
                presets = appSettings.presets,
                palette = palette,
            )
        }
    }
}

@Composable
private fun ReaderPager(
    viewModel: MainViewModel,
    book: LoadedBook,
    settings: ReaderSettings,
    fontSpec: ReaderFontSpec,
    presets: List<ReaderPreset>,
    palette: ReaderPalette,
) {
    val location = viewModel.location.collectAsStateWithLifecycle().value ?: ReadingProgress()
    var layoutState by remember(book.summary.id) { mutableStateOf<ReaderLayout?>(null) }

    LaunchedEffect(book.summary.id, settings) {
        layoutState = withContext(Dispatchers.Default) {
            ReaderLayoutEngine.compute(book.content, settings)
        }
    }

    val layout = layoutState
    val totalPages = layout?.totalPages ?: 1
    val scope = rememberCoroutineScope()
    var currentPage by remember { mutableIntStateOf(0) }
    var pagerStateRef by remember { mutableStateOf<PagerState?>(null) }
    var showToc by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    BackHandler(enabled = showToc) {
        showToc = false
    }

    LaunchedEffect(layout) {
        if (layout != null) {
            currentPage = layout.pageFor(location)
                .coerceIn(0, (layout.totalPages - 1).coerceAtLeast(0))
        }
    }

    val currentChapter = layout?.chapterAt(currentPage)
    val tocWithPages: List<Pair<app.tateyomi.data.TocEntry, Int>> =
        remember(layout, book.content.toc) {
            val currentLayout = layout
            if (currentLayout == null) {
                emptyList()
            } else {
                book.content.toc
                    .map { entry -> entry to currentLayout.pageForToc(entry) }
                    .sortedBy { it.second }
            }
        }
    val currentSectionTitle = tocWithPages
        .lastOrNull { it.second <= currentPage }
        ?.first
        ?.title
        ?.takeIf { it.isNotBlank() }
    val progress = layout?.progressFor(currentPage)

    fun nextPage() {
        val pagerState = pagerStateRef ?: return
        if (pagerState.currentPage < totalPages - 1) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
        }
    }

    fun previousPage() {
        val pagerState = pagerStateRef ?: return
        if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        ReaderTopBar(
            title = book.summary.title,
            chapter = currentSectionTitle ?: currentChapter?.title.orEmpty(),
            percent = progress?.let { formatPercent(it.overallFraction) } ?: "0%",
            palette = palette,
            onBack = viewModel::closeBook,
        )

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(settings.vertical) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val up = waitForUpOrCancellation()
                        if (up != null) {
                            val distance = (up.position - down.position).getDistance()
                            if (distance <= viewConfiguration.touchSlop) {
                                up.consume()
                                val x = down.position.x
                                val width = size.width.toFloat()
                                when {
                                    x < width * 0.28f -> if (settings.vertical) nextPage() else previousPage()
                                    x > width * 0.72f -> if (settings.vertical) previousPage() else nextPage()
                                    else -> Unit
                                }
                            }
                        }
                    }
                },
        ) {
            val currentLayout = layout
            if (currentLayout == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = palette.accent, strokeWidth = 2.dp)
                }
            } else {
                key(currentLayout) {
                    val initialPage = currentLayout.pageFor(location)
                        .coerceIn(0, (currentLayout.totalPages - 1).coerceAtLeast(0))
                    val pagerState = rememberPagerState(initialPage = initialPage) {
                        currentLayout.totalPages
                    }
                    LaunchedEffect(pagerState) {
                        pagerStateRef = pagerState
                        snapshotFlow { pagerState.settledPage }
                            .distinctUntilChanged()
                            .collect { page ->
                                currentPage = page
                                viewModel.onPageChanged(
                                    page = page,
                                    totalPages = currentLayout.totalPages,
                                    progress = currentLayout.progressFor(page),
                                )
                            }
                    }
                    DisposableEffect(pagerState) {
                        onDispose {
                            if (pagerStateRef === pagerState) pagerStateRef = null
                        }
                    }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = settings.vertical,
                        beyondViewportPageCount = 1,
                    ) { page ->
                        ReaderPageView(
                            book = book,
                            layout = currentLayout,
                            page = page,
                            fontSpec = fontSpec,
                            palette = palette,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        ReaderBottomBar(
            page = currentPage.coerceIn(0, totalPages - 1),
            totalPages = totalPages,
            palette = palette,
            onSeek = { target ->
                pagerStateRef?.let { pagerState ->
                    scope.launch {
                        pagerState.scrollToPage(target.coerceIn(0, totalPages - 1))
                    }
                }
            },
            onOpenToc = {
                if (layout != null) {
                    showSettings = false
                    showToc = true
                }
            },
            onOpenSettings = {
                showToc = false
                showSettings = true
            },
        )
    }

    if (showToc) {
        val currentLayout = layout
        if (currentLayout != null) {
            TocSheet(
                layout = currentLayout,
                toc = book.content.toc,
                currentPage = currentPage,
                palette = palette,
                onDismiss = { showToc = false },
                onSelectEntry = { entry ->
                    showToc = false
                    pagerStateRef?.let { pagerState ->
                        scope.launch { pagerState.scrollToPage(currentLayout.pageForToc(entry)) }
                    }
                },
            )
        }
    }

    if (showSettings) {
        ReaderSettingsSheet(
            settings = settings,
            presets = presets,
            palette = palette,
            onApply = viewModel::updateReaderSettings,
            onSavePreset = viewModel::savePreset,
            onDeletePreset = viewModel::deletePreset,
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    chapter: String,
    percent: String,
    palette: ReaderPalette,
    onBack: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .fillMaxWidth()
            .shadow(6.dp, MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        color = palette.surface,
        contentColor = palette.text,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "戻る",
                    tint = palette.text,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = palette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = chapter,
                    fontSize = 11.sp,
                    color = palette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = percent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = palette.accent,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

@Composable
private fun ReaderBottomBar(
    page: Int,
    totalPages: Int,
    palette: ReaderPalette,
    onSeek: (Int) -> Unit,
    onOpenToc: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var sliderValue by remember(page) { mutableFloatStateOf(page.toFloat()) }
    Surface(
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .fillMaxWidth()
            .shadow(8.dp, MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        color = palette.surface,
        contentColor = palette.text,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${page + 1}",
                    fontSize = 12.sp,
                    color = palette.muted,
                )
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { onSeek(sliderValue.roundToInt()) },
                    valueRange = 0f..(totalPages - 1).coerceAtLeast(1).toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = palette.accent,
                        activeTrackColor = palette.accent,
                        inactiveTrackColor = palette.rule,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                Text(
                    text = "$totalPages",
                    fontSize = 12.sp,
                    color = palette.muted,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TextButton(onClick = onOpenToc, colors = ButtonDefaults.textButtonColors(contentColor = palette.text)) {
                    Icon(Icons.AutoMirrored.Outlined.Toc, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("目次", fontSize = 13.sp)
                }
                TextButton(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.textButtonColors(contentColor = palette.text),
                ) {
                    Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("組式", fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                if (page >= totalPages - 1) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = palette.accent.copy(alpha = 0.12f),
                        contentColor = palette.accent,
                    ) {
                        Text(
                            text = "読了",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderLoading(palette: ReaderPalette, onBack: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "戻る",
                tint = palette.text,
            )
        }
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(color = palette.accent, strokeWidth = 2.dp)
            Spacer(Modifier.height(18.dp))
            Text("読み込み中…", color = palette.muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ReaderError(
    palette: ReaderPalette,
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "戻る",
                tint = palette.text,
            )
        }
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = message,
                color = palette.text,
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = palette.muted)) {
                    Text("戻る")
                }
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accent,
                        contentColor = Color(0xFFFFF8F2),
                    ),
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("再試行")
                }
            }
        }
    }
}

@Composable
private fun KeepScreenAwake() {
    val view = LocalView.current
    val activity = view.context.findActivity()
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (window != null && controller != null) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (window != null) {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                controller?.show(WindowInsetsCompat.Type.systemBars())
                val dark = view.context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
                controller?.isAppearanceLightStatusBars = !dark
                controller?.isAppearanceLightNavigationBars = !dark
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
