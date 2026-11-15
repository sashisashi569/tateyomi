package app.tateyomi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Paper = Color(0xFFF4EEE2)
val PaperSurface = Color(0xFFFBF7EE)
val Ink = Color(0xFF24211C)
val InkMuted = Color(0xFF6F675A)
val Moss = Color(0xFF1F3A34)
val Vermilion = Color(0xFFA34A3A)
val Hairline = Color(0xFFD9CDB9)

val NightBackground = Color(0xFF131210)
val NightSurface = Color(0xFF1D1B16)
val NightInk = Color(0xFFE8E1D3)
val NightMuted = Color(0xFFA39A88)

private val LightColors = lightColorScheme(
    primary = Moss,
    onPrimary = PaperSurface,
    primaryContainer = Color(0xFFDDE7DF),
    onPrimaryContainer = Color(0xFF15251F),
    secondary = Vermilion,
    onSecondary = Color(0xFFFFF8F2),
    secondaryContainer = Color(0xFFF3DDD5),
    onSecondaryContainer = Color(0xFF3E150D),
    tertiary = Color(0xFF6A5B3C),
    onTertiary = Color(0xFFFFF8EE),
    background = Paper,
    onBackground = Ink,
    surface = PaperSurface,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEEE5D4),
    onSurfaceVariant = InkMuted,
    outline = Hairline,
    outlineVariant = Color(0xFFE7DECE),
    inverseSurface = Moss,
    inverseOnSurface = PaperSurface,
    error = Color(0xFF9B3328),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAFC9BC),
    onPrimary = Color(0xFF13251F),
    primaryContainer = Color(0xFF2A443C),
    onPrimaryContainer = Color(0xFFD5E8DF),
    secondary = Color(0xFFE2A190),
    onSecondary = Color(0xFF3E150D),
    secondaryContainer = Color(0xFF5A2B22),
    onSecondaryContainer = Color(0xFFF8DCD4),
    tertiary = Color(0xFFD6C39B),
    onTertiary = Color(0xFF3C2F12),
    background = NightBackground,
    onBackground = NightInk,
    surface = NightSurface,
    onSurface = NightInk,
    surfaceVariant = Color(0xFF2B281F),
    onSurfaceVariant = NightMuted,
    outline = Color(0xFF4A4438),
    outlineVariant = Color(0xFF333027),
    inverseSurface = NightInk,
    inverseOnSurface = NightBackground,
    error = Color(0xFFFFB4A8),
    onError = Color(0xFF5F150B),
)

private val AmoledDarkColors = DarkColors.copy(
    background = Color.Black,
    surface = Color(0xFF080808),
    surfaceVariant = Color(0xFF151515),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF080808),
    surfaceContainer = Color(0xFF0D0D0D),
    surfaceContainerHigh = Color(0xFF141414),
    surfaceContainerHighest = Color(0xFF1A1A1A),
    outline = Color(0xFF3E3A32),
    outlineVariant = Color(0xFF2A2823),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 44.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 42.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.8.sp,
    ),
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

@Composable
fun TateyomiTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        !darkTheme -> LightColors
        amoled -> AmoledDarkColors
        else -> DarkColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
