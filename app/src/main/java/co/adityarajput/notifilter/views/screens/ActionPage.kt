package co.adityarajput.notifilter.views.screens

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import co.adityarajput.notifilter.R
import co.adityarajput.notifilter.data.models.Action
import co.adityarajput.notifilter.services.NotificationListener
import co.adityarajput.notifilter.utils.*
import co.adityarajput.notifilter.viewmodels.FormError
import co.adityarajput.notifilter.viewmodels.FormPage
import co.adityarajput.notifilter.viewmodels.UpsertFilterViewModel
import co.adityarajput.notifilter.views.components.ErrorText
import kotlinx.coroutines.launch

private val permissions = listOf(
    Permission.ACCESSIBILITY_SERVICE,
    Permission.POST_NOTIFICATIONS,
    Permission.NOTIFICATION_POLICY,
    Permission.SCHEDULE_EXACT_ALARM,
)

@Composable
fun ColumnScope.ActionPage(viewModel: UpsertFilterViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val handler = remember { Handler(Looper.getMainLooper()) }
    var hasPermissions by remember { mutableStateOf(context.isGranted(permissions)) }
    val hasPinnedLogWidget by produceState(initialValue = false) {
        value = context.isLogWidgetUsed()
    }

    val watcher = object : Runnable {
        override fun run() {
            hasPermissions = context.isGranted(permissions)

            if (NotificationListener.isServiceInitialized && hasPermissions.getValue(Permission.POST_NOTIFICATIONS))
                NotificationListener.createAlertNotificationChannel()

            if (!NotificationListener.isServiceInitialized || !hasPermissions.all { it.value })
                handler.postDelayed(this, 500)
        }
    }
    DisposableEffect(Unit) {
        handler.post(watcher)
        onDispose { handler.removeCallbacksAndMessages(null) }
    }

    Text(
        stringResource(R.string.action_page_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Normal,
    )
    Action.entries.forEach {
        Row(
            Modifier
                .fillMaxWidth()
                .selectable((it == viewModel.state.values.action)) {
                    viewModel.updateForm(
                        viewModel.state.page,
                        viewModel.state.values.copy(action = it),
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                viewModel.state.values.action.isOfType(it),
                null,
                Modifier.padding(horizontal = dimensionResource(R.dimen.padding_small)),
            )
            Text(
                it.description(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Normal,
            )
        }
        AnimatedVisibility(
            it is Action.TAP_NOTIFICATION
                    && viewModel.state.values.action is Action.TAP_NOTIFICATION
                    && !hasPermissions.getValue(Permission.ACCESSIBILITY_SERVICE),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = dimensionResource(R.dimen.padding_medium)),
                Arrangement.spacedBy(dimensionResource(R.dimen.padding_medium)),
            ) {
                ErrorText(R.string.accessibility_service_description)
                Button(
                    { context.request(Permission.ACCESSIBILITY_SERVICE) },
                    Modifier.align(Alignment.CenterHorizontally),
                    colors = ButtonDefaults.buttonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                ) {
                    Text(
                        stringResource(R.string.enable_service),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
        }
        AnimatedVisibility(it is Action.TAP_BUTTON && viewModel.state.values.action is Action.TAP_BUTTON) {
            Column(
                Modifier.fillMaxWidth(),
                Arrangement.spacedBy(dimensionResource(R.dimen.padding_medium)),
            ) {
                OutlinedTextField(
                    (viewModel.state.values.action as? Action.TAP_BUTTON)?.buttonRegex ?: "",
                    { value ->
                        viewModel.updateForm(
                            viewModel.state.page,
                            viewModel.state.values.copy(action = Action.TAP_BUTTON(value)),
                        )
                    },
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.button_pattern)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                )
                if (viewModel.state.error == FormError.INVALID_BUTTON_REGEX) ErrorText(R.string.invalid_regex)
            }
        }
        AnimatedVisibility(it is Action.BATCH && viewModel.state.values.action is Action.BATCH) {
            IntegerInput(
                (viewModel.state.values.action as? Action.BATCH)?.batchLength ?: 3,
                1,
                12,
                R.string.batch_frequency,
            ) { value ->
                viewModel.updateForm(
                    viewModel.state.page,
                    viewModel.state.values.copy(action = Action.BATCH(value)),
                )
            }
        }
        AnimatedVisibility(it is Action.DELAY && viewModel.state.values.action is Action.DELAY) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimensionResource(R.dimen.padding_medium)),
                Arrangement.spacedBy(dimensionResource(R.dimen.padding_medium)),
            ) {
                val useDelayFor =
                    (viewModel.state.values.action as? Action.DELAY)?.delayLength != null
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(!useDelayFor) {
                            viewModel.updateForm(
                                viewModel.state.page,
                                viewModel.state.values.copy(action = Action.DELAY()),
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        !useDelayFor,
                        null,
                        Modifier.padding(horizontal = dimensionResource(R.dimen.padding_small)),
                    )
                    Text(
                        stringResource(R.string.delay_while_active),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Normal,
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(useDelayFor) {
                            viewModel.updateForm(
                                viewModel.state.page,
                                viewModel.state.values.copy(action = Action.DELAY(5)),
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        useDelayFor,
                        null,
                        Modifier.padding(horizontal = dimensionResource(R.dimen.padding_small)),
                    )
                    Text(
                        stringResource(R.string.delay_for),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Normal,
                    )
                }
                IntegerInput(
                    (viewModel.state.values.action as? Action.DELAY)?.delayLength ?: 5,
                    1,
                    30,
                    R.string.delay_length,
                ) { value ->
                    viewModel.updateForm(
                        viewModel.state.page,
                        viewModel.state.values.copy(action = Action.DELAY(value)),
                    )
                }
            }
        }
        AnimatedVisibility(it is Action.DEBOUNCE && viewModel.state.values.action is Action.DEBOUNCE) {
            Column(
                Modifier.fillMaxWidth(),
                Arrangement.spacedBy(dimensionResource(R.dimen.padding_medium)),
            ) {
                IntegerInput(
                    (viewModel.state.values.action as? Action.DEBOUNCE)?.cooldownLength ?: 2,
                    1,
                    30,
                    R.string.cooldown_length,
                ) { value ->
                    viewModel.updateForm(
                        viewModel.state.page,
                        viewModel.state.values.copy(action = Action.DEBOUNCE(value)),
                    )
                }
                Text(
                    stringResource(R.string.explain_debounce),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Normal,
                )
                if (viewModel.state.error == FormError.CANT_DEBOUNCE_ANY) ErrorText(R.string.cant_debounce_any)
            }
        }
        AnimatedVisibility(
            it is Action.ALERT
                    && viewModel.state.values.action is Action.ALERT
                    && !hasPermissions.getValue(Permission.POST_NOTIFICATIONS),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = dimensionResource(R.dimen.padding_medium)),
                Arrangement.spacedBy(dimensionResource(R.dimen.padding_medium)),
            ) {
                ErrorText(R.string.alert_notifications_description)
                Button(
                    { context.request(Permission.POST_NOTIFICATIONS) },
                    Modifier.align(Alignment.CenterHorizontally),
                    colors = ButtonDefaults.buttonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                ) {
                    Text(
                        stringResource(R.string.grant_permission),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
        }
        AnimatedVisibility(it is Action.DISTURB && viewModel.state.values.action is Action.DISTURB) {
            Column(
                Modifier.fillMaxWidth(),
                Arrangement.spacedBy(dimensionResource(R.dimen.padding_medium)),
            ) {
                IntegerInput(
                    (viewModel.state.values.action as? Action.DISTURB)?.pauseLength ?: 5,
                    1,
                    30,
                    R.string.pause_length,
                ) { value ->
                    viewModel.updateForm(
                        viewModel.state.page,
                        viewModel.state.values.copy(action = Action.DISTURB(value)),
                    )
                }
                if (!hasPermissions.getValue(Permission.NOTIFICATION_POLICY)) {
                    ErrorText(R.string.notification_policy_permission_description)
                    Button(
                        { context.request(Permission.NOTIFICATION_POLICY) },
                        Modifier.align(Alignment.CenterHorizontally),
                        colors = ButtonDefaults.buttonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                    ) {
                        Text(
                            stringResource(R.string.grant_permission),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                }
            }
        }
        AnimatedVisibility(it is Action.DISMISS_STALE && viewModel.state.values.action is Action.DISMISS_STALE) {
            Column(
                Modifier.fillMaxWidth(),
                Arrangement.spacedBy(dimensionResource(R.dimen.padding_medium)),
            ) {
                IntegerInput(
                    (viewModel.state.values.action as? Action.DISMISS_STALE)?.retentionLength ?: 15,
                    5,
                    300,
                    R.string.retention_length,
                    5,
                    30,
                    3,
                ) { value ->
                    viewModel.updateForm(
                        viewModel.state.page,
                        viewModel.state.values.copy(action = Action.DISMISS_STALE(value)),
                    )
                }
                if (!hasPermissions.getValue(Permission.SCHEDULE_EXACT_ALARM)) {
                    ErrorText(R.string.exact_alarm_permission_description)
                    Button(
                        { context.request(Permission.SCHEDULE_EXACT_ALARM) },
                        Modifier.align(Alignment.CenterHorizontally),
                        colors = ButtonDefaults.buttonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                    ) {
                        Text(
                            stringResource(R.string.disable_optimization),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                }
            }
        }
        AnimatedVisibility(it is Action.READ && viewModel.state.values.action is Action.READ) {
            val action = (viewModel.state.values.action as? Action.READ)
                ?: Action.READ("\${title}: \${content}")
            Column(
                Modifier.fillMaxWidth(),
                Arrangement.spacedBy(dimensionResource(R.dimen.padding_medium)),
            ) {
                OutlinedTextField(
                    action.speechTemplate,
                    { value ->
                        viewModel.updateForm(
                            viewModel.state.page,
                            viewModel.state.values.copy(action = action.copy(speechTemplate = value)),
                        )
                    },
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.speech_template)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                )
                Text(
                    AnnotatedString.fromHtml(
                        stringResource(R.string.notification_template_advice),
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
                val testTtsSentence = stringResource(R.string.test_tts_sentence)
                Button(
                    {
                        coroutineScope.launch {
                            runCatching { TextToSpeech.initialize(context) }
                            if (TextToSpeech.available) {
                                TextToSpeech.speak(testTtsSentence, 0)
                            } else if (TextToSpeech.ready) {
                                Toast.makeText(context, context.getString(R.string.tts_not_available), Toast.LENGTH_SHORT).show()
                                TextToSpeech.openInstallationScreen(context)
                            } else {
                                Toast.makeText(context, context.getString(R.string.tts_not_initialized), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    Modifier.align(Alignment.CenterHorizontally),
                    colors = ButtonDefaults.buttonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                ) {
                    Text(
                        stringResource(R.string.test_tts),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
        }
        AnimatedVisibility(it is Action.REPLACE && viewModel.state.values.action is Action.REPLACE) {
            val action = (viewModel.state.values.action as? Action.REPLACE)
                ?: Action.REPLACE($$"${app} - ${title}", $$"${content}")
            Column(
                Modifier.fillMaxWidth(),
                Arrangement.spacedBy(dimensionResource(R.dimen.padding_medium)),
            ) {
                OutlinedTextField(
                    action.titleTemplate,
                    { value ->
                        viewModel.updateForm(
                            viewModel.state.page,
                            viewModel.state.values.copy(action = action.copy(titleTemplate = value)),
                        )
                    },
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.title_template)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                )
                OutlinedTextField(
                    action.contentTemplate,
                    { value ->
                        viewModel.updateForm(
                            viewModel.state.page,
                            viewModel.state.values.copy(action = action.copy(contentTemplate = value)),
                        )
                    },
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.content_template)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                )
                Text(
                    AnnotatedString.fromHtml(
                        stringResource(R.string.notification_template_advice),
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
                if (!hasPermissions.getValue(Permission.POST_NOTIFICATIONS)) {
                    ErrorText(R.string.replace_notifications_description)
                    Button(
                        { context.request(Permission.POST_NOTIFICATIONS) },
                        Modifier.align(Alignment.CenterHorizontally),
                        colors = ButtonDefaults.buttonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                    ) {
                        Text(
                            stringResource(R.string.grant_permission),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
    HorizontalDivider()
    Text(
        stringResource(R.string.display_options),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Normal,
    )
    Row(
        Modifier
            .padding(horizontal = dimensionResource(R.dimen.padding_small))
            .toggleable(viewModel.state.values.historyEnabled) {
                viewModel.updateForm(
                    FormPage.ACTION,
                    viewModel.state.values.copy(historyEnabled = it),
                )
            },
        Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
        Alignment.CenterVertically,
    ) {
        Checkbox(viewModel.state.values.historyEnabled, null)
        Text(
            stringResource(R.string.describe_history_screen),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Normal,
        )
    }
    Row(
        Modifier
            .padding(horizontal = dimensionResource(R.dimen.padding_small))
            .toggleable(viewModel.state.values.widgetEnabled) {
                viewModel.updateForm(
                    FormPage.ACTION,
                    viewModel.state.values.copy(widgetEnabled = it),
                )
            },
        Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
        Alignment.CenterVertically,
    ) {
        Checkbox(viewModel.state.values.widgetEnabled, null)
        Text(
            stringResource(R.string.describe_widget),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Normal,
        )
    }
    if (viewModel.state.values.widgetEnabled) {
        if (!hasPermissions.getValue(Permission.ACCESSIBILITY_SERVICE)) {
            Button(
                { context.request(Permission.ACCESSIBILITY_SERVICE) },
                Modifier.align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.buttonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
            ) {
                Text(
                    stringResource(R.string.make_tappable),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
        if (!hasPinnedLogWidget) {
            Button(
                context::addLogWidgetToHomeScreen,
                Modifier.align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.buttonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
            ) {
                Text(
                    stringResource(R.string.prompt_log_widget),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
    }
}
