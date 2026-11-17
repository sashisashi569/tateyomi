package app.tateyomi

import app.tateyomi.data.EpubParser
import app.tateyomi.data.ImageBlock
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Development-only regression test for image-heavy books.
 *
 * Run with:
 *   TATEYOMI_LARGE_EPUB=~/large-test.epub gradle testDebugUnitTest --tests '*LargeImportTest*'
 */
class LargeImportTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun parsesLargeEpubWithoutKeepingImagesInMemory() {
        val path = System.getenv("TATEYOMI_LARGE_EPUB") ?: return
        val file = File(path)
        assumeTrue(file.isFile)
        val imageDir = temporaryFolder.newFolder("large-images")

        val parsed = EpubParser.parse(file, imageDir = imageDir)

        assertEquals(0, parsed.images.size)
        val images = imageDir.listFiles().orEmpty()
        assertTrue("expected streamed images, got ${images.size}", images.size >= 20)
        assertTrue(parsed.chapters[0].blocks.count { it is ImageBlock } >= 20)
        println(
            "large import: ${file.length() / (1024 * 1024)}MB source, " +
                "${parsed.charCount} chars, ${images.size} images streamed",
        )
    }
}
