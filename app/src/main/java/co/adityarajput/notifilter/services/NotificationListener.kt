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
        const val NOTIFICATION_SOUND_DURATION = 3000L
        fun createAlertNotificationChannel() { if (instance.notificationManager.getNotificationChannel(Constants.ALERT_NOTIFICATION_CHANNEL_ID) == null) instance.notificationManager.createNotificationChannel(NotificationChannel(Constants.ALERT_NOTIFICATION_CHANNEL_ID, "NotiFilter Alert Service", NotificationManager.IMPORTANCE_HIGH).apply { description = "Required for ALERT Actions" }) }
        fun createReplaceNotificationChannel(filterId: Int, openSettings: Boolean = false) { val channelId = Constants.getReplaceNotificationChannelId(filterId); if (instance.notificationManager.getNotificationChannel(channelId) == null) instance.notificationManager.createNotificationChannel(NotificationChannel(channelId, "NotiFilter Replace Notifications for Filter #$filterId", NotificationManager.IMPORTANCE_HIGH).apply { description = "Required for REPLACE Actions" }); if (openSettings) instance.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, instance.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, channelId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        fun updateForegroundStatus(runInForeground: Boolean): Boolean { if (!isServiceInitialized) return false; if (runInForeground) instance.startForeground() else instance.stopForeground(STOP_FOREGROUND_REMOVE); return true }
    }
    private val serviceJob = SupervisorJob(); private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private val repository by lazy { AppContainer(this).repository }; private val sharedPreferences by lazy { getSharedPreferences(Constants.SETTINGS, MODE_PRIVATE) }
    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }; private val notificationManager by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }; private val alarmManager by lazy { getSystemService(ALARM_SERVICE) as AlarmManager }
    @Volatile private var filters: List<Filter> = emptyList(); @Volatile private var notifications: List<Notification> = emptyList(); @Volatile private var cooldowns: Map<Int, Long> = emptyMap()

    override fun onCreate() { super.onCreate(); instance = this; PendingNotificationRegistry.initialize(this); Logger.i("NotificationListener", "Service created"); if (sharedPreferences.getBoolean(Constants.RUN_IN_FOREGROUND, false)) startForeground(); serviceScope.launch { repository.filters().collectLatest { filters = it } }; serviceScope.launch { notifications = repository.notifications().first() } }
    fun startForeground() { notificationManager.createNotificationChannel(NotificationChannel(Constants.FOREGROUND_NOTIFICATION_CHANNEL_ID, "NotiFilter Foreground Service", NotificationManager.IMPORTANCE_LOW).apply { enableLights(false); enableVibration(false); setShowBadge(false); setSound(null, null) }); startForeground(Constants.FOREGROUND_NOTIFICATION_ID, NotificationCompat.Builder(this, Constants.FOREGROUND_NOTIFICATION_CHANNEL_ID).setContentTitle(getString(R.string.app_name_launcher)).setContentText(getString(R.string.foreground_notification_content)).setSmallIcon(R.drawable.ic_launcher_foreground).setOngoing(true).setSilent(true).build()) }
    override fun onListenerConnected() { super.onListenerConnected(); requestListenerHints(0); serviceScope.launch { delay(500.milliseconds); reconcilePendingNotifications() } }
    private fun reconcilePendingNotifications() { runCatching { PendingNotificationRegistry.reconcile(this, snoozedNotifications) }.onFailure { Logger.e("NotificationListener", "Failed to reconcile pending notifications", it) } }
    private fun snoozePending(sbn: StatusBarNotification, filter: Filter, duration: Long) { val now = System.currentTimeMillis(); PendingNotificationRegistry.record(this, sbn, filter.id, now, now + duration); snoozeNotification(sbn.key, duration); serviceScope.launch { delay(1500.milliseconds); reconcilePendingNotifications() } }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.notification.flags and FLAG_GROUP_SUMMARY != 0) return
        val notification = Notification(sbn); val intents = Intents(sbn)
        // A pending entry is NotiFlow history until its theoretical release time. Android may
        // temporarily republish, replace or cancel the underlying snooze, so do not erase that
        // durable trace here. PendingNotificationsScreen expires it at the predicted release.
        val filter = filters.filter { (notification.origin == it.app.packageName || it.app == Any) && it.enabled && it.schedule.includesNow() && it.matchesTextOf(notification) }.minByOrNull { it.priority }
        if (filter == null) { return }
        when (filter.action) {
            is Action.DISMISS -> { dismissNotification(sbn.key, sbn.isClearable) }
            is Action.TAP_NOTIFICATION -> { try { intents.launchMain() } catch (_: Exception) { return } }
            is Action.TAP_BUTTON -> { try { intents.actions.entries.find { filter.action.buttonRegex.containsMatchIn(it.key) }?.value?.send() } catch (_: Exception) { return } }
            is Action.BATCH -> { val zone = ZoneId.systemDefault(); val now = ZonedDateTime.now(zone); val today = now.toLocalDate().atStartOfDay(zone); var batchLength = filter.action.batchLength * 3600000L; if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) batchLength /= 60; val until = batchLength - today.until(now, MILLIS) % batchLength; if (until < 1000L || batchLength - until < 1000L) { return } else if (notifications.any { it.data == notification.data }) { return } else snoozePending(sbn, filter, min(until, now.until(today.plusDays(1), MILLIS))) }
            is Action.DELAY -> { val zone = ZoneId.systemDefault(); val now = ZonedDateTime.now(zone); val delay = if (filter.action.delayLength != null) filter.action.delayLength * 60000L else { val end = filter.schedule.endOfActiveRange(now) ?: run { return }; now.until(end, MILLIS) }; if (delay < 1000L) { return } else if (notifications.any { notification.matches(it, delay) }) { return } else snoozePending(sbn, filter, delay) }
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
