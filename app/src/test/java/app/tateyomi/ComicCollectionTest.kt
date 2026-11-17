package app.tateyomi

import app.tateyomi.data.BookFormat
import app.tateyomi.data.ComicArchiveParser
import app.tateyomi.data.ImageBlock
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Development-only smoke test over local CBZ / CBR samples.
 *
 * Run with:
 *   TATEYOMI_COMIC_DIR=~/comic-samples gradle testDebugUnitTest --tests '*ComicCollectionTest*'
 */
class ComicCollectionTest {

    @Test
    fun parseCorpus() {
        val rootPath = System.getenv("TATEYOMI_COMIC_DIR") ?: return
        val root = File(rootPath)
        assumeTrue(root.isDirectory)
        val files = root.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in setOf("cbz", "cbr", "zip", "rar") }
            .sortedBy { it.path }
            .toList()
        assumeTrue(files.isNotEmpty())

        val failures = ArrayList<String>()
        val report = StringBuilder()
        files.forEach { file ->
            val output = File(root, "out-${file.nameWithoutExtension}")
            output.deleteRecursively()
            output.mkdirs()
            val started = System.nanoTime()
            val result = runCatching {
                when (file.extension.lowercase()) {
                    "cbz", "zip" -> ComicArchiveParser.parseZip(file, output, BookFormat.CBZ)
                    else -> ComicArchiveParser.parseRar(file, output, BookFormat.CBR)
                }
            }
            val ms = (System.nanoTime() - started) / 1_000_000
            result.fold(
                onSuccess = { book ->
                    val images = book.chapters.sumOf { chapter ->
                        chapter.blocks.count { it is ImageBlock }
                    }
                    report.appendLine(
                        "OK   ${file.name} | ${ms}ms | ${book.chapters.size}ch " +
                            "pages=$images files=${book.imageFiles.size} | ${book.title} / ${book.author}",
                    )
                },
                onFailure = { error ->
                    failures += file.name
                    report.appendLine(
                        "FAIL ${file.name} | ${ms}ms | ${error.javaClass.simpleName}: ${error.message}",
                    )
                    report.appendLine(error.stackTraceToString())
                },
            )
            output.deleteRecursively()
        }
        report.appendLine("---- ${files.size - failures.size}/${files.size} parsed")
        println(report)
        check(failures.isEmpty()) { "Failed to parse ${failures.size} comic archive(s)\n$report" }
    }
}
