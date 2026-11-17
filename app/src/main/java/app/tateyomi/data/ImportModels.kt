package app.tateyomi.data

data class ImportOutcome(
    val imported: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val firstError: String? = null,
)

/** Returns true to replace the existing book, false to cancel this import. */
typealias BookConflictResolver = suspend (
    existing: BookSummary,
    incomingTitle: String,
    incomingAuthor: String,
) -> Boolean

/** Returns true to merge into the existing folder, false to skip the archive. */
typealias FolderConflictResolver = suspend (existing: LibraryFolder) -> Boolean

/** Returns the folder name to create, or null to import without a folder. */
typealias ArchiveFolderResolver = suspend (archiveName: String) -> String?
