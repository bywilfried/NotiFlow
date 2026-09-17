package co.adityarajput.notifilter.data.models

import android.service.notification.StatusBarNotification

/** Runtime metadata for a notification intentionally snoozed by a NotiFlow filter. */
data class PendingNotification(
    val key: String,
    val filterId: Int,
    val notification: Notification,
    val snoozedAt: Long,
    /** Exact wake-up deadline already handed to Android for the current snooze cycle. */
    val committedUntil: Long,
    /** Whether Android still exposes this notification through snoozedNotifications. */
    val androidPresent: Boolean = true,
) {
    companion object {
        fun from(sbn: StatusBarNotification, filterId: Int, snoozedAt: Long, committedUntil: Long) =
            PendingNotification(
                key = sbn.key,
                filterId = filterId,
                notification = Notification(sbn),
                snoozedAt = snoozedAt,
                committedUntil = committedUntil,
                androidPresent = true,
            )
    }
}
