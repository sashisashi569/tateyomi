package app.tateyomi.data

import kotlinx.serialization.Serializable

/** Library ordering. Persisted in settings.json so it survives restarts. */
@Serializable
enum class LibrarySort {
    RECENT,
    ADDED_DESC,
    ADDED_ASC,
    TITLE,
    TITLE_DESC,
    AUTHOR,
    AUTHOR_DESC,
}
