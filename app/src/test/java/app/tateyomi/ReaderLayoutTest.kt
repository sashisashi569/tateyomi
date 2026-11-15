package app.tateyomi

import app.tateyomi.data.BlockStyle
import app.tateyomi.data.BookContent
import app.tateyomi.data.Chapter
import app.tateyomi.data.ImageBlock
import app.tateyomi.data.ReaderSettings
import app.tateyomi.data.ReadingProgress
import app.tateyomi.data.RubySpan
import app.tateyomi.data.TextBlock
import app.tateyomi.reader.PageContent
import app.tateyomi.reader.ReaderLayoutEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderLayoutTest {

    private val settings = ReaderSettings(
        vertical = true,
        charsPerLine = 20,
        linesPerPage = 5,
        indent = 1,
        marginDp = 16,
    )

    @Test
    fun indentReducesFirstLineCapacity() {
        val spans = ReaderLayoutEngine.splitIntoLines("あ".repeat(10), 9)
        assertEquals(2, spans.size)
        assertEquals(9, spans[0].end - spans[0].start)
        assertEquals(1, spans[1].end - spans[1].start)
    }

    @Test
    fun paginatesTextAndChapters() {
        val content = BookContent(
            chapters = listOf(
                chapter("第一章", "あ".repeat(100)),
                chapter("第二章", "い".repeat(50)),
            ),
        )
        val layout = ReaderLayoutEngine.compute(content, settings)
        assertTrue(layout.totalPages >= 3)
        assertEquals(0, layout.chapterStartPage(0))
        assertTrue(layout.chapterStartPage(1) > 0)
        assertEquals(1f, layout.progressFor(layout.totalPages - 1).overallFraction)
        assertEquals(layout.totalPages - 1, layout.pageFor(ReadingProgress(overallFraction = 1f)))
    }

    @Test
    fun pageAndProgressRoundTrip() {
        val content = BookContent(
            chapters = listOf(
                chapter("A", "あ".repeat(300)),
                chapter("B", "い".repeat(150)),
            ),
        )
        val layout = ReaderLayoutEngine.compute(content, settings)
        for (page in 0 until layout.totalPages) {
            val progress = layout.progressFor(page)
            val restored = layout.pageFor(progress)
            assertEquals("round trip page $page", page, restored)
        }
    }

    @Test
    fun imagePagesAreKeepAndCausePageBreak() {
        val content = BookContent(
            chapters = listOf(
                Chapter(
                    title = "絵",
                    blocks = listOf(
                        TextBlock("前の文章です。"),
                        ImageBlock("a.jpg"),
                        TextBlock("後の文章です。"),
                    ),
                    charCount = 12,
                ),
            ),
        )
        val layout = ReaderLayoutEngine.compute(content, settings)
        assertEquals(3, layout.totalPages)
        assertTrue(layout.pageContent(0) is PageContent.Text)
        assertTrue(layout.pageContent(1) is PageContent.Image)
        assertTrue(layout.pageContent(2) is PageContent.Text)
    }

    @Test
    fun imageOnlyBookUsesPageProgress() {
        val content = BookContent(
            chapters = listOf(
                Chapter("絵1", listOf(ImageBlock("a.jpg")), 0),
                Chapter("絵2", listOf(ImageBlock("b.jpg")), 0),
            ),
        )
        val layout = ReaderLayoutEngine.compute(content, settings)
        assertEquals(2, layout.totalPages)
        assertEquals(0.5f, layout.progressFor(0).overallFraction)
        assertEquals(1f, layout.progressFor(1).overallFraction)
        assertEquals(1, layout.pageFor(ReadingProgress(overallFraction = 1f)))
    }

    @Test
    fun kinsokuKeepsClosingPunctuationOffLineStart() {
        val text = "あああああ。いいいい"
        val spans = ReaderLayoutEngine.splitIntoLines(text, 5)
        assertTrue(spans.size >= 2)
        spans.drop(1).forEach { span ->
            val first = text[span.start]
            assertFalse("line starts with $first", first == '。' || first == '、')
        }
    }

    @Test
    fun kinsokuKeepsOpeningBracketOffLineEnd() {
        val text = "ああああ「いいいい"
        val spans = ReaderLayoutEngine.splitIntoLines(text, 5)
        spans.dropLast(1).forEach { span ->
            val last = text[span.end - 1]
            assertFalse("line ends with $last", last == '「')
        }
    }

    @Test
    fun latinWordsBreakAtSpacesWhenPossible() {
        val spans = ReaderLayoutEngine.splitIntoLines("hello wonderful world", 10)
        assertEquals("hello", "hello wonderful world".substring(spans[0].start, spans[0].end))
    }

    @Test
    fun rubyBaseIsKeptTogetherWhenItFits() {
        val text = "あいうえおかきく"
        val ruby = listOf(RubySpan(2, 7, "うえおかき"))
        val spans = ReaderLayoutEngine.splitIntoLines(text, 5, ruby)
        assertFalse(spans.any { span -> span.start < 7 && span.end > 2 && span.start != 2 && span.end != 7 })
        assertEquals(2, spans[1].start)
    }

    @Test
    fun headingGetsBlankLineWhenFollowingTextInHorizontalMode() {
        val content = BookContent(
            chapters = listOf(
                Chapter(
                    title = "章",
                    blocks = listOf(
                        TextBlock("本文です。"),
                        TextBlock("見出し", style = BlockStyle.HEADING, indent = false, centered = true),
                        TextBlock("続きです。"),
                    ),
                    charCount = 15,
                ),
            ),
        )
        val layout = ReaderLayoutEngine.compute(content, settings.copy(vertical = false))
        val firstPage = layout.pageContent(0) as PageContent.Text
        assertTrue(firstPage.lines.any { it.blank })
    }

    @Test
    fun tocEntryWithBlockAnchorJumpsIntoChapter() {
        val content = BookContent(
            chapters = listOf(
                Chapter(
                    title = "章",
                    blocks = listOf(
                        TextBlock("あ".repeat(120)),
                        TextBlock("見出し", style = BlockStyle.HEADING, indent = false, centered = true, anchor = "h"),
                        TextBlock("い".repeat(50)),
                    ),
                    charCount = 223,
                ),
            ),
        )
        val layout = ReaderLayoutEngine.compute(content, settings)
        val target = layout.pageForBlock(0, 1)
        assertTrue(target > 0)
        assertEquals(
            target,
            layout.pageForToc(app.tateyomi.data.TocEntry("見出し", 0, 0, 1)),
        )
        assertEquals(0, layout.pageForToc(app.tateyomi.data.TocEntry("章", 0, 0, null)))
    }

    @Test
    fun lineSpacingIsClamped() {
        assertEquals(
            1.0f,
            ReaderSettings(lineSpacing = 0.5f).normalized().lineSpacing,
            0.0001f,
        )
        assertEquals(
            2.5f,
            ReaderSettings(lineSpacing = 4.0f).normalized().lineSpacing,
            0.0001f,
        )
    }

    private fun chapter(title: String, text: String): Chapter = Chapter(
        title = title,
        blocks = listOf(TextBlock(text)),
        charCount = text.length,
    )
}
