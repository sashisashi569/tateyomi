package app.tateyomi

import app.tateyomi.data.ArchiveFolderMode
import app.tateyomi.data.AppSettings
import app.tateyomi.data.FolderSortPrefs
import app.tateyomi.data.ImportOptions
import app.tateyomi.data.LibrarySort
import app.tateyomi.data.ReaderSettings
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {

    @Test
    fun bulkImportAsksForArchiveFolderByDefault() {
        assertEquals(ArchiveFolderMode.ASK, ImportOptions().archiveFolderMode)
    }

    @Test
    fun marginRangeIsZeroTo168() {
        assertEquals(0, ReaderSettings(marginDp = -1).normalized().marginDp)
        assertEquals(84, ReaderSettings(marginDp = 84).normalized().marginDp)
        assertEquals(168, ReaderSettings(marginDp = 168).normalized().marginDp)
        assertEquals(168, ReaderSettings(marginDp = 999).normalized().marginDp)
    }

    @Test
    fun rubyScaleDefaultsAndClamps() {
        assertEquals(0.5f, ReaderSettings().rubyScale, 0.0001f)
        assertEquals(0.3f, ReaderSettings(rubyScale = 0.1f).normalized().rubyScale, 0.0001f)
        assertEquals(0.8f, ReaderSettings(rubyScale = 9f).normalized().rubyScale, 0.0001f)
        assertEquals(0.55f, ReaderSettings(rubyScale = 0.554f).normalized().rubyScale, 0.0001f)
    }

    @Test
    fun coverIsSkippedByDefault() {
        assertTrue(ReaderSettings().skipCover)
    }

    @Test
    fun folderSortPrefsDefaultToTheWholeLibraryDefaults() {
        val prefs = FolderSortPrefs()
        assertEquals(LibrarySort.RECENT, prefs.sort)
        assertFalse(prefs.readBooksLast)
    }

    @Test
    fun appSettingsRoundTripsFolderSorts() {
        val settings = AppSettings(
            librarySort = LibrarySort.TITLE,
            librarySortReadLast = true,
            folderSorts = mapOf(
                "f1" to FolderSortPrefs(sort = LibrarySort.RANDOM, readBooksLast = true),
                UNFILED_KEY to FolderSortPrefs(sort = LibrarySort.AUTHOR),
            ),
        )
        val json = Json { encodeDefaults = false }
        val encoded = json.encodeToString(AppSettings.serializer(), settings)
        val decoded = json.decodeFromString(AppSettings.serializer(), encoded)
        assertEquals(settings, decoded)
        assertTrue("f1" in encoded)
    }

    @Test
    fun appSettingsDecodesLegacyJsonWithoutSortPrefs() {
        val json = Json { ignoreUnknownKeys = false }
        val decoded = json.decodeFromString(
            AppSettings.serializer(),
            """{"reader": {"vertical": false}}""",
        )
        assertEquals(LibrarySort.RECENT, decoded.librarySort)
        assertFalse(decoded.librarySortReadLast)
        assertTrue(decoded.folderSorts.isEmpty())
        assertFalse(decoded.reader.vertical)
    }

    private companion object {
        const val UNFILED_KEY = "__unfiled__"
    }
}
