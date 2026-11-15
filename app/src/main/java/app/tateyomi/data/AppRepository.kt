package app.tateyomi.data

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Single source of truth for books, progress, settings and statistics.
 * Everything lives in the app's private files directory; EPUBs are copied on
 * import so the reader never depends on a SAF URI staying valid.
 */
class AppRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
    }

    private val rootDir = File(context.filesDir, "tateyomi")
    private val booksDir = File(rootDir, "books")
    private val tempDir = File(rootDir, "tmp")
    private val libraryFile = File(rootDir, "library.json")
    private val settingsFile = File(rootDir, "settings.json")
    private val statsFile = File(rootDir, "stats.json")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    private val _library = MutableStateFlow<List<BookSummary>>(emptyList())
    val library: StateFlow<List<BookSummary>> = _library.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _stats = MutableStateFlow(ReadingStats())
    val stats: StateFlow<ReadingStats> = _stats.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        booksDir.mkdirs()
        tempDir.mkdirs()
        scope.launch { load() }
    }

    private suspend fun load() {
        val loadedLibrary = readJson(libraryFile, LibraryFile.serializer()) ?: LibraryFile()
        val available = loadedLibrary.books.filter { bookDir(it.id).isDirectory }
        _library.value = sortBooks(available)
        if (available.size != loadedLibrary.books.size) {
            persistLibrary()
        }

        val loadedSettings = readJson(settingsFile, AppSettings.serializer()) ?: AppSettings()
        _settings.value = loadedSettings.copy(
            reader = loadedSettings.reader.normalized(),
            presets = loadedSettings.presets.map { it.copy(settings = it.settings.normalized()) },
        )

        _stats.value = readJson(statsFile, ReadingStats.serializer()) ?: ReadingStats()
        _ready.value = true
    }

    private fun sortBooks(books: List<BookSummary>): List<BookSummary> =
        books.sortedWith(compareByDescending<BookSummary> { it.lastOpenedAt ?: it.addedAt })

    fun bookDir(id: String): File = File(booksDir, id)

    fun coverFile(book: BookSummary): File? =
        book.coverFile?.let { File(bookDir(book.id), it).takeIf(File::isFile) }

    fun imageFile(bookId: String, name: String): File = File(bookDir(bookId), "images/$name")

    suspend fun importEpub(uri: Uri): BookSummary = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val tempFile = File(tempDir, "$id.epub")
        val dir = bookDir(id)
        try {
            copyUri(uri, tempFile)
            if (!looksLikeZip(tempFile)) throw EpubException("EPUBファイルではありません")
            val parsed = withContext(Dispatchers.Default) { EpubParser.parse(tempFile) }

            dir.mkdirs()
            File(dir, "images").mkdirs()
            tempFile.copyTo(File(dir, "source.epub"), overwrite = true)

            parsed.images.forEach { (name, bytes) ->
                val target = File(dir, "images/$name")
                target.parentFile?.mkdirs()
                target.writeBytes(bytes)
            }

            CoverGenerator.writeCover(
                original = parsed.coverBytes,
                title = parsed.title,
                author = parsed.author,
                outFile = File(dir, "cover.jpg"),
            )

            val content = BookContent(
                chapters = parsed.chapters,
                toc = parsed.toc,
                charCount = parsed.charCount,
            )
            writeJson(File(dir, "content.json"), BookContent.serializer()) { content }

            val summary = BookSummary(
                id = id,
                title = parsed.title.ifBlank { "無題の本" },
                author = parsed.author,
                coverFile = "cover.jpg",
                charCount = parsed.charCount,
                chapterCount = parsed.chapters.size,
                addedAt = System.currentTimeMillis(),
                lastOpenedAt = null,
                progress = null,
            )
            _library.update { sortBooks(it + summary) }
            persistLibrary()
            summary
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw if (e is EpubException) e else EpubException(e.message ?: "読み込みに失敗しました")
        } finally {
            tempFile.delete()
        }
    }

    suspend fun loadContent(id: String): BookContent = withContext(Dispatchers.IO) {
        val dir = bookDir(id)
        val file = File(dir, "content.json")
        val cached = readJson(file, BookContent.serializer())
        if (cached != null) return@withContext cached

        // Recover from the original EPUB if the derived content was lost.
        val source = File(dir, "source.epub")
        if (!source.isFile) throw EpubException("この本のデータが見つかりません")
        val parsed = withContext(Dispatchers.Default) { EpubParser.parse(source) }
        val content = BookContent(parsed.chapters, parsed.toc, parsed.charCount)
        writeJson(file, BookContent.serializer()) { content }
        content
    }

    suspend fun touchBook(id: String) {
        _library.update { books ->
            sortBooks(books.map { book ->
                if (book.id == id) book.copy(lastOpenedAt = System.currentTimeMillis()) else book
            })
        }
        persistLibrary()
    }

    suspend fun saveProgress(id: String, progress: ReadingProgress) {
        _library.update { books ->
            books.map { book -> if (book.id == id) book.copy(progress = progress) else book }
        }
        persistLibrary()
    }

    suspend fun deleteBook(id: String) {
        withContext(Dispatchers.IO) {
            bookDir(id).deleteRecursively()
            _library.update { books -> books.filterNot { it.id == id } }
            _stats.update { stats ->
                stats.copy(
                    books = stats.books - id,
                )
            }
            persistLibrary()
            persistStats()
        }
    }

    fun updateReaderSettings(reader: ReaderSettings) {
        val normalized = reader.normalized()
        _settings.update { it.copy(reader = normalized) }
        scope.launch { persistSettings() }
    }

    fun savePreset(name: String, reader: ReaderSettings) {
        val cleanName = name.trim().take(24)
        if (cleanName.isEmpty()) return
        _settings.update { current ->
            current.copy(
                presets = current.presets + ReaderPreset(
                    id = UUID.randomUUID().toString(),
                    name = cleanName,
                    settings = reader.normalized(),
                ),
            )
        }
        scope.launch { persistSettings() }
    }

    fun deletePreset(id: String) {
        _settings.update { current ->
            current.copy(presets = current.presets.filterNot { it.id == id })
        }
        scope.launch { persistSettings() }
    }

    fun resetReaderSettings() {
        updateReaderSettings(ReaderSettings.Default)
    }

    suspend fun addReading(bookId: String, seconds: Long, chars: Long, pages: Long) {
        if (seconds <= 0 && chars <= 0 && pages <= 0) return
        val today = LocalDate.now().toString()
        _stats.update { current ->
            val book = current.books[bookId] ?: BookStats()
            current.copy(
                totalSeconds = current.totalSeconds + seconds.coerceAtLeast(0),
                totalChars = current.totalChars + chars.coerceAtLeast(0),
                totalPages = current.totalPages + pages.coerceAtLeast(0),
                dailySeconds = if (seconds > 0) {
                    current.dailySeconds + (today to (current.dailySeconds[today] ?: 0L) + seconds)
                } else {
                    current.dailySeconds
                },
                dailyChars = if (chars > 0) {
                    current.dailyChars + (today to (current.dailyChars[today] ?: 0L) + chars)
                } else {
                    current.dailyChars
                },
                books = current.books + (
                    bookId to book.copy(
                        seconds = book.seconds + seconds.coerceAtLeast(0),
                        chars = book.chars + chars.coerceAtLeast(0),
                        pages = book.pages + pages.coerceAtLeast(0),
                        lastReadAt = System.currentTimeMillis(),
                    )
                    ),
            )
        }
        persistStats()
    }

    private suspend fun persistLibrary() {
        writeJson(libraryFile, LibraryFile.serializer()) { LibraryFile(books = _library.value) }
    }

    private suspend fun persistSettings() {
        writeJson(settingsFile, AppSettings.serializer()) { _settings.value }
    }

    private suspend fun persistStats() {
        writeJson(statsFile, ReadingStats.serializer()) { _stats.value }
    }

    private suspend fun <T> writeJson(
        file: File,
        serializer: KSerializer<T>,
        value: () -> T,
    ) {
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                try {
                    file.parentFile?.mkdirs()
                    val payload = value()
                    val temporary = File(file.parentFile, "${file.name}.tmp")
                    temporary.writeText(json.encodeToString(serializer, payload))
                    if (!temporary.renameTo(file)) {
                        file.writeText(temporary.readText())
                        temporary.delete()
                    }
                } catch (e: Exception) {
                    Log.w("Tateyomi", "Failed to write ${file.name}", e)
                }
            }
        }
    }

    private fun <T> readJson(file: File, serializer: KSerializer<T>): T? {
        if (!file.isFile) return null
        return try {
            json.decodeFromString(serializer, file.readText())
        } catch (e: Exception) {
            Log.w("Tateyomi", "Failed to read ${file.name}", e)
            null
        }
    }

    private fun copyUri(uri: Uri, target: File) {
        target.parentFile?.mkdirs()
        val input = context.contentResolver.openInputStream(uri)
            ?: throw EpubException("ファイルを開けませんでした")
        input.use { source ->
            target.outputStream().buffered().use { output ->
                source.copyTo(output, 128 * 1024)
            }
        }
        if (target.length() == 0L) throw EpubException("ファイルが空です")
    }

    private fun looksLikeZip(file: File): Boolean {
        if (!file.isFile || file.length() < 4) return false
        return file.inputStream().use { input ->
            val header = ByteArray(4)
            val read = input.read(header)
            read == 4 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte() &&
                (header[2] == 3.toByte() || header[2] == 5.toByte() || header[2] == 7.toByte())
        }
    }
}
