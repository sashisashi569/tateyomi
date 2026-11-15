package app.tateyomi.data

import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One portable JSON file containing presets and reading statistics. */
@Serializable
data class BackupFile(
    val format: String = "tateyomi-backup",
    val version: Int = 1,
    val exportedAt: Long = 0L,
    val presets: List<ReaderPreset> = emptyList(),
    val stats: ReadingStats = ReadingStats(),
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
    val statsBookCount: Int,
)
