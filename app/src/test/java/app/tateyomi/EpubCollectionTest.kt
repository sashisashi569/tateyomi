package app.tateyomi

import app.tateyomi.data.BlockStyle
import app.tateyomi.data.EpubParser
import app.tateyomi.data.ImageBlock
import app.tateyomi.data.TextBlock
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Development-only smoke test over a local EPUB corpus.
 *
 * Run with:
 *   TATEYOMI_EPUB_DIR=~/epub-collection gradle testDebugUnitTest --tests '*EpubCollectionTest*'
 *
 * The test is skipped when the corpus is not present, so normal builds do not
 * depend on it.
 */
class EpubCollectionTest {

    @Test
    fun parseCorpus() {
        val rootPath = System.getenv("TATEYOMI_EPUB_DIR") ?: return
        val root = File(rootPath)
        assumeTrue(root.isDirectory)
        val files = root.walkTopDown()
            .filter { it.isFile && it.extension.equals("epub", true) }
            .sortedBy { it.path }
            .toList()
        assumeTrue(files.isNotEmpty())

        var failures = 0
        val report = StringBuilder()
        files.forEach { file ->
            val started = System.nanoTime()
            val result = runCatching { EpubParser.parse(file) }
            val ms = (System.nanoTime() - started) / 1_000_000
            result.fold(
                onSuccess = { book ->
                    val textBlocks = book.chapters.sumOf { chapter ->
                        chapter.blocks.count { it is TextBlock }
                    }
                    val imageBlocks = book.chapters.sumOf { chapter ->
                        chapter.blocks.count { it is ImageBlock }
                    }
                    val emptyChapters = book.chapters.count { it.charCount == 0 }
                    val headings = book.chapters.sumOf { chapter ->
                        chapter.blocks.filterIsInstance<TextBlock>()
                            .count { it.style == BlockStyle.HEADING || it.style == BlockStyle.SUBHEADING }
                    }
                    val ruby = book.chapters.sumOf { chapter ->
                        chapter.blocks.filterIsInstance<TextBlock>().sumOf { it.ruby.size }
                    }
                    report.appendLine(
                        "OK   ${file.name} | ${ms}ms | ${book.chapters.size}ch " +
                            "${book.charCount}chars | text=$textBlocks img=$imageBlocks " +
                            "head=$headings ruby=$ruby empty=$emptyChapters " +
                            "cover=${book.coverBytes?.size ?: 0} imgs=${book.images.size} " +
                            "toc=${book.toc.size} | ${book.title} / ${book.author}"
                    )
                },
                onFailure = { error ->
                    failures++
                    report.appendLine("FAIL ${file.name} | ${ms}ms | ${error.javaClass.simpleName}: ${error.message}")
                },
            )
        }
        report.appendLine("---- ${files.size - failures}/${files.size} parsed")
        println(report)
        check(failures == 0) { "Failed to parse $failures EPUB(s)\n$report" }
    }
}
