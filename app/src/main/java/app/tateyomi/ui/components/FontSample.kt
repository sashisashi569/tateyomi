package app.tateyomi.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.tateyomi.reader.ReaderFontSpec
import app.tateyomi.reader.ReaderTypefaces

@Composable
fun FontSample(
    font: ReaderFontSpec,
    color: Color,
    modifier: Modifier = Modifier,
    text: String = "あいうえお 漢字 Aa 123",
    fontSize: TextUnit = 16.sp,
) {
    val typeface = remember(font.file?.absolutePath, font.family) {
        ReaderTypefaces.typeface(font, bold = false)
    }
    val density = LocalDensity.current
    val sizePx = with(density) { fontSize.toPx() }
    Canvas(modifier.clipToBounds()) {
        drawIntoCanvas { canvas ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                textSize = sizePx
                this.color = color.toArgb()
            }
            val metrics = paint.fontMetrics
            val baseline = (size.height - (metrics.ascent + metrics.descent)) / 2f
            canvas.nativeCanvas.drawText(text, 0f, baseline, paint)
        }
    }
}
