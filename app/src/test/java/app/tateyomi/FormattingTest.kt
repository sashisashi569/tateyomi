package app.tateyomi

import app.tateyomi.data.ReadingStats
import app.tateyomi.util.currentReadingStreak
import app.tateyomi.util.formatCharCount
import app.tateyomi.util.formatDuration
import app.tateyomi.util.formatPercent
import app.tateyomi.util.formatReadingTimeShort
import app.tateyomi.util.japaneseWeekday
import app.tateyomi.util.longestReadingStreak
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {

    @Test
    fun formatsDurations() {
        assertEquals("0分", formatDuration(0))
        assertEquals("45秒", formatDuration(45))
        assertEquals("1分", formatDuration(90))
        assertEquals("1時間", formatDuration(3600))
        assertEquals("1時間2分", formatDuration(3720))
        assertEquals("1分", formatReadingTimeShort(30))
        assertEquals("2時間", formatReadingTimeShort(7200))
    }

    @Test
    fun formatsCharacterCounts() {
        assertEquals("0字", formatCharCount(0))
        assertEquals("999字", formatCharCount(999))
        assertEquals("1万字", formatCharCount(10_000))
        assertEquals("1.2万字", formatCharCount(12_345))
    }

    @Test
    fun formatsPercent() {
        assertEquals("0%", formatPercent(0f))
        assertEquals("50%", formatPercent(0.5f))
        assertEquals("100%", formatPercent(1f))
    }

    @Test
    fun readsJapaneseWeekdays() {
        assertEquals("月", japaneseWeekday(LocalDate.of(2026, 11, 16)))
        assertEquals("日", japaneseWeekday(LocalDate.of(2026, 11, 15)))
    }

    @Test
    fun calculatesCurrentStreak() {
        val today = LocalDate.of(2026, 11, 15)
        val stats = ReadingStats(
            dailySeconds = mapOf(
                "2026-11-15" to 10,
                "2026-11-14" to 20,
                "2026-11-13" to 30,
                "2026-11-11" to 40,
            ),
        )
        assertEquals(3, currentReadingStreak(stats, today))
        assertEquals(3, longestReadingStreak(stats))
    }

    @Test
    fun streakAllowsYesterdayWhenTodayIsEmpty() {
        val today = LocalDate.of(2026, 11, 15)
        val stats = ReadingStats(
            dailySeconds = mapOf(
                "2026-11-14" to 20,
                "2026-11-13" to 30,
            ),
        )
        assertEquals(2, currentReadingStreak(stats, today))
    }

    @Test
    fun streakIsZeroWithoutRecentReading() {
        val today = LocalDate.of(2026, 11, 15)
        val stats = ReadingStats(dailySeconds = mapOf("2026-11-01" to 20))
        assertEquals(0, currentReadingStreak(stats, today))
        assertEquals(1, longestReadingStreak(stats))
    }
}
