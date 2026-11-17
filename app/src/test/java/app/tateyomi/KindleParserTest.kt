package app.tateyomi

import app.tateyomi.data.BookFormat
import app.tateyomi.data.KindleParser
import app.tateyomi.data.TextBlock
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KindleParserTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val pixelPng: ByteArray = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAFgwJ/lK3Q6wAAAABJRU5ErkJggg==",
    )

    @Test
    fun parsesMobi7TextMetadataAndImage() {
        val file = writeMobi()
        val parsed = KindleParser.parse(file)

        assertEquals("Kindleテスト", parsed.title)
        assertEquals("テスト著者", parsed.author)
        assertEquals(BookFormat.MOBI, parsed.format)
        assertEquals(1, parsed.chapters.size)
        val text = parsed.chapters[0].blocks.filterIsInstance<TextBlock>()
            .joinToString("\n") { it.text }
        assertTrue(text.contains("こんにちは。"))
        assertTrue(parsed.chapters[0].blocks.any { it is app.tateyomi.data.ImageBlock })
        assertTrue(parsed.images.isNotEmpty())
        assertNotNull(parsed.coverBytes)
        assertTrue(parsed.toc.any { it.title == "第一章" })
    }

    @Test
    fun streamsMobiImagesToDirectory() {
        val file = writeMobi()
        val imageDir = temporaryFolder.newFolder("kindle-images")
        val parsed = KindleParser.parse(file, imageDir = imageDir)

        assertEquals(0, parsed.images.size)
        assertTrue(parsed.imageFiles.isNotEmpty())
        assertTrue(imageDir.listFiles().orEmpty().any { it.isFile })
        assertTrue(parsed.chapters[0].blocks.any { it is app.tateyomi.data.ImageBlock })
        assertNotNull(parsed.coverBytes)
    }

    @Test
    fun parsesKf8TextWithEmbeddedImage() {
        val html = """
            <html><body>
              <h1>KF8の章</h1>
              <p>KF8の本文です。</p>
              <p><img src="kindle:embed:00001?mime=image/png" alt="挿絵"/></p>
            </body></html>
        """.trimIndent().toByteArray(Charsets.UTF_8)
        val file = writeMobi(version = 8, html = html, headerLength = 0x100)
        val parsed = KindleParser.parse(file)

        assertEquals(BookFormat.AZW3, parsed.format)
        assertEquals("Kindleテスト", parsed.title)
        val text = parsed.chapters.flatMap { it.blocks }.filterIsInstance<TextBlock>()
            .joinToString("\n") { it.text }
        assertTrue(text.contains("KF8の本文です。"))
        assertTrue(parsed.chapters.any { chapter ->
            chapter.blocks.any { it is app.tateyomi.data.ImageBlock }
        })
        assertTrue(parsed.images.isNotEmpty())
        assertNotNull(parsed.coverBytes)
    }

    private fun writeMobi(
        version: Int = 6,
        html: ByteArray = """
            <html><body>
              <h1>第一章</h1>
              <p>こんにちは。</p>
              <p><img recindex="00001" alt="挿絵"/></p>
            </body></html>
        """.trimIndent().toByteArray(Charsets.UTF_8),
        headerLength: Int = 0xE4,
    ): File {
        val exthRecords = ByteArrayOutputStream()
        fun exth(id: Int, value: ByteArray) {
            exthRecords.write(int32(id))
            exthRecords.write(int32(8 + value.size))
            exthRecords.write(value)
        }
        exth(100, "テスト著者".toByteArray(Charsets.UTF_8))
        exth(503, "Kindleテスト".toByteArray(Charsets.UTF_8))
        exth(201, int32(0))
        exth(524, "ja".toByteArray(Charsets.UTF_8))
        val exthBody = exthRecords.toByteArray()
        val exthLength = 12 + exthBody.size
        val padding = (4 - (exthLength % 4)) % 4

        val record0 = ByteArray(16 + headerLength + exthLength + padding)
        record0[0] = 0
        record0[1] = 1 // no compression
        writeI32(record0, 4, html.size)
        writeU16(record0, 8, 1)
        writeU16(record0, 10, 4096)
        record0[16] = 'M'.code.toByte()
        record0[17] = 'O'.code.toByte()
        record0[18] = 'B'.code.toByte()
        record0[19] = 'I'.code.toByte()
        writeI32(record0, 20, headerLength)
        writeI32(record0, 24, 2)
        writeI32(record0, 28, 65001)
        writeI32(record0, 36, version)
        writeI32(record0, 0x50, 2)
        writeI32(record0, 0x54, 0)
        writeI32(record0, 0x58, 0)
        writeI32(record0, 0x6C, 2)
        writeI32(record0, 0x70, -1)
        writeI32(record0, 0x74, 0)
        writeI32(record0, 0x80, 0x40)
        writeU16(record0, 0xC0, 1)
        writeU16(record0, 0xC2, 1)
        writeU16(record0, 0xF2, 0)
        writeI32(record0, 0xC0, -1)
        writeI32(record0, 0xC4, 0)
        writeI32(record0, 0xF8, -1)
        writeI32(record0, 0xFC, -1)

        val exthOffset = 16 + headerLength
        "EXTH".toByteArray(Charsets.ISO_8859_1).copyInto(record0, exthOffset)
        writeI32(record0, exthOffset + 4, exthLength)
        writeI32(record0, exthOffset + 8, 4)
        exthBody.copyInto(record0, exthOffset + 12)

        val records = listOf(record0, html, pixelPng)
        val file = temporaryFolder.newFile("book.mobi")
        file.outputStream().buffered().use { out ->
            val header = ByteArray(78)
            "MOBIテスト".toByteArray(Charsets.UTF_8).copyInto(header, 0, 0, minOf(8, 32))
            header[60] = 'B'.code.toByte()
            header[61] = 'O'.code.toByte()
            header[62] = 'O'.code.toByte()
            header[63] = 'K'.code.toByte()
            header[64] = 'M'.code.toByte()
            header[65] = 'O'.code.toByte()
            header[66] = 'B'.code.toByte()
            header[67] = 'I'.code.toByte()
            writeU16(header, 76, records.size)
            out.write(header)

            var offset = 78 + records.size * 8 + 2
            records.forEachIndexed { index, record ->
                out.write(int32(offset))
                out.write(byteArrayOf(0, 0, 0, index.toByte()))
                offset += record.size
            }
            out.write(byteArrayOf(0, 0))
            records.forEach { out.write(it) }
        }
        return file
    }

    private fun int32(value: Int): ByteArray {
        val bytes = ByteArray(4)
        writeI32(bytes, 0, value)
        return bytes
    }

    private fun writeI32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }
}
