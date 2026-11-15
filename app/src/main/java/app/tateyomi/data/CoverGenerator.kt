package app.tateyomi.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.io.File

/**
 * Covers are stored as a normalized 600x900 JPEG. If the EPUB has no usable
 * cover we draw a small typographic cover instead of showing a grey box.
 */
object CoverGenerator {

    private const val WIDTH = 600
    private const val HEIGHT = 900

    fun writeCover(original: ByteArray?, title: String, author: String, outFile: File): Boolean {
        return try {
            val bitmap = original?.let { decodeScaled(it) }
            if (bitmap != null) {
                compose(bitmap, outFile)
                bitmap.recycle()
            } else {
                placeholder(title, author, outFile)
            }
            true
        } catch (_: Throwable) {
            runCatching { placeholder(title, author, outFile) }
            true
        }
    }

    private fun decodeScaled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= WIDTH && bounds.outHeight / (sample * 2) >= HEIGHT) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun compose(source: Bitmap, outFile: File) {
        val result = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.rgb(244, 238, 226))

        val scale = minOf(WIDTH.toFloat() / source.width, HEIGHT.toFloat() / source.height)
        val drawWidth = source.width * scale
        val drawHeight = source.height * scale
        val left = (WIDTH - drawWidth) / 2f
        val top = (HEIGHT - drawHeight) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, null, RectF(left, top, left + drawWidth, top + drawHeight), paint)
        write(result, outFile)
        result.recycle()
    }

    private fun placeholder(title: String, author: String, outFile: File) {
        val result = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val background = Paint(Paint.ANTI_ALIAS_FLAG)
        background.shader = LinearGradient(
            0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(),
            intArrayOf(Color.rgb(247, 242, 232), Color.rgb(232, 221, 200)),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), background)

        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.argb(90, 111, 103, 90)
        }
        canvas.drawRect(28f, 28f, WIDTH - 28f, HEIGHT - 28f, border)

        val seal = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(163, 74, 58) }
        canvas.drawRoundRect(RectF(58f, 64f, 128f, 134f), 6f, 6f, seal)
        val sealText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(250, 244, 233)
            textSize = 44f
            typeface = Typeface.create("serif", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("縦", 93f, 114f, sealText)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(36, 33, 28)
            textSize = 48f
            typeface = Typeface.create("serif", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val cleanTitle = title.trim().ifBlank { "無題の本" }
        val columns = cleanTitle.chunked(9).take(4)
        var x = WIDTH - 110f
        columns.forEach { column ->
            var y = 120f
            column.forEach { ch ->
                if (!ch.isWhitespace()) {
                    canvas.drawText(ch.toString(), x, y + titlePaint.textSize, titlePaint)
                }
                y += 58f
            }
            x -= 66f
        }

        val authorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(111, 103, 90)
            textSize = 28f
            typeface = Typeface.create("serif", Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
        }
        if (author.isNotBlank()) {
            canvas.drawText(author.take(26), 58f, HEIGHT - 74f, authorPaint)
        }
        val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(110, 163, 74, 58)
            textSize = 22f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
            letterSpacing = 0.2f
        }
        canvas.drawText("TATEYOMI", 58f, HEIGHT - 38f, markPaint)

        write(result, outFile)
        result.recycle()
    }

    private fun write(bitmap: Bitmap, outFile: File) {
        outFile.parentFile?.mkdirs()
        outFile.outputStream().buffered().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
    }
}
