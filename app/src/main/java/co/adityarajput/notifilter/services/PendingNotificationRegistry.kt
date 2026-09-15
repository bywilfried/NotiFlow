package co.adityarajput.notifilter.services

import android.service.notification.StatusBarNotification
import co.adityarajput.notifilter.data.models.PendingNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Runtime attribution for notifications intentionally held for later delivery by NotiFlow. */
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
            sbn.key to PendingNotification.from(sbn, filterId, snoozedAt, committedUntil)
        )
    }

    fun remove(key: String) {
        _entries.value = _entries.value - key
    }

    /**
     * Android is the source of truth. Local entries absent from getSnoozedNotifications()
     * are removed; Android snoozes unknown to NotiFlow are deliberately not imported.
     */
    fun reconcile(snoozed: Array<StatusBarNotification>) {
        val liveKeys = snoozed.asSequence().map { it.key }.toSet()
        _entries.value = _entries.value.filterKeys { it in liveKeys }
    }

    fun all(): List<PendingNotification> = entries.value.values.toList()

    fun forFilter(filterId: Int): List<PendingNotification> =
        entries.value.values.filter { it.filterId == filterId }

    fun countForFilter(filterId: Int): Int =
        entries.value.values.count { it.filterId == filterId }

    fun clear() {
        _entries.value = emptyMap()
    }
}
