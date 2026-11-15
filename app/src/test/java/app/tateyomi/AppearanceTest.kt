package app.tateyomi

import app.tateyomi.data.AppSettings
import app.tateyomi.data.AppThemeMode
import app.tateyomi.data.AppearanceSettings
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
    fun settingsWithoutAppearanceLoadDefaults() {
        val decoded = json.decodeFromString(
            AppSettings.serializer(),
            """{"presets":[],"fonts":[]}""",
        )
        assertEquals(AppearanceSettings(), decoded.appearance)
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
}
