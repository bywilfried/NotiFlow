package co.adityarajput.notifilter.data.models

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduleTest {
    private fun time(day: Int, hour: Int, minute: Int = 0): ZonedDateTime =
        ZonedDateTime.of(2026, 10, 19 + day, hour, minute, 0, 0, ZoneId.of("Europe/Paris"))

    @Test fun weekendIsOneContinuousAndroidSnooze() {
        val schedule = Schedule(slots = listOf(WeeklySlot(4 * 1440 + 1110, 7 * 1440 + 510)))
        assertTrue(schedule.includes(time(5, 0)))
        assertTrue(schedule.includes(time(6, 23)))
        assertTrue(schedule.includes(time(7, 8, 29)))
        assertFalse(schedule.includes(time(7, 8, 30)))
        assertEquals(time(7, 8, 30), schedule.endOfActiveRange(time(5, 0)))
    }

    @Test fun legacyMidnightBoundariesAreMerged() {
        val legacy = Schedule(ranges = mapOf(
            7 to listOf(TimeRange(0, 1440)),
            1 to listOf(TimeRange(0, 1440)),
            2 to listOf(TimeRange(0, 510)),
        ))
        val converted = legacy.modernized()
        assertEquals(1, converted.slots.size)
        assertEquals(5 * 1440, converted.slots.single().start)
        assertEquals(7 * 1440 + 510, converted.slots.single().end)
    }

    @Test fun fullWeekCoverageIsRejectedEvenAcrossMultipleSlots() {
        val schedule = Schedule(slots = listOf(
            WeeklySlot(0, 5000), WeeklySlot(5000, 10080),
        ))
        assertFalse(schedule.isRangeValid())
        assertTrue(schedule.coversWholeWeek())
    }

    @Test fun oneFreeMinuteIsAllowed() {
        assertTrue(Schedule(slots = listOf(WeeklySlot(0, 10079))).isRangeValid())
    }

    @Test fun sundayToMondayBoundaryIsHandled() {
        val schedule = Schedule(slots = listOf(WeeklySlot(6 * 1440 + 1110, WEEK_END)))
        assertTrue(schedule.includes(time(7, 0)))
        assertFalse(schedule.includes(time(7, 8, 30)))
    }

    private companion object { const val WEEK_END = 7 * 1440 + 510 }
}
