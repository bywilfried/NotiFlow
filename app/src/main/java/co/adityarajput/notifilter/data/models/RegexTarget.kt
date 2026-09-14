package co.adityarajput.notifilter.data.models

import co.adityarajput.notifilter.R
import kotlinx.serialization.Serializable

@Serializable
enum class RegexTarget(val description: Int? = null) {
    ALL(R.string.all_notifications),
    TITLE(R.string.title),
    CONTENT(R.string.content),
    OR(R.string.title_or_content),
    AND(R.string.title_and_content),
    CONTEXT(R.string.contextual_data),
    CHANNEL(R.string.notification_channel),
    EXPRESSION;
}
