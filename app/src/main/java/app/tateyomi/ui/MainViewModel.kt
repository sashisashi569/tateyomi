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
import app.tateyomi.data.ImportOptions
import app.tateyomi.data.LibraryFolder
import app.tateyomi.data.LibrarySort
import app.tateyomi.data.ReaderPreset
import app.tateyomi.data.ReaderSettings
import app.tateyomi.data.ReadingProgress
import app.tateyomi.data.ReadingStats
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
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

const val UNFILED_FOLDER_ID = "__unfiled__"

/** Library search / folder / tag selection, kept across reader navigation. */
data class LibraryFilter(
    val query: String = "",
    val folderId: String? = null,
    val tags: Set<String> = emptySet(),
    val sort: LibrarySort = LibrarySort.RECENT,
)

data class ImportBookConflict(
    val existing: BookSummary,
    val incomingTitle: String,
    val incomingAuthor: String,
    val decision: CompletableDeferred<Boolean>,
)

data class ImportFolderConflict(
    val existing: LibraryFolder,
    val decision: CompletableDeferred<Boolean>,
)

data class ImportArchiveFolderChoice(
    val archiveName: String,
    val decision: CompletableDeferred<String?>,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository = (application as TateyomiApp).repository

    val library: StateFlow<List<BookSummary>> = repository.library
    val folders: StateFlow<List<LibraryFolder>> = repository.folders
    val settings: StateFlow<AppSettings> = repository.settings
    val stats: StateFlow<ReadingStats> = repository.stats
    val ready: StateFlow<Boolean> = repository.ready

    private val _screen = MutableStateFlow<Screen>(Screen.Library)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _libraryFilter = MutableStateFlow(LibraryFilter())
    val libraryFilter: StateFlow<LibraryFilter> = _libraryFilter.asStateFlow()

    private val _importBookConflict = MutableStateFlow<ImportBookConflict?>(null)
    val importBookConflict: StateFlow<ImportBookConflict?> = _importBookConflict.asStateFlow()

    private val _importFolderConflict = MutableStateFlow<ImportFolderConflict?>(null)
    val importFolderConflict: StateFlow<ImportFolderConflict?> =
        _importFolderConflict.asStateFlow()

    private val _importArchiveFolderChoice = MutableStateFlow<ImportArchiveFolderChoice?>(null)
    val importArchiveFolderChoice: StateFlow<ImportArchiveFolderChoice?> =
        _importArchiveFolderChoice.asStateFlow()

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

    private data class ImportRequest(val uri: Uri, val folderId: String?)

    private val importQueue = Channel<ImportRequest>(Channel.UNLIMITED)

    init {
        viewModelScope.launch {
            repository.ready.first { it }
            _libraryFilter.update { it.copy(sort = repository.settings.value.librarySort) }
            while (isActive) {
                val batch = ArrayList<ImportRequest>()
                batch += importQueue.receive()
                while (true) {
                    batch += importQueue.tryReceive().getOrNull() ?: break
                }
                runImport(batch)
            }
        }
    }

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
            } catch (e: OutOfMemoryError) {
                _readerState.value = ReaderUiState.Error(
                    message = "本が大きすぎて開けませんでした",
                    bookId = id,
                )
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

    fun updateLibraryFilter(filter: LibraryFilter) {
        val previous = _libraryFilter.value
        _libraryFilter.value = filter
        if (filter.sort != previous.sort) {
            repository.updateLibrarySort(filter.sort)
        }
    }

    fun updateImportOptions(options: ImportOptions) {
        repository.updateImportOptions(options)
    }

    fun resolveImportBookConflict(replace: Boolean) {
        _importBookConflict.value?.decision?.complete(replace)
    }

    fun resolveImportFolderConflict(merge: Boolean) {
        _importFolderConflict.value?.decision?.complete(merge)
    }

    fun resolveImportArchiveFolderChoice(folderName: String?) {
        _importArchiveFolderChoice.value?.decision?.complete(folderName)
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
        importIntoFolder(uris, currentImportFolder())
    }

    /** Entry point for ACTION_SEND / ACTION_SEND_MULTIPLE from other apps. */
    fun importShared(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val targetFolder = currentImportFolder()
        if (_screen.value is Screen.Reader) {
            closeBook()
        } else if (_screen.value !is Screen.Library) {
            _screen.value = Screen.Library
        }
        val sort = _libraryFilter.value.sort
        _libraryFilter.value = LibraryFilter(sort = sort)
        importIntoFolder(uris, targetFolder)
    }

    private fun currentImportFolder(): String? =
        _libraryFilter.value.folderId?.takeIf { it != UNFILED_FOLDER_ID }

    private fun importIntoFolder(uris: List<Uri>, folderId: String?) {
        uris.forEach { uri -> importQueue.trySend(ImportRequest(uri, folderId)) }
    }

    private suspend fun runImport(requests: List<ImportRequest>) {
        if (requests.isEmpty()) return
        val context: Context = getApplication()
        var imported = 0
        var skipped = 0
        var failed = 0
        var firstError: String? = null
        for ((index, request) in requests.withIndex()) {
            _importState.value = ImportUiState(current = index + 1, total = requests.size)
            try {
                val outcome = withContext(Dispatchers.IO) {
                    repository.importBook(
                        uri = request.uri,
                        folderId = request.folderId,
                        bookConflictResolver = { existing, title, author ->
                            askBookConflict(existing, title, author)
                        },
                        folderConflictResolver = { existing ->
                            askFolderConflict(existing)
                        },
                        archiveFolderResolver = { archiveName ->
                            askArchiveFolderChoice(archiveName)
                        },
                    )
                }
                imported += outcome.imported
                skipped += outcome.skipped
                failed += outcome.failed
                if (firstError == null) firstError = outcome.firstError
            } catch (e: OutOfMemoryError) {
                if (firstError == null) {
                    firstError = "本が大きすぎて読み込めませんでした"
                }
                break
            } catch (e: Exception) {
                if (firstError == null) {
                    firstError = e.message ?: "本を読み込めませんでした"
                }
            }
        }
        _importState.value = null
        when {
            imported > 0 -> {
                val notes = buildList {
                    if (skipped > 0) add("${skipped}冊はキャンセル")
                    if (failed > 0) add("${failed}冊は失敗")
                }
                val suffix = if (notes.isEmpty()) "" else "（${notes.joinToString("、")}）"
                _messages.tryEmit("${imported}冊を本棚に追加しました$suffix")
            }
            skipped > 0 && failed == 0 -> _messages.tryEmit("追加をキャンセルしました")
            else -> _messages.tryEmit(firstError ?: context.getString(app.tateyomi.R.string.import_empty))
        }
    }

    private suspend fun askBookConflict(
        existing: BookSummary,
        incomingTitle: String,
        incomingAuthor: String,
    ): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        _importBookConflict.value = ImportBookConflict(
            existing = existing,
            incomingTitle = incomingTitle,
            incomingAuthor = incomingAuthor,
            decision = deferred,
        )
        return try {
            deferred.await()
        } finally {
            _importBookConflict.value = null
        }
    }

    private suspend fun askFolderConflict(existing: LibraryFolder): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        _importFolderConflict.value = ImportFolderConflict(
            existing = existing,
            decision = deferred,
        )
        return try {
            deferred.await()
        } finally {
            _importFolderConflict.value = null
        }
    }

    private suspend fun askArchiveFolderChoice(archiveName: String): String? {
        val name = archiveName.substringBeforeLast('.').trim().ifBlank { archiveName }
        val deferred = CompletableDeferred<String?>()
        _importArchiveFolderChoice.value = ImportArchiveFolderChoice(
            archiveName = name,
            decision = deferred,
        )
        return try {
            deferred.await()
        } finally {
            _importArchiveFolderChoice.value = null
        }
    }

    fun deleteBook(id: String) {
        viewModelScope.launch {
            val title = repository.library.value.firstOrNull { it.id == id }?.title
            runCatching { repository.deleteBook(id) }
                .onSuccess {
                    _messages.tryEmit(
                        if (title != null) "「$title」を削除しました" else "本を削除しました",
                    )
                }
                .onFailure { _messages.tryEmit(it.message ?: "削除できませんでした") }
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            runCatching { repository.createFolder(name) }
                .onSuccess { _messages.tryEmit("フォルダを作成しました") }
                .onFailure { _messages.tryEmit(it.message ?: "フォルダを作成できませんでした") }
        }
    }

    fun renameFolder(id: String, name: String) {
        viewModelScope.launch {
            runCatching { repository.renameFolder(id, name) }
                .onSuccess { _messages.tryEmit("フォルダ名を変更しました") }
                .onFailure { _messages.tryEmit(it.message ?: "フォルダ名を変更できませんでした") }
        }
    }

    fun deleteFolder(id: String) {
        viewModelScope.launch {
            runCatching { repository.deleteFolder(id) }
                .onSuccess { _messages.tryEmit("フォルダを削除しました") }
                .onFailure { _messages.tryEmit(it.message ?: "フォルダを削除できませんでした") }
        }
    }

    fun moveBook(id: String, folderId: String?) = moveBooks(setOf(id), folderId)

    fun moveBooks(ids: Set<String>, folderId: String?) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.moveBooks(ids, folderId) }
                .onSuccess {
                    _messages.tryEmit(
                        if (ids.size == 1) "本を移動しました" else "${ids.size}冊を移動しました",
                    )
                }
                .onFailure { _messages.tryEmit(it.message ?: "移動できませんでした") }
        }
    }

    fun createFolderAndMove(bookId: String, name: String) =
        createFolderAndMoveBooks(setOf(bookId), name)

    fun createFolderAndMoveBooks(ids: Set<String>, name: String) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                val folder = repository.createFolder(name)
                repository.moveBooks(ids, folder.id)
            }.onSuccess {
                _messages.tryEmit(
                    if (ids.size == 1) "本を移動しました" else "${ids.size}冊を移動しました",
                )
            }.onFailure {
                _messages.tryEmit(it.message ?: "移動できませんでした")
            }
        }
    }

    fun updateTagsOnBooks(ids: Set<String>, add: Set<String>, remove: Set<String>) {
        if (ids.isEmpty() || (add.isEmpty() && remove.isEmpty())) return
        viewModelScope.launch {
            runCatching { repository.updateBookTags(ids, add, remove) }
                .onSuccess { _messages.tryEmit("タグを変更しました") }
                .onFailure { _messages.tryEmit(it.message ?: "タグを変更できませんでした") }
        }
    }

    fun deleteBooks(ids: Set<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.deleteBooks(ids) }
                .onSuccess {
                    _messages.tryEmit(
                        if (ids.size == 1) "本を削除しました" else "${ids.size}冊を削除しました",
                    )
                }
                .onFailure { _messages.tryEmit(it.message ?: "削除できませんでした") }
        }
    }

    fun updateBookDetails(id: String, title: String, author: String, tags: List<String>) {
        viewModelScope.launch {
            runCatching { repository.updateBookDetails(id, title, author, tags) }
                .onSuccess { _messages.tryEmit("本の情報を保存しました") }
                .onFailure { _messages.tryEmit(it.message ?: "保存できませんでした") }
        }
    }

    fun resetBookProgress(id: String) {
        viewModelScope.launch {
            runCatching { repository.resetBookProgress(id) }
                .onSuccess { _messages.tryEmit("読書進捗をリセットしました") }
                .onFailure { _messages.tryEmit(it.message ?: "読書進捗をリセットできませんでした") }
        }
    }

    fun setBookCover(id: String, uri: Uri) {
        viewModelScope.launch {
            runCatching { repository.setBookCover(id, uri) }
                .onSuccess { _messages.tryEmit("表紙を変更しました") }
                .onFailure { _messages.tryEmit(it.message ?: "表紙を変更できませんでした") }
        }
    }

    fun resetBookCover(id: String) {
        viewModelScope.launch {
            runCatching { repository.resetBookCover(id) }
                .onSuccess { _messages.tryEmit("表紙をリセットしました") }
                .onFailure { _messages.tryEmit(it.message ?: "表紙をリセットできませんでした") }
        }
    }

    fun exportBook(id: String, uri: Uri) {
        viewModelScope.launch {
            val title = repository.library.value.firstOrNull { it.id == id }?.title
            runCatching { withContext(Dispatchers.IO) { repository.exportBook(id, uri) } }
                .onSuccess {
                    _messages.tryEmit(
                        if (title != null) "「$title」を書き出しました" else "本を書き出しました",
                    )
                }
                .onFailure { _messages.tryEmit(it.message ?: "書き出せませんでした") }
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
