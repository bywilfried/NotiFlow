package co.adityarajput.notifilter.utils

import co.adityarajput.notifilter.data.models.Schedule
import java.time.ZonedDateTime

/**
 * Forecasts the first moment a notification can leave a chain of scheduled ranges.
 *
 * [committedUntil] is the deadline already handed to Android and is a hard lower bound:
 * later schedule edits may change the forecast after that instant, but can never make the
 * displayed release earlier than an already committed Android snooze.
 *
 * Returns null when no release can be found in the search horizon (continuous schedule).
 */
fun Schedule.forecastRelease(
    committedUntil: ZonedDateTime,
    horizonDays: Long = 8,
): ZonedDateTime? {
    var cursor = committedUntil
    val horizon = committedUntil.plusDays(horizonDays)

    while (cursor.isBefore(horizon)) {
        if (!includes(cursor)) return cursor
        val end = endOfActiveRange(cursor) ?: return cursor
        if (!end.isAfter(cursor)) return cursor
        cursor = end
    }

    return null
}
