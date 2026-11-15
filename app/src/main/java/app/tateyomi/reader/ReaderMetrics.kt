package app.tateyomi.reader

import app.tateyomi.data.ReaderSettings
import kotlin.math.min

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
    marginPx: Float,
): ReaderMetrics {
    val width = widthPx.coerceAtLeast(1f)
    val height = heightPx.coerceAtLeast(1f)
    val margin = marginPx.coerceIn(0f, min(width, height) * 0.22f)
    val usableWidth = (width - margin * 2f).coerceAtLeast(1f)
    val usableHeight = (height - margin * 2f).coerceAtLeast(1f)
    val chars = settings.charsPerLine.coerceAtLeast(1)
    val lines = settings.linesPerPage.coerceAtLeast(1)
    val lineSpacing = settings.lineSpacing.coerceIn(
        ReaderSettings.MIN_LINE_SPACING,
        ReaderSettings.MAX_LINE_SPACING,
    )

    val inlineExtent = if (settings.vertical) usableHeight else usableWidth
    val blockExtent = if (settings.vertical) usableWidth else usableHeight
    val fontSize = min(
        inlineExtent / chars,
        blockExtent / (lines * lineSpacing),
    ).coerceAtLeast(1f)
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
