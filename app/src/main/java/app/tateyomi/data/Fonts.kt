package app.tateyomi.data

import kotlinx.serialization.Serializable

class FontException(message: String) : Exception(message)

@Serializable
data class ManagedFont(
    val id: String,
    val name: String,
    val family: String? = null,
    val fileName: String? = null,
    val builtIn: Boolean = false,
)

object BuiltInFonts {
    const val MINCHO_ID = "system:mincho"
    const val GOTHIC_ID = "system:gothic"
    const val MONOSPACE_ID = "system:monospace"

    val all: List<ManagedFont> = listOf(
        ManagedFont(MINCHO_ID, "明朝", family = "serif", builtIn = true),
        ManagedFont(GOTHIC_ID, "ゴシック", family = "sans-serif", builtIn = true),
        ManagedFont(MONOSPACE_ID, "等幅", family = "monospace", builtIn = true),
    )

    fun find(id: String): ManagedFont? = all.firstOrNull { it.id == id }

    fun idFor(choice: FontChoice): String = when (choice) {
        FontChoice.MINCHO -> MINCHO_ID
        FontChoice.GOTHIC -> GOTHIC_ID
        FontChoice.MONOSPACE -> MONOSPACE_ID
    }

    fun choiceFor(id: String): FontChoice? = when (id) {
        MINCHO_ID -> FontChoice.MINCHO
        GOTHIC_ID -> FontChoice.GOTHIC
        MONOSPACE_ID -> FontChoice.MONOSPACE
        else -> null
    }
}

/** The effective font for settings written before custom fonts existed. */
fun ReaderSettings.effectiveFontId(): String =
    fontId?.takeIf { it.isNotBlank() } ?: BuiltInFonts.idFor(font)

fun fontDisplayName(fontId: String, fonts: List<ManagedFont>): String =
    BuiltInFonts.find(fontId)?.name
        ?: fonts.firstOrNull { it.id == fontId }?.name
        ?: "明朝"
