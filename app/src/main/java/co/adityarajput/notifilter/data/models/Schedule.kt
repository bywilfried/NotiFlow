package co.adityarajput.notifilter.data.models

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import co.adityarajput.notifilter.R
import kotlinx.serialization.Serializable
import java.time.ZonedDateTime
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar

/** Minutes since Monday 00:00. End may exceed 10080 when crossing Sunday. */
@Serializable
data class WeeklySlot(val start: Int, val end: Int) {
    init {
        require(start in 0 until WEEK && end > start && end < start + WEEK) {
            "A slot must last between 1 minute and 167h59"
        }
    }
    companion object { const val WEEK = 7 * 24 * 60 }
}

@Serializable
data class Schedule(
    /** Kept for decoding existing installations. New schedules use slots. */
    val ranges: Map<Int, List<TimeRange>> = emptyMap(),
    val slots: List<WeeklySlot> = emptyList(),
) {
    val description @Composable get() = if (normalizedSlots().isEmpty()) "" else stringResource(R.string.schedule_page_title)

    /** Converts legacy Sunday=1 ... Saturday=7 ranges and joins touching/overlapping pieces. */
    fun normalizedSlots(): List<WeeklySlot> {
        if (slots.isNotEmpty() || ranges.isEmpty()) return slots.sortedBy { it.start }
        val pieces = ranges.flatMap { (day, dayRanges) ->
            val monday = (day + 5) % 7
            dayRanges.filter { it.start in 0..1439 && it.end in 1..1440 && it.start < it.end }
                .map { WeeklySlot(monday * 1440 + it.start, monday * 1440 + it.end) }
        }.sortedBy { it.start }
        if (pieces.isEmpty()) return emptyList()
        val merged = mutableListOf<WeeklySlot>()
        pieces.forEach { part ->
            val last = merged.lastOrNull()
            if (last != null && part.start <= last.end)
                merged[merged.lastIndex] = WeeklySlot(last.start, maxOf(last.end, part.end))
            else merged.add(part)
        }
        // The Sunday -> Monday boundary is continuous as well.
        if (merged.size > 1 && merged.first().start == 0 && merged.last().end == WeeklySlot.WEEK) {
            val first = merged.removeAt(0)
            val last = merged.removeAt(merged.lastIndex)
            merged.add(WeeklySlot(last.start, first.end + WeeklySlot.WEEK))
        }
        return merged.sortedBy { it.start }
    }

    fun modernized() = Schedule(slots = normalizedSlots())

    /** Covers the complete weekly circle, including combinations of separate slots. */
    fun coversWholeWeek(): Boolean {
        val parts = normalizedSlots().flatMap {
            if (it.end > WeeklySlot.WEEK)
                listOf(it.start to WeeklySlot.WEEK, 0 to (it.end - WeeklySlot.WEEK))
            else listOf(it.start to it.end)
        }.sortedBy { it.first }
        var covered = 0
        for ((start, end) in parts) {
            if (start > covered) return false
            covered = maxOf(covered, end)
            if (covered >= WeeklySlot.WEEK) return true
        }
        return false
    }

    fun isRangeValid() = normalizedSlots().all {
        it.start in 0 until WeeklySlot.WEEK && it.end > it.start &&
            it.end - it.start < WeeklySlot.WEEK
    } && !coversWholeWeek()

    private fun minuteOfWeek(time: ZonedDateTime) =
        (time.dayOfWeek.value - 1) * 1440 + time.hour * 60 + time.minute

    fun includesNow(calendar: Calendar = Calendar.getInstance()): Boolean {
        val day = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val minute = day * 1440 + calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return normalizedSlots().any { minute in it.start until it.end || minute + WeeklySlot.WEEK in it.start until it.end }
    }

    fun includes(time: ZonedDateTime): Boolean {
        val minute = minuteOfWeek(time)
        return normalizedSlots().any { minute in it.start until it.end || minute + WeeklySlot.WEEK in it.start until it.end }
    }

    /** Earliest end among active slots, extended through touching/overlapping slots. */
    fun endOfActiveRange(now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        val minute = minuteOfWeek(now)
        val intervals = normalizedSlots().flatMap { slot ->
            listOf(slot.start - WeeklySlot.WEEK to slot.end - WeeklySlot.WEEK,
                slot.start to slot.end, slot.start + WeeklySlot.WEEK to slot.end + WeeklySlot.WEEK)
        }
        var end = intervals.filter { minute >= it.first && minute < it.second }
            .maxOfOrNull { it.second } ?: return null
        // Never extend beyond one week. Full coverage is rejected by validation.
        repeat(intervals.size) {
            val next = intervals.filter { it.first <= end && it.second > end }.maxOfOrNull { it.second }
            if (next != null) end = next
        }
        val monday = now.toLocalDate().minusDays((now.dayOfWeek.value - 1).toLong()).atStartOfDay(now.zone)
        return monday.plusMinutes(end.toLong())
    }

    fun predictedRelease(committedUntil: Long): Long? {
        var cursor = Instant.ofEpochMilli(committedUntil).atZone(ZoneId.systemDefault())
        repeat(32) {
            val end = endOfActiveRange(cursor) ?: return maxOf(committedUntil, cursor.toInstant().toEpochMilli())
            if (!end.isAfter(cursor)) return maxOf(committedUntil, cursor.toInstant().toEpochMilli())
            cursor = end
        }
        return null
    }
}
