package app.tateyomi

import app.tateyomi.data.BookFormat
import app.tateyomi.data.ComicArchiveParser
import app.tateyomi.data.ImageBlock
import java.io.File
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ComicArchiveParserTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val pixelPng: ByteArray = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAFgwJ/lK3Q6wAAAABJRU5ErkJggg==",
    )

    @Test
    fun sortsPagesNaturallyAndReadsComicInfo() {
        val zipFile = temporaryFolder.newFile("comic.cbz")
        ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
            fun entry(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            entry(
                "ComicInfo.xml",
                """
                <ComicInfo>
                  <Series>テストシリーズ</Series>
                  <Number>3</Number>
                  <Writer>テスト著者</Writer>
                </ComicInfo>
                """.trimIndent().toByteArray(),
            )
            entry("page10.png", pixelPng)
            entry("page2.png", pixelPng)
            entry("page1.png", pixelPng)
            entry("__MACOSX/._page1.png", pixelPng)
        }

        val output = temporaryFolder.newFolder("images")
        val parsed = ComicArchiveParser.parseZip(zipFile, output, BookFormat.CBZ)

        assertEquals("テストシリーズ 3", parsed.title)
        assertEquals("テスト著者", parsed.author)
        assertEquals(1, parsed.chapters.size)
        val pages = parsed.chapters[0].blocks.filterIsInstance<ImageBlock>()
        assertEquals(3, pages.size)
        assertEquals("page1.png", pages[0].alt)
        assertEquals("page2.png", pages[1].alt)
        assertEquals("page10.png", pages[2].alt)
        assertEquals(3, parsed.imageFiles.size)
        assertTrue(parsed.imageFiles.keys.all { File(output, it).isFile })
        assertNotNull(parsed.coverBytes)
        assertEquals(null, parsed.coverImageName)
    }

    @Test
    fun naturalOrderKeepsNumericPartsInNumericOrder() {
        val names = listOf("page10.jpg", "page2.jpg", "page1.jpg", "cover.jpg")
        assertEquals(
            listOf("cover.jpg", "page1.jpg", "page2.jpg", "page10.jpg"),
            names.sortedWith(app.tateyomi.data.naturalOrder()),
        )
    }
}
