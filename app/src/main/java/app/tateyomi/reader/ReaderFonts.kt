package app.tateyomi.reader

import app.tateyomi.data.BuiltInFonts
import app.tateyomi.data.ManagedFont
import java.io.File

/** A system family or an imported font file, ready for Typeface creation. */
data class ReaderFontSpec(
    val family: String? = null,
    val file: File? = null,
)

fun resolveFontSpec(
    fontId: String,
    fonts: List<ManagedFont>,
    fileForId: (String) -> File?,
): ReaderFontSpec {
    BuiltInFonts.find(fontId)?.let { builtIn ->
        return ReaderFontSpec(family = builtIn.family ?: "serif")
    }
    val custom = fonts.firstOrNull { it.id == fontId }
    val file = custom?.let { fileForId(it.id) }
    if (file != null) {
        return ReaderFontSpec(file = file)
    }
    return ReaderFontSpec(
        family = BuiltInFonts.find(BuiltInFonts.MINCHO_ID)?.family ?: "serif",
    )
}
