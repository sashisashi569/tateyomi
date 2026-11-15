package app.tateyomi

import app.tateyomi.data.BackupFile
import app.tateyomi.data.BackupJson
import app.tateyomi.data.BookStats
import app.tateyomi.data.ReaderPreset
import app.tateyomi.data.ReaderSettings
import app.tateyomi.data.ReadingStats
import app.tateyomi.data.mergePresetsByName
import app.tateyomi.data.mergeReadingStats
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupTest {

    @Test
    fun importedPresetOverwritesSameNameAndKeepsId() {
        val existing = ReaderPreset(
            id = "old-id",
            name = "通勤用",
            settings = ReaderSettings(charsPerLine = 30),
        )
        val imported = ReaderPreset(
            id = "new-id",
            name = "通勤用",
            settings = ReaderSettings(charsPerLine = 40),
        )
        val fresh = ReaderPreset(
            id = "night-id",
            name = "夜用",
            settings = ReaderSettings(theme = app.tateyomi.data.ReaderTheme.DARK),
        )

        val merged = mergePresetsByName(listOf(existing), listOf(imported, fresh))

        assertEquals(2, merged.size)
        assertEquals("old-id", merged[0].id)
        assertEquals(40, merged[0].settings.charsPerLine)
        assertEquals("夜用", merged[1].name)
    }

    @Test
    fun duplicateNamesInsideImportAreCollapsed() {
        val first = ReaderPreset("1", "同じ名前", ReaderSettings(charsPerLine = 20))
        val second = ReaderPreset("2", "同じ名前", ReaderSettings(charsPerLine = 50))

        val merged = mergePresetsByName(emptyList(), listOf(first, second))

        assertEquals(1, merged.size)
        assertEquals(50, merged[0].settings.charsPerLine)
    }

    @Test
    fun readingStatsMergeUsesMonotonicMaximum() {
        val current = ReadingStats(
            totalSeconds = 100,
            totalChars = 1_000,
            dailySeconds = mapOf("2026-01-01" to 50),
            books = mapOf("a" to BookStats(seconds = 30, lastReadAt = 10)),
        )
        val imported = ReadingStats(
            totalSeconds = 80,
            totalChars = 2_000,
            dailySeconds = mapOf("2026-01-01" to 40, "2026-01-02" to 20),
            books = mapOf(
                "a" to BookStats(seconds = 60, lastReadAt = 20),
                "b" to BookStats(seconds = 10),
            ),
        )

        val merged = mergeReadingStats(current, imported)

        assertEquals(100L, merged.totalSeconds)
        assertEquals(2_000L, merged.totalChars)
        assertEquals(50L, merged.dailySeconds["2026-01-01"])
        assertEquals(20L, merged.dailySeconds["2026-01-02"])
        assertEquals(60L, merged.books.getValue("a").seconds)
        assertEquals(20L, merged.books.getValue("a").lastReadAt)
        assertEquals(10L, merged.books.getValue("b").seconds)
    }

    @Test
    fun backupJsonRoundTrips() {
        val backup = BackupFile(
            exportedAt = 123L,
            presets = listOf(
                ReaderPreset("p1", "通勤", ReaderSettings(charsPerLine = 36)),
            ),
            stats = ReadingStats(totalSeconds = 42),
        )

        val text = BackupJson.encodeToString(BackupFile.serializer(), backup)
        val decoded = BackupJson.decodeFromString(BackupFile.serializer(), text)

        assertEquals(backup, decoded)
    }
}
