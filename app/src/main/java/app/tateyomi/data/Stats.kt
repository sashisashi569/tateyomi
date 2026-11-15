package app.tateyomi.data

import kotlinx.serialization.Serializable

@Serializable
data class BookStats(
    val seconds: Long = 0,
    val chars: Long = 0,
    val pages: Long = 0,
    val lastReadAt: Long = 0,
)

/**
 * All counters are monotonic. Daily maps use ISO dates ("2026-11-15") so they
 * stay readable in the JSON file and are easy to chart.
 */
@Serializable
data class ReadingStats(
    val version: Int = 1,
    val totalSeconds: Long = 0,
    val totalChars: Long = 0,
    val totalPages: Long = 0,
    val dailySeconds: Map<String, Long> = emptyMap(),
    val dailyChars: Map<String, Long> = emptyMap(),
    val books: Map<String, BookStats> = emptyMap(),
)
