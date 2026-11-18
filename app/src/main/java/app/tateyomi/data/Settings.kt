package app.tateyomi.data

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
enum class ReaderTheme {
    PAPER,
    WHITE,
    DARK,
}

/** App chrome theme, independent of the reader paper color. */
@Serializable
enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/** How one/two layout pages are shown on screen. */
@Serializable
enum class SpreadMode {
    /** One page in portrait, two pages in landscape. */
    AUTO,
    SINGLE,
    SPREAD,
}

@Serializable
data class AppearanceSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    /** Pure black app background while the app theme resolves to dark. */
    val amoled: Boolean = false,
)

/**
 * The typesetting grid. [charsPerLine] is the number of characters in one
 * vertical column (or one horizontal row), [linesPerPage] is the number of
 * columns/rows on a page. Character size is derived from the page size, so
 * the layout stays stable across screen sizes and orientations.
 */
@Serializable
data class ReaderSettings(
    val vertical: Boolean = true,
    val charsPerLine: Int = 40,
    val linesPerPage: Int = 15,
    val lineSpacing: Float = 1.5f,
    val indent: Int = 1,
    val marginDp: Int = 20,
    val fontId: String = BuiltInFonts.MINCHO_ID,
    val theme: ReaderTheme = ReaderTheme.PAPER,
    val spread: SpreadMode = SpreadMode.AUTO,
    val spreadGapDp: Int = 0,
    val skipCover: Boolean = true,
    val rubyScale: Float = 0.5f,
) {
    fun normalized(): ReaderSettings = copy(
        charsPerLine = charsPerLine.coerceIn(MIN_CHARS, MAX_CHARS),
        linesPerPage = linesPerPage.coerceIn(MIN_LINES, MAX_LINES),
        lineSpacing = ((lineSpacing * 10f).roundToInt() / 10f)
            .coerceIn(MIN_LINE_SPACING, MAX_LINE_SPACING),
        indent = indent.coerceIn(0, 3),
        marginDp = marginDp.coerceIn(MIN_MARGIN, MAX_MARGIN),
        spreadGapDp = spreadGapDp.coerceIn(MIN_SPREAD_GAP, MAX_SPREAD_GAP),
        rubyScale = ((rubyScale * 100f).roundToInt() / 100f)
            .coerceIn(MIN_RUBY_SCALE, MAX_RUBY_SCALE),
    )

    companion object {
        const val MIN_CHARS = 16
        const val MAX_CHARS = 64
        const val MIN_LINES = 5
        const val MAX_LINES = 32
        const val MIN_LINE_SPACING = 1.0f
        const val MAX_LINE_SPACING = 2.5f
        const val MIN_MARGIN = 0
        const val MAX_MARGIN = 168
        const val MIN_SPREAD_GAP = -64
        const val MAX_SPREAD_GAP = 64
        const val MIN_RUBY_SCALE = 0.3f
        const val MAX_RUBY_SCALE = 0.8f
        const val DEFAULT_RUBY_SCALE = 0.5f

        val Default = ReaderSettings()

        val Bunko = ReaderSettings(
            vertical = true,
            charsPerLine = 42,
            linesPerPage = 16,
            lineSpacing = 1.5f,
            indent = 1,
            marginDp = 16,
        )

        val Tandoku = ReaderSettings(
            vertical = true,
            charsPerLine = 38,
            linesPerPage = 14,
            lineSpacing = 1.5f,
            indent = 1,
            marginDp = 24,
        )

        val LargePrint = ReaderSettings(
            vertical = true,
            charsPerLine = 26,
            linesPerPage = 11,
            lineSpacing = 1.6f,
            indent = 1,
            marginDp = 18,
            fontId = BuiltInFonts.GOTHIC_ID,
        )

        val Horizontal = ReaderSettings(
            vertical = false,
            charsPerLine = 24,
            linesPerPage = 30,
            lineSpacing = 1.7f,
            indent = 1,
            marginDp = 20,
        )
    }
}

@Serializable
data class ReaderPreset(
    val id: String,
    val name: String,
    val settings: ReaderSettings,
)

/** Resolves [SpreadMode.AUTO] against the current orientation. */
fun ReaderSettings.usesSpread(landscape: Boolean): Boolean = when (spread) {
    SpreadMode.AUTO -> landscape
    SpreadMode.SINGLE -> false
    SpreadMode.SPREAD -> true
}

@Serializable
enum class ArchiveFolderMode {
    /** Ask for every bulk archive. */
    ASK,
    /** Always use a folder named after the archive. */
    ALWAYS,
    /** Never create an archive folder. */
    NEVER,
}

@Serializable
data class ImportOptions(
    /** Expand archives that contain multiple book files. */
    val expandArchives: Boolean = true,
    /** What to do with the archive-name folder for bulk imports. */
    val archiveFolderMode: ArchiveFolderMode = ArchiveFolderMode.ASK,
)

/**
 * Sort order for one library scope. [AppSettings.folderSorts] keys these by
 * folder id; the unfiled pseudo folder has its own entry. Folders without an
 * entry fall back to the global defaults on [AppSettings].
 */
@Serializable
data class FolderSortPrefs(
    val sort: LibrarySort = LibrarySort.RECENT,
    /** Finished books sink to the end regardless of [sort]. */
    val readBooksLast: Boolean = false,
)

@Serializable
data class AppSettings(
    val reader: ReaderSettings = ReaderSettings.Default,
    val presets: List<ReaderPreset> = emptyList(),
    val fonts: List<ManagedFont> = emptyList(),
    val appearance: AppearanceSettings = AppearanceSettings(),
    val importOptions: ImportOptions = ImportOptions(),
    /** Sort order for the whole library; also the default for folders without saved prefs. */
    val librarySort: LibrarySort = LibrarySort.RECENT,
    /** Whether finished books sink to the end while the whole library is shown. */
    val librarySortReadLast: Boolean = false,
    /** Saved sort prefs per folder id, keyed like [AppSettings.librarySort] plus the unfiled id. */
    val folderSorts: Map<String, FolderSortPrefs> = emptyMap(),
)

data class BuiltInPreset(
    val key: String,
    val name: String,
    val settings: ReaderSettings,
)

val BuiltInPresets = listOf(
    BuiltInPreset("bunko", "文庫", ReaderSettings.Bunko),
    BuiltInPreset("tandoku", "単行本", ReaderSettings.Tandoku),
    BuiltInPreset("large", "大活字", ReaderSettings.LargePrint),
    BuiltInPreset("horizontal", "横書き", ReaderSettings.Horizontal),
)
