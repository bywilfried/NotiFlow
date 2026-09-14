package co.adityarajput.notifilter.data.models

import android.app.Notification as AndroidNotification
import android.app.Person
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import co.adityarajput.notifilter.services.NotificationListener
import kotlin.math.abs

@Entity(tableName = "notifications")
data class Notification(
    val title: String,

    val content: String,

    val origin: String,

    val timestamp: Long,

    @ColumnInfo(defaultValue = "1")
    val showInHistory: Boolean = true,

    @ColumnInfo(defaultValue = "0")
    val showInWidget: Boolean = false,

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
) {
    /**
     * Extra matching data is deliberately not persisted in Room yet. It is populated for live
     * notifications and used by the rule matcher, while the existing history schema remains
     * backwards compatible.
     */
    @field:Ignore
    var subText: String = ""
        private set

    @field:Ignore
    var bigText: String = ""
        private set

    @field:Ignore
    var summaryText: String = ""
        private set

    @field:Ignore
    var textLines: String = ""
        private set

    @field:Ignore
    var conversationTitle: String = ""
        private set

    @field:Ignore
    var channel: String = ""
        private set

    @field:Ignore
    var channelName: String = ""
        private set

    @field:Ignore
    var contextualData: String = ""
        private set

    @field:Ignore
    var tag: String = ""
        private set

    @field:Ignore
    var group: String = ""
        private set

    @field:Ignore
    var category: String = ""
        private set

    @field:Ignore
    var shortcut: String = ""
        private set

    constructor(
        sbn: StatusBarNotification,
        showInHistory: Boolean = true,
        showInWidget: Boolean = false,
        id: Int = 0,
        channelName: String = resolveChannelName(sbn),
    ) : this(
        sbn.notification.extras.getString(AndroidNotification.EXTRA_TITLE) ?: "",
        sbn.notification.extras.getCharSequence(AndroidNotification.EXTRA_TEXT)?.toString() ?: "",
        sbn.packageName, sbn.postTime, showInHistory, showInWidget, id,
    ) {
        val extras = sbn.notification.extras
        subText = extras.getCharSequence(AndroidNotification.EXTRA_SUB_TEXT)?.toString() ?: ""
        bigText = extras.getCharSequence(AndroidNotification.EXTRA_BIG_TEXT)?.toString() ?: ""
        summaryText = extras.getCharSequence(AndroidNotification.EXTRA_SUMMARY_TEXT)?.toString() ?: ""
        textLines = extras.getCharSequenceArray(AndroidNotification.EXTRA_TEXT_LINES)
            ?.joinToString("\n") { it.toString() }
            ?: ""
        conversationTitle = extras.getCharSequence(AndroidNotification.EXTRA_CONVERSATION_TITLE)
            ?.toString()
            ?: ""
        channel = sbn.notification.channelId ?: ""
        this.channelName = channelName
        contextualData = extractContextualText(extras)
        tag = sbn.tag ?: ""
        group = sbn.groupKey ?: ""
        category = sbn.notification.category ?: ""
        shortcut = sbn.notification.shortcutId ?: ""
    }

    fun valueOf(field: NotificationField): String = when (field) {
        NotificationField.TITLE -> title
        NotificationField.CONTENT -> content
        NotificationField.SUB_TEXT -> subText
        NotificationField.BIG_TEXT -> bigText
        NotificationField.SUMMARY_TEXT -> summaryText
        NotificationField.TEXT_LINES -> textLines
        NotificationField.CONVERSATION_TITLE -> conversationTitle
        NotificationField.CHANNEL -> listOf(channelName, channel)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
    }

    val data get() = listOf(origin, title, content, timestamp)

    /**
     * Checks whether this notification is the same as another,
     * apart from a time delay (tolerating 10 seconds of further mismatch).
     */
    fun matches(other: Notification, delay: Long = 0) =
        this.origin == other.origin
                && this.title == other.title
                && this.content == other.content
                && abs(abs(this.timestamp - other.timestamp) - delay) < 10 * 1000L

    fun appNameFrom(packages: List<App>) =
        packages.find { it.packageName == origin }?.name ?: origin

    companion object {
        private val primaryTextKeys = setOf(
            AndroidNotification.EXTRA_TITLE,
            AndroidNotification.EXTRA_TEXT,
        )

        private fun resolveChannelName(sbn: StatusBarNotification): String {
            if (!NotificationListener.isServiceInitialized) return ""
            val channelId = sbn.notification.channelId ?: return ""
            return runCatching {
                NotificationListener.instance
                    .getNotificationChannel(sbn.packageName, sbn.user, channelId)
                    ?.name
                    ?.toString()
                    .orEmpty()
            }.getOrDefault("")
        }

        private fun extractContextualText(extras: Bundle): String =
            extras.keySet()
                .asSequence()
                .filterNot { it in primaryTextKeys }
                .flatMap { key -> collectText(extras.get(key)).asSequence() }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString("\n")

        private fun collectText(value: Any?): List<String> = when (value) {
            null -> emptyList()
            is CharSequence -> listOf(value.toString())
            is Person -> listOfNotNull(value.name?.toString())
            is Bundle -> value.keySet().flatMap { collectText(value.get(it)) }
            is Array<*> -> value.flatMap(::collectText)
            is Iterable<*> -> value.flatMap(::collectText)
            else -> emptyList()
        }
    }
}
