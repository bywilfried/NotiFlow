package co.adityarajput.notifilter.services

import android.content.Context
import android.service.notification.StatusBarNotification
import co.adityarajput.notifilter.data.models.PendingNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Persistent NotiFlow attribution, reconciled against Android's real snoozed notifications. */
object PendingNotificationRegistry {
    private const val PREFS = "notiflow_pending_notifications"
    private const val KEY = "attributions"

    private data class Attribution(val filterId: Int, val snoozedAt: Long, val committedUntil: Long)
    private val _entries = MutableStateFlow<Map<String, PendingNotification>>(emptyMap())
    val entries: StateFlow<Map<String, PendingNotification>> = _entries.asStateFlow()
    private var attributions: Map<String, Attribution> = emptyMap()
    private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        attributions = runCatching {
            if (raw.isNullOrBlank()) emptyMap() else {
                val array = JSONArray(raw)
                buildMap {
                    for (i in 0 until array.length()) {
                        val o = array.getJSONObject(i)
                        put(o.getString("key"), Attribution(o.getInt("filterId"), o.getLong("snoozedAt"), o.getLong("committedUntil")))
                    }
                }
            }
        }.getOrDefault(emptyMap())
        initialized = true
    }

    @Synchronized
    fun record(context: Context, sbn: StatusBarNotification, filterId: Int, snoozedAt: Long, committedUntil: Long) {
        initialize(context)
        attributions = attributions + (sbn.key to Attribution(filterId, snoozedAt, committedUntil))
        _entries.value = _entries.value + (sbn.key to PendingNotification.from(sbn, filterId, snoozedAt, committedUntil))
        persist(context)
    }

    @Synchronized
    fun remove(context: Context, key: String) {
        initialize(context)
        attributions = attributions - key
        _entries.value = _entries.value - key
        persist(context)
    }

    /** Android is the source of truth. Rebuild visible entries from persisted NotiFlow attribution. */
    @Synchronized
    fun reconcile(context: Context, snoozed: Array<StatusBarNotification>) {
        initialize(context)
        val live = snoozed.associateBy { it.key }
        val surviving = attributions.filterKeys { it in live }
        attributions = surviving
        _entries.value = surviving.mapNotNull { (key, a) ->
            live[key]?.let { key to PendingNotification.from(it, a.filterId, a.snoozedAt, a.committedUntil) }
        }.toMap()
        persist(context)
    }

    fun all(): List<PendingNotification> = entries.value.values.toList()
    fun forFilter(filterId: Int): List<PendingNotification> = entries.value.values.filter { it.filterId == filterId }
    fun countForFilter(filterId: Int): Int = entries.value.values.count { it.filterId == filterId }

    private fun persist(context: Context) {
        val array = JSONArray()
        attributions.forEach { (key, a) -> array.put(JSONObject().apply {
            put("key", key); put("filterId", a.filterId); put("snoozedAt", a.snoozedAt); put("committedUntil", a.committedUntil)
        }) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}
