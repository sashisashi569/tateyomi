package app.tateyomi.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.tateyomi.TateyomiApp
import app.tateyomi.data.AppRepository
import app.tateyomi.data.AppSettings
import app.tateyomi.data.AppearanceSettings
import app.tateyomi.data.BACKUP_VERSION
import app.tateyomi.data.BackupFile
import app.tateyomi.data.BackupJson
import app.tateyomi.data.BookContent
import app.tateyomi.data.BookSummary
import app.tateyomi.data.EpubException
import app.tateyomi.data.ReaderPreset
import app.tateyomi.data.ReaderSettings
import app.tateyomi.data.ReadingProgress
import app.tateyomi.data.ReadingStats
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Library : Screen
    data class Reader(val bookId: String) : Screen
    data object Stats : Screen
    data object Settings : Screen
}

data class LoadedBook(
    val summary: BookSummary,
    val content: BookContent,
    val bookDir: File,
)

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Ready(val book: LoadedBook) : ReaderUiState
    data class Error(val message: String, val bookId: String) : ReaderUiState
}

data class ImportUiState(
    val current: Int,
    val total: Int,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository = (application as TateyomiApp).repository

    val library: StateFlow<List<BookSummary>> = repository.library
    val settings: StateFlow<AppSettings> = repository.settings
    val stats: StateFlow<ReadingStats> = repository.stats
    val ready: StateFlow<Boolean> = repository.ready

    private val _screen = MutableStateFlow<Screen>(Screen.Library)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _readerState = MutableStateFlow<ReaderUiState?>(null)
    val readerState: StateFlow<ReaderUiState?> = _readerState.asStateFlow()

    private val _location = MutableStateFlow<ReadingProgress?>(null)
    val location: StateFlow<ReadingProgress?> = _location.asStateFlow()

    private val _importState = MutableStateFlow<ImportUiState?>(null)
    val importState: StateFlow<ImportUiState?> = _importState.asStateFlow()

    private val _fontBusy = MutableStateFlow(false)
    val fontBusy: StateFlow<Boolean> = _fontBusy.asStateFlow()

    private val _backupBusy = MutableStateFlow(false)
    val backupBusy: StateFlow<Boolean> = _backupBusy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var progressSaveJob: Job? = null
    private var readingTicker: Job? = null
    private var sessionBookId: String? = null
    private var sessionActive = false
    private var foreground = true
    private var lastAccrualMs = 0L
    private var pendingChars = 0L
    private var pendingPages = 0L

    fun openBook(id: String) {
        if (_screen.value is Screen.Reader && (_readerState.value as? ReaderUiState.Ready)?.book?.summary?.id == id) {
            return
        }
        _screen.value = Screen.Reader(id)
        _readerState.value = ReaderUiState.Loading
        _location.value = null
        viewModelScope.launch {
            try {
                val content = withContext(Dispatchers.IO) { repository.loadContent(id) }
                val known = repository.library.value.firstOrNull { it.id == id }
                    ?: throw EpubException("本棚にこの本が見つかりません")
                repository.touchBook(id)
                val summary = repository.library.value.firstOrNull { it.id == id } ?: known
                val location = summary.progress ?: ReadingProgress()
                _location.value = location
                _readerState.value = ReaderUiState.Ready(
                    LoadedBook(summary = summary, content = content, bookDir = repository.bookDir(id)),
                )
                beginSession(id)
            } catch (e: Exception) {
                _readerState.value = ReaderUiState.Error(
                    message = (e as? EpubException)?.message ?: (e.message ?: "本を開けませんでした"),
                    bookId = id,
                )
            }
        }
    }

    fun retryOpen(id: String) {
        _readerState.value = null
        openBook(id)
    }

    fun closeBook() {
        val book = (_readerState.value as? ReaderUiState.Ready)?.book
        val location = _location.value
        progressSaveJob?.cancel()
        if (book != null && location != null) {
            viewModelScope.launch { repository.saveProgress(book.summary.id, location) }
        }
        endSession()
        _readerState.value = null
        _location.value = null
        _screen.value = Screen.Library
    }

    fun back() {
        when (_screen.value) {
            is Screen.Reader -> closeBook()
            is Screen.Stats -> _screen.value = Screen.Library
            is Screen.Settings -> _screen.value = Screen.Library
            is Screen.Library -> Unit
        }
    }

    fun openStats() {
        _screen.value = Screen.Stats
    }

    fun openSettings() {
        _screen.value = Screen.Settings
    }

    fun coverFile(book: BookSummary): File? = repository.coverFile(book)

    fun fontFile(fontId: String): File? = repository.fontFile(fontId)

    fun addFont(uri: Uri) {
        if (_fontBusy.value) return
        viewModelScope.launch {
            _fontBusy.value = true
            try {
                val font = withContext(Dispatchers.IO) { repository.addFont(uri) }
                _messages.tryEmit("「${font.name}」を追加しました。表示設定から選べます")
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: "フォントを追加できませんでした")
            } finally {
                _fontBusy.value = false
            }
        }
    }

    fun deleteFont(id: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.deleteFont(id) }
                _messages.tryEmit("フォントを削除しました")
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: "フォントを削除できませんでした")
            }
        }
    }

    fun exportBackup(uri: Uri) {
        if (_backupBusy.value) return
        viewModelScope.launch {
            _backupBusy.value = true
            try {
                val backup = repository.createBackup()
                val text = BackupJson.encodeToString(BackupFile.serializer(), backup)
                withContext(Dispatchers.IO) {
                    val output = getApplication<Application>().contentResolver
                        .openOutputStream(uri)
                        ?: error("ファイルを開けませんでした")
                    output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
                _messages.tryEmit("バックアップを書き出しました")
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: "バックアップを書き出せませんでした")
            } finally {
                _backupBusy.value = false
            }
        }
    }

    fun exportPreset(preset: ReaderPreset, uri: Uri) {
        if (_backupBusy.value) return
        viewModelScope.launch {
            _backupBusy.value = true
            try {
                val backup = BackupFile(
                    version = BACKUP_VERSION,
                    exportedAt = System.currentTimeMillis(),
                    presets = listOf(preset),
                    stats = ReadingStats(),
                )
                val text = BackupJson.encodeToString(BackupFile.serializer(), backup)
                withContext(Dispatchers.IO) {
                    val output = getApplication<Application>().contentResolver
                        .openOutputStream(uri)
                        ?: error("ファイルを開けませんでした")
                    output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
                _messages.tryEmit("プリセット「${preset.name}」を書き出しました")
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: "プリセットを書き出せませんでした")
            } finally {
                _backupBusy.value = false
            }
        }
    }

    fun importBackup(uri: Uri) {
        if (_backupBusy.value) return
        viewModelScope.launch {
            _backupBusy.value = true
            try {
                val text = withContext(Dispatchers.IO) {
                    val bytes = getApplication<Application>().contentResolver
                        .openInputStream(uri)
                        ?.use { it.readBytes() }
                        ?: error("ファイルを開けませんでした")
                    if (bytes.size > 8 * 1024 * 1024) {
                        error("バックアップファイルが大きすぎます")
                    }
                    bytes.toString(Charsets.UTF_8)
                }
                val backup = BackupJson.decodeFromString(BackupFile.serializer(), text)
                val result = withContext(Dispatchers.IO) { repository.importBackup(backup) }
                _messages.tryEmit(
                    "プリセット${result.presetCount}件・" +
                        "読書進捗${result.progressBookCount}冊・" +
                        "統計${result.statsBookCount}冊を読み込みました",
                )
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: "バックアップを読み込めませんでした")
            } finally {
                _backupBusy.value = false
            }
        }
    }

    fun importBooks(uris: List<Uri>) {
        if (uris.isEmpty() || _importState.value != null) return
        val context: Context = getApplication()
        viewModelScope.launch {
            var imported = 0
            var firstError: String? = null
            uris.forEachIndexed { index, uri ->
                _importState.value = ImportUiState(current = index + 1, total = uris.size)
                try {
                    withContext(Dispatchers.IO) { repository.importEpub(uri) }
                    imported++
                } catch (e: Exception) {
                    if (firstError == null) {
                        firstError = e.message ?: "EPUBを読み込めませんでした"
                    }
                }
            }
            _importState.value = null
            when {
                imported > 0 && firstError == null -> _messages.tryEmit("${imported}冊を本棚に追加しました")
                imported > 0 -> _messages.tryEmit("${imported}冊を追加しました（${firstError}）")
                else -> _messages.tryEmit(firstError ?: context.getString(app.tateyomi.R.string.import_empty))
            }
        }
    }

    fun deleteBook(id: String) {
        viewModelScope.launch {
            runCatching { repository.deleteBook(id) }
                .onFailure { _messages.tryEmit(it.message ?: "削除できませんでした") }
        }
    }

    fun updateReaderSettings(reader: ReaderSettings) {
        repository.updateReaderSettings(reader)
    }

    fun updateAppearance(appearance: AppearanceSettings) {
        repository.updateAppearance(appearance)
    }

    fun applyPreset(settings: ReaderSettings) {
        repository.updateReaderSettings(settings)
    }

    fun savePreset(name: String, reader: ReaderSettings) {
        repository.savePreset(name, reader)
    }

    fun deletePreset(preset: ReaderPreset) {
        repository.deletePreset(preset.id)
    }

    fun resetSettings() {
        repository.resetReaderSettings()
    }

    /**
     * Called by the reader whenever the visible page changes. [progress] is
     * layout-independent, so changing font size or orientation can restore the
     * same place afterwards.
     */
    fun onPageChanged(
        page: Int,
        totalPages: Int,
        progress: ReadingProgress,
        advancedPages: Int,
    ) {
        val book = (_readerState.value as? ReaderUiState.Ready)?.book ?: return
        if (advancedPages > 0 && totalPages > 0) {
            pendingPages += advancedPages
            pendingChars += (book.content.charCount / totalPages).coerceAtLeast(1).toLong() *
                advancedPages
        }

        val updated = progress.copy(updatedAt = System.currentTimeMillis())
        _location.value = updated
        progressSaveJob?.cancel()
        progressSaveJob = viewModelScope.launch {
            delay(700)
            repository.saveProgress(book.summary.id, updated)
        }
    }

    fun onForegroundChanged(isForeground: Boolean) {
        foreground = isForeground
        if (isForeground) {
            if (sessionBookId != null && !sessionActive) {
                sessionActive = true
                lastAccrualMs = System.currentTimeMillis()
            }
        } else {
            if (sessionActive) {
                flushSession()
                sessionActive = false
            }
        }
    }

    private fun beginSession(bookId: String) {
        flushSession()
        sessionBookId = bookId
        lastAccrualMs = System.currentTimeMillis()
        sessionActive = foreground
        readingTicker?.cancel()
        readingTicker = viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                if (sessionActive) flushSession()
            }
        }
    }

    private fun endSession() {
        flushSession()
        sessionBookId = null
        sessionActive = false
        readingTicker?.cancel()
        readingTicker = null
    }

    private fun flushSession() {
        val id = sessionBookId ?: return
        val now = System.currentTimeMillis()
        val seconds = if (sessionActive) ((now - lastAccrualMs) / 1000L).coerceAtLeast(0) else 0
        lastAccrualMs = now
        val chars = pendingChars
        val pages = pendingPages
        pendingChars = 0
        pendingPages = 0
        if (seconds <= 0 && chars <= 0 && pages <= 0) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.addReading(id, seconds = seconds, chars = chars, pages = pages)
        }
    }

    override fun onCleared() {
        flushSession()
        super.onCleared()
    }
}
