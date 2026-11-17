package app.tateyomi

import app.tateyomi.data.ArchiveExtractor
import app.tateyomi.data.ContainerFormat
import app.tateyomi.data.EpubException
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArchiveExtractorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun detectsAndExtractsBulkZip() {
        val zip = temporaryFolder.newFile("bulk.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            fun entry(name: String, text: String) {
                out.putNextEntry(ZipEntry(name))
                out.write(text.toByteArray())
                out.closeEntry()
            }
            entry("book1.epub", "one")
            entry("nested/book2.mobi", "two")
            entry("cover.txt", "not a book")
        }

        assertEquals(ContainerFormat.ZIP, ArchiveExtractor.detect(zip))
        assertTrue(ArchiveExtractor.zipHasBookEntries(zip))
        val destination = temporaryFolder.newFolder("zip-out")
        ArchiveExtractor.extract(zip, ContainerFormat.ZIP, destination)

        val books = ArchiveExtractor.findBookFiles(destination)
        assertEquals(listOf("book1.epub", "nested/book2.mobi"), books.map { it.relativeTo(destination).path })
    }

    @Test
    fun imageZipIsNotBulk() {
        val zip = temporaryFolder.newFile("comic.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            fun entry(name: String) {
                out.putNextEntry(ZipEntry(name))
                out.write(byteArrayOf(1, 2, 3))
                out.closeEntry()
            }
            entry("page1.jpg")
            entry("page2.jpg")
        }

        assertFalse(ArchiveExtractor.zipHasBookEntries(zip))
    }

    @Test
    fun detectsAndExtractsSevenZ() {
        val archive = temporaryFolder.newFile("bulk.7z")
        SevenZOutputFile(archive).use { out ->
            fun entry(name: String, text: String) {
                val entry = out.createArchiveEntry(File(name), name)
                out.putArchiveEntry(entry)
                out.write(text.toByteArray())
                out.closeArchiveEntry()
            }
            entry("book.azw3", "book")
            entry("page.jpg", "image")
        }

        assertEquals(ContainerFormat.SEVEN_Z, ArchiveExtractor.detect(archive))
        val destination = temporaryFolder.newFolder("seven-out")
        ArchiveExtractor.extract(archive, ContainerFormat.SEVEN_Z, destination)

        assertEquals(1, ArchiveExtractor.findBookFiles(destination).size)
        assertEquals(1, ArchiveExtractor.findImageFiles(destination).size)
    }

    @Test
    fun rejectsZipSlipPaths() {
        val zip = temporaryFolder.newFile("slip.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            out.putNextEntry(ZipEntry("../escape.txt"))
            out.write("bad".toByteArray())
            out.closeEntry()
        }
        val destination = temporaryFolder.newFolder("slip-out")
        var threw = false
        try {
            ArchiveExtractor.extract(zip, ContainerFormat.ZIP, destination)
        } catch (_: EpubException) {
            threw = true
        }
        assertTrue(threw)
    }
}
