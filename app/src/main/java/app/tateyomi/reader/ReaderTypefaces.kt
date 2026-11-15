package app.tateyomi.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.util.concurrent.ConcurrentHashMap

object ReaderTypefaces {
    private val cache = ConcurrentHashMap<String, Typeface>()
    private val verticalFormsCache = ConcurrentHashMap<String, Set<Char>>()

    fun typeface(font: ReaderFontSpec, bold: Boolean): Typeface {
        val key = "${font.file?.absolutePath ?: font.family ?: "serif"}:$bold"
        return cache.getOrPut(key) {
            val base = font.file?.let { file ->
                runCatching { Typeface.createFromFile(file) }.getOrNull()
            } ?: Typeface.create(font.family ?: "serif", Typeface.NORMAL)
            if (bold) Typeface.create(base, Typeface.BOLD) else base
        }
    }

    /**
     * Characters for which the font actually provides a `vert` alternate.
     * A true vertical font has these; many horizontal fonts do not, so the
     * renderer falls back to rotating the horizontal glyph for those.
     */
    fun verticalFormChars(font: ReaderFontSpec): Set<Char> {
        val key = font.file?.absolutePath ?: font.family ?: "serif"
        return verticalFormsCache.getOrPut(key) {
            val typeface = typeface(font, bold = false)
            ManuallyRotatedInVertical.filterTo(mutableSetOf()) { ch ->
                hasVerticalForm(typeface, ch)
            }
        }
    }

    private fun hasVerticalForm(typeface: Typeface, ch: Char): Boolean {
        val size = 96
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = 64f
            textAlign = Paint.Align.CENTER
            color = Color.BLACK
        }
        fun render(feature: String?): Bitmap {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            paint.fontFeatureSettings = feature
            val metrics = paint.fontMetrics
            val baseline = size / 2f - (metrics.ascent + metrics.descent) / 2f
            canvas.drawText(ch.toString(), size / 2f, baseline, paint)
            return bitmap
        }
        val plain = render(null)
        val vertical = render("vert")
        val same = plain.sameAs(vertical)
        plain.recycle()
        vertical.recycle()
        return !same
    }
}
