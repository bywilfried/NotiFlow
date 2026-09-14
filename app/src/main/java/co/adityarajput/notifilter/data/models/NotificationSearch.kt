package co.adityarajput.notifilter.data.models

import co.adityarajput.notifilter.utils.containsMatchIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class NotificationField {
    TITLE,
    CONTENT,
    SUB_TEXT,
    BIG_TEXT,
    SUMMARY_TEXT,
    TEXT_LINES,
    CONVERSATION_TITLE,
    CHANNEL,
}

@Serializable
data class NotificationFieldCriterion(
    val field: NotificationField,
    val pattern: String = "",
    val required: Boolean = false,
)

@Serializable
data class NotificationSearchConfig(
    val allFields: Boolean = false,
    val allFieldsPattern: String = "",
    val criteria: List<NotificationFieldCriterion> = emptyList(),
) {
    fun matches(notification: Notification): Boolean {
        if (allFields) {
            if (allFieldsPattern.isBlank()) return false
            return NotificationField.entries.any {
                allFieldsPattern.containsMatchIn(notification.valueOf(it))
            }
        }

        if (criteria.isEmpty()) return false

        val required = criteria.filter { it.required }
        val optional = criteria.filterNot { it.required }

        if (required.any { !it.pattern.containsMatchIn(notification.valueOf(it.field)) }) {
            return false
        }

        return optional.isEmpty() || optional.any {
            it.pattern.containsMatchIn(notification.valueOf(it.field))
        }
    }

    companion object {
        fun decode(value: String): NotificationSearchConfig =
            runCatching { Json.decodeFromString<NotificationSearchConfig>(value) }
                .getOrDefault(NotificationSearchConfig())
    }
}

fun NotificationSearchConfig.encode(): String = Json.encodeToString(this)
