package app.tateyomi.ui.library

import app.tateyomi.data.BookSummary
import app.tateyomi.data.LibrarySort
import java.text.Collator
import java.util.Locale
import kotlin.math.min
import kotlin.random.Random

val LibrarySort.label: String
    get() = when (this) {
        LibrarySort.RECENT -> "更新が新しい順"
        LibrarySort.ADDED_DESC -> "追加が新しい順"
        LibrarySort.ADDED_ASC -> "追加が古い順"
        LibrarySort.TITLE -> "書名順"
        LibrarySort.TITLE_DESC -> "書名の逆順"
        LibrarySort.AUTHOR -> "著者順"
        LibrarySort.AUTHOR_DESC -> "著者の逆順"
        LibrarySort.RANDOM -> "ランダム"
    }

/**
 * Orders books for one library scope. [randomSeed] keeps [LibrarySort.RANDOM]
 * stable while the process lives; it is reseeded per launch so the order
 * changes between sessions. [readBooksLast] sinks finished books to the end
 * while keeping the underlying order within both groups.
 */
internal fun sortLibraryBooks(
    books: List<BookSummary>,
    sort: LibrarySort,
    readBooksLast: Boolean = false,
    randomSeed: Long = 0L,
): List<BookSummary> {
    val collator = Collator.getInstance(Locale.JAPANESE)
    fun byTitle(a: BookSummary, b: BookSummary): Int =
        naturalCompare(a.title, b.title, collator)
    fun byAuthor(a: BookSummary, b: BookSummary): Int {
        val blankA = a.author.isBlank()
        val blankB = b.author.isBlank()
        if (blankA != blankB) return if (blankA) 1 else -1
        if (blankA) return byTitle(a, b)
        val byName = naturalCompare(a.author, b.author, collator)
        return if (byName != 0) byName else byTitle(a, b)
    }

    val ordered = when (sort) {
        LibrarySort.RECENT -> books.sortedWith(
            compareByDescending<BookSummary> { it.lastOpenedAt ?: it.addedAt }
                .thenComparator(::byTitle),
        )
        LibrarySort.ADDED_DESC -> books.sortedWith(
            compareByDescending<BookSummary> { it.addedAt }.thenComparator(::byTitle),
        )
        LibrarySort.ADDED_ASC -> books.sortedWith(
            compareBy<BookSummary> { it.addedAt }.thenComparator(::byTitle),
        )
        LibrarySort.TITLE -> books.sortedWith(
            Comparator { a, b -> byTitle(a, b) },
        )
        LibrarySort.TITLE_DESC -> books.sortedWith(
            Comparator { a, b -> byTitle(b, a) },
        )
        LibrarySort.AUTHOR -> books.sortedWith(
            Comparator { a, b -> byAuthor(a, b) },
        )
        LibrarySort.AUTHOR_DESC -> books.sortedWith(
            Comparator { a, b ->
                val blankA = a.author.isBlank()
                val blankB = b.author.isBlank()
                if (blankA != blankB) {
                    if (blankA) 1 else -1
                } else if (blankA) {
                    byTitle(a, b)
                } else {
                    val byName = naturalCompare(b.author, a.author, collator)
                    if (byName != 0) byName else byTitle(a, b)
                }
            },
        )
        LibrarySort.RANDOM -> books.shuffled(Random(randomSeed))
    }
    if (!readBooksLast) return ordered
    val unfinished = ordered.filter { it.percent < 100 }
    val finished = ordered.filter { it.percent >= 100 }
    return unfinished + finished
}

/**
 * Compares text while treating runs of digits as numbers. This keeps volume
 * numbers in the expected order: 1, 2, 3, ... 10, 11 instead of 1, 10, 11, 2.
 * Full-width digits are handled as well.
 */
internal fun naturalCompare(a: String, b: String, collator: Collator): Int {
    val left = tokenize(a)
    val right = tokenize(b)
    val shared = min(left.size, right.size)
    for (index in 0 until shared) {
        val first = left[index]
        val second = right[index]
        val compared = when {
            first.numeric && second.numeric -> compareNumber(first.text, second.text)
            else -> collator.compare(first.text, second.text)
        }
        if (compared != 0) return compared
    }
    return left.size.compareTo(right.size)
}

private data class SortToken(val text: String, val numeric: Boolean)

private fun tokenize(value: String): List<SortToken> {
    if (value.isEmpty()) return emptyList()
    val tokens = ArrayList<SortToken>()
    var start = 0
    var numeric = value[0].isDigit()
    for (index in 1..value.length) {
        val nextNumeric = index < value.length && value[index].isDigit()
        if (index == value.length || nextNumeric != numeric) {
            tokens += SortToken(value.substring(start, index), numeric)
            start = index
            numeric = nextNumeric
        }
    }
    return tokens
}

private fun compareNumber(a: String, b: String): Int {
    val left = normalizeDigits(a)
    val right = normalizeDigits(b)
    if (left.length != right.length) return left.length.compareTo(right.length)
    val compared = left.compareTo(right)
    if (compared != 0) return compared
    // "01" and "1" are the same number; make the order deterministic.
    return a.length.compareTo(b.length)
}

private fun normalizeDigits(value: String): String {
    val digits = buildString(value.length) {
        value.forEach { ch -> append(Character.digit(ch, 10)) }
    }.trimStart('0')
    return digits.ifEmpty { "0" }
}
