package app.tateyomi.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A stable, layout-independent reading location inside a book. */
@Serializable
data class ReadingProgress(
    val chapterIndex: Int = 0,
    val chapterFraction: Float = 0f,
    val overallFraction: Float = 0f,
    val updatedAt: Long = 0L,
)

@Serializable
data class BookSummary(
    val id: String,
    val title: String,
    val author: String = "",
    val coverFile: String? = null,
    val charCount: Int = 0,
    val chapterCount: Int = 0,
    val addedAt: Long = 0L,
    val lastOpenedAt: Long? = null,
    val progress: ReadingProgress? = null,
) {
    val percent: Int
        get() = ((progress?.overallFraction ?: 0f) * 100f).toInt().coerceIn(0, 100)
}

/**
 * Bump when parsing semantics change so cached content.json files are
 * re-derived from the stored source.epub. 3: images that stand alone in a
 * paragraph became full-page illustrations instead of 1-character inline
 * images.
 */
const val BOOK_CONTENT_VERSION = 3

@Serializable
data class BookContent(
    val chapters: List<Chapter> = emptyList(),
    val toc: List<TocEntry> = emptyList(),
    val charCount: Int = 0,
    /** Image file name used as the book cover, when it also appears in the spine. */
    val coverImage: String? = null,
    /** Cache schema version; older caches are re-parsed from source.epub. */
    val version: Int = 1,
)

@Serializable
data class Chapter(
    val title: String = "",
    val blocks: List<Block> = emptyList(),
    val charCount: Int = 0,
)

@Serializable
data class TocEntry(
    val title: String,
    val chapterIndex: Int,
    val level: Int = 0,
    val blockIndex: Int? = null,
)

@Serializable
sealed interface Block

@Serializable
@SerialName("text")
data class TextBlock(
    val text: String,
    val style: BlockStyle = BlockStyle.BODY,
    val indent: Boolean = true,
    val centered: Boolean = false,
    val ruby: List<RubySpan> = emptyList(),
    val bold: List<StyleRange> = emptyList(),
    val inlineImages: List<InlineImageSpan> = emptyList(),
    val anchor: String? = null,
) : Block

@Serializable
@SerialName("image")
data class ImageBlock(
    val file: String,
    val width: Int = 0,
    val height: Int = 0,
    val alt: String = "",
    val anchor: String? = null,
) : Block

@Serializable
data class RubySpan(
    val start: Int,
    val end: Int,
    val text: String,
)

@Serializable
data class StyleRange(
    val start: Int,
    val end: Int,
)

/** A small inline image (gaiji) occupying one character cell in [TextBlock.text]. */
@Serializable
data class InlineImageSpan(
    val start: Int,
    val end: Int,
    val file: String,
    val alt: String = "",
)

@Serializable
enum class BlockStyle {
    BODY,
    HEADING,
    SUBHEADING,
    QUOTE,
    CAPTION,
    POEM,
}

/** Used for the on-disk library index. */
@Serializable
data class LibraryFile(
    val version: Int = 1,
    val books: List<BookSummary> = emptyList(),
)
