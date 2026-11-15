package app.tateyomi.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tateyomi.data.BookSummary
import app.tateyomi.data.BookStats
import app.tateyomi.data.ReadingStats
import app.tateyomi.ui.theme.Moss
import app.tateyomi.ui.theme.MossLight
import app.tateyomi.ui.theme.MossLighter
import app.tateyomi.ui.theme.Paper
import app.tateyomi.util.currentReadingStreak
import app.tateyomi.util.formatCharCount
import app.tateyomi.util.formatDuration
import app.tateyomi.util.formatReadingTimeShort
import app.tateyomi.util.japaneseWeekday
import app.tateyomi.util.lastNDays
import app.tateyomi.util.longestReadingStreak
import java.time.LocalDate

@Composable
fun StatsScreen(
    books: List<BookSummary>,
    stats: ReadingStats,
    onBack: () -> Unit,
) {
    val today = rememberToday()
    val todaySeconds = stats.dailySeconds[today.toString()] ?: 0L
    val streak = currentReadingStreak(stats, today)
    val longestStreak = longestReadingStreak(stats)
    val finished = books.count { (it.progress?.overallFraction ?: 0f) >= 0.995f }
    val readingDays = stats.dailySeconds.count { it.value > 0 }
    val week = lastNDays(7, today)
    val weekSeconds = week.sumOf { stats.dailySeconds[it.toString()] ?: 0L }
    val perBook = books.mapNotNull { book ->
        stats.books[book.id]?.takeIf { it.seconds > 0 }?.let { book to it }
    }.sortedByDescending { it.second.seconds }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "戻る")
            }
            Column(Modifier.padding(start = 2.dp)) {
                Text(
                    text = "読書統計",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 28.sp,
                )
                Text(
                    text = "READING STATS",
                    fontSize = 8.sp,
                    letterSpacing = 1.4.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "${books.size}冊",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(14.dp))
        HeroCard(
            todaySeconds = todaySeconds,
            totalSeconds = stats.totalSeconds,
            totalChars = stats.totalChars,
            readingDays = readingDays,
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(
                value = "${streak}日",
                label = "連続読書記録",
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                value = "${finished}冊",
                label = "読了",
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                value = formatCharCount(stats.totalChars),
                label = "文字数",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(16.dp))
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "直近7日間",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatReadingTimeShort(weekSeconds),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(18.dp))
                WeeklyChart(
                    days = week,
                    seconds = week.map { stats.dailySeconds[it.toString()] ?: 0L },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "本ごとの読書",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "最長連続 ${longestStreak}日",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (perBook.isEmpty()) {
                    Text(
                        text = "まだ読書記録がありません。本を開くと、読書時間が記録されます。",
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    perBook.forEachIndexed { index, (book, bookStats) ->
                        if (index > 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp)
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant),
                            )
                        }
                        BookStatsRow(book, bookStats)
                    }
                }
            }
        }

        if (stats.totalSeconds == 0L) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "読書時間は、本を開いているあいだ自動的に記録されます。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun HeroCard(
    todaySeconds: Long,
    totalSeconds: Long,
    totalChars: Long,
    readingDays: Int,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Moss, MossLight, MossLighter),
                    ),
                )
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            Column {
                Text(
                    text = "今日の読書",
                    fontSize = 11.sp,
                    letterSpacing = 1.2.sp,
                    color = Paper.copy(alpha = 0.72f),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formatDuration(todaySeconds),
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 38.sp,
                    color = Paper,
                )
                Spacer(Modifier.height(16.dp))
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "累計",
                            fontSize = 9.sp,
                            color = Paper.copy(alpha = 0.6f),
                        )
                        Text(
                            text = formatReadingTimeShort(totalSeconds),
                            fontSize = 13.sp,
                            color = Paper,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "読書日数",
                            fontSize = 9.sp,
                            color = Paper.copy(alpha = 0.6f),
                        )
                        Text(
                            text = "${readingDays}日",
                            fontSize = 13.sp,
                            color = Paper,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "文字数",
                            fontSize = 9.sp,
                            color = Paper.copy(alpha = 0.6f),
                        )
                        Text(
                            text = formatCharCount(totalChars),
                            fontSize = 13.sp,
                            color = Paper,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Text(
                text = value,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeeklyChart(days: List<LocalDate>, seconds: List<Long>) {
    val maxSeconds = (seconds.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val today = LocalDate.now()
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEachIndexed { index, day ->
            val sec = seconds.getOrElse(index) { 0L }
            val ratio = (sec.toFloat() / maxSeconds.toFloat()).coerceIn(0f, 1f)
            val isToday = day == today
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = if (sec > 0) "${(sec + 59) / 60}" else "",
                    fontSize = 8.sp,
                    color = if (isToday) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .height(72.dp)
                        .width(18.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height((4 + 68 * ratio).dp)
                            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                            .background(
                                if (isToday) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                                },
                            ),
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = japaneseWeekday(day),
                    fontSize = 10.sp,
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isToday) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun BookStatsRow(book: BookSummary, stats: BookStats) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFFEAE0CE), Color(0xFFDCCDB4)),
                        ),
                    ),
            ) {
                Text(
                    text = "縦",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 10.sp,
                    color = Color(0xFF5C5244),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatReadingTimeShort(stats.seconds),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${book.percent}%",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Spacer(Modifier.height(7.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((book.progress?.overallFraction ?: 0f).coerceIn(0f, 1f))
                    .background(MaterialTheme.colorScheme.secondary),
            )
        }
    }
}

@Composable
private fun rememberToday(): LocalDate {
    val today = LocalDate.now()
    return androidx.compose.runtime.remember(today) { today }
}
