package app.tateyomi

import app.tateyomi.data.BlockStyle
import app.tateyomi.data.TextBlock
import app.tateyomi.data.XhtmlParser
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class XhtmlParserTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val pixelPng: ByteArray = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAFgwJ/lK3Q6wAAAABJRU5ErkJggg==",
    )

    @Test
    fun parsesAozoraStyleXhtmlMetadataAndRuby() {
        val file = temporaryFolder.newFile("aozora.xhtml")
        file.writeText(
            """
            <html>
              <head>
                <title>吾輩は猫である</title>
                <meta name="DC.creator" content="夏目漱石"/>
              </head>
              <body>
                <h1 class="jtitle">吾輩は猫である</h1>
                <p class="author">夏目漱石</p>
                <p>吾輩は<ruby><rb>猫</rb><rt>ねこ</rt></ruby>である。</p>
              </body>
            </html>
            """.trimIndent(),
        )

        val parsed = XhtmlParser.parse(file)

        assertEquals("吾輩は猫である", parsed.title)
        assertEquals("夏目漱石", parsed.author)
        assertEquals(1, parsed.chapters.size)
        val blocks = parsed.chapters[0].blocks.filterIsInstance<TextBlock>()
        assertTrue(blocks.any { it.style == BlockStyle.HEADING })
        assertTrue(blocks.any { block -> block.text.contains("吾輩は猫である。") })
        assertTrue(blocks.any { block -> block.ruby.any { it.text == "ねこ" } })
    }

    @Test
    fun parsesXhtmlWithImagesInsideZip() {
        val zipFile = temporaryFolder.newFile("aozora.zip")
        ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
            fun entry(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            entry(
                "book.xhtml",
                """
                <html><head><title>挿絵つき</title></head><body>
                  <h1>第一話</h1>
                  <p>本文です。</p>
                  <p><img src="images/pic.png" alt="挿絵"/></p>
                </body></html>
                """.trimIndent().toByteArray(),
            )
            entry("images/pic.png", pixelPng)
        }

        val parsed = ZipFile(zipFile).use { zip ->
            XhtmlParser.parseArchive(zip, "挿絵つき")
        }

        assertEquals("挿絵つき", parsed.title)
        assertEquals(1, parsed.chapters.size)
        assertTrue(parsed.chapters[0].blocks.any { it is app.tateyomi.data.ImageBlock })
        assertEquals(1, parsed.images.size)
    }

    @Test
    fun streamsArchiveImagesToDirectory() {
        val zipFile = temporaryFolder.newFile("aozora-stream.zip")
        ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
            fun entry(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            entry(
                "book.xhtml",
                """
                <html><head><title>画像ストリーム</title></head><body>
                  <p><img src="images/pic.png" alt="挿絵"/></p>
                </body></html>
                """.trimIndent().toByteArray(),
            )
            entry("images/pic.png", pixelPng)
        }
        val imageDir = temporaryFolder.newFolder("xhtml-images")

        val parsed = ZipFile(zipFile).use { zip ->
            XhtmlParser.parseArchive(zip, "画像ストリーム", imageDir)
        }

        assertEquals(0, parsed.images.size)
        assertEquals(1, imageDir.listFiles().orEmpty().size)
        assertTrue(parsed.chapters[0].blocks.any { it is app.tateyomi.data.ImageBlock })
    }
}
