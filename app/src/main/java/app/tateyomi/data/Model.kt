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

/** File type of the stored source. */
@Serializable
enum class BookFormat {
    EPUB,
    XHTML,
    CBZ,
    CBR,
    MOBI,
    AZW3,
    SEVEN_Z,
}

@Serializable
data class LibraryFolder(
    val id: String,
    val name: String,
    val createdAt: Long = 0L,
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
    val folderId: String? = null,
    val tags: List<String> = emptyList(),
    val format: BookFormat = BookFormat.EPUB,
    /** Name of the original file inside the book directory. */
    val sourceFile: String = "source.epub",
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
    val version: Int = 2,
    val books: List<BookSummary> = emptyList(),
    val folders: List<LibraryFolder> = emptyList(),
)

/** Keeps generated TOC sheets usable for dictionaries and image-heavy books. */
const val MAX_GENERATED_TOC_ENTRIES = 2000

/** Guards against importing text that cannot fit comfortably in the reader. */
const val MAX_IMPORT_CHARS = 10_000_000

/** UI-independent normalization for tags entered by the user. */
fun sanitizeTags(tags: List<String>): List<String> = tags
    .map { it.trim().take(24) }
    .filter { it.isNotEmpty() }
    .distinct()
    .take(20)

fun parseTagInput(input: String): List<String> =
    sanitizeTags(input.split(',', '、', '\n', '\t').map { it.trim() })

private fun normalizedBookPart(value: String): String =
    value.trim().replace(Regex("[\\s\\u3000]+"), "").lowercase()

/**
 * A stable identity for import conflict detection. Titles must match and
 * authors must match when both are present; a missing author does not match a
 * named author.
 */
fun sameBookIdentity(
    titleA: String,
    authorA: String,
    titleB: String,
    authorB: String,
): Boolean {
    if (normalizedBookPart(titleA) != normalizedBookPart(titleB)) return false
    val leftAuthor = normalizedBookPart(authorA)
    val rightAuthor = normalizedBookPart(authorB)
    return leftAuthor == rightAuthor
}

fun findDuplicateBook(
    books: List<BookSummary>,
    title: String,
    author: String,
): BookSummary? = books.firstOrNull { book ->
    sameBookIdentity(book.title, book.author, title, author)
}
