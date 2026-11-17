package app.tateyomi

import app.tateyomi.data.ArchiveFolderMode
import app.tateyomi.data.ImportOptions
import app.tateyomi.data.ReaderSettings
import org.junit.Assert.assertEquals
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
}
