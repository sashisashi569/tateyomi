package app.tateyomi.util

import app.tateyomi.data.ReadingStats
import java.time.LocalDate
import java.util.Locale

fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "0分"
    if (seconds < 60) return "${seconds}秒"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}時間${minutes}分"
        hours > 0 -> "${hours}時間"
        minutes > 0 -> "${minutes}分"
        else -> "${seconds}秒"
    }
}

fun formatReadingTimeShort(seconds: Long): String {
    if (seconds <= 0) return "0分"
    val minutes = (seconds + 59) / 60
    if (minutes < 60) return "${minutes}分"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0L) "${hours}時間" else "${hours}時間${rest}分"
}

fun formatCharCount(chars: Long): String {
    if (chars <= 0) return "0字"
    if (chars < 10_000) return "${chars}字"
    val value = chars / 10_000.0
    val formatted = String.format(Locale.JAPAN, "%.1f", value).removeSuffix(".0")
    return "${formatted}万字"
}

fun formatPercent(fraction: Float): String {
    val percent = (fraction * 100f).toInt().coerceIn(0, 100)
    return "$percent%"
}

fun currentReadingStreak(stats: ReadingStats, today: LocalDate = LocalDate.now()): Int {
    var day = today
    if ((stats.dailySeconds[day.toString()] ?: 0L) <= 0) {
        day = day.minusDays(1)
        if ((stats.dailySeconds[day.toString()] ?: 0L) <= 0) return 0
    }
    var streak = 0
    while ((stats.dailySeconds[day.toString()] ?: 0L) > 0) {
        streak++
        day = day.minusDays(1)
    }
    return streak
}

fun longestReadingStreak(stats: ReadingStats): Int {
    val days = stats.dailySeconds.filterValues { it > 0 }.keys
        .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        .sorted()
    var best = 0
    var current = 0
    var previous: LocalDate? = null
    days.forEach { day ->
        current = if (previous != null && previous!!.plusDays(1) == day) current + 1 else 1
        if (current > best) best = current
        previous = day
    }
    return best
}

fun lastNDays(n: Int, today: LocalDate = LocalDate.now()): List<LocalDate> =
    (n - 1 downTo 0).map { today.minusDays(it.toLong()) }

fun japaneseWeekday(day: LocalDate): String =
    arrayOf("月", "火", "水", "木", "金", "土", "日")[day.dayOfWeek.value - 1]
