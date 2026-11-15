package app.tateyomi

import app.tateyomi.data.FontMetadata
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FontMetadataTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun nonFontFileReturnsNull() {
        val file = temporaryFolder.newFile("not-a-font.ttf")
        file.writeText("this is not a font")
        assertNull(FontMetadata.familyName(file))
    }
}
