package co.adityarajput.notifilter.views.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import co.adityarajput.notifilter.R
import co.adityarajput.notifilter.data.models.NotificationField
import co.adityarajput.notifilter.data.models.NotificationFieldCriterion
import co.adityarajput.notifilter.data.models.RegexTarget
import co.adityarajput.notifilter.viewmodels.FormError
import co.adityarajput.notifilter.viewmodels.UpsertFilterViewModel
import co.adityarajput.notifilter.views.components.ErrorText
import co.adityarajput.notifilter.views.components.WarningText

private fun NotificationField.labelRes(): Int = when (this) {
    NotificationField.TITLE -> R.string.notification_field_title
    NotificationField.CONTENT -> R.string.notification_field_content
    NotificationField.SUB_TEXT -> R.string.notification_field_subtext
    NotificationField.BIG_TEXT -> R.string.notification_field_big_text
    NotificationField.SUMMARY_TEXT -> R.string.notification_field_summary
    NotificationField.TEXT_LINES -> R.string.notification_field_text_lines
    NotificationField.CONVERSATION_TITLE -> R.string.notification_field_conversation
    NotificationField.CHANNEL -> R.string.notification_field_channel
}

@Composable
fun PatternPage(viewModel: UpsertFilterViewModel) {
    val values = viewModel.state.values
    val config = values.searchConfig
    val selectedCount = config.criteria.size

    Text(
        stringResource(R.string.pattern_page_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Normal,
    )

    MatchModeRow(
        selected = values.regexTarget == RegexTarget.ALL,
        label = R.string.match_all_notifications,
    ) {
        viewModel.updateForm(
            viewModel.state.page,
            values.copy(regexTarget = RegexTarget.ALL),
        )
    }

    MatchModeRow(
        selected = values.regexTarget == RegexTarget.SEARCH_FIELDS,
        label = R.string.match_search_notification,
    ) {
        viewModel.updateForm(
            viewModel.state.page,
            values.copy(regexTarget = RegexTarget.SEARCH_FIELDS),
        )
    }

    AnimatedVisibility(values.regexTarget == RegexTarget.SEARCH_FIELDS) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = dimensionResource(R.dimen.padding_large)),
            Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
        ) {
            val canSelectAllFields = config.criteria.isEmpty()
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = config.allFields,
                        enabled = canSelectAllFields || config.allFields,
                    ) { checked ->
                        viewModel.updateForm(
                            viewModel.state.page,
                            values.copy(
                                searchConfig = config.copy(
                                    allFields = checked,
                                    criteria = if (checked) emptyList() else config.criteria,
                                ),
                            ),
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = config.allFields,
                    onCheckedChange = null,
                    enabled = canSelectAllFields || config.allFields,
                )
                Column(Modifier.padding(start = dimensionResource(R.dimen.padding_small))) {
                    Text(
                        stringResource(R.string.search_all_fields),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        stringResource(R.string.search_all_fields_hint),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            AnimatedVisibility(config.allFields) {
                OutlinedTextField(
                    value = config.allFieldsPattern,
                    onValueChange = { pattern ->
                        viewModel.updateForm(
                            viewModel.state.page,
                            values.copy(
                                searchConfig = config.copy(allFieldsPattern = pattern),
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.search_term)) },
                    placeholder = { Text(stringResource(R.string.pattern_placeholder)) },
                    singleLine = true,
                )
            }

            Text(
                stringResource(R.string.search_individual_fields),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )

            NotificationField.entries.forEach { field ->
                val criterion = config.criteria.firstOrNull { it.field == field }
                val selected = criterion != null
                val individualEnabled = !config.allFields

                Column(
                    Modifier.fillMaxWidth(),
                    Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = selected,
                                enabled = individualEnabled,
                            ) { checked ->
                                val updatedCriteria = if (checked) {
                                    config.criteria + NotificationFieldCriterion(field = field)
                                } else {
                                    config.criteria.filterNot { it.field == field }
                                }
                                viewModel.updateForm(
                                    viewModel.state.page,
                                    values.copy(
                                        searchConfig = config.copy(
                                            allFields = false,
                                            criteria = updatedCriteria,
                                        ),
                                    ),
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = null,
                            enabled = individualEnabled,
                        )
                        Text(
                            stringResource(field.labelRes()),
                            Modifier
                                .weight(1f)
                                .padding(start = dimensionResource(R.dimen.padding_small)),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        if (selected && selectedCount > 1) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = criterion?.required == true,
                                    onCheckedChange = { required ->
                                        val updated = config.criteria.map {
                                            if (it.field == field) it.copy(required = required) else it
                                        }
                                        viewModel.updateForm(
                                            viewModel.state.page,
                                            values.copy(
                                                searchConfig = config.copy(criteria = updated),
                                            ),
                                        )
                                    },
                                )
                                Text(
                                    stringResource(R.string.search_required),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }

                    AnimatedVisibility(selected) {
                        OutlinedTextField(
                            value = criterion?.pattern.orEmpty(),
                            onValueChange = { pattern ->
                                val updated = config.criteria.map {
                                    if (it.field == field) it.copy(pattern = pattern) else it
                                }
                                viewModel.updateForm(
                                    viewModel.state.page,
                                    values.copy(
                                        searchConfig = config.copy(criteria = updated),
                                    ),
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = dimensionResource(R.dimen.padding_large)),
                            label = { Text(stringResource(R.string.search_term)) },
                            placeholder = { Text(stringResource(R.string.pattern_placeholder)) },
                            singleLine = true,
                        )
                    }
                }
            }

            AnimatedVisibility(selectedCount > 1) {
                Text(
                    stringResource(R.string.search_required_hint),
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            Text(
                AnnotatedString.fromHtml(
                    stringResource(R.string.pattern_advice),
                    TextLinkStyles(
                        SpanStyle(
                            MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ),
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Normal,
            )
        }
    }

    HorizontalDivider()

    MatchModeRow(
        selected = values.regexTarget == RegexTarget.EXPRESSION,
        label = R.string.match_advanced_expression,
        supporting = R.string.expression_users_hint,
    ) {
        viewModel.updateForm(
            viewModel.state.page,
            values.copy(regexTarget = RegexTarget.EXPRESSION),
        )
    }

    AnimatedVisibility(values.regexTarget == RegexTarget.EXPRESSION) {
        Column(
            Modifier.fillMaxWidth(),
            Arrangement.spacedBy(dimensionResource(R.dimen.padding_medium)),
        ) {
            OutlinedTextField(
                values.queryPattern,
                {
                    viewModel.updateForm(
                        viewModel.state.page,
                        values.copy(queryPattern = it),
                    )
                },
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.expression)) },
                placeholder = { Text(stringResource(R.string.expression_placeholder)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            )

            Text(
                AnnotatedString.fromHtml(
                    stringResource(R.string.expression_advice),
                    TextLinkStyles(
                        SpanStyle(
                            MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ),
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Normal,
            )
        }
    }

    if (viewModel.state.error == FormError.INVALID_NOTIFICATION_REGEX) ErrorText(R.string.invalid_regex)
    if (viewModel.state.error == FormError.INVALID_EXPRESSION) ErrorText(R.string.invalid_expression)
    viewModel.state.warnings.forEach { WarningText(it.description) }
}

@Composable
private fun MatchModeRow(
    selected: Boolean,
    label: Int,
    supporting: Int? = null,
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected,
            null,
            Modifier.padding(horizontal = dimensionResource(R.dimen.padding_small)),
        )
        Column {
            Text(
                stringResource(label),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Normal,
            )
            supporting?.let {
                Text(
                    stringResource(it),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
    }
}
