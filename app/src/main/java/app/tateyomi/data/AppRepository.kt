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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

private const val MAX_FONT_BYTES = 64L * 1024 * 1024
private const val MAX_FOLDER_NAME = 40
private const val MAX_COVER_BYTES = 64L * 1024 * 1024
private const val MAX_ARCHIVE_DEPTH = 2

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

    /**
     * Settings are strict while the app is in 0.0.x: removed or renamed fields
     * invalidate the file instead of being migrated. New installs and schema
     * changes simply start from the current defaults.
     */
    private val settingsJson = Json {
        ignoreUnknownKeys = false
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

    private val _folders = MutableStateFlow<List<LibraryFolder>>(emptyList())
    val folders: StateFlow<List<LibraryFolder>> = _folders.asStateFlow()

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
        // Any temp file left after a crash or process kill is not in use.
        tempDir.listFiles()?.forEach { it.deleteRecursively() }
        scope.launch { load() }
    }

    private suspend fun load() {
        val loadedLibrary = readJson(libraryFile, LibraryFile.serializer()) ?: LibraryFile()
        val folders = loadedLibrary.folders
            .map { it.copy(name = it.name.trim().take(MAX_FOLDER_NAME)) }
            .filter { it.id.isNotBlank() && it.name.isNotBlank() }
            .distinctBy { it.id }
        val sortedFolders = folders.sortedBy { it.name.lowercase() }
        _folders.value = sortedFolders
        val folderIds = folders.map { it.id }.toSet()
        val available = loadedLibrary.books
            .filter { bookDir(it.id).isDirectory }
            .map { book ->
                book.copy(
                    folderId = book.folderId?.takeIf { it in folderIds },
                    tags = sanitizeTags(book.tags),
                )
            }
        _library.value = sortBooks(available)
        if (available.size != loadedLibrary.books.size || available != loadedLibrary.books ||
            sortedFolders != loadedLibrary.folders
        ) {
            persistLibrary()
        }

        val loadedSettings = readJson(settingsFile, AppSettings.serializer(), settingsJson)
            ?: AppSettings()
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
                        reader.copy(fontId = BuiltInFonts.MINCHO_ID)
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
            return if (id !in validIds) {
                reader.copy(fontId = BuiltInFonts.MINCHO_ID)
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

    private fun queryDisplayName(uri: Uri): String? {
        val queried = try {
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
            null
        }
        return queried?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment
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

    suspend fun importBook(
        uri: Uri,
        folderId: String? = null,
        bookConflictResolver: BookConflictResolver? = null,
        folderConflictResolver: FolderConflictResolver? = null,
        archiveFolderResolver: ArchiveFolderResolver? = null,
    ): ImportOutcome = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val displayName = queryDisplayName(uri)
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: "book"
        val tempFile = File(tempDir, "$id.source")
        try {
            copyUri(uri, tempFile)
            importContainer(
                sourceFile = tempFile,
                displayName = displayName,
                targetFolderId = folderId,
                bookConflictResolver = bookConflictResolver,
                folderConflictResolver = folderConflictResolver,
                archiveFolderResolver = archiveFolderResolver,
                depth = 0,
            )
        } catch (e: OutOfMemoryError) {
            // Let the import queue stop the batch instead of retrying the
            // remaining large files in a low-memory state.
            throw e
        } catch (e: Exception) {
            ImportOutcome(
                failed = 1,
                firstError = if (e is EpubException) e.message else (e.message ?: "読み込みに失敗しました"),
            )
        } finally {
            tempFile.delete()
        }
    }

    suspend fun importEpub(uri: Uri) {
        importBook(uri)
    }

    private suspend fun importContainer(
        sourceFile: File,
        displayName: String,
        targetFolderId: String?,
        bookConflictResolver: BookConflictResolver?,
        folderConflictResolver: FolderConflictResolver?,
        archiveFolderResolver: ArchiveFolderResolver?,
        depth: Int,
    ): ImportOutcome {
        val options = _settings.value.importOptions
        val container = ArchiveExtractor.detect(sourceFile)
        if (options.expandArchives && container != null && depth < MAX_ARCHIVE_DEPTH) {
            if (container == ContainerFormat.ZIP && ArchiveExtractor.zipHasBookEntries(sourceFile)) {
                val bulkDir = File(tempDir, "bulk-${UUID.randomUUID()}")
                try {
                    ArchiveExtractor.extract(sourceFile, container, bulkDir)
                    val books = ArchiveExtractor.findBookFiles(bulkDir)
                    if (books.isNotEmpty()) {
                        return importBulkDirectory(
                            root = bulkDir,
                            archiveDisplayName = displayName,
                            targetFolderId = targetFolderId,
                            bookConflictResolver = bookConflictResolver,
                            folderConflictResolver = folderConflictResolver,
                            archiveFolderResolver = archiveFolderResolver,
                            depth = depth,
                        )
                    }
                } finally {
                    bulkDir.deleteRecursively()
                }
            } else if (container == ContainerFormat.RAR || container == ContainerFormat.SEVEN_Z) {
                val bulkDir = File(tempDir, "bulk-${UUID.randomUUID()}")
                try {
                    ArchiveExtractor.extract(sourceFile, container, bulkDir)
                    val books = ArchiveExtractor.findBookFiles(bulkDir)
                    if (books.isNotEmpty()) {
                        return importBulkDirectory(
                            root = bulkDir,
                            archiveDisplayName = displayName,
                            targetFolderId = targetFolderId,
                            bookConflictResolver = bookConflictResolver,
                            folderConflictResolver = folderConflictResolver,
                            archiveFolderResolver = archiveFolderResolver,
                            depth = depth,
                        )
                    }
                    val images = ArchiveExtractor.findImageFiles(bulkDir)
                    if (images.isNotEmpty()) {
                        val format = if (container == ContainerFormat.SEVEN_Z) {
                            BookFormat.SEVEN_Z
                        } else {
                            BookFormat.CBR
                        }
                        return importSingleFile(
                            sourceFile = sourceFile,
                            displayName = displayName,
                            targetFolderId = targetFolderId,
                            bookConflictResolver = bookConflictResolver,
                            format = format,
                            sourceExtension = sourceExtension(format, displayName),
                            parseWith = { imageDir ->
                                ComicArchiveParser.parseDirectory(
                                    sourceDir = bulkDir,
                                    outputDir = imageDir,
                                    format = format,
                                    fallbackTitle = displayName.substringBeforeLast('.'),
                                )
                            },
                        )
                    }
                    return ImportOutcome(
                        failed = 1,
                        firstError = "対応する本が見つかりません",
                    )
                } finally {
                    bulkDir.deleteRecursively()
                }
            }
        }

        val format = detectFormat(sourceFile, displayName)
        return importSingleFile(
            sourceFile = sourceFile,
            displayName = displayName,
            targetFolderId = targetFolderId,
            bookConflictResolver = bookConflictResolver,
            format = format,
            sourceExtension = sourceExtension(format, displayName),
        )
    }

    private suspend fun importBulkDirectory(
        root: File,
        archiveDisplayName: String,
        targetFolderId: String?,
        bookConflictResolver: BookConflictResolver?,
        folderConflictResolver: FolderConflictResolver?,
        archiveFolderResolver: ArchiveFolderResolver?,
        depth: Int,
    ): ImportOutcome {
        val books = ArchiveExtractor.findBookFiles(root)
        if (books.isEmpty()) return ImportOutcome()
        val mode = _settings.value.importOptions.archiveFolderMode
        val defaultName = archiveDisplayName.substringBeforeLast('.').trim()
            .ifBlank { archiveDisplayName }
        val folderName = when (mode) {
            ArchiveFolderMode.ALWAYS -> defaultName
            ArchiveFolderMode.NEVER -> null
            ArchiveFolderMode.ASK -> if (depth == 0) {
                archiveFolderResolver?.invoke(defaultName)
            } else {
                null
            }
        }
        val folderId = if (folderName != null) {
            val folder = resolveArchiveFolder(
                folderName = folderName,
                targetFolderId = targetFolderId,
                resolver = folderConflictResolver,
            ) ?: return ImportOutcome(skipped = books.size)
            folder.id
        } else {
            targetFolderId
        }

        var imported = 0
        var skipped = 0
        var failed = 0
        var firstError: String? = null
        for (book in books) {
            val relativeName = book.relativeTo(root).invariantSeparatorsPath
            val outcome = try {
                importContainer(
                    sourceFile = book,
                    displayName = relativeName,
                    targetFolderId = folderId,
                    bookConflictResolver = bookConflictResolver,
                    folderConflictResolver = folderConflictResolver,
                    archiveFolderResolver = archiveFolderResolver,
                    depth = depth + 1,
                )
            } catch (e: OutOfMemoryError) {
                throw e
            } catch (e: Exception) {
                ImportOutcome(
                    failed = 1,
                    firstError = if (e is EpubException) e.message else (e.message ?: "読み込みに失敗しました"),
                )
            }
            imported += outcome.imported
            skipped += outcome.skipped
            failed += outcome.failed
            if (firstError == null) firstError = outcome.firstError
        }
        return ImportOutcome(imported, skipped, failed, firstError)
    }

    private suspend fun resolveArchiveFolder(
        folderName: String,
        targetFolderId: String?,
        resolver: FolderConflictResolver?,
    ): LibraryFolder? {
        val name = folderName.trim()
            .replace(Regex("[\\s\\u3000]+"), " ")
            .take(MAX_FOLDER_NAME)
            .ifBlank { "追加した本" }
        val existing = _folders.value.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (existing != null) {
            if (existing.id == targetFolderId) return existing
            val merge = resolver?.invoke(existing) ?: true
            return if (merge) existing else null
        }
        return try {
            createFolder(name)
        } catch (_: EpubException) {
            _folders.value.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }
    }

    private suspend fun importSingleFile(
        sourceFile: File,
        displayName: String,
        targetFolderId: String?,
        bookConflictResolver: BookConflictResolver?,
        format: BookFormat,
        sourceExtension: String,
        parseWith: (suspend (imageDir: File) -> ParsedEpub)? = null,
    ): ImportOutcome {
        val newId = UUID.randomUUID().toString()
        val newDir = bookDir(newId)
        try {
            newDir.mkdirs()
            File(newDir, "images").mkdirs()
            val imageDir = File(newDir, "images")
            val parsed = withContext(Dispatchers.Default) {
                parseWith?.invoke(imageDir)
                    ?: parseSource(sourceFile, format, displayName.substringBeforeLast('.'), imageDir)
            }
            if (parsed.charCount > MAX_IMPORT_CHARS) {
                throw EpubException("本文が大きすぎて読み込めません")
            }

            val existing = findDuplicateBook(_library.value, parsed.title, parsed.author)
            val replace = if (existing != null && bookConflictResolver != null) {
                bookConflictResolver(existing, parsed.title, parsed.author)
            } else {
                false
            }
            if (existing != null && bookConflictResolver != null && !replace) {
                newDir.deleteRecursively()
                return ImportOutcome(skipped = 1)
            }

            val sourceFileTarget = File(newDir, "source.$sourceExtension")
            if (!sourceFile.renameTo(sourceFileTarget)) {
                sourceFile.copyTo(sourceFileTarget, overwrite = true)
                sourceFile.delete()
            }
            parsed.images.forEach { (name, bytes) ->
                val target = safeImageTarget(newDir, name) ?: return@forEach
                target.parentFile?.mkdirs()
                target.writeBytes(bytes)
            }

            val coverBytes = parsed.coverBytes?.takeIf { it.size <= MAX_COVER_BYTES }
                ?: parsed.coverImageFile?.takeIf {
                    it.isFile && it.length() <= MAX_COVER_BYTES
                }?.readBytes()
                ?: parsed.coverImageName?.let { name ->
                    val file = safeImageTarget(newDir, name)
                    if (file != null && file.isFile && file.length() <= MAX_COVER_BYTES) {
                        file.readBytes()
                    } else {
                        null
                    }
                }
            CoverGenerator.writeCover(
                original = coverBytes,
                title = parsed.title,
                author = parsed.author,
                outFile = File(newDir, "cover.jpg"),
            )

            val content = BookContent(
                chapters = parsed.chapters,
                toc = parsed.toc,
                charCount = parsed.charCount,
                coverImage = parsed.coverImageName,
                version = BOOK_CONTENT_VERSION,
            )
            writeJson(File(newDir, "content.json"), BookContent.serializer()) { content }

            val targetFolder = targetFolderId?.takeIf { target ->
                _folders.value.any { it.id == target }
            }
            if (existing != null && replace) {
                val summary = existing.copy(
                    title = parsed.title.ifBlank { "無題の本" },
                    author = parsed.author,
                    coverFile = "cover.jpg",
                    charCount = parsed.charCount,
                    chapterCount = parsed.chapters.size,
                    folderId = targetFolder ?: existing.folderId,
                    tags = sanitizeTags(existing.tags + parsed.suggestedTags),
                    format = format,
                    sourceFile = "source.$sourceExtension",
                )
                replaceBookDirectory(existing.id, newDir)
                _library.update { books ->
                    sortBooks(books.map { if (it.id == existing.id) summary else it })
                }
            } else {
                val summary = BookSummary(
                    id = newId,
                    title = parsed.title.ifBlank { "無題の本" },
                    author = parsed.author,
                    coverFile = "cover.jpg",
                    charCount = parsed.charCount,
                    chapterCount = parsed.chapters.size,
                    addedAt = System.currentTimeMillis(),
                    lastOpenedAt = null,
                    progress = null,
                    folderId = targetFolder,
                    tags = sanitizeTags(parsed.suggestedTags),
                    format = format,
                    sourceFile = "source.$sourceExtension",
                )
                _library.update { sortBooks(it + summary) }
            }
            persistLibrary()
            return ImportOutcome(imported = 1)
        } catch (e: OutOfMemoryError) {
            newDir.deleteRecursively()
            throw e
        } catch (e: Exception) {
            newDir.deleteRecursively()
            return ImportOutcome(
                failed = 1,
                firstError = if (e is EpubException) e.message else (e.message ?: "読み込みに失敗しました"),
            )
        }
    }

    private fun replaceBookDirectory(existingId: String, newDir: File) {
        val oldDir = bookDir(existingId)
        if (!oldDir.exists()) {
            if (!newDir.renameTo(oldDir)) {
                newDir.copyRecursively(oldDir, overwrite = true)
                newDir.deleteRecursively()
            }
            return
        }
        val backup = File(booksDir, "$existingId.replaced-${UUID.randomUUID()}")
        if (!oldDir.renameTo(backup)) {
            throw EpubException("本の置き換えに失敗しました")
        }
        try {
            if (!newDir.renameTo(oldDir)) {
                newDir.copyRecursively(oldDir, overwrite = true)
                newDir.deleteRecursively()
            }
            backup.deleteRecursively()
        } catch (e: Exception) {
            oldDir.deleteRecursively()
            backup.renameTo(oldDir)
            throw EpubException("本の置き換えに失敗しました")
        }
    }

    private suspend fun parseSource(
        file: File,
        format: BookFormat,
        fallbackTitle: String,
        imageDir: File,
    ): ParsedEpub = when (format) {
        BookFormat.EPUB -> EpubParser.parse(file, fallbackTitle, imageDir)
        BookFormat.XHTML -> {
            if (looksLikeZip(file)) {
                java.util.zip.ZipFile(file).use { zip ->
                    XhtmlParser.parseArchive(zip, fallbackTitle, imageDir)
                }
            } else {
                XhtmlParser.parse(file, fallbackTitle, imageDir)
            }
        }
        BookFormat.CBZ -> ComicArchiveParser.parseZip(file, imageDir, BookFormat.CBZ, fallbackTitle)
        BookFormat.CBR -> ComicArchiveParser.parseRar(file, imageDir, BookFormat.CBR, fallbackTitle)
        BookFormat.MOBI, BookFormat.AZW3 -> KindleParser.parse(file, fallbackTitle, imageDir)
        BookFormat.SEVEN_Z -> {
            val extracted = File(tempDir, "sevenz-${UUID.randomUUID()}")
            extracted.mkdirs()
            try {
                ArchiveExtractor.extract(file, ContainerFormat.SEVEN_Z, extracted)
                ComicArchiveParser.parseDirectory(
                    sourceDir = extracted,
                    outputDir = imageDir,
                    format = BookFormat.SEVEN_Z,
                    fallbackTitle = fallbackTitle,
                )
            } finally {
                extracted.deleteRecursively()
            }
        }
    }

    suspend fun loadContent(id: String): BookContent = withContext(Dispatchers.IO) {
        val dir = bookDir(id)
        val file = File(dir, "content.json")
        val cached = readJson(file, BookContent.serializer())
        if (cached != null && cached.version >= BOOK_CONTENT_VERSION) return@withContext cached

        // Recover from the stored source if the derived content was lost or
        // uses an older cache schema.
        val summary = _library.value.firstOrNull { it.id == id }
        val sourceName = summary?.sourceFile ?: "source.epub"
        val format = summary?.format ?: BookFormat.EPUB
        val source = File(dir, sourceName).takeIf { it.isFile }
            ?: File(dir, "source.epub").takeIf { it.isFile }
        if (source == null) {
            return@withContext cached ?: throw EpubException("この本のデータが見つかりません")
        }
        val parsed = withContext(Dispatchers.Default) {
            parseSource(
                file = source,
                format = format,
                fallbackTitle = summary?.title ?: source.nameWithoutExtension,
                imageDir = File(dir, "images"),
            )
        }
        if (parsed.charCount > MAX_IMPORT_CHARS) {
            throw EpubException("本文が大きすぎて読み込めません")
        }
        parsed.images.forEach { (name, bytes) ->
            val target = safeImageTarget(dir, name) ?: return@forEach
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
        }
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

    private fun safeImageTarget(dir: File, name: String): File? {
        val safe = name.replace('\\', '/').substringAfterLast('/')
        if (safe.isBlank() || safe.contains("..")) return null
        return File(File(dir, "images"), safe)
    }

    private fun detectFormat(file: File, displayName: String): BookFormat {
        val extension = displayName.substringAfterLast('.', "").lowercase()
        val header = readHeader(file, 16)
        if (header.size >= 4 &&
            header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte() &&
            (header[2] == 3.toByte() || header[2] == 5.toByte() || header[2] == 7.toByte())
        ) {
            return try {
                java.util.zip.ZipFile(file).use { zip ->
                    when {
                        zip.getEntry("META-INF/container.xml") != null -> BookFormat.EPUB
                        zip.entries().asSequence().any { entry ->
                            !entry.isDirectory && setOf("xhtml", "html", "htm")
                                .contains(entry.name.substringAfterLast('.', "").lowercase())
                        } -> BookFormat.XHTML
                        else -> BookFormat.CBZ
                    }
                }
            } catch (e: Exception) {
                throw EpubException("ZIPを読み込めませんでした（破損している可能性があります）")
            }
        }
        if (header.size >= 4 &&
            header[0] == 'R'.code.toByte() && header[1] == 'a'.code.toByte() &&
            header[2] == 'r'.code.toByte() && header[3] == '!'.code.toByte()
        ) {
            return BookFormat.CBR
        }
        if (header.size >= 6 &&
            header[0] == 0x37.toByte() && header[1] == 0x7A.toByte() &&
            header[2] == 0xBC.toByte() && header[3] == 0xAF.toByte() &&
            header[4] == 0x27.toByte() && header[5] == 0x1C.toByte()
        ) {
            return BookFormat.SEVEN_Z
        }
        if (looksLikePalmDb(file)) {
            return if (extension == "azw3" || extension == "kf8") BookFormat.AZW3 else BookFormat.MOBI
        }
        if (extension in setOf("xhtml", "html", "htm")) return BookFormat.XHTML
        if (extension == "epub") throw EpubException("EPUBファイルではありません")
        if (extension == "cbz") throw EpubException("CBZファイルではありません")
        if (extension in setOf("cbr", "rar")) throw EpubException("RARファイルではありません")
        if (extension == "7z") throw EpubException("7zファイルではありません")
        if (extension in setOf("mobi", "prc", "azw", "azw3", "kf8", "pdb")) {
            throw EpubException("Kindleファイルではありません")
        }
        val textPrefix = readHeader(file, 2048)
            .toString(Charsets.UTF_8)
            .trimStart('\uFEFF', ' ', '\t', '\r', '\n')
            .lowercase()
        if (textPrefix.startsWith("<!doctype html") || textPrefix.startsWith("<html") ||
            (textPrefix.startsWith("<?xml") && textPrefix.contains("<html"))
        ) {
            return BookFormat.XHTML
        }
        throw EpubException("対応していない形式です")
    }

    private fun sourceExtension(format: BookFormat, displayName: String): String {
        val extension = displayName.substringAfterLast('.', "").lowercase()
        return when (format) {
            BookFormat.EPUB -> "epub"
            BookFormat.XHTML -> if (extension in setOf("xhtml", "html", "htm", "zip", "cbz")) {
                extension
            } else {
                "xhtml"
            }
            BookFormat.CBZ -> if (extension == "zip") "zip" else "cbz"
            BookFormat.CBR -> if (extension in setOf("cbr", "rar")) extension else "cbr"
            BookFormat.MOBI -> if (extension in setOf("mobi", "prc", "azw", "pdb")) extension else "mobi"
            BookFormat.AZW3 -> if (extension in setOf("azw3", "kf8", "azw")) extension else "azw3"
            BookFormat.SEVEN_Z -> "7z"
        }
    }

    private fun readHeader(file: File, length: Int): ByteArray {
        if (!file.isFile) return ByteArray(0)
        return try {
            file.inputStream().use { input ->
                val bytes = ByteArray(length)
                val read = input.read(bytes)
                if (read <= 0) ByteArray(0) else bytes.copyOf(read)
            }
        } catch (_: Exception) {
            ByteArray(0)
        }
    }

    private fun looksLikePalmDb(file: File): Boolean {
        val header = readHeader(file, 68)
        if (header.size < 68) return false
        val type = String(header, 60, 4, Charsets.ISO_8859_1)
        val creator = String(header, 64, 4, Charsets.ISO_8859_1)
        return (type == "BOOK" && creator == "MOBI") || (type == "TEXt" && creator == "REAd")
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
        deleteBooks(setOf(id))
    }

    suspend fun deleteBooks(ids: Set<String>) {
        if (ids.isEmpty()) return
        withContext(Dispatchers.IO) {
            ids.forEach { id -> bookDir(id).deleteRecursively() }
            _library.update { books -> books.filterNot { it.id in ids } }
            _stats.update { stats ->
                stats.copy(books = stats.books - ids)
            }
            persistLibrary()
            persistStats()
        }
    }

    suspend fun createFolder(name: String): LibraryFolder = withContext(Dispatchers.IO) {
        val cleanName = name.trim().replace(Regex("[\\s\\u3000]+"), " ").take(MAX_FOLDER_NAME)
        if (cleanName.isEmpty()) throw EpubException("フォルダ名を入力してください")
        if (_folders.value.any { it.name.equals(cleanName, ignoreCase = true) }) {
            throw EpubException("フォルダ名が重複しています")
        }
        val folder = LibraryFolder(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            createdAt = System.currentTimeMillis(),
        )
        _folders.update { (it + folder).sortedBy { item -> item.name.lowercase() } }
        persistLibrary()
        folder
    }

    suspend fun renameFolder(id: String, name: String) = withContext(Dispatchers.IO) {
        val cleanName = name.trim().replace(Regex("[\\s\\u3000]+"), " ").take(MAX_FOLDER_NAME)
        if (cleanName.isEmpty()) throw EpubException("フォルダ名を入力してください")
        if (_folders.value.any { it.id != id && it.name.equals(cleanName, ignoreCase = true) }) {
            throw EpubException("フォルダ名が重複しています")
        }
        _folders.update { folders ->
            folders.map { if (it.id == id) it.copy(name = cleanName) else it }
                .sortedBy { it.name.lowercase() }
        }
        persistLibrary()
    }

    suspend fun deleteFolder(id: String) = withContext(Dispatchers.IO) {
        _folders.update { folders -> folders.filterNot { it.id == id } }
        _library.update { books ->
            sortBooks(books.map { book ->
                if (book.folderId == id) book.copy(folderId = null) else book
            })
        }
        persistLibrary()
    }

    suspend fun moveBook(id: String, folderId: String?) = moveBooks(setOf(id), folderId)

    suspend fun moveBooks(ids: Set<String>, folderId: String?) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val target = folderId?.takeIf { folder -> _folders.value.any { it.id == folder } }
        _library.update { books ->
            sortBooks(books.map { book ->
                if (book.id in ids) book.copy(folderId = target) else book
            })
        }
        persistLibrary()
    }

    suspend fun updateBookTags(
        ids: Set<String>,
        add: Set<String>,
        remove: Set<String>,
    ) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val toAdd = sanitizeTags(add.toList())
        val toRemove = sanitizeTags(remove.toList()).toSet()
        _library.update { books ->
            sortBooks(books.map { book ->
                if (book.id in ids) {
                    book.copy(tags = sanitizeTags(book.tags + toAdd - toRemove))
                } else {
                    book
                }
            })
        }
        persistLibrary()
    }

    suspend fun updateBookDetails(id: String, title: String, author: String, tags: List<String>) =
        withContext(Dispatchers.IO) {
            val cleanTitle = title.trim()
                .replace(Regex("[\\r\\n\\t]+"), " ")
                .take(200)
                .ifBlank { "無題の本" }
            val cleanAuthor = author.trim().replace(Regex("[\\s\\u3000]+"), " ").take(120)
            val cleanTags = sanitizeTags(tags)
            _library.update { books ->
                sortBooks(books.map { book ->
                    if (book.id == id) {
                        book.copy(title = cleanTitle, author = cleanAuthor, tags = cleanTags)
                    } else {
                        book
                    }
                })
            }
            persistLibrary()
        }

    suspend fun resetBookProgress(id: String) = withContext(Dispatchers.IO) {
        _library.update { books ->
            sortBooks(books.map { book ->
                if (book.id == id) book.copy(progress = null, lastOpenedAt = null) else book
            })
        }
        persistLibrary()
    }

    suspend fun setBookCover(id: String, uri: Uri) = withContext(Dispatchers.IO) {
        val book = _library.value.firstOrNull { it.id == id }
            ?: throw EpubException("本棚にこの本がありません")
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(32 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_COVER_BYTES) throw EpubException("画像が大きすぎます")
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: throw EpubException("画像を開けませんでした")
        val coverName = "cover-${System.currentTimeMillis()}.jpg"
        val wrote = CoverGenerator.writeCover(
            original = bytes,
            title = book.title,
            author = book.author,
            outFile = File(bookDir(id), coverName),
        )
        if (!wrote) throw EpubException("表紙を設定できませんでした")
        _library.update { books ->
            books.map { if (it.id == id) it.copy(coverFile = coverName) else it }
        }
        persistLibrary()
    }

    suspend fun resetBookCover(id: String) = withContext(Dispatchers.IO) {
        val book = _library.value.firstOrNull { it.id == id }
            ?: throw EpubException("本棚にこの本がありません")
        val coverName = "cover-${System.currentTimeMillis()}.jpg"
        CoverGenerator.writeCover(
            original = null,
            title = book.title,
            author = book.author,
            outFile = File(bookDir(id), coverName),
        )
        _library.update { books ->
            books.map { if (it.id == id) it.copy(coverFile = coverName) else it }
        }
        persistLibrary()
    }

    suspend fun exportBook(id: String, uri: Uri) = withContext(Dispatchers.IO) {
        val book = _library.value.firstOrNull { it.id == id }
            ?: throw EpubException("本棚にこの本がありません")
        val source = File(bookDir(id), book.sourceFile)
        if (!source.isFile) throw EpubException("書き出せるファイルがありません")
        val output = context.contentResolver.openOutputStream(uri)
            ?: throw EpubException("ファイルを開けませんでした")
        output.use { target ->
            source.inputStream().use { input -> input.copyTo(target, 128 * 1024) }
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

    fun updateImportOptions(importOptions: ImportOptions) {
        _settings.update { it.copy(importOptions = importOptions) }
        scope.launch { persistSettings() }
    }

    fun updateLibrarySort(sort: LibrarySort) {
        if (_settings.value.librarySort == sort) return
        _settings.update { it.copy(librarySort = sort) }
        scope.launch { persistSettings() }
    }

    fun savePreset(name: String, reader: ReaderSettings) {
        val cleanName = name.trim().take(24)
        if (cleanName.isEmpty()) return
        _settings.update { current ->
            val existing = current.presets.firstOrNull { it.name == cleanName }
            val preset = ReaderPreset(
                id = existing?.id ?: UUID.randomUUID().toString(),
                name = cleanName,
                settings = reader.normalized(),
            )
            current.copy(
                presets = if (existing == null) {
                    current.presets + preset
                } else {
                    current.presets.map { if (it.id == existing.id) preset else it }
                },
            )
        }
        scope.launch { persistSettings() }
    }

    fun createBackup(): BackupFile = BackupFile(
        version = BACKUP_VERSION,
        exportedAt = System.currentTimeMillis(),
        presets = _settings.value.presets,
        books = _library.value.mapNotNull { book ->
            book.progress?.let { progress ->
                BackupBookProgress(
                    bookId = book.id,
                    title = book.title,
                    author = book.author,
                    charCount = book.charCount,
                    lastOpenedAt = book.lastOpenedAt,
                    progress = progress,
                )
            }
        },
        stats = _stats.value,
    )

    suspend fun importBackup(backup: BackupFile): BackupImportResult {
        if (backup.format.isNotBlank() && backup.format != "tateyomi-backup") {
            throw EpubException("縦読みのバックアップファイルではありません")
        }
        if (backup.version > BACKUP_VERSION) {
            throw EpubException("このバックアップは新しい形式のため読み込めません")
        }
        val mergedPresets = mergePresetsByName(_settings.value.presets, backup.presets)
        _settings.update { it.copy(presets = mergedPresets) }
        persistSettings()

        var progressBookCount = 0
        _library.update { books ->
            sortBooks(books.map { book ->
                val entry = findBackupProgress(book, backup.books) ?: return@map book
                val merged = mergeBookProgress(book.progress, entry.progress)
                val importedLast = entry.lastOpenedAt ?: entry.progress?.updatedAt ?: 0L
                val lastOpenedAt = maxOf(book.lastOpenedAt ?: 0L, importedLast)
                    .takeIf { it > 0L }
                if (merged != book.progress || lastOpenedAt != book.lastOpenedAt) {
                    progressBookCount++
                }
                book.copy(progress = merged, lastOpenedAt = lastOpenedAt)
            })
        }
        if (progressBookCount > 0) persistLibrary()

        _stats.value = mergeReadingStats(_stats.value, backup.stats)
        persistStats()
        return BackupImportResult(
            presetCount = backup.presets.size,
            progressBookCount = progressBookCount,
            statsBookCount = backup.stats.books.size,
        )
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
        writeJson(libraryFile, LibraryFile.serializer()) {
            LibraryFile(books = _library.value, folders = _folders.value)
        }
    }

    private suspend fun persistSettings() {
        writeJson(settingsFile, AppSettings.serializer()) { _settings.value }
    }

    private suspend fun persistStats() {
        writeJson(statsFile, ReadingStats.serializer()) { _stats.value }
    }

    @OptIn(ExperimentalSerializationApi::class)
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
                    temporary.outputStream().buffered().use { output ->
                        json.encodeToStream(serializer, payload, output)
                    }
                    if (!temporary.renameTo(file)) {
                        temporary.inputStream().buffered().use { input ->
                            file.outputStream().buffered().use { output ->
                                input.copyTo(output, 128 * 1024)
                            }
                        }
                        temporary.delete()
                    }
                } catch (e: Exception) {
                    Log.w("Tateyomi", "Failed to write ${file.name}", e)
                }
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun <T> readJson(
        file: File,
        serializer: KSerializer<T>,
        format: Json = json,
    ): T? {
        if (!file.isFile) return null
        return try {
            file.inputStream().buffered().use { input ->
                format.decodeFromStream(serializer, input)
            }
        } catch (e: Exception) {
            Log.w("Tateyomi", "Failed to read ${file.name}", e)
            null
        }
    }

    private fun copyUri(uri: Uri, target: File) {
        target.parentFile?.mkdirs()
        val input = try {
            context.contentResolver.openInputStream(uri)
        } catch (_: Exception) {
            null
        } ?: throw EpubException("ファイルを開けませんでした")
        try {
            input.use { source ->
                target.outputStream().buffered().use { output ->
                    source.copyTo(output, 128 * 1024)
                }
            }
        } catch (e: EpubException) {
            throw e
        } catch (_: Exception) {
            throw EpubException("ファイルを読み込めませんでした")
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
