package co.adityarajput.notifilter.utils

import co.adityarajput.notifilter.data.models.*
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Predict the first moment at which no enabled DELAY/BATCH filter that matches this
 * notification is scheduled to hold it. The Android snooze already committed is a hard
 * lower bound; schedule edits can extend the forecast, but can never move it before it.
 * null means the current weekly schedules form an endless continuous chain.
 */
fun predictPendingRelease(
    notification: Notification,
    committedUntil: Long,
    filters: List<Filter>,
): Long? {
    val zone = ZoneId.systemDefault()
    var cursor = Instant.ofEpochMilli(committedUntil).atZone(zone).withSecond(0).withNano(0)
    val minimum = committedUntil
    val seen = mutableSetOf<Pair<Int, Int>>()

    while (true) {
        val state = (cursor.dayOfWeek.value % 7 + 1) to (cursor.hour * 60 + cursor.minute)
        if (!seen.add(state)) return null

        val active = filters
            .asSequence()
            .filter { it.enabled && it.action.hasMeaningfulPendingNotifications }
            .filter { it.app == Any || it.app.packageName == notification.origin }
            .filter { it.matchesTextOf(notification) }
            .filter { it.schedule.includes(cursor) }
            .minByOrNull { it.priority }
            ?: return maxOf(minimum, cursor.toInstant().toEpochMilli())

        val end = active.schedule.endOfActiveRange(cursor)
            ?: return maxOf(minimum, cursor.toInstant().toEpochMilli())
        if (!end.isAfter(cursor)) return maxOf(minimum, cursor.toInstant().toEpochMilli())
        cursor = end
    }
}

data class PendingReleaseForecast(val committedUntil: ZonedDateTime, val predictedRelease: ZonedDateTime?) {
    val isContinuous: Boolean get() = predictedRelease == null
    fun withPrediction(candidate: ZonedDateTime?): PendingReleaseForecast {
        val bounded = candidate?.let { if (it.isBefore(committedUntil)) committedUntil else it }
        return copy(predictedRelease = bounded)
    }
}

fun pendingReleaseLowerBound(committedUntil: ZonedDateTime) = PendingReleaseForecast(committedUntil, committedUntil)
