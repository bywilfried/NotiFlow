package co.adityarajput.notifilter.data.models

import kotlinx.serialization.Serializable

@Serializable
enum class RegexTarget(val description: Int? = null) {
    ALL,

    // Legacy targets kept for backwards compatibility with existing filters/imports.
    TITLE,
    CONTENT,
    OR,
    AND,
    CONTEXT,
    CHANNEL,

    SEARCH_FIELDS,
    EXPRESSION;
}
