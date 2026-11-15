package app.tateyomi

import app.tateyomi.data.ReaderSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTest {

    @Test
    fun marginRangeIsZeroTo168() {
        assertEquals(0, ReaderSettings(marginDp = -1).normalized().marginDp)
        assertEquals(84, ReaderSettings(marginDp = 84).normalized().marginDp)
        assertEquals(168, ReaderSettings(marginDp = 168).normalized().marginDp)
        assertEquals(168, ReaderSettings(marginDp = 999).normalized().marginDp)
    }
}
