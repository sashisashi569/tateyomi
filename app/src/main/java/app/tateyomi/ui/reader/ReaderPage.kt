package app.tateyomi.ui.reader

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tateyomi.data.BlockStyle
import app.tateyomi.data.ImageBlock
import app.tateyomi.data.ReaderSettings
import app.tateyomi.data.TextBlock
import app.tateyomi.reader.LayoutLine
import app.tateyomi.reader.PageContent
import app.tateyomi.reader.ReaderLayout
import app.tateyomi.reader.ReaderFontSpec
import app.tateyomi.reader.ReaderTypefaces
import app.tateyomi.reader.SpreadLayout
import app.tateyomi.reader.computeReaderMetrics
import app.tateyomi.reader.needsManualVerticalRotation
import app.tateyomi.ui.LoadedBook
import app.tateyomi.ui.components.rememberBitmap
import app.tateyomi.ui.theme.ReaderPalette
import java.io.File
import kotlin.math.max
import kotlin.math.min

@Composable
fun ReaderPageView(
    book: LoadedBook,
    layout: ReaderLayout,
    page: Int,
    fontSpec: ReaderFontSpec,
    palette: ReaderPalette,
    modifier: Modifier = Modifier,
) {
    val content = layout.pageContent(page) ?: return
    val ref = layout.pageRef(page)
    val settings = layout.settings
    when (content) {
        is PageContent.Text -> {
            val chapter = book.content.chapters.getOrNull(ref.chapterIndex)
            ReaderTextPage(
                chapterBlocks = chapter?.blocks.orEmpty(),
                content = content,
                settings = settings,
                fontSpec = fontSpec,
                palette = palette,
                pageNumber = page + 1,
                modifier = modifier,
            )
        }
        is PageContent.Image -> {
            ReaderImagePage(
                image = content.block,
                bookDir = book.bookDir,
                settings = settings,
                palette = palette,
                pageNumber = page + 1,
                modifier = modifier,
            )
        }
    }
}

/**
 * Renders two reading-order pages side by side. Vertical Japanese keeps page
 * N on the right and N+1 on the left; horizontal text keeps N on the left.
 */
@Composable
fun ReaderSpreadView(
    book: LoadedBook,
    layout: ReaderLayout,
    spreadIndex: Int,
    fontSpec: ReaderFontSpec,
    palette: ReaderPalette,
    modifier: Modifier = Modifier,
) {
    val first = SpreadLayout.startPage(spreadIndex, layout.totalPages)
    val second = SpreadLayout.secondPage(spreadIndex, layout.totalPages)
    val vertical = layout.settings.vertical
    Box(modifier.background(palette.background)) {
        Row(Modifier.fillMaxSize()) {
            val gap = Modifier.width(layout.settings.spreadGapDp.dp)
            if (vertical) {
                SpreadSlot(second, book, layout, fontSpec, palette)
                Spacer(gap)
                SpreadSlot(first, book, layout, fontSpec, palette)
            } else {
                SpreadSlot(first, book, layout, fontSpec, palette)
                Spacer(gap)
                SpreadSlot(second, book, layout, fontSpec, palette)
            }
        }
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .width(1.dp)
                .background(palette.rule.copy(alpha = 0.35f)),
        )
    }
}

@Composable
private fun RowScope.SpreadSlot(
    page: Int?,
    book: LoadedBook,
    layout: ReaderLayout,
    fontSpec: ReaderFontSpec,
    palette: ReaderPalette,
) {
    Box(Modifier.weight(1f).fillMaxHeight()) {
        if (page != null) {
            ReaderPageView(
                book = book,
                layout = layout,
                page = page,
                fontSpec = fontSpec,
                palette = palette,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ReaderTextPage(
    chapterBlocks: List<app.tateyomi.data.Block>,
    content: PageContent.Text,
    settings: ReaderSettings,
    fontSpec: ReaderFontSpec,
    palette: ReaderPalette,
    pageNumber: Int,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val marginPx = with(density) { settings.marginDp.dp.toPx() }
    val paints = remember(
        fontSpec.file?.absolutePath,
        fontSpec.family,
        settings.vertical,
        palette.text,
        palette.muted,
    ) {
        ReaderPaints(
            font = fontSpec,
            vertical = settings.vertical,
            textColor = palette.text.toArgb(),
            mutedColor = palette.muted.toArgb(),
        )
    }

    androidx.compose.foundation.Canvas(modifier.fillMaxSize().background(palette.background)) {
        val metrics = computeReaderMetrics(settings, size.width, size.height, marginPx)
        val margin = metrics.margin
        val usableWidth = metrics.usableWidth
        val usableHeight = metrics.usableHeight
        val fontSize = metrics.fontSize
        val linePitch = metrics.linePitch
        val lines = metrics.effectiveLines
        paints.updateFontSize(fontSize)

        val top = margin
        val left = margin
        val right = size.width - margin
        val inlineStart = if (settings.vertical) {
            top + metrics.inlineOffset
        } else {
            left + metrics.inlineOffset
        }

        // The line pitch is an exact multiple of the glyph size. The whole
        // block is centred when 字数／行数 leave spare space.
        val blockSpan = linePitch * lines
        val blockAnchor = if (settings.vertical) {
            right - (usableWidth - blockSpan) / 2f
        } else {
            top + (usableHeight - blockSpan) / 2f
        }

        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            content.lines.forEachIndexed { lineIndex, line ->
                if (line.blank) return@forEachIndexed
                val block = chapterBlocks.getOrNull(line.blockIndex) as? TextBlock ?: return@forEachIndexed
                if (line.end <= line.start) return@forEachIndexed
                val paintSet = paints.forBlock(block)
                val isHeading = block.style == BlockStyle.HEADING ||
                    block.style == BlockStyle.SUBHEADING
                val lineCenter = if (settings.vertical) {
                    blockAnchor - linePitch * (lineIndex + 0.5f)
                } else {
                    blockAnchor + linePitch * (lineIndex + 0.5f)
                }

                var offset = 0
                val lineLength = line.end - line.start
                while (offset < lineLength) {
                    val textIndex = line.start + offset
                    val ch = block.text[textIndex]
                    val paint = if (!isHeading && isBold(block, textIndex)) {
                        paints.boldBody
                    } else {
                        paintSet
                    }
                    val runnableAscii = ch.code in 0x20..0x7E
                    if (runnableAscii) {
                        var runEnd = offset
                        while (runEnd < lineLength &&
                            block.text[line.start + runEnd].code in 0x20..0x7E
                        ) {
                            runEnd++
                        }
                        val run = block.text.substring(line.start + offset, line.start + runEnd)
                        if (run.isNotBlank()) {
                            val cell = line.indent + offset
                            val slots = runEnd - offset
                            if (settings.vertical) {
                                val x = lineCenter
                                val topY = inlineStart + fontSize * cell
                                drawVerticalRun(
                                    native,
                                    run,
                                    x,
                                    topY,
                                    slots * fontSize,
                                    paint,
                                )
                            } else {
                                val x = inlineStart + fontSize * cell
                                val centerY = lineCenter
                                drawHorizontalRun(native, run, x, centerY, slots * fontSize, paint)
                            }
                        }
                        offset = runEnd
                        continue
                    }
                    if (ch != ' ' && ch != '\u3000') {
                        val cell = line.indent + offset
                        if (cell >= 0) {
                            if (settings.vertical) {
                                val x = lineCenter
                                val y = inlineStart + fontSize * (cell + 0.5f)
                                drawVerticalGlyph(native, ch, x, y, paint)
                            } else {
                                val x = inlineStart + fontSize * (cell + 0.5f)
                                val y = lineCenter
                                drawHorizontalGlyph(native, ch, x, y, paint)
                            }
                        }
                    }
                    offset++
                }

                drawRuby(
                    canvas = native,
                    block = block,
                    line = line,
                    lineCenter = lineCenter,
                    inlineStart = inlineStart,
                    settings = settings,
                    paint = paints.ruby,
                    fontSize = fontSize,
                    right = right,
                    margin = margin,
                )
            }

            if (margin >= with(density) { 12.dp.toPx() }) {
                val pagePaint = paints.pageNumber
                pagePaint.textSize = with(density) { 10.sp.toPx() }
                val baseline = size.height - max(4f, margin * 0.32f)
                native.drawText(pageNumber.toString(), size.width / 2f, baseline, pagePaint)
            }
        }
    }
}

@Composable
private fun ReaderImagePage(
    image: ImageBlock,
    bookDir: File,
    settings: ReaderSettings,
    palette: ReaderPalette,
    pageNumber: Int,
    modifier: Modifier,
) {
    val bitmap = rememberBitmap(File(bookDir, "images/${image.file}"), 2048)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(settings.marginDp.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = image.alt.ifBlank { null },
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = palette.muted,
                    strokeWidth = 1.5.dp,
                )
            }
        }
        Text(
            text = pageNumber.toString(),
            color = palette.muted.copy(alpha = 0.7f),
            fontSize = 10.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp),
            textAlign = TextAlign.Center,
        )
    }
}

private fun isBold(block: TextBlock, index: Int): Boolean =
    block.bold.any { index >= it.start && index < it.end }

private fun drawHorizontalGlyph(canvas: Canvas, ch: Char, x: Float, y: Float, paint: Paint) {
    val metrics = paint.fontMetrics
    val baseline = y - (metrics.ascent + metrics.descent) / 2f
    canvas.drawText(ch.toString(), x, baseline, paint)
}

/**
 * Half-width text (Latin words, numbers) is rotated as one continuous run in
 * vertical writing. Drawing each glyph in its own cell would insert gaps
 * between letters; drawing the run and scaling only when it is wider than its
 * allotted cells keeps words readable.
 */
private fun drawVerticalRun(
    canvas: Canvas,
    run: String,
    x: Float,
    topY: Float,
    allocated: Float,
    paint: Paint,
) {
    val oldAlign = paint.textAlign
    val oldScale = paint.textScaleX
    paint.textAlign = Paint.Align.LEFT
    val width = paint.measureText(run)
    if (width > allocated && width > 0f) {
        paint.textScaleX = (allocated / width).coerceAtMost(1f)
    }
    val metrics = paint.fontMetrics
    canvas.save()
    canvas.translate(x, topY)
    canvas.rotate(90f)
    val baseline = -(metrics.ascent + metrics.descent) / 2f
    canvas.drawText(run, 0f, baseline, paint)
    canvas.restore()
    paint.textAlign = oldAlign
    paint.textScaleX = oldScale
}

private fun drawHorizontalRun(
    canvas: Canvas,
    run: String,
    x: Float,
    centerY: Float,
    allocated: Float,
    paint: Paint,
) {
    val oldAlign = paint.textAlign
    val oldScale = paint.textScaleX
    paint.textAlign = Paint.Align.LEFT
    val width = paint.measureText(run)
    if (width > allocated && width > 0f) {
        paint.textScaleX = (allocated / width).coerceAtMost(1f)
    }
    val metrics = paint.fontMetrics
    val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
    canvas.drawText(run, x, baseline, paint)
    paint.textAlign = oldAlign
    paint.textScaleX = oldScale
}

private fun drawVerticalGlyph(canvas: Canvas, ch: Char, x: Float, y: Float, paint: Paint) {
    val metrics = paint.fontMetrics
    val manualRotation = needsManualVerticalRotation(ch)
    if (manualRotation || ch.code in 0x21..0x7E) {
        val oldFeatures = paint.fontFeatureSettings
        if (manualRotation) {
            // System fonts do not always expose a `vert` alternate for these
            // symbols (especially when the glyph comes from a Latin fallback).
            // Draw the horizontal form with the feature disabled, then rotate
            // it so ellipses and dashes run down the column correctly.
            paint.fontFeatureSettings = null
        }
        // Half-width Latin runs are rotated clockwise in Japanese vertical text.
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(90f)
        val baseline = -(metrics.ascent + metrics.descent) / 2f
        canvas.drawText(ch.toString(), 0f, baseline, paint)
        canvas.restore()
        if (manualRotation) paint.fontFeatureSettings = oldFeatures
    } else {
        val baseline = y - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(ch.toString(), x, baseline, paint)
    }
}


private fun drawRuby(
    canvas: Canvas,
    block: TextBlock,
    line: LayoutLine,
    lineCenter: Float,
    inlineStart: Float,
    settings: ReaderSettings,
    paint: Paint,
    fontSize: Float,
    right: Float,
    margin: Float,
) {
    if (block.ruby.isEmpty()) return
    block.ruby.forEach { span ->
        val visibleStart = max(span.start, line.start)
        val visibleEnd = min(span.end, line.end)
        if (visibleEnd <= visibleStart) return@forEach
        val firstCell = line.indent + (visibleStart - line.start)
        val lastCell = line.indent + (visibleEnd - line.start) - 1
        val fullReading = span.text
        if (fullReading.isEmpty()) return@forEach

        // A ruby span that had to be split across lines shows only the part of
        // the reading that belongs to the visible base characters.
        val baseTotal = (span.end - span.start).coerceAtLeast(1)
        val visibleOffset = visibleStart - span.start
        val visibleCount = visibleEnd - visibleStart
        val sliceStart = (fullReading.length * visibleOffset / baseTotal)
            .coerceIn(0, fullReading.length - 1)
        val sliceEnd = ((fullReading.length * (visibleOffset + visibleCount) + baseTotal - 1) / baseTotal)
            .coerceIn(sliceStart + 1, fullReading.length)
        val reading = fullReading.substring(sliceStart, sliceEnd)

        paint.textSize = fontSize * 0.36f
        val rubySize = paint.textSize
        val baseSpanPx: Float
        val centerMain: Float
        if (settings.vertical) {
            baseSpanPx = (lastCell - firstCell + 1) * fontSize
            centerMain = inlineStart + (firstCell + lastCell + 1) * fontSize / 2f
            val x = min(
                right - rubySize * 0.2f,
                lineCenter + fontSize * 0.46f,
            )
            val spacing = min(rubySize * 1.15f, baseSpanPx / reading.length)
            val total = spacing * reading.length
            var y = centerMain - total / 2f + spacing / 2f
            reading.forEach { ch ->
                drawHorizontalGlyph(canvas, ch, x, y, paint)
                y += spacing
            }
        } else {
            baseSpanPx = (lastCell - firstCell + 1) * fontSize
            centerMain = inlineStart + (firstCell + lastCell + 1) * fontSize / 2f
            val y = max(margin + rubySize, lineCenter - fontSize * 0.62f)
            val spacing = min(rubySize * 1.1f, baseSpanPx / reading.length)
            val total = spacing * reading.length
            var x = centerMain - total / 2f + spacing / 2f
            reading.forEach { ch ->
                drawHorizontalGlyph(canvas, ch, x, y, paint)
                x += spacing
            }
        }
    }
}

private class ReaderPaints(
    font: ReaderFontSpec,
    vertical: Boolean,
    private val textColor: Int,
    private val mutedColor: Int,
) {
    private val normal: Typeface = ReaderTypefaces.typeface(font, bold = false)
    private val bold: Typeface = ReaderTypefaces.typeface(font, bold = true)
    private val vertFeature = if (vertical) "vert" else null

    val body: Paint = base(normal)
    val boldBody: Paint = base(normal).apply { isFakeBoldText = true }
    val heading: Paint = base(bold)
    val subheading: Paint = base(bold)
    val caption: Paint = base(normal)
    val ruby: Paint = base(normal).apply { color = (textColor and 0x00FFFFFF) or (0xD8 shl 24) }
    val pageNumber: Paint = base(normal).apply {
        color = (mutedColor and 0x00FFFFFF) or (0xB0 shl 24)
        textAlign = Paint.Align.CENTER
    }

    private fun base(typeface: Typeface): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        color = textColor
        textAlign = Paint.Align.CENTER
        fontFeatureSettings = vertFeature
    }

    fun updateFontSize(size: Float) {
        body.textSize = size
        boldBody.textSize = size
        heading.textSize = size
        subheading.textSize = size * 0.94f
        caption.textSize = size * 0.82f
        ruby.textSize = size * 0.40f
        pageNumber.textSize = min(size * 0.55f, 18f)
    }

    fun forBlock(block: TextBlock): Paint = when (block.style) {
        BlockStyle.HEADING -> heading
        BlockStyle.SUBHEADING -> subheading
        BlockStyle.CAPTION -> caption
        else -> body
    }
}
