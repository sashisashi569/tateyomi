package app.tateyomi

import app.tateyomi.data.BuiltInFonts
import app.tateyomi.data.ManagedFont
import app.tateyomi.data.ReaderSettings
import app.tateyomi.data.fontDisplayName
import app.tateyomi.reader.resolveFontSpec
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FontsTest {

    @Test
    fun readerSettingsUseBuiltInMinchoByDefault() {
        assertEquals(BuiltInFonts.MINCHO_ID, ReaderSettings().fontId)
    }

    @Test
    fun customFontResolvesToFile() {
        val file = File("/tmp/tateyomi-test-font.ttf")
        val font = ManagedFont("custom:test", "Test", fileName = "test.ttf")
        val spec = resolveFontSpec("custom:test", listOf(font)) { file }
        assertEquals(file, spec.file)
        assertNull(spec.family)
    }

    @Test
    fun missingCustomFontFallsBackToSerif() {
        val spec = resolveFontSpec("custom:missing", emptyList()) { null }
        assertEquals("serif", spec.family)
        assertNull(spec.file)
    }

    @Test
    fun displayNamesUseBuiltInOrCustomName() {
        val custom = ManagedFont("custom:test", "追加フォント", fileName = "test.ttf")
        assertEquals("明朝", fontDisplayName(BuiltInFonts.MINCHO_ID, listOf(custom)))
        assertEquals("追加フォント", fontDisplayName("custom:test", listOf(custom)))
    }
}
