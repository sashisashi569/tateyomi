package app.tateyomi.data

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
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

private const val MAX_FONT_BYTES = 64L * 1024 * 1024

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
    private val fontsDir = File(rootDir, "fonts")
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
        fontsDir.mkdirs()
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
        val sanitized = sanitizeFonts(loadedSettings)
        _settings.value = sanitized.copy(
            reader = sanitized.reader.normalized(),
            presets = sanitized.presets.map { it.copy(settings = it.settings.normalized()) },
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

    fun fontFile(fontId: String): File? {
        val font = _settings.value.fonts.firstOrNull { it.id == fontId } ?: return null
        val name = font.fileName ?: return null
        return File(fontsDir, name).takeIf(File::isFile)
    }

    suspend fun addFont(uri: Uri): ManagedFont = withContext(Dispatchers.IO) {
        val uuid = UUID.randomUUID().toString()
        val id = "custom:$uuid"
        val temp = File(tempDir, "$uuid.font")
        try {
            copyUri(uri, temp)
            if (temp.length() > MAX_FONT_BYTES) {
                throw FontException("フォントファイルが大きすぎます（上限 64MB）")
            }
            val extension = sniffFontExtension(temp)
                ?: throw FontException("TTF / OTF / TTC フォントではありません")
            try {
                Typeface.createFromFile(temp)
            } catch (_: Exception) {
                throw FontException("フォントを読み込めませんでした")
            }
            val displayName = queryDisplayName(uri)
                ?.substringBeforeLast('.')
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            val familyName = FontMetadata.familyName(temp)
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.take(80)
            val name = familyName ?: displayName ?: "追加フォント"

            fontsDir.mkdirs()
            val fileName = "$uuid.$extension"
            val target = File(fontsDir, fileName)
            temp.copyTo(target, overwrite = true)
            val font = ManagedFont(
                id = id,
                name = name,
                fileName = fileName,
                builtIn = false,
            )
            _settings.update { current -> current.copy(fonts = current.fonts + font) }
            persistSettings()
            font
        } catch (e: FontException) {
            throw e
        } catch (e: Exception) {
            throw FontException(e.message ?: "フォントを追加できませんでした")
        } finally {
            temp.delete()
        }
    }

    suspend fun deleteFont(id: String) {
        withContext(Dispatchers.IO) {
            val font = _settings.value.fonts.firstOrNull { it.id == id } ?: return@withContext
            font.fileName?.let { File(fontsDir, it).delete() }
            _settings.update { current ->
                fun clean(reader: ReaderSettings): ReaderSettings =
                    if (reader.fontId == id) {
                        reader.copy(fontId = BuiltInFonts.MINCHO_ID, font = FontChoice.MINCHO)
                    } else {
                        reader
                    }
                current.copy(
                    fonts = current.fonts.filterNot { it.id == id },
                    reader = clean(current.reader),
                    presets = current.presets.map { preset ->
                        preset.copy(settings = clean(preset.settings))
                    },
                )
            }
            persistSettings()
        }
    }

    private fun sanitizeFonts(settings: AppSettings): AppSettings {
        val validCustom = settings.fonts.filter { font ->
            font.fileName?.let { File(fontsDir, it).isFile } == true
        }
        val validIds = BuiltInFonts.all.map { it.id }.toSet() + validCustom.map { it.id }
        fun clean(reader: ReaderSettings): ReaderSettings {
            val id = reader.fontId
            return if (id != null && id !in validIds) {
                reader.copy(fontId = null, font = FontChoice.MINCHO)
            } else {
                reader
            }
        }
        return settings.copy(
            fonts = validCustom,
            reader = clean(settings.reader),
            presets = settings.presets.map { preset ->
                preset.copy(settings = clean(preset.settings))
            },
        )
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (_: Exception) {
        uri.lastPathSegment
    }

    private fun sniffFontExtension(file: File): String? {
        if (!file.isFile || file.length() < 4) return null
        val header = ByteArray(4)
        file.inputStream().use { input -> input.read(header) }
        return when {
            String(header, Charsets.ISO_8859_1) == "OTTO" -> "otf"
            String(header, Charsets.ISO_8859_1) == "ttcf" -> "ttc"
            String(header, Charsets.ISO_8859_1) == "true" -> "ttf"
            String(header, Charsets.ISO_8859_1) == "typ1" -> "ttf"
            header[0] == 0.toByte() && header[1] == 1.toByte() &&
                header[2] == 0.toByte() && header[3] == 0.toByte() -> "ttf"
            else -> null
        }
    }

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
                coverImage = parsed.coverImageName,
                version = BOOK_CONTENT_VERSION,
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
        if (cached != null && cached.version >= BOOK_CONTENT_VERSION) return@withContext cached

        // Recover from the original EPUB if the derived content was lost or
        // uses an older cache schema (inline gaiji, cover metadata, etc.).
        val source = File(dir, "source.epub")
        if (!source.isFile) {
            return@withContext cached ?: throw EpubException("この本のデータが見つかりません")
        }
        val parsed = withContext(Dispatchers.Default) { EpubParser.parse(source) }
        val content = BookContent(
            chapters = parsed.chapters,
            toc = parsed.toc,
            charCount = parsed.charCount,
            coverImage = parsed.coverImageName,
            version = BOOK_CONTENT_VERSION,
        )
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

    fun updateAppearance(appearance: AppearanceSettings) {
        _settings.update { it.copy(appearance = appearance) }
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
