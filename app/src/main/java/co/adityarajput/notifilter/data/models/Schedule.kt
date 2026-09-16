package co.adityarajput.notifilter.data.models

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import co.adityarajput.notifilter.R
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZonedDateTime
import java.util.Calendar

@Serializable
data class Schedule(val ranges: Map<Int, List<TimeRange>> = (1..7).associateWith { emptyList() }) {
    val description @Composable get() = buildString {
        val activeDays = ranges.filterValues { it.isNotEmpty() }.keys
        when (activeDays) {
            setOf(1, 2, 3, 4, 5, 6, 7) -> append("")
            setOf(2, 3, 4, 5, 6) -> append(stringResource(R.string.on_weekdays))
            setOf(1, 7) -> append(stringResource(R.string.on_weekends))
            else -> append("")
        }
    }

    fun includesNow(calendar: Calendar = Calendar.getInstance()): Boolean {
        val day = calendar.get(Calendar.DAY_OF_WEEK)
        val minute = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return ranges[day].orEmpty().any { minute >= it.start && minute < it.end }
    }

    fun includes(time: ZonedDateTime): Boolean {
        val day = time.dayOfWeek.value % 7 + 1
        val minute = time.hour * 60 + time.minute
        return ranges[day].orEmpty().any { minute >= it.start && minute < it.end }
    }

    fun endOfActiveRange(now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        val day = now.dayOfWeek.value % 7 + 1
        val minute = now.hour * 60 + now.minute
        val range = ranges[day].orEmpty().filter { minute >= it.start && minute < it.end }.maxByOrNull { it.end } ?: return null
        return boundary(now, range.end)
    }

    fun predictedRelease(committedUntil: Long): Long? {
        var cursor = Instant.ofEpochMilli(committedUntil).atZone(java.time.ZoneId.systemDefault()).withSecond(0).withNano(0)
        val minimum = committedUntil
        val seen = mutableSetOf<Pair<Int, Int>>()
        while (true) {
            val day = cursor.dayOfWeek.value % 7 + 1
            val minute = cursor.hour * 60 + cursor.minute
            if (!seen.add(day to minute)) return null
            val touching = ranges[day].orEmpty().filter { (it.start <= minute && minute < it.end) || it.start == minute }.maxByOrNull { it.end }
                ?: return maxOf(minimum, cursor.toInstant().toEpochMilli())
            val next = boundary(cursor, touching.end)
            if (!next.isAfter(cursor)) return maxOf(minimum, cursor.toInstant().toEpochMilli())
            cursor = next
        }
    }

    private fun boundary(base: ZonedDateTime, minute: Int): ZonedDateTime = if (minute == 1440) base.plusDays(1).toLocalDate().atStartOfDay(base.zone) else base.withHour(minute / 60).withMinute(minute % 60).withSecond(0).withNano(0)
    fun isRangeValid() = ranges.values.flatten().all { it.start in 0..1440 && it.end in 0..1440 && it.start < it.end }
}
