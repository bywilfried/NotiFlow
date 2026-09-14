package co.adityarajput.notifilter.viewmodels

import android.app.Notification.FLAG_GROUP_SUMMARY
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.adityarajput.notifilter.R
import co.adityarajput.notifilter.data.Cache
import co.adityarajput.notifilter.data.Repository
import co.adityarajput.notifilter.data.models.*
import co.adityarajput.notifilter.services.NotificationListener
import co.adityarajput.notifilter.utils.Logger
import co.adityarajput.notifilter.utils.containsMatchIn
import co.adityarajput.notifilter.utils.evaluateAgainst
import co.adityarajput.notifilter.utils.isValidRegex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

private fun Filter.searchConfig(): NotificationSearchConfig = when (regexTarget) {
    RegexTarget.SEARCH_FIELDS -> NotificationSearchConfig.decode(regexPattern)
    RegexTarget.TITLE -> NotificationSearchConfig(
        criteria = listOf(NotificationFieldCriterion(NotificationField.TITLE, regexPattern)),
    )
    RegexTarget.CONTENT -> NotificationSearchConfig(
        criteria = listOf(NotificationFieldCriterion(NotificationField.CONTENT, regexPattern)),
    )
    RegexTarget.OR -> NotificationSearchConfig(
        criteria = listOf(
            NotificationFieldCriterion(NotificationField.TITLE, regexPattern),
            NotificationFieldCriterion(NotificationField.CONTENT, regexPattern),
        ),
    )
    RegexTarget.AND -> NotificationSearchConfig(
        criteria = listOf(
            NotificationFieldCriterion(NotificationField.TITLE, regexPattern, required = true),
            NotificationFieldCriterion(
                NotificationField.CONTENT,
                secondaryRegexPattern ?: "",
                required = true,
            ),
        ),
    )
    RegexTarget.CONTEXT -> NotificationSearchConfig(
        criteria = listOf(
            NotificationFieldCriterion(NotificationField.SUB_TEXT, regexPattern),
            NotificationFieldCriterion(NotificationField.BIG_TEXT, regexPattern),
            NotificationFieldCriterion(NotificationField.SUMMARY_TEXT, regexPattern),
            NotificationFieldCriterion(NotificationField.TEXT_LINES, regexPattern),
            NotificationFieldCriterion(NotificationField.CONVERSATION_TITLE, regexPattern),
        ),
    )
    RegexTarget.CHANNEL -> NotificationSearchConfig(
        criteria = listOf(NotificationFieldCriterion(NotificationField.CHANNEL, regexPattern)),
    )
    else -> NotificationSearchConfig()
}

private fun Filter.editTarget(): RegexTarget = when (regexTarget) {
    RegexTarget.ALL -> RegexTarget.ALL
    RegexTarget.EXPRESSION -> RegexTarget.EXPRESSION
    else -> RegexTarget.SEARCH_FIELDS
}

class UpsertFilterViewModel(
    filter: Filter?,
    private val repository: Repository,
    packageManager: PackageManager,
) : ViewModel() {
    data class State(
        val page: FormPage = FormPage.ZAPPER,
        val values: Values = Values(),
        val error: FormError? = null,
        val warnings: List<FormWarning> = listOf(),
    )

    data class Values(
        val filterId: Int = 0,
        val notification: Notification? = null,
        val app: App = None,
        val regexTarget: RegexTarget = RegexTarget.SEARCH_FIELDS,
        val queryPattern: String = "",
        val secondaryQueryPattern: String = "",
        val searchConfig: NotificationSearchConfig = NotificationSearchConfig(),
        val action: Action = Action.DISMISS,
        val schedule: Schedule = Schedule(),
        val historyEnabled: Boolean = true,
        val widgetEnabled: Boolean = false,
        val priority: Int = 0,
    ) {
        constructor(filter: Filter) : this(
            filterId = filter.id,
            app = filter.app,
            regexTarget = filter.editTarget(),
            queryPattern = if (filter.regexTarget == RegexTarget.EXPRESSION) filter.regexPattern else "",
            searchConfig = filter.searchConfig(),
            action = filter.action,
            schedule = filter.schedule,
            historyEnabled = filter.historyEnabled,
            widgetEnabled = filter.widgetEnabled,
            priority = filter.priority,
        )

        fun toFilter(): Filter {
            val storedPattern = when (regexTarget) {
                RegexTarget.ALL -> ""
                RegexTarget.SEARCH_FIELDS -> searchConfig.encode()
                RegexTarget.EXPRESSION -> queryPattern
                else -> queryPattern
            }

            return Filter(
                app = app,
                regexPattern = storedPattern,
                action = action,
                regexTarget = regexTarget,
                secondaryRegexPattern = null,
                schedule = schedule,
                historyEnabled = historyEnabled,
                widgetEnabled = widgetEnabled,
                id = filterId,
                priority = if (filterId == 0) Int.MAX_VALUE else priority,
            )
        }
    }

    var state by mutableStateOf(
        if (filter == null) State()
        else State(FormPage.PACKAGE, Values(filter), null),
    )

    var visibleApps by mutableStateOf<List<App>>(emptyList())

    var allPackages by mutableStateOf<List<App>>(emptyList())

    var activeNotifications by mutableStateOf(
        if (!NotificationListener.isServiceInitialized) emptyList() else
            NotificationListener.instance
                .activeNotifications
                .filter { it.notification.flags and FLAG_GROUP_SUMMARY == 0 }
                .mapIndexed { i, sbn -> Notification(sbn, id = i) },
    )

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                allPackages = Cache.getAllPackages(packageManager)
                visibleApps = Cache.getVisibleApps(packageManager)
            }

            while (true) {
                activeNotifications =
                    if (!NotificationListener.isServiceInitialized) emptyList() else
                        NotificationListener.instance.activeNotifications
                            .filter { it.notification.flags and FLAG_GROUP_SUMMARY == 0 }
                            .mapIndexed { i, sbn -> Notification(sbn, id = i) }
                delay(500.milliseconds)
            }
        }
    }

    fun updateForm(page: FormPage, values: Values) {
        state = State(page, values, getError(page, values), getWarnings(page, values))
    }

    private fun getError(
        page: FormPage = state.page,
        values: Values = state.values,
    ): FormError? {
        when (page) {
            FormPage.ZAPPER -> return null

            FormPage.PACKAGE -> if (values.app == None) return FormError.BLANK_FIELDS

            FormPage.PATTERN -> when (values.regexTarget) {
                RegexTarget.ALL -> return null

                RegexTarget.SEARCH_FIELDS -> {
                    val config = values.searchConfig
                    if (config.allFields) {
                        if (config.allFieldsPattern.isBlank()) return FormError.BLANK_FIELDS
                        if (!config.allFieldsPattern.isValidRegex()) {
                            return FormError.INVALID_NOTIFICATION_REGEX
                        }
                    } else {
                        if (config.criteria.isEmpty()) return FormError.BLANK_FIELDS
                        if (config.criteria.any { it.pattern.isBlank() }) return FormError.BLANK_FIELDS
                        if (config.criteria.any { !it.pattern.isValidRegex() }) {
                            return FormError.INVALID_NOTIFICATION_REGEX
                        }
                    }
                }

                RegexTarget.EXPRESSION -> {
                    if (values.queryPattern.isBlank()) return FormError.BLANK_FIELDS
                    try {
                        values.queryPattern.evaluateAgainst(values.notification)
                    } catch (_: Exception) {
                        return FormError.INVALID_EXPRESSION
                    }
                }

                else -> {
                    if (values.queryPattern.isBlank()) return FormError.BLANK_FIELDS
                    if (!values.queryPattern.isValidRegex()) return FormError.INVALID_NOTIFICATION_REGEX
                }
            }

            FormPage.ACTION -> {
                if (values.action is Action.TAP_BUTTON) {
                    if (values.action.buttonRegex.isBlank()) return FormError.BLANK_FIELDS

                    if (!values.action.buttonRegex.isValidRegex()) return FormError.INVALID_BUTTON_REGEX
                }

                if (values.action is Action.DEBOUNCE && values.app == Any) {
                    return FormError.CANT_DEBOUNCE_ANY
                }
            }

            FormPage.SCHEDULE -> {
                if (!values.schedule.isRangeValid()) return FormError.INVALID_TIME_RANGE
                if (values.schedule.ranges.values.all { it.isEmpty() }) return FormError.BLANK_FIELDS
            }
        }
        return null
    }

    private fun getWarnings(
        page: FormPage = state.page,
        values: Values = state.values,
    ): List<FormWarning> {
        if (page != FormPage.PATTERN || values.notification == null) return listOf()
        if (values.regexTarget == RegexTarget.ALL || values.regexTarget == RegexTarget.SEARCH_FIELDS) {
            return listOf()
        }

        try {
            val regexTarget = values.regexTarget
            val notification = values.notification

            if (regexTarget == RegexTarget.EXPRESSION) {
                return if (values.queryPattern.evaluateAgainst(notification)) emptyList() else
                    listOf(FormWarning.EXPRESSION_DOESNT_MATCH_NOTIFICATION)
            }

            val warnings = mutableListOf<FormWarning>()
            if (
                regexTarget != RegexTarget.CONTENT
                && !values.queryPattern.containsMatchIn(notification.title)
            ) warnings.add(FormWarning.REGEX_DOESNT_MATCH_TITLE)
            if (
                (regexTarget == RegexTarget.CONTENT || regexTarget == RegexTarget.OR)
                && !values.queryPattern.containsMatchIn(notification.content)
            ) warnings.add(FormWarning.REGEX_DOESNT_MATCH_CONTENT)

            return warnings
        } catch (_: Exception) {
            return listOf()
        }
    }

    suspend fun submitForm() {
        if (getError() == null) {
            val filter = state.values.toFilter()
            Logger.d(
                "FiltersViewModel.submitForm",
                "${if (state.values.filterId == 0) "Adding" else "Updating"} $filter",
            )
            repository.upsert(filter)
        }
    }
}

enum class FormPage {
    ZAPPER, PACKAGE, PATTERN, ACTION, SCHEDULE;

    fun isFirstPage() = this == ZAPPER

    fun isFinalPage() = this == SCHEDULE

    fun next() = entries[ordinal + 1]

    fun previous() = entries[ordinal - 1]
}

enum class FormError {
    BLANK_FIELDS,
    INVALID_NOTIFICATION_REGEX,
    INVALID_EXPRESSION,
    INVALID_BUTTON_REGEX,
    CANT_DEBOUNCE_ANY,
    INVALID_TIME_RANGE,
}

enum class FormWarning(val description: Int) {
    REGEX_DOESNT_MATCH_TITLE(R.string.pattern_doesnt_match_title),
    REGEX_DOESNT_MATCH_CONTENT(R.string.pattern_doesnt_match_content),
    EXPRESSION_DOESNT_MATCH_NOTIFICATION(R.string.expression_doesnt_match_notification),
}
