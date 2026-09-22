package co.adityarajput.notifilter.services

import android.app.AlarmManager
import android.app.Notification.FLAG_GROUP_SUMMARY
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.media.AudioManager
import android.os.Build
import android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import co.adityarajput.notifilter.Constants
import co.adityarajput.notifilter.R
import co.adityarajput.notifilter.data.AppContainer
import co.adityarajput.notifilter.data.Cache
import co.adityarajput.notifilter.data.models.*
import co.adityarajput.notifilter.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit.MILLIS
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class NotificationListener : NotificationListenerService() {
    companion object {
        @Volatile private var _instance: NotificationListener? = null
        var instance: NotificationListener
            get() = _instance ?: throw IllegalStateException("NotificationListener not initialized")
            private set(value) { _instance = value }
        val isServiceInitialized get() = _instance != null
        data class RemovalDiagnostic(val timestamp: Long, val key: String, val packageName: String, val title: String, val reason: Int, val reasonName: String)
        private val _removalDiagnostics = MutableStateFlow<List<RemovalDiagnostic>>(emptyList())
        val removalDiagnostics = _removalDiagnostics.asStateFlow()
        fun clearRemovalDiagnostics() { _removalDiagnostics.value = emptyList() }

        data class SnoozedSnapshotDiagnostic(
            val key: String,
            val packageName: String,
            val title: String,
            val changedFields: List<String>,
        )
        private var snoozedSnapshotBaseline: Map<String, Map<String, String>> = emptyMap()
        private val _snoozedSnapshotDiagnostics = MutableStateFlow<List<SnoozedSnapshotDiagnostic>>(emptyList())
        val snoozedSnapshotDiagnostics = _snoozedSnapshotDiagnostics.asStateFlow()

        fun captureSnoozedSnapshotBaseline() {
            if (!isServiceInitialized) return
            snoozedSnapshotBaseline = instance.snoozedNotifications.associate { it.key to instance.snapshotFields(it) }
            _snoozedSnapshotDiagnostics.value = emptyList()
        }

        fun refreshSnoozedSnapshotDiagnostics() {
            if (!isServiceInitialized || snoozedSnapshotBaseline.isEmpty()) return
            val current = instance.snoozedNotifications.associateBy { it.key }
            _snoozedSnapshotDiagnostics.value = snoozedSnapshotBaseline.mapNotNull { (key, before) ->
                val sbn = current[key] ?: return@mapNotNull SnoozedSnapshotDiagnostic(
                    key, before["package"].orEmpty(), before["title"].orEmpty(), listOf("snoozedNotifications: PRESENT -> ABSENT")
                )
                val after = instance.snapshotFields(sbn)
                val changes = (before.keys + after.keys).distinct().mapNotNull { field ->
                    val old = before[field]
                    val new = after[field]
                    if (old == new) null else "$field: $old -> $new"
                }
                if (changes.isEmpty()) null else SnoozedSnapshotDiagnostic(
                    key, sbn.packageName, after["title"].orEmpty(), changes
                )
            }
        }
        const val NOTIFICATION_SOUND_DURATION = 3000L
        fun createAlertNotificationChannel() { if (instance.notificationManager.getNotificationChannel(Constants.ALERT_NOTIFICATION_CHANNEL_ID) == null) instance.notificationManager.createNotificationChannel(NotificationChannel(Constants.ALERT_NOTIFICATION_CHANNEL_ID, "NotiFilter Alert Service", NotificationManager.IMPORTANCE_HIGH).apply { description = "Required for ALERT Actions" }) }
        fun createReplaceNotificationChannel(filterId: Int, openSettings: Boolean = false) { val channelId = Constants.getReplaceNotificationChannelId(filterId); if (instance.notificationManager.getNotificationChannel(channelId) == null) instance.notificationManager.createNotificationChannel(NotificationChannel(channelId, "NotiFilter Replace Notifications for Filter #$filterId", NotificationManager.IMPORTANCE_HIGH).apply { description = "Required for REPLACE Actions" }); if (openSettings) instance.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, instance.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, channelId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        fun updateForegroundStatus(runInForeground: Boolean): Boolean { if (!isServiceInitialized) return false; if (runInForeground) instance.startForeground() else instance.stopForeground(STOP_FOREGROUND_REMOVE); return true }
    }
    private val serviceJob = SupervisorJob(); private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private val repository by lazy { AppContainer(this).repository }; private val sharedPreferences by lazy { getSharedPreferences(Constants.SETTINGS, MODE_PRIVATE) }
    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }; private val notificationManager by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }; private val alarmManager by lazy { getSystemService(ALARM_SERVICE) as AlarmManager }
    @Volatile private var filters: List<Filter> = emptyList(); @Volatile private var notifications: List<Notification> = emptyList(); @Volatile private var cooldowns: Map<Int, Long> = emptyMap()

    override fun onCreate() { super.onCreate(); instance = this; PendingNotificationRegistry.initialize(this); Logger.i("NotificationListener", "Service created"); if (sharedPreferences.getBoolean(Constants.RUN_IN_FOREGROUND, false)) startForeground(); serviceScope.launch { repository.filters().collectLatest { filters = it } }; serviceScope.launch { notifications = repository.notifications().first() }; serviceScope.launch { while (isActive) { refreshPendingNotifications(); delay(2.seconds) } } }
    fun startForeground() { notificationManager.createNotificationChannel(NotificationChannel(Constants.FOREGROUND_NOTIFICATION_CHANNEL_ID, "NotiFilter Foreground Service", NotificationManager.IMPORTANCE_LOW).apply { enableLights(false); enableVibration(false); setShowBadge(false); setSound(null, null) }); startForeground(Constants.FOREGROUND_NOTIFICATION_ID, NotificationCompat.Builder(this, Constants.FOREGROUND_NOTIFICATION_CHANNEL_ID).setContentTitle(getString(R.string.app_name_launcher)).setContentText(getString(R.string.foreground_notification_content)).setSmallIcon(R.drawable.ic_launcher_foreground).setOngoing(true).setSilent(true).build()) }
    override fun onListenerConnected() { super.onListenerConnected(); requestListenerHints(0); serviceScope.launch { delay(500.milliseconds); reconcilePendingNotifications() } }
    private fun reconcilePendingNotifications() { runCatching { PendingNotificationRegistry.reconcile(this, snoozedNotifications) }.onFailure { Logger.e("NotificationListener", "Failed to reconcile pending notifications", it) } }
    private fun refreshPendingNotifications() {
        runCatching {
            PendingNotificationRegistry.reconcile(this, snoozedNotifications)
            val now = System.currentTimeMillis()
            val expired = PendingNotificationRegistry.all().mapNotNull { item ->
                val release = predictPendingRelease(item.notification, item.committedUntil, filters, item.filterId)
                item.key.takeIf { release != null && release <= now }
            }
            PendingNotificationRegistry.removeAll(this, expired)
        }.onFailure { Logger.e("NotificationListener", "Failed to refresh pending notifications", it) }
    }
    private fun removePending(key: String) = PendingNotificationRegistry.remove(this, key)
    private fun snoozePending(sbn: StatusBarNotification, filter: Filter, duration: Long) { val now = System.currentTimeMillis(); PendingNotificationRegistry.record(this, sbn, filter.id, now, now + duration); snoozeNotification(sbn.key, duration); serviceScope.launch { delay(1500.milliseconds); reconcilePendingNotifications() } }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        super.onNotificationRemoved(sbn, rankingMap, reason)
        val key = sbn?.key ?: return
        val pending = PendingNotificationRegistry.entries.value[key]
        if (pending != null) {
            val title = sbn.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
            val reasonName = removalReasonName(reason)
            _removalDiagnostics.value = (listOf(RemovalDiagnostic(System.currentTimeMillis(), key, sbn.packageName, title, reason, reasonName)) + _removalDiagnostics.value).take(100)
            Logger.i(
                "PendingRemovalDiagnostic",
                "pending notification removed: key=$key package=${sbn.packageName} title=$title reason=$reason ($reasonName)"
            )
        }
    }

    private fun snapshotFields(sbn: StatusBarNotification): Map<String, String> {
        val n = sbn.notification
        val extras = n.extras
        fun pendingIntentSummary(pi: android.app.PendingIntent?): String =
            if (pi == null) "null" else runCatching {
                "creatorPackage=${pi.creatorPackage},creatorUid=${pi.creatorUid},immutable=${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) pi.isImmutable else "n/a"}"
            }.getOrElse { "error:${it.javaClass.simpleName}" }
        val extraValues = extras.keySet().sorted().joinToString("|") { key ->
            val value = runCatching { extras.get(key) }.getOrNull()
            "$key=${when (value) {
                is Array<*> -> value.contentDeepToString()
                is IntArray -> value.contentToString()
                is LongArray -> value.contentToString()
                is BooleanArray -> value.contentToString()
                is CharArray -> value.contentToString()
                else -> value?.toString()
            }}"
        }
        val actions = n.actions?.mapIndexed { index, action ->
            "$index:${action.title}:${pendingIntentSummary(action.actionIntent)}"
        }?.joinToString("|").orEmpty()
        return linkedMapOf(
            "package" to sbn.packageName,
            "title" to extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty(),
            "text" to extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty(),
            "id" to sbn.id.toString(),
            "tag" to sbn.tag.orEmpty(),
            "postTime" to sbn.postTime.toString(),
            "notification.when" to n.`when`.toString(),
            "flags" to n.flags.toString(),
            "group" to n.group.orEmpty(),
            "overrideGroupKey" to sbn.overrideGroupKey.orEmpty(),
            "isGroup" to sbn.isGroup.toString(),
            "isClearable" to sbn.isClearable.toString(),
            "isOngoing" to sbn.isOngoing.toString(),
            "channelId" to (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) n.channelId else "").orEmpty(),
            "category" to n.category.orEmpty(),
            "shortcutId" to (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) n.shortcutId else "").orEmpty(),
            "contentIntent" to pendingIntentSummary(n.contentIntent),
            "deleteIntent" to pendingIntentSummary(n.deleteIntent),
            "fullScreenIntent" to pendingIntentSummary(n.fullScreenIntent),
            "actions" to actions,
            "extras" to extraValues,
        )
    }

    private fun removalReasonName(reason: Int): String = when (reason) {
        REASON_CLICK -> "CLICK"
        REASON_CANCEL -> "CANCEL"
        REASON_CANCEL_ALL -> "CANCEL_ALL"
        REASON_ERROR -> "ERROR"
        REASON_PACKAGE_CHANGED -> "PACKAGE_CHANGED"
        REASON_USER_STOPPED -> "USER_STOPPED"
        REASON_PACKAGE_BANNED -> "PACKAGE_BANNED"
        REASON_APP_CANCEL -> "APP_CANCEL"
        REASON_APP_CANCEL_ALL -> "APP_CANCEL_ALL"
        REASON_LISTENER_CANCEL -> "LISTENER_CANCEL"
        REASON_LISTENER_CANCEL_ALL -> "LISTENER_CANCEL_ALL"
        REASON_GROUP_SUMMARY_CANCELED -> "GROUP_SUMMARY_CANCELED"
        REASON_GROUP_OPTIMIZATION -> "GROUP_OPTIMIZATION"
        REASON_PACKAGE_SUSPENDED -> "PACKAGE_SUSPENDED"
        REASON_PROFILE_TURNED_OFF -> "PROFILE_TURNED_OFF"
        REASON_UNAUTOBUNDLED -> "UNAUTOBUNDLED"
        REASON_CHANNEL_BANNED -> "CHANNEL_BANNED"
        REASON_SNOOZED -> "SNOOZED"
        REASON_TIMEOUT -> "TIMEOUT"
        REASON_CHANNEL_REMOVED -> "CHANNEL_REMOVED"
        REASON_CLEAR_DATA -> "CLEAR_DATA"
        REASON_ASSISTANT_CANCEL -> "ASSISTANT_CANCEL"
        REASON_LOCKDOWN -> "LOCKDOWN"
        else -> "UNKNOWN"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.notification.flags and FLAG_GROUP_SUMMARY != 0) return
        val wasPending = PendingNotificationRegistry.entries.value.containsKey(sbn.key); val notification = Notification(sbn); val intents = Intents(sbn)
        // A pending entry is NotiFlow history until its theoretical release time. Android may
        // temporarily republish, replace or cancel the underlying snooze, so do not erase that
        // durable trace here. PendingNotificationsScreen expires it at the predicted release.
        val filter = filters.filter { (notification.origin == it.app.packageName || it.app == Any) && it.enabled && it.schedule.includesNow() && it.matchesTextOf(notification) }.minByOrNull { it.priority }
        if (filter == null) { return }
        when (filter.action) {
            is Action.DISMISS -> { dismissNotification(sbn.key, sbn.isClearable) }
            is Action.TAP_NOTIFICATION -> { try { intents.launchMain() } catch (_: Exception) { return } }
            is Action.TAP_BUTTON -> { try { intents.actions.entries.find { filter.action.buttonRegex.containsMatchIn(it.key) }?.value?.send() } catch (_: Exception) { return } }
            is Action.BATCH -> { val zone = ZoneId.systemDefault(); val now = ZonedDateTime.now(zone); val today = now.toLocalDate().atStartOfDay(zone); var batchLength = filter.action.batchLength * 3600000L; if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) batchLength /= 60; val until = batchLength - today.until(now, MILLIS) % batchLength; if (until < 1000L || batchLength - until < 1000L) { if (wasPending) removePending(sbn.key) } else if (notifications.any { it.data == notification.data }) { return } else snoozePending(sbn, filter, min(until, now.until(today.plusDays(1), MILLIS))) }
            is Action.DELAY -> { val zone = ZoneId.systemDefault(); val now = ZonedDateTime.now(zone); val delay = if (filter.action.delayLength != null) filter.action.delayLength * 60000L else { val end = filter.schedule.endOfActiveRange(now) ?: run { return }; now.until(end, MILLIS) }; if (delay < 1000L) { if (wasPending) removePending(sbn.key) } else if (notifications.any { notification.matches(it, delay) }) { return } else snoozePending(sbn, filter, delay) }
            is Action.DEBOUNCE -> { if (!cooldowns.containsKey(filter.id)) { cooldowns += filter.id to System.currentTimeMillis() + filter.action.cooldownLength * 60000L; muteNotificationsWhileCooldown(filter) } else cooldowns += filter.id to System.currentTimeMillis() + filter.action.cooldownLength * 60000L }
            is Action.MUTE -> { serviceScope.launch { delay(300.milliseconds); requestListenerHints(HINT_HOST_DISABLE_NOTIFICATION_EFFECTS); delay(NOTIFICATION_SOUND_DURATION.milliseconds); requestListenerHints(0) } }
            is Action.ALERT -> { notificationManager.notify(Constants.ALERT_NOTIFICATION_ID, NotificationCompat.Builder(this, Constants.ALERT_NOTIFICATION_CHANNEL_ID).setContentTitle(getString(R.string.app_name_launcher)).setSmallIcon(R.drawable.ic_launcher_foreground).setAutoCancel(true).build()) }
            is Action.DISTURB -> { if (!cooldowns.containsKey(filter.id) && audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) { cooldowns += filter.id to System.currentTimeMillis() + filter.action.pauseLength * 60000L; snoozeNotification(sbn.key, 100); disableDNDWhileCooldown(filter) } }
            is Action.DISMISS_STALE -> { serviceScope.launch { var retention = filter.action.retentionLength * 60000L; if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) retention /= 5; alarmManager.sendIntent(this@NotificationListener, retention, sbn.key.hashCode()) { action = Constants.ACTION_DISMISS_STALE; putExtra(Constants.EXTRA_SBN_KEY, sbn.key); putExtra(Constants.EXTRA_SBN_IS_CLEARABLE, sbn.isClearable) } } }
            is Action.REPLACE -> { createReplaceNotificationChannel(filter.id); val packages = Cache.getAllPackages(packageManager); cancelNotification(sbn.key); notificationManager.notify(Constants.getReplaceNotificationId(filter.id), NotificationCompat.Builder(this, Constants.getReplaceNotificationChannelId(filter.id)).setContentTitle(filter.action.titleTemplate.replaceWithNotificationData(notification, packages)).setContentText(filter.action.contentTemplate.replaceWithNotificationData(notification, packages)).setSmallIcon(R.drawable.ic_launcher_foreground).setContentIntent(sbn.notification.contentIntent).build()) }
        }
        serviceScope.launch { repository.registerHit(filter, notification.copy(showInHistory = filter.historyEnabled, showInWidget = filter.widgetEnabled, filterId = filter.id)); Cache.intents[notification.data.hashCode()] = intents; notifications = repository.notifications().first() }
    }
    fun dismissNotification(key: String, isClearable: Boolean) { if (isClearable) try { cancelNotification(key) } catch (_: Throwable) {} else snoozeNotification(key, 18000000L) }
    private fun muteNotificationsWhileCooldown(filter: Filter) { serviceScope.launch { delay(NOTIFICATION_SOUND_DURATION.milliseconds); requestListenerHints(HINT_HOST_DISABLE_NOTIFICATION_EFFECTS); try { while ((cooldowns[filter.id] ?: 0) >= System.currentTimeMillis()) delay(500.milliseconds) } finally { cooldowns -= filter.id; requestListenerHints(0) } } }
    private fun disableDNDWhileCooldown(filter: Filter) { val original = notificationManager.currentInterruptionFilter; val ringer = audioManager.ringerMode; audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL); if (Build.VERSION.SDK_INT <= VANILLA_ICE_CREAM) notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL); serviceScope.launch { try { while ((cooldowns[filter.id] ?: 0) >= System.currentTimeMillis()) delay(500.milliseconds) } finally { cooldowns -= filter.id; audioManager.ringerMode = ringer; if (Build.VERSION.SDK_INT <= VANILLA_ICE_CREAM) notificationManager.setInterruptionFilter(original) } } }
    override fun onListenerDisconnected() { super.onListenerDisconnected(); if (instance == this) _instance = null }
    override fun onDestroy() { super.onDestroy(); if (sharedPreferences.getBoolean(Constants.RUN_IN_FOREGROUND, false)) stopForeground(STOP_FOREGROUND_REMOVE); serviceJob.cancel() }
}
