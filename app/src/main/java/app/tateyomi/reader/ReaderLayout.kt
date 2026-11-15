package app.tateyomi.reader

import app.tateyomi.data.BlockStyle
import app.tateyomi.data.BookContent
import app.tateyomi.data.Chapter
import app.tateyomi.data.ImageBlock
import app.tateyomi.data.ReaderSettings
import app.tateyomi.data.ReadingProgress
import app.tateyomi.data.RubySpan
import app.tateyomi.data.TextBlock
import app.tateyomi.data.TocEntry
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One rendered line: a vertical column when [ReaderSettings.vertical], otherwise a row. */
data class LayoutLine(
    val blockIndex: Int,
    val start: Int,
    val end: Int,
    val indent: Int,
    val blank: Boolean = false,
)

sealed interface PageContent {
    data class Text(val lines: List<LayoutLine>) : PageContent
    data class Image(val block: ImageBlock, val blockIndex: Int) : PageContent
}

data class ChapterLayout(
    val index: Int,
    val title: String,
    val pages: List<PageContent>,
    val charStart: Int,
    val charCount: Int,
)

data class PageRef(
    val chapterIndex: Int,
    val pageIndex: Int,
    val chapterPageCount: Int,
)

class ReaderLayout(
    val chapters: List<ChapterLayout>,
    val totalPages: Int,
    val totalChars: Int,
    val settings: ReaderSettings,
) {
    private val pageStarts: IntArray = IntArray(chapters.size)

    init {
        var page = 0
        chapters.forEachIndexed { index, chapter ->
            pageStarts[index] = page
            page += chapter.pages.size
        }
    }

    fun pageRef(page: Int): PageRef {
        if (chapters.isEmpty()) return PageRef(0, 0, 1)
        val safePage = page.coerceIn(0, max(0, totalPages - 1))
        var low = 0
        var high = chapters.lastIndex
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            if (pageStarts[mid] <= safePage) low = mid else high = mid - 1
        }
        val chapter = chapters[low]
        return PageRef(low, (safePage - pageStarts[low]).coerceIn(0, chapter.pages.lastIndex), chapter.pages.size)
    }

    fun globalPage(chapterIndex: Int, pageInChapter: Int): Int {
        if (chapters.isEmpty()) return 0
        val chapter = chapterIndex.coerceIn(0, chapters.lastIndex)
        return (pageStarts[chapter] + pageInChapter.coerceIn(0, chapters[chapter].pages.lastIndex))
            .coerceIn(0, totalPages - 1)
    }

    fun chapterStartPage(chapterIndex: Int): Int = globalPage(chapterIndex, 0)

    fun pageForBlock(chapterIndex: Int, blockIndex: Int): Int {
        val chapter = chapters.getOrNull(chapterIndex) ?: return 0
        chapter.pages.forEachIndexed { pageIndex, page ->
            val hit = when (page) {
                is PageContent.Text -> page.lines.any { it.blockIndex == blockIndex }
                is PageContent.Image -> page.blockIndex == blockIndex
            }
            if (hit) return globalPage(chapterIndex, pageIndex)
        }
        return chapterStartPage(chapterIndex)
    }

    fun pageForToc(entry: TocEntry): Int {
        return entry.blockIndex?.let { pageForBlock(entry.chapterIndex, it) }
            ?: chapterStartPage(entry.chapterIndex)
    }

    fun chapterAt(page: Int): ChapterLayout = chapters[pageRef(page).chapterIndex]

    fun pageContent(page: Int): PageContent? {
        val ref = pageRef(page)
        return chapters.getOrNull(ref.chapterIndex)?.pages?.getOrNull(ref.pageIndex)
    }

    fun progressFor(page: Int): ReadingProgress {
        if (chapters.isEmpty()) return ReadingProgress()
        val safePage = page.coerceIn(0, max(0, totalPages - 1))
        val ref = pageRef(safePage)
        val chapter = chapters[ref.chapterIndex]
        val fraction = if (chapter.pages.size <= 1) {
            1f
        } else {
            ref.pageIndex.toFloat() / (chapter.pages.size - 1).toFloat()
        }
        val overall = when {
            safePage >= totalPages - 1 -> 1f
            totalChars <= 0 -> (safePage + 1).toFloat() / totalPages.toFloat()
            else -> {
                val base = (chapter.charStart + fraction * chapter.charCount) / totalChars.toFloat()
                base.coerceIn(0f, 0.9999f)
            }
        }
        return ReadingProgress(
            chapterIndex = ref.chapterIndex,
            chapterFraction = fraction,
            overallFraction = overall,
        )
    }

    fun pageFor(progress: ReadingProgress): Int {
        if (chapters.isEmpty()) return 0
        if (progress.overallFraction >= 0.999f) return max(0, totalPages - 1)
        val chapterIndex = progress.chapterIndex.coerceIn(0, chapters.lastIndex)
        val chapter = chapters[chapterIndex]
        val inChapter = if (chapter.pages.size <= 1) {
            0
        } else {
            (progress.chapterFraction.coerceIn(0f, 1f) * (chapter.pages.size - 1)).roundToInt()
        }
        return globalPage(chapterIndex, inChapter)
    }

    fun describe(page: Int): String {
        if (chapters.isEmpty()) return ""
        val chapter = chapterAt(page)
        return chapter.title
    }
}

object ReaderLayoutEngine {

    fun compute(content: BookContent, settings: ReaderSettings): ReaderLayout {
        val normalized = settings.normalized()
        val chapters = ArrayList<ChapterLayout>(content.chapters.size)
        var charCursor = 0
        content.chapters.forEachIndexed { index, chapter ->
            val pages = layoutChapter(chapter, normalized)
            val chars = chapter.blocks.filterIsInstance<TextBlock>().sumOf { it.text.length }
            chapters += ChapterLayout(
                index = index,
                title = chapter.title.ifBlank { "第${index + 1}章" },
                pages = pages,
                charStart = charCursor,
                charCount = chars,
            )
            charCursor += chars
        }
        val totalPages = chapters.sumOf { it.pages.size }.coerceAtLeast(1)
        return ReaderLayout(
            chapters = chapters.ifEmpty { listOf(emptyChapter()) },
            totalPages = totalPages,
            totalChars = charCursor,
            settings = normalized,
        )
    }

    private fun emptyChapter(): ChapterLayout = ChapterLayout(
        index = 0,
        title = "",
        pages = listOf(PageContent.Text(listOf(LayoutLine(-1, 0, 0, 0, blank = true)))),
        charStart = 0,
        charCount = 0,
    )

    private fun layoutChapter(chapter: Chapter, settings: ReaderSettings): List<PageContent> {
        val linesPerPage = settings.linesPerPage
        val charsPerLine = settings.charsPerLine
        val pages = ArrayList<PageContent>()
        val current = ArrayList<LayoutLine>(linesPerPage)

        fun flushText() {
            if (current.isNotEmpty()) {
                pages += PageContent.Text(current.toList())
                current.clear()
            }
        }

        fun addLine(line: LayoutLine) {
            if (current.size >= linesPerPage) flushText()
            current += line
        }

        fun addBlankLine() {
            addLine(LayoutLine(-1, 0, 0, 0, blank = true))
        }

        chapter.blocks.forEachIndexed { blockIndex, block ->
            when (block) {
                is ImageBlock -> {
                    flushText()
                    pages += PageContent.Image(block, blockIndex)
                }
                is TextBlock -> {
                    val heading = block.style == BlockStyle.HEADING || block.style == BlockStyle.SUBHEADING
                    if (heading && !settings.vertical && current.isNotEmpty()) {
                        // A little air before a chapter/section heading. In
                        // vertical mode a blank line is a whole empty column,
                        // so headings are separated by weight instead.
                        addBlankLine()
                    }
                    if (block.text.isEmpty()) {
                        addBlankLine()
                    } else {
                        val segments = block.text.split('\n')
                        segments.forEachIndexed { segmentIndex, segment ->
                            if (segment.isEmpty()) {
                                addBlankLine()
                                return@forEachIndexed
                            }
                            val firstLine = segmentIndex == 0
                            var indent = if (block.indent && firstLine) {
                                settings.indent.coerceIn(0, max(0, charsPerLine - 2))
                            } else {
                                0
                            }
                            val capacity = max(1, charsPerLine - indent)
                            val spans = splitIntoLines(segment, capacity, block.ruby)
                            spans.forEachIndexed { lineIndex, span ->
                                var lineIndent = if (lineIndex == 0) indent else 0
                                if (block.centered && !settings.vertical) {
                                    lineIndent = max(0, (charsPerLine - (span.end - span.start)) / 2)
                                }
                                addLine(
                                    LayoutLine(
                                        blockIndex = blockIndex,
                                        start = span.start,
                                        end = span.end,
                                        indent = lineIndent,
                                    )
                                )
                            }
                        }
                    }
                    if (heading && !settings.vertical && current.size < linesPerPage - 1) {
                        addBlankLine()
                    }
                }
            }
        }
        flushText()
        if (pages.isEmpty()) {
            pages += PageContent.Text(listOf(LayoutLine(-1, 0, 0, 0, blank = true)))
        }
        return pages
    }

    internal data class Span(val start: Int, val end: Int)

    /**
     * Splits a single paragraph segment into display lines of [capacity]
     * characters. Japanese kinsoku rules are applied at the break, and ruby
     * spans are kept on one line where possible.
     */
    internal fun splitIntoLines(
        text: String,
        capacity: Int,
        ruby: List<RubySpan> = emptyList(),
    ): List<Span> {
        if (text.isEmpty()) return emptyList()
        val cap = max(1, capacity)
        val result = ArrayList<Span>()
        var pos = 0
        while (pos < text.length) {
            // Don't start a line with an ASCII space.
            while (pos < text.length && text[pos] == ' ') pos++
            if (pos >= text.length) break

            var candidate = min(pos + cap, text.length)
            if (candidate < text.length) {
                candidate = adjustBreak(text, pos, candidate, ruby)
            }
            var end = candidate
            while (end > pos && text[end - 1] == ' ') end--
            if (end <= pos) {
                end = min(pos + 1, text.length)
            }
            result += Span(pos, end)
            pos = max(end, candidate)
            while (pos < text.length && text[pos] == ' ') pos++
        }
        return result
    }

    private fun adjustBreak(
        text: String,
        start: Int,
        initial: Int,
        ruby: List<RubySpan>,
    ): Int {
        var candidate = initial
        val limit = text.length
        val capacity = (initial - start).coerceAtLeast(1)

        // Never split a surrogate pair.
        if (candidate < limit && Character.isLowSurrogate(text[candidate]) &&
            Character.isHighSurrogate(text[candidate - 1])
        ) {
            candidate--
        }

        // Keep a ruby base together when it fits on the line; otherwise allow
        // the span to split rather than producing an almost empty line.
        var rubyGuard = 0
        while (rubyGuard++ < 8) {
            val crossing = ruby.firstOrNull { it.start < candidate && it.end > candidate }
                ?: break
            val spanLength = crossing.end - crossing.start
            val fitsWhole = crossing.start >= start && crossing.end <= start + capacity
            val fitsOnNextLine = crossing.start > start && spanLength <= capacity
            candidate = when {
                fitsWhole -> crossing.end
                fitsOnNextLine -> crossing.start
                else -> break
            }
        }

        // Prefer breaking at spaces inside Latin words, but do not make a
        // needlessly short line.
        val nextIsWord = candidate < limit && text[candidate].isAsciiWord()
        val prevIsWord = candidate > start && text[candidate - 1].isAsciiWord()
        if (nextIsWord && prevIsWord) {
            val space = text.lastIndexOf(' ', candidate - 1)
            if (space > start + capacity / 3) candidate = space
        }

        // Kinsoku shori: closing punctuation must not start a line, opening
        // punctuation must not end one. Pull the break back a character at a
        // time, but always make progress.
        var guard = 0
        while (candidate > start + 1 && candidate < limit && guard++ < 12) {
            val next = text[candidate]
            val prev = text[candidate - 1]
            if (next in NO_LINE_START || prev in NO_LINE_END) {
                candidate--
            } else {
                break
            }
        }
        if (candidate <= start) candidate = min(start + 1, limit)
        return candidate
    }

    private fun Char.isAsciiWord(): Boolean =
        this.code < 128 && (isLetterOrDigit() || this == '-' || this == '\'')

    // Closing punctuation / small kana / long vowel marks that should not
    // begin a line. This follows the common Japanese kinsoku set.
    private const val NO_LINE_START =
        "、。，．・：；？！゛゜ヽヾゝゞ々ーァィゥェォッャュョヮヵヶぁぃぅぇぉっゃゅょゎゕゖ" +
            ".,:;?!)]}」』】〕〉》〗〙〛”’〟"

    // Opening punctuation that should not end a line.
    private const val NO_LINE_END =
        "([{「『【〔〈《〖〘〚“‘｛（［〈《【〔"
}
