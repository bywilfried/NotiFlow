package co.adityarajput.notifilter.utils

import java.time.ZonedDateTime

/**
 * Release prediction state for a snoozed notification.
 * [committedUntil] is the deadline already handed to Android and therefore a hard lower bound.
 * [predictedRelease] may move as future, not-yet-committed schedule ranges are edited.
 * A null prediction represents a schedule with no currently foreseeable release ("Never").
 */
data class PendingReleaseForecast(
    val committedUntil: ZonedDateTime,
    val predictedRelease: ZonedDateTime?,
) {
    val isContinuous: Boolean get() = predictedRelease == null

    fun withPrediction(candidate: ZonedDateTime?): PendingReleaseForecast {
        val bounded = candidate?.let { if (it.isBefore(committedUntil)) committedUntil else it }
        return copy(predictedRelease = bounded)
    }
}

fun pendingReleaseLowerBound(committedUntil: ZonedDateTime) =
    PendingReleaseForecast(
        committedUntil = committedUntil,
        predictedRelease = committedUntil,
    )
