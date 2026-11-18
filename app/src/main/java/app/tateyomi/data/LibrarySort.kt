package app.tateyomi.data

import kotlinx.serialization.Serializable

/**
 * Library ordering. Persisted in settings.json so it survives restarts.
 * [RANDOM] is only stable per process; it is reseeded on every launch.
 */
@Serializable
enum class LibrarySort {
    RECENT,
    ADDED_DESC,
    ADDED_ASC,
    TITLE,
    TITLE_DESC,
    AUTHOR,
    AUTHOR_DESC,
    RANDOM,
}
