package app.tateyomi.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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

fun readerColorScheme(palette: ReaderPalette): ColorScheme {
    val container = lerp(palette.surface, palette.accent, 0.13f)
    return if (palette.isDark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color(0xFF2A120C),
            primaryContainer = container,
            onPrimaryContainer = palette.accent,
            secondary = Color(0xFFB7C9BD),
            onSecondary = Color(0xFF14231C),
            secondaryContainer = container,
            onSecondaryContainer = palette.accent,
            background = palette.background,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = Color(0xFF2A2721),
            onSurfaceVariant = palette.muted,
            outline = palette.rule,
            outlineVariant = Color(0xFF2F2C25),
            error = Color(0xFFFFB4A8),
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color(0xFFFFF8F2),
            primaryContainer = container,
            onPrimaryContainer = palette.accent,
            secondary = palette.accent,
            onSecondary = Color(0xFFFFF8F2),
            secondaryContainer = container,
            onSecondaryContainer = palette.accent,
            background = palette.background,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = Color(0xFFEEE7D9),
            onSurfaceVariant = palette.muted,
            outline = palette.rule,
            outlineVariant = Color(0xFFE8E0D2),
            error = Color(0xFF9B3328),
        )
    }
}
