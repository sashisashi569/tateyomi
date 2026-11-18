package app.tateyomi

import app.tateyomi.data.BookFormat
import app.tateyomi.data.BookSummary
import app.tateyomi.data.LibraryFile
import app.tateyomi.data.LibraryFolder
import app.tateyomi.data.LibrarySort
import app.tateyomi.data.ReadingProgress
import app.tateyomi.data.parseTagInput
import app.tateyomi.data.sanitizeTags
import app.tateyomi.ui.LibraryFilter
import app.tateyomi.ui.UNFILED_FOLDER_ID
import app.tateyomi.ui.library.folderScopedBooks
import app.tateyomi.ui.library.sortLibraryBooks
import app.tateyomi.ui.library.visibleLibraryBooks
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryModelTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun tagInputSplitsJapaneseAndAsciiSeparators() {
        assertEquals(
            listOf("短編", "時代小説", "ミステリ"),
            parseTagInput("短編, 時代小説、ミステリ"),
        )
    }

    @Test
    fun tagsAreTrimmedDeduplicatedAndLimited() {
        val tags = sanitizeTags((1..30).map { " タグ$it " } + listOf("タグ1", "  "))
        assertEquals(20, tags.size)
        assertEquals("タグ1", tags.first())
        assertTrue(tags.none { it.isBlank() })
    }

    @Test
    fun duplicateIdentityIgnoresSpacingAndCase() {
        assertTrue(
            app.tateyomi.data.sameBookIdentity(
                "創約　とある魔術の禁書目録（４）",
                "鎌池和馬",
                "創約 とある魔術の禁書目録（４）",
                "鎌池和馬",
            ),
        )
        assertFalse(
            app.tateyomi.data.sameBookIdentity("本", "著者A", "本", "著者B"),
        )
        val books = listOf(
            BookSummary(id = "1", title = "本", author = "著者A"),
            BookSummary(id = "2", title = "本", author = "著者B"),
        )
        assertEquals(
            "2",
            app.tateyomi.data.findDuplicateBook(books, "本", "著者B")?.id,
        )
    }

    @Test
    fun librarySortOrdersByTitleAuthorAndDates() {
        val books = listOf(
            BookSummary(id = "1", title = "b", author = "著者B", addedAt = 20, lastOpenedAt = 10),
            BookSummary(id = "2", title = "a", author = "著者A", addedAt = 10, lastOpenedAt = 30),
            BookSummary(id = "3", title = "c", author = "", addedAt = 30, lastOpenedAt = null),
        )

        assertEquals(
            listOf("a", "b", "c"),
            sortLibraryBooks(books, LibrarySort.TITLE).map { it.title },
        )
        assertEquals(
            listOf("a", "b", "c"),
            sortLibraryBooks(books, LibrarySort.AUTHOR).map { it.title },
        )
        assertEquals(
            listOf("2", "3", "1"),
            sortLibraryBooks(books, LibrarySort.RECENT).map { it.id },
        )
        assertEquals(
            listOf("3", "1", "2"),
            sortLibraryBooks(books, LibrarySort.ADDED_DESC).map { it.id },
        )
    }

    @Test
    fun titleSortUsesNaturalNumberOrder() {
        val books = listOf(
            BookSummary(id = "10", title = "本10"),
            BookSummary(id = "2", title = "本2"),
            BookSummary(id = "1", title = "本1"),
            BookSummary(id = "11", title = "本11"),
        )
        assertEquals(
            listOf("本1", "本2", "本10", "本11"),
            sortLibraryBooks(books, LibrarySort.TITLE).map { it.title },
        )

        val fullWidth = listOf(
            BookSummary(id = "10", title = "第１０巻"),
            BookSummary(id = "2", title = "第２巻"),
            BookSummary(id = "1", title = "第１巻"),
        )
        assertEquals(
            listOf("第１巻", "第２巻", "第１０巻"),
            sortLibraryBooks(fullWidth, LibrarySort.TITLE).map { it.title },
        )
    }

    @Test
    fun readBooksLastSinksFinishedBooksWhileKeepingOrder() {
        val books = listOf(
            BookSummary(id = "1", title = "a", addedAt = 10, progress = ReadingProgress(overallFraction = 1f)),
            BookSummary(id = "2", title = "b", addedAt = 20),
            BookSummary(id = "3", title = "c", addedAt = 30, progress = ReadingProgress(overallFraction = 0.5f)),
            BookSummary(id = "4", title = "d", addedAt = 5, progress = ReadingProgress(overallFraction = 0.999f)),
        )

        // ADDED_ASC gives 4, 1, 2, 3; only id 1 counts as finished.
        assertEquals(
            listOf("4", "2", "3", "1"),
            sortLibraryBooks(books, LibrarySort.ADDED_ASC, readBooksLast = true).map { it.id },
        )
        assertEquals(
            listOf("4", "1", "2", "3"),
            sortLibraryBooks(books, LibrarySort.ADDED_ASC).map { it.id },
        )
    }

    @Test
    fun randomSortIsStablePerSeedAndKeepsEveryBook() {
        val books = (1..10).map { BookSummary(id = "$it", title = "本$it") }
        val first = sortLibraryBooks(books, LibrarySort.RANDOM, randomSeed = 42L)
        val second = sortLibraryBooks(books, LibrarySort.RANDOM, randomSeed = 42L)

        assertEquals(first.map { it.id }, second.map { it.id })
        assertEquals(books.map { it.id }.toSet(), first.map { it.id }.toSet())

        val withReadLast = sortLibraryBooks(
            books + BookSummary(id = "done", title = "読了本", progress = ReadingProgress(overallFraction = 1f)),
            LibrarySort.RANDOM,
            readBooksLast = true,
            randomSeed = 42L,
        )
        assertEquals("done", withReadLast.last().id)
        assertEquals(11, withReadLast.size)
    }

    @Test
    fun visibleLibraryBooksScopesSortsAndFilters() {
        val books = listOf(
            BookSummary(id = "1", title = "銀河鉄道の夜", author = "宮沢賢治", folderId = "f1", addedAt = 10),
            BookSummary(
                id = "2",
                title = "注文の多い料理店",
                author = "宮沢賢治",
                folderId = "f1",
                addedAt = 20,
                progress = ReadingProgress(overallFraction = 1f),
            ),
            BookSummary(id = "3", title = "星めぐりの歌", folderId = null, addedAt = 30),
        )

        assertEquals(books, folderScopedBooks(books, null))
        assertEquals(listOf("3"), folderScopedBooks(books, UNFILED_FOLDER_ID).map { it.id })
        val scoped = folderScopedBooks(books, "f1")
        assertEquals(setOf("1", "2"), scoped.map { it.id }.toSet())

        // ADDED_DESC gives 2, 1; sinking the finished book flips them.
        val ordered = visibleLibraryBooks(
            scoped,
            LibraryFilter(sort = LibrarySort.ADDED_DESC, readBooksLast = true),
        )
        assertEquals(listOf("1", "2"), ordered.map { it.id })

        val searched = visibleLibraryBooks(
            scoped,
            LibraryFilter(query = "銀河", sort = LibrarySort.ADDED_DESC, readBooksLast = true),
        )
        assertEquals(listOf("1"), searched.map { it.id })

        val tagged = visibleLibraryBooks(
            scoped,
            LibraryFilter(sort = LibrarySort.TITLE, tags = setOf("童話")),
        )
        assertEquals(emptyList<String>(), tagged.map { it.id })
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @Test
    fun libraryJsonRoundTripsThroughStreams() {
        val source = LibraryFile(
            books = listOf(BookSummary(id = "b1", title = "本", tags = listOf("タグ"))),
            folders = listOf(LibraryFolder("f1", "フォルダ")),
        )
        val output = ByteArrayOutputStream()
        json.encodeToStream(LibraryFile.serializer(), source, output)
        val decoded = json.decodeFromStream(
            LibraryFile.serializer(),
            ByteArrayInputStream(output.toByteArray()),
        )
        assertEquals(source, decoded)
    }

    @Test
    fun libraryJsonRoundTripsFoldersAndTags() {
        val file = LibraryFile(
            books = listOf(
                BookSummary(
                    id = "b1",
                    title = "本",
                    folderId = "f1",
                    tags = listOf("タグ"),
                    format = BookFormat.AZW3,
                    sourceFile = "source.azw3",
                ),
            ),
            folders = listOf(LibraryFolder("f1", "フォルダ")),
        )
        val decoded = json.decodeFromString(
            LibraryFile.serializer(),
            json.encodeToString(LibraryFile.serializer(), file),
        )
        assertEquals(file, decoded)
    }
}
