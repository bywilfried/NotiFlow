package co.adityarajput.notifilter.services

import android.content.Context
import android.service.notification.StatusBarNotification
import co.adityarajput.notifilter.data.models.Notification
import co.adityarajput.notifilter.data.models.PendingNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Persistent NotiFlow pending state. Android snoozes report presence, not ownership. */
object PendingNotificationRegistry {
    private const val PREFS = "notiflow_pending_notifications"
    private const val KEY = "attributions"

    private val _entries = MutableStateFlow<Map<String, PendingNotification>>(emptyMap())
    val entries: StateFlow<Map<String, PendingNotification>> = _entries.asStateFlow()
    private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        _entries.value = runCatching {
            if (raw.isNullOrBlank()) emptyMap() else {
                val array = JSONArray(raw)
                buildMap {
                    for (i in 0 until array.length()) {
                        val o = array.getJSONObject(i)
                        // Legacy entries did not contain a notification snapshot and cannot be
                        // restored safely. New entries are self-contained after their first record.
                        if (!o.has("notification")) continue
                        val n = o.getJSONObject("notification")
                        val pending = PendingNotification(
                            key = o.getString("key"),
                            filterId = o.getInt("filterId"),
                            notification = Notification(
                                title = n.optString("title"),
                                content = n.optString("content"),
                                origin = n.optString("origin"),
                                timestamp = n.optLong("timestamp"),
                            ).restoreMatchingData(
                                subText = n.optString("subText"),
                                bigText = n.optString("bigText"),
                                summaryText = n.optString("summaryText"),
                                textLines = n.optString("textLines"),
                                conversationTitle = n.optString("conversationTitle"),
                                channel = n.optString("channel"),
                                channelName = n.optString("channelName"),
                                contextualData = n.optString("contextualData"),
                                tag = n.optString("tag"),
                                group = n.optString("group"),
                                category = n.optString("category"),
                                shortcut = n.optString("shortcut"),
                            ),
                            snoozedAt = o.getLong("snoozedAt"),
                            committedUntil = o.getLong("committedUntil"),
                            androidPresent = o.optBoolean("androidPresent", true),
                        )
                        put(pending.key, pending)
                    }
                }
            }
        }.getOrDefault(emptyMap())
        initialized = true
    }

    @Synchronized
    fun record(context: Context, sbn: StatusBarNotification, filterId: Int, snoozedAt: Long, committedUntil: Long) {
        initialize(context)
        _entries.value = _entries.value + (sbn.key to PendingNotification.from(sbn, filterId, snoozedAt, committedUntil))
        persist(context)
    }

    @Synchronized
    fun remove(context: Context, key: String) {
        initialize(context)
        _entries.value = _entries.value - key
        persist(context)
    }

    @Synchronized
    fun removeAll(context: Context, keys: Collection<String>) {
        if (keys.isEmpty()) return
        initialize(context)
        _entries.value = _entries.value - keys.toSet()
        persist(context)
    }

    /**
     * Keep NotiFlow's pending record even if Android no longer reports the snooze. Originating apps
     * can cancel or replace snoozed notifications, so absence from Android is only a state change.
     */
    @Synchronized
    fun reconcile(context: Context, snoozed: Array<StatusBarNotification>) {
        initialize(context)
        val live = snoozed.associateBy { it.key }
        _entries.value = _entries.value.mapValues { (key, pending) ->
            live[key]?.let {
                PendingNotification.from(it, pending.filterId, pending.snoozedAt, pending.committedUntil)
            } ?: pending.copy(androidPresent = false)
        }
        persist(context)
    }

    fun all(): List<PendingNotification> = entries.value.values.toList()
    fun forFilter(filterId: Int): List<PendingNotification> = entries.value.values.filter { it.filterId == filterId }
    fun countForFilter(filterId: Int): Int = entries.value.values.count { it.filterId == filterId }

    private fun persist(context: Context) {
        val array = JSONArray()
        _entries.value.values.forEach { pending -> array.put(JSONObject().apply {
            put("key", pending.key)
            put("filterId", pending.filterId)
            put("snoozedAt", pending.snoozedAt)
            put("committedUntil", pending.committedUntil)
            put("androidPresent", pending.androidPresent)
            put("notification", JSONObject().apply {
                put("title", pending.notification.title)
                put("content", pending.notification.content)
                put("origin", pending.notification.origin)
                put("timestamp", pending.notification.timestamp)
                put("subText", pending.notification.subText)
                put("bigText", pending.notification.bigText)
                put("summaryText", pending.notification.summaryText)
                put("textLines", pending.notification.textLines)
                put("conversationTitle", pending.notification.conversationTitle)
                put("channel", pending.notification.channel)
                put("channelName", pending.notification.channelName)
                put("contextualData", pending.notification.contextualData)
                put("tag", pending.notification.tag)
                put("group", pending.notification.group)
                put("category", pending.notification.category)
                put("shortcut", pending.notification.shortcut)
            })
        }) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}
