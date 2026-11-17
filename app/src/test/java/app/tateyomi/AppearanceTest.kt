package app.tateyomi

import app.tateyomi.data.AppSettings
import app.tateyomi.data.AppThemeMode
import app.tateyomi.data.AppearanceSettings
import app.tateyomi.data.LibrarySort
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppearanceTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Test
    fun defaultAppearanceFollowsSystemWithoutAmoled() {
        val appearance = AppSettings().appearance
        assertEquals(AppThemeMode.SYSTEM, appearance.themeMode)
        assertFalse(appearance.amoled)
    }

    @Test
    fun appearanceRoundTrips() {
        val settings = AppSettings(
            appearance = AppearanceSettings(
                themeMode = AppThemeMode.DARK,
                amoled = true,
            ),
        )
        val encoded = json.encodeToString(AppSettings.serializer(), settings)
        val decoded = json.decodeFromString(AppSettings.serializer(), encoded)
        assertEquals(settings.appearance, decoded.appearance)
    }

    @Test
    fun librarySortRoundTrips() {
        val settings = AppSettings(librarySort = LibrarySort.TITLE_DESC)
        val encoded = json.encodeToString(AppSettings.serializer(), settings)
        val decoded = json.decodeFromString(AppSettings.serializer(), encoded)
        assertEquals(LibrarySort.TITLE_DESC, decoded.librarySort)
    }
}
