package app.tateyomi

import app.tateyomi.data.ReaderSettings
import app.tateyomi.reader.computeReaderMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderMetricsTest {

    @Test
    fun shrinksGlyphsSoRequestedLinesAndSpacingFit() {
        val metrics = computeReaderMetrics(
            settings = ReaderSettings(
                vertical = true,
                charsPerLine = 40,
                linesPerPage = 20,
                lineSpacing = 2.0f,
            ),
            widthPx = 1000f,
            heightPx = 2000f,
            marginPx = 0f,
        )

        // Height allows 50px glyphs, but 20 lines at 2.0 spacing only allow
        // 25px glyphs across the 1000px width.
        assertEquals(25f, metrics.fontSize, 0.001f)
        assertEquals(50f, metrics.linePitch, 0.001f)
        assertEquals(20, metrics.effectiveLines)
        // The 40-character column is 1000px tall, so it is centred in 2000px.
        assertEquals(500f, metrics.inlineOffset, 0.001f)
    }

    @Test
    fun increasingLineCountShrinksEverythingButKeepsSpacingRatio() {
        val base = ReaderSettings(
            vertical = true,
            charsPerLine = 40,
            linesPerPage = 10,
            lineSpacing = 1.5f,
        )
        val many = base.copy(linesPerPage = 30)
        val a = computeReaderMetrics(base, 1000f, 2000f, 0f)
        val b = computeReaderMetrics(many, 1000f, 2000f, 0f)

        assertEquals(10, a.effectiveLines)
        assertEquals(30, b.effectiveLines)
        assertTrue(b.fontSize < a.fontSize)
        assertTrue(b.linePitch < a.linePitch)
        assertEquals(1.5f, a.linePitch / a.fontSize, 0.0001f)
        assertEquals(1.5f, b.linePitch / b.fontSize, 0.0001f)
        assertTrue(b.linePitch * 30 <= 1000f + 0.01f)
    }

    @Test
    fun horizontalUsesWidthForGlyphsAndHeightForLines() {
        val metrics = computeReaderMetrics(
            settings = ReaderSettings(
                vertical = false,
                charsPerLine = 25,
                linesPerPage = 40,
                lineSpacing = 1.6f,
            ),
            widthPx = 1000f,
            heightPx = 1600f,
            marginPx = 0f,
        )

        // Width allows 40px glyphs; 40 lines at 1.6 spacing allow 25px.
        assertEquals(25f, metrics.fontSize, 0.001f)
        assertEquals(40f, metrics.linePitch, 0.001f)
        assertEquals(40, metrics.effectiveLines)
        assertEquals(187.5f, metrics.inlineOffset, 0.001f)
    }
}
