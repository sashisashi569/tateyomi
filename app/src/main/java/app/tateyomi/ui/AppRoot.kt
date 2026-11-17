package app.tateyomi.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import app.tateyomi.data.AppThemeMode
import app.tateyomi.ui.library.LibraryScreen
import app.tateyomi.ui.reader.ReaderScreen
import app.tateyomi.ui.stats.StatsScreen
import app.tateyomi.ui.theme.TateyomiTheme

@Composable
fun TateyomiAppRoot(viewModel: MainViewModel = viewModel()) {
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val libraryFilter by viewModel.libraryFilter.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val readerState by viewModel.readerState.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val fontBusy by viewModel.fontBusy.collectAsStateWithLifecycle()
    val backupBusy by viewModel.backupBusy.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val importBookConflict by viewModel.importBookConflict.collectAsStateWithLifecycle()
    val importFolderConflict by viewModel.importFolderConflict.collectAsStateWithLifecycle()
    val importArchiveFolderChoice by viewModel.importArchiveFolderChoice.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbar.showSnackbar(message)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onForegroundChanged(true)
                Lifecycle.Event.ON_STOP -> viewModel.onForegroundChanged(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = screen !is Screen.Library) {
        viewModel.back()
    }

    val appearance = settings.appearance
    val darkTheme = when (appearance.themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    // The reader chrome follows the app theme, not the paper color, so the
    // system bars should match the app theme even while a paper page is open.
    val systemBarsDark = darkTheme
    val view = LocalView.current
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
            !systemBarsDark
    }

    TateyomiTheme(darkTheme = darkTheme, amoled = appearance.amoled) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                when (val current = screen) {
                    is Screen.Library -> LibraryScreen(
                        books = library,
                        folders = folders,
                        filter = libraryFilter,
                        ready = ready,
                        importState = importState,
                        coverFile = viewModel::coverFile,
                        onImport = viewModel::importBooks,
                        onOpenBook = viewModel::openBook,
                        onOpenStats = viewModel::openStats,
                        onOpenSettings = viewModel::openSettings,
                        onDeleteBook = viewModel::deleteBook,
                        onDeleteBooks = viewModel::deleteBooks,
                        onCreateFolder = viewModel::createFolder,
                        onRenameFolder = viewModel::renameFolder,
                        onDeleteFolder = viewModel::deleteFolder,
                        onMoveBook = viewModel::moveBook,
                        onMoveBooks = viewModel::moveBooks,
                        onCreateFolderAndMove = viewModel::createFolderAndMove,
                        onCreateFolderAndMoveBooks = viewModel::createFolderAndMoveBooks,
                        onUpdateBook = viewModel::updateBookDetails,
                        onUpdateTags = viewModel::updateTagsOnBooks,
                        onResetProgress = viewModel::resetBookProgress,
                        onSetCover = viewModel::setBookCover,
                        onResetCover = viewModel::resetBookCover,
                        onExportBook = viewModel::exportBook,
                        onFilterChange = viewModel::updateLibraryFilter,
                    )
                    is Screen.Reader -> ReaderScreen(
                        viewModel = viewModel,
                        state = readerState ?: ReaderUiState.Loading,
                        appSettings = settings,
                    )
                    is Screen.Stats -> StatsScreen(
                        books = library,
                        stats = stats,
                        onBack = viewModel::back,
                    )
                    is Screen.Settings -> app.tateyomi.ui.settings.SettingsScreen(
                        settings = settings,
                        books = library,
                        stats = stats,
                        fontBusy = fontBusy,
                        backupBusy = backupBusy,
                        fontFileFor = viewModel::fontFile,
                        onBack = viewModel::back,
                        onReaderSettingsChange = viewModel::updateReaderSettings,
                        onSavePreset = viewModel::savePreset,
                        onDeletePreset = viewModel::deletePreset,
                        onExportPreset = viewModel::exportPreset,
                        onAppearanceChange = viewModel::updateAppearance,
                        onAddFont = viewModel::addFont,
                        onDeleteFont = viewModel::deleteFont,
                        onExportBackup = viewModel::exportBackup,
                        onImportBackup = viewModel::importBackup,
                        onImportOptionsChange = viewModel::updateImportOptions,
                    )
                }

                SnackbarHost(
                    hostState = snackbar,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                )

                importArchiveFolderChoice?.let { choice ->
                    ImportArchiveFolderDialog(
                        choice = choice,
                        onResolve = viewModel::resolveImportArchiveFolderChoice,
                    )
                }
                importBookConflict?.let { conflict ->
                    ImportBookConflictDialog(
                        conflict = conflict,
                        onResolve = viewModel::resolveImportBookConflict,
                    )
                }
                importFolderConflict?.let { conflict ->
                    ImportFolderConflictDialog(
                        conflict = conflict,
                        onResolve = viewModel::resolveImportFolderConflict,
                    )
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
