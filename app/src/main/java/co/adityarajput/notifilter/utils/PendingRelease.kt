package co.adityarajput.notifilter.utils

import co.adityarajput.notifilter.data.models.Schedule
import java.time.ZonedDateTime

/**
 * Forecasting is intentionally kept separate from the Android snooze deadline.
 * The committed deadline is immutable once handed to Android; future schedule ranges may
 * extend the displayed forecast, but a later edit must never move it before this lower bound.
 */
data class PendingReleaseForecast(
    val committedUntil: ZonedDateTime,
    val predictedRelease: ZonedDateTime?,
) {
    val isContinuous: Boolean get() = predictedRelease == null
}

/** Creates the immutable lower-bound state used by the pending-notification UI. */
fun Schedule.pendingReleaseLowerBound(committedUntil: ZonedDateTime) =
    PendingReleaseForecast(
        committedUntil = committedUntil,
        predictedRelease = committedUntil,
    )
