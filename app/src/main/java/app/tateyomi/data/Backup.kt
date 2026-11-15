package app.tateyomi.data

import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val BACKUP_VERSION = 2

/** One portable JSON file containing presets and reading statistics. */
@Serializable
data class BackupFile(
    val format: String = "tateyomi-backup",
    val version: Int = 1,
    val exportedAt: Long = 0L,
    val presets: List<ReaderPreset> = emptyList(),
    val books: List<BackupBookProgress> = emptyList(),
    val stats: ReadingStats = ReadingStats(),
)

/** Reading position of one library book, matched by id or title/author. */
@Serializable
data class BackupBookProgress(
    val bookId: String = "",
    val title: String = "",
    val author: String = "",
    val charCount: Int = 0,
    val lastOpenedAt: Long? = null,
    val progress: ReadingProgress? = null,
)

val BackupJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    prettyPrint = true
}

/**
 * Imported presets replace existing presets with the same name, and new names
 * are appended. Names are unique in the resulting list.
 */
fun mergePresetsByName(
    existing: List<ReaderPreset>,
    imported: List<ReaderPreset>,
): List<ReaderPreset> {
    val byName = LinkedHashMap<String, ReaderPreset>()
    existing.forEach { preset ->
        val name = preset.name.trim().take(24)
        if (name.isNotEmpty()) byName[name] = preset.copy(name = name)
    }
    imported.forEach { preset ->
        val name = preset.name.trim().take(24)
        if (name.isEmpty()) return@forEach
        val current = byName[name]
        byName[name] = preset.copy(
            id = current?.id ?: UUID.randomUUID().toString(),
            name = name,
            settings = preset.settings.normalized(),
        )
    }
    return byName.values.toList()
}

/** Counters are monotonic, so importing a backup never reduces current totals. */
fun mergeReadingStats(current: ReadingStats, imported: ReadingStats): ReadingStats =
    ReadingStats(
        version = maxOf(current.version, imported.version),
        totalSeconds = maxOf(current.totalSeconds, imported.totalSeconds),
        totalChars = maxOf(current.totalChars, imported.totalChars),
        totalPages = maxOf(current.totalPages, imported.totalPages),
        dailySeconds = mergeMaxMap(current.dailySeconds, imported.dailySeconds),
        dailyChars = mergeMaxMap(current.dailyChars, imported.dailyChars),
        books = (current.books.keys + imported.books.keys).associateWith { id ->
            val a = current.books[id] ?: BookStats()
            val b = imported.books[id] ?: BookStats()
            BookStats(
                seconds = maxOf(a.seconds, b.seconds),
                chars = maxOf(a.chars, b.chars),
                pages = maxOf(a.pages, b.pages),
                lastReadAt = maxOf(a.lastReadAt, b.lastReadAt),
            )
        },
    )

private fun mergeMaxMap(
    current: Map<String, Long>,
    imported: Map<String, Long>,
): Map<String, Long> =
    (current.keys + imported.keys).associateWith { key ->
        maxOf(current[key] ?: 0L, imported[key] ?: 0L)
    }

data class BackupImportResult(
    val presetCount: Int,
    val progressBookCount: Int,
    val statsBookCount: Int,
)

/** The most recent progress wins, so importing an old backup does not rewind. */
fun mergeBookProgress(
    current: ReadingProgress?,
    imported: ReadingProgress?,
): ReadingProgress? = when {
    imported == null -> current
    current == null -> imported
    imported.updatedAt >= current.updatedAt -> imported
    else -> current
}

/** Finds progress for a library book by stable id, then by title and author. */
fun findBackupProgress(
    book: BookSummary,
    entries: List<BackupBookProgress>,
): BackupBookProgress? {
    entries.firstOrNull { it.bookId.isNotBlank() && it.bookId == book.id }?.let { return it }
    val title = normalizeBookKey(book.title)
    val author = normalizeBookKey(book.author)
    return entries.firstOrNull {
        normalizeBookKey(it.title) == title && normalizeBookKey(it.author) == author
    }
}

private fun normalizeBookKey(value: String): String =
    value.trim().replace(Regex("[\\s\\u3000]+"), "")
