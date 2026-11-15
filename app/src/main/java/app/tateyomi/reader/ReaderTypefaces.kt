package app.tateyomi.reader

import android.graphics.Typeface
import java.util.concurrent.ConcurrentHashMap

object ReaderTypefaces {
    private val cache = ConcurrentHashMap<String, Typeface>()

    fun typeface(font: ReaderFontSpec, bold: Boolean): Typeface {
        val key = "${font.file?.absolutePath ?: font.family ?: "serif"}:$bold"
        return cache.getOrPut(key) {
            val base = font.file?.let { file ->
                runCatching { Typeface.createFromFile(file) }.getOrNull()
            } ?: Typeface.create(font.family ?: "serif", Typeface.NORMAL)
            if (bold) Typeface.create(base, Typeface.BOLD) else base
        }
    }
}
