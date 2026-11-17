package app.tateyomi

import app.tateyomi.data.BookFormat
import app.tateyomi.data.BookSummary
import app.tateyomi.data.LibraryFile
import app.tateyomi.data.LibraryFolder
import app.tateyomi.data.parseTagInput
import app.tateyomi.data.sanitizeTags
import app.tateyomi.data.LibrarySort
import app.tateyomi.ui.library.sortLibraryBooks
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
