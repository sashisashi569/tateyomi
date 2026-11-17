package app.tateyomi

import app.tateyomi.data.ImageBlock
import app.tateyomi.data.KindleParser
import app.tateyomi.data.TextBlock
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Development-only smoke test over local MOBI / AZW3 samples.
 *
 * Run with:
 *   TATEYOMI_KINDLE_DIR=~/kindle-samples gradle testDebugUnitTest --tests '*KindleCollectionTest*'
 */
class KindleCollectionTest {

    @Test
    fun parseCorpus() {
        val rootPath = System.getenv("TATEYOMI_KINDLE_DIR") ?: return
        val root = File(rootPath)
        assumeTrue(root.isDirectory)
        val files = root.walkTopDown()
            .filter {
                it.isFile && it.extension.lowercase() in setOf("mobi", "azw", "azw3", "prc")
            }
            .sortedBy { it.path }
            .toList()
        assumeTrue(files.isNotEmpty())

        val failures = ArrayList<String>()
        val report = StringBuilder()
        files.forEach { file ->
            val started = System.nanoTime()
            val result = runCatching { KindleParser.parse(file) }
            val ms = (System.nanoTime() - started) / 1_000_000
            result.fold(
                onSuccess = { book ->
                    val text = book.chapters.sumOf { chapter ->
                        chapter.blocks.count { it is TextBlock }
                    }
                    val images = book.chapters.sumOf { chapter ->
                        chapter.blocks.count { it is ImageBlock }
                    }
                    report.appendLine(
                        "OK   ${file.name} | ${ms}ms | ${book.format} | " +
                            "${book.chapters.size}ch ${book.charCount}chars | " +
                            "text=$text img=$images imgs=${book.images.size} | " +
                            "${book.title} / ${book.author}",
                    )
                },
                onFailure = { error ->
                    failures += file.name
                    report.appendLine(
                        "FAIL ${file.name} | ${ms}ms | ${error.javaClass.simpleName}: ${error.message}",
                    )
                },
            )
        }
        report.appendLine("---- ${files.size - failures.size}/${files.size} parsed")
        println(report)
        check(failures.isEmpty()) { "Failed to parse ${failures.size} Kindle file(s)\n$report" }
    }
}
