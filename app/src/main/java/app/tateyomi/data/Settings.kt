package app.tateyomi.data

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
enum class FontChoice {
    MINCHO,
    GOTHIC,
    MONOSPACE,
}

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
    val font: FontChoice = FontChoice.MINCHO,
    val fontId: String? = null,
    val theme: ReaderTheme = ReaderTheme.PAPER,
    val spread: SpreadMode = SpreadMode.AUTO,
    val spreadGapDp: Int = 0,
) {
    fun normalized(): ReaderSettings = copy(
        charsPerLine = charsPerLine.coerceIn(MIN_CHARS, MAX_CHARS),
        linesPerPage = linesPerPage.coerceIn(MIN_LINES, MAX_LINES),
        lineSpacing = ((lineSpacing * 10f).roundToInt() / 10f)
            .coerceIn(MIN_LINE_SPACING, MAX_LINE_SPACING),
        indent = indent.coerceIn(0, 3),
        marginDp = marginDp.coerceIn(MIN_MARGIN, MAX_MARGIN),
        spreadGapDp = spreadGapDp.coerceIn(MIN_SPREAD_GAP, MAX_SPREAD_GAP),
    )

    companion object {
        const val MIN_CHARS = 16
        const val MAX_CHARS = 64
        const val MIN_LINES = 5
        const val MAX_LINES = 32
        const val MIN_LINE_SPACING = 1.0f
        const val MAX_LINE_SPACING = 2.5f
        const val MIN_MARGIN = 0
        const val MAX_MARGIN = 56
        const val MIN_SPREAD_GAP = 0
        const val MAX_SPREAD_GAP = 64

        val Default = ReaderSettings()

        val Bunko = ReaderSettings(
            vertical = true,
            charsPerLine = 42,
            linesPerPage = 16,
            lineSpacing = 1.5f,
            indent = 1,
            marginDp = 16,
            font = FontChoice.MINCHO,
        )

        val Tandoku = ReaderSettings(
            vertical = true,
            charsPerLine = 38,
            linesPerPage = 14,
            lineSpacing = 1.5f,
            indent = 1,
            marginDp = 24,
            font = FontChoice.MINCHO,
        )

        val LargePrint = ReaderSettings(
            vertical = true,
            charsPerLine = 26,
            linesPerPage = 11,
            lineSpacing = 1.6f,
            indent = 1,
            marginDp = 18,
            font = FontChoice.GOTHIC,
        )

        val Horizontal = ReaderSettings(
            vertical = false,
            charsPerLine = 24,
            linesPerPage = 30,
            lineSpacing = 1.7f,
            indent = 1,
            marginDp = 20,
            font = FontChoice.MINCHO,
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
data class AppSettings(
    val reader: ReaderSettings = ReaderSettings.Default,
    val presets: List<ReaderPreset> = emptyList(),
    val fonts: List<ManagedFont> = emptyList(),
    val appearance: AppearanceSettings = AppearanceSettings(),
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
