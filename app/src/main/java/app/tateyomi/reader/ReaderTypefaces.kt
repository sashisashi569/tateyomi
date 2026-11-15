package app.tateyomi.reader

import android.graphics.Typeface
import app.tateyomi.data.FontChoice
import java.util.concurrent.ConcurrentHashMap

object ReaderTypefaces {
    private val cache = ConcurrentHashMap<String, Typeface>()

    fun forChoice(choice: FontChoice, bold: Boolean): Typeface {
        val style = if (bold) Typeface.BOLD else Typeface.NORMAL
        val key = "${choice.name}:$style"
        return cache.getOrPut(key) {
            val family = when (choice) {
                FontChoice.MINCHO -> "serif"
                FontChoice.GOTHIC -> "sans-serif"
                FontChoice.MONOSPACE -> "monospace"
            }
            Typeface.create(family, style)
        }
    }
}
