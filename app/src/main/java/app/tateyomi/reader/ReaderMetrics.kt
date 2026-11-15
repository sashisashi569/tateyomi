package app.tateyomi.reader

import app.tateyomi.data.ReaderSettings
import kotlin.math.min

/** The dp value treated as one em for the margin setting. */
const val MARGIN_BASE_DP = 16f

/**
 * Geometry shared by the paginator and the canvas renderer.
 *
 * The glyph size is the smaller of what fits [ReaderSettings.charsPerLine]
 * along the inline axis and what fits [ReaderSettings.linesPerPage] at
 * [ReaderSettings.lineSpacing] along the block axis. In other words, the whole
 * text block is scaled down until the requested lines and line spacing fit the
 * available page area. [ReaderSettings.linesPerPage] is always honoured.
 */
data class ReaderMetrics(
    val margin: Float,
    val usableWidth: Float,
    val usableHeight: Float,
    val fontSize: Float,
    val linePitch: Float,
    val effectiveLines: Int,
    val charsPerLine: Int,
    val inlineOffset: Float,
)

fun computeReaderMetrics(
    settings: ReaderSettings,
    widthPx: Float,
    heightPx: Float,
    marginEm: Float,
): ReaderMetrics {
    val width = widthPx.coerceAtLeast(1f)
    val height = heightPx.coerceAtLeast(1f)
    val chars = settings.charsPerLine.coerceAtLeast(1)
    val lines = settings.linesPerPage.coerceAtLeast(1)
    val lineSpacing = settings.lineSpacing.coerceIn(
        ReaderSettings.MIN_LINE_SPACING,
        ReaderSettings.MAX_LINE_SPACING,
    )

    // The margin is expressed in em (multiples of the rendered glyph size),
    // not in fixed pixels. This keeps margins and line spacing visually
    // proportional when the glyph size changes between portrait and landscape
    // (or between one-page and spread layouts).
    val marginFactor = marginEm.coerceAtLeast(0f)
    val inlineExtentRaw = if (settings.vertical) height else width
    val blockExtentRaw = if (settings.vertical) width else height
    val fontSize = min(
        inlineExtentRaw / (chars + 2f * marginFactor),
        blockExtentRaw / (lines * lineSpacing + 2f * marginFactor),
    ).coerceAtLeast(1f)
    val margin = marginFactor * fontSize
    val usableWidth = (width - margin * 2f).coerceAtLeast(1f)
    val usableHeight = (height - margin * 2f).coerceAtLeast(1f)

    val inlineExtent = if (settings.vertical) usableHeight else usableWidth
    val linePitch = fontSize * lineSpacing
    val inlineOffset = ((inlineExtent - fontSize * chars) / 2f).coerceAtLeast(0f)

    return ReaderMetrics(
        margin = margin,
        usableWidth = usableWidth,
        usableHeight = usableHeight,
        fontSize = fontSize,
        linePitch = linePitch,
        effectiveLines = lines,
        charsPerLine = chars,
        inlineOffset = inlineOffset,
    )
}
