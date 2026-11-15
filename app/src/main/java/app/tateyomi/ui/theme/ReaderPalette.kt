package app.tateyomi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.tateyomi.data.ReaderTheme

data class ReaderPalette(
    val background: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val rule: Color,
    val isDark: Boolean,
)

fun readerPalette(theme: ReaderTheme): ReaderPalette = when (theme) {
    ReaderTheme.PAPER -> ReaderPalette(
        background = Color(0xFFF4EEE2),
        surface = Color(0xFFFBF7EE),
        text = Color(0xFF24211C),
        muted = Color(0xFF7A7264),
        accent = Color(0xFFA34A3A),
        rule = Color(0xFFD9CDB9),
        isDark = false,
    )
    ReaderTheme.WHITE -> ReaderPalette(
        background = Color(0xFFFBFBF9),
        surface = Color(0xFFFFFFFF),
        text = Color(0xFF1C1C1A),
        muted = Color(0xFF76736C),
        accent = Color(0xFF9A3F2F),
        rule = Color(0xFFE4E2DC),
        isDark = false,
    )
    ReaderTheme.DARK -> ReaderPalette(
        background = Color(0xFF121210),
        surface = Color(0xFF1C1B17),
        text = Color(0xFFDED7C8),
        muted = Color(0xFF9C9484),
        accent = Color(0xFFD98B78),
        rule = Color(0xFF3A372F),
        isDark = true,
    )
}

/** App chrome palette (library, settings, reader bars, sheets). */
@androidx.compose.runtime.Composable
fun materialReaderPalette(): ReaderPalette {
    val scheme = MaterialTheme.colorScheme
    return ReaderPalette(
        background = scheme.background,
        surface = scheme.surface,
        text = scheme.onSurface,
        muted = scheme.onSurfaceVariant,
        accent = scheme.primary,
        rule = scheme.outlineVariant,
        isDark = scheme.background.luminance() < 0.5f,
    )
}
