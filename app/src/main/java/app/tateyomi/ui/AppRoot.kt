package app.tateyomi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.tateyomi.ui.library.LibraryScreen
import app.tateyomi.ui.reader.ReaderScreen
import app.tateyomi.ui.stats.StatsScreen

@Composable
fun TateyomiAppRoot(viewModel: MainViewModel = viewModel()) {
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val readerState by viewModel.readerState.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val fontBusy by viewModel.fontBusy.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()

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

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val current = screen) {
            is Screen.Library -> LibraryScreen(
                books = library,
                ready = ready,
                importState = importState,
                coverFile = viewModel::coverFile,
                onImport = viewModel::importBooks,
                onOpenBook = viewModel::openBook,
                onOpenStats = viewModel::openStats,
                onOpenSettings = viewModel::openSettings,
                onDeleteBook = viewModel::deleteBook,
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
                fontBusy = fontBusy,
                fontFileFor = viewModel::fontFile,
                onBack = viewModel::back,
                onReaderSettingsChange = viewModel::updateReaderSettings,
                onSavePreset = viewModel::savePreset,
                onDeletePreset = viewModel::deletePreset,
                onAddFont = viewModel::addFont,
                onDeleteFont = viewModel::deleteFont,
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
        )
    }
}
