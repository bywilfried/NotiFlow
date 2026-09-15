package co.adityarajput.notifilter.services

import android.service.notification.StatusBarNotification
import co.adityarajput.notifilter.data.models.PendingNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory attribution for notifications intentionally snoozed by NotiFlow.
 *
 * Entries are exposed to the UI only after reconciliation with Android's snoozed
 * notifications. This registry deliberately does not persist across service restarts:
 * without a matching live Android snooze, stale local data must never be presented as pending.
 */
object PendingNotificationRegistry {
    private val _entries = MutableStateFlow<Map<String, PendingNotification>>(emptyMap())
    val entries: StateFlow<Map<String, PendingNotification>> = _entries.asStateFlow()

    fun record(
        sbn: StatusBarNotification,
        filterId: Int,
        snoozedAt: Long,
        committedUntil: Long,
    ) {
        _entries.value = _entries.value + (
            sbn.key to PendingNotification.from(
                sbn = sbn,
                filterId = filterId,
                snoozedAt = snoozedAt,
                committedUntil = committedUntil,
            )
        )
    }

    fun remove(key: String) {
        _entries.value = _entries.value - key
    }

    fun reconcile(snoozed: Array<StatusBarNotification>) {
        val liveKeys = snoozed.asSequence().map { it.key }.toSet()
        _entries.value = _entries.value.filterKeys { it in liveKeys }
    }

    fun clear() {
        _entries.value = emptyMap()
    }
}
