package app.tateyomi.ui.library

import app.tateyomi.data.BookSummary
import app.tateyomi.ui.LibraryFilter
import app.tateyomi.ui.UNFILED_FOLDER_ID

/**
 * The library pipeline shared by the bookshelf grid and the reader's
 * next/previous order so both see the exact same list.
 */

/** Books inside one folder scope: all, one folder, or the unfiled shelf. */
internal fun folderScopedBooks(
    books: List<BookSummary>,
    folderId: String?,
): List<BookSummary> = when (folderId) {
    null -> books
    UNFILED_FOLDER_ID -> books.filter { it.folderId == null }
    else -> books.filter { it.folderId == folderId }
}

/** Folder-scoped books in the filter's sort order, then trimmed by tags and query. */
internal fun visibleLibraryBooks(
    scoped: List<BookSummary>,
    filter: LibraryFilter,
): List<BookSummary> {
    val sorted = sortLibraryBooks(
        books = scoped,
        sort = filter.sort,
        readBooksLast = filter.readBooksLast,
        randomSeed = filter.randomSeed,
    )
    val search = filter.query.trim()
    return sorted.filter { book ->
        val tagsMatch = filter.tags.isEmpty() || filter.tags.all { it in book.tags }
        val queryMatch = search.isBlank() ||
            book.title.contains(search, ignoreCase = true) ||
            book.author.contains(search, ignoreCase = true) ||
            book.tags.any { it.contains(search, ignoreCase = true) }
        tagsMatch && queryMatch
    }
}
