package co.adityarajput.notifilter.data.models

import android.service.notification.StatusBarNotification

/**
 * Runtime metadata for a notification intentionally snoozed by a NotiFlow filter.
 *
 * Android remains the source of truth for whether the notification is actually snoozed.
 * This model only keeps the NotiFlow attribution and the snooze deadline already committed
 * to Android so the UI can associate a snoozed notification with its filter.
 */
data class PendingNotification(
    val key: String,
    val filterId: Int,
    val notification: Notification,
    val snoozedAt: Long,
    val committedUntil: Long,
) {
    companion object {
        fun from(
            sbn: StatusBarNotification,
            filterId: Int,
            snoozedAt: Long,
            committedUntil: Long,
        ) = PendingNotification(
            key = sbn.key,
            filterId = filterId,
            notification = Notification(sbn),
            snoozedAt = snoozedAt,
            committedUntil = committedUntil,
        )
    }
}
