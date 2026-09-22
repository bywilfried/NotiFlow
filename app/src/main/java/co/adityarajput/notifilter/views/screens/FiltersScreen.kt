package co.adityarajput.notifilter.views.screens

import android.content.Context.MODE_PRIVATE
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import co.adityarajput.notifilter.Constants.SETTINGS
import co.adityarajput.notifilter.Constants.SHOW_MISSING_PERMISSIONS_DIALOG
import co.adityarajput.notifilter.R
import co.adityarajput.notifilter.data.models.*
import co.adityarajput.notifilter.services.NotificationListener
import co.adityarajput.notifilter.services.PendingNotificationRegistry
import co.adityarajput.notifilter.utils.*
import co.adityarajput.notifilter.viewmodels.*
import co.adityarajput.notifilter.views.components.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

@Composable
fun FiltersScreen(
    goToUpsertFilterScreen: (String) -> Unit,
    goToNotificationsScreen: () -> Unit,
    goToPendingNotificationsScreen: () -> Unit,
    goToFilterPendingNotificationsScreen: (Int) -> Unit,
    goToSettingsScreen: () -> Unit,
    viewModel: FiltersViewModel = viewModel(factory = Provider.Factory),
) {
    val context = LocalContext.current
    val state = viewModel.state.collectAsState()
    val pending by PendingNotificationRegistry.entries.collectAsState()
    var hasPermissions by remember(state.value.filters) { mutableStateOf(context.isGranted(permissionsRequired(state.value.filters ?: listOf()))) }
    var showMissingPermissionsDialog by remember { mutableStateOf(context.getSharedPreferences(SETTINGS, MODE_PRIVATE).getBoolean(SHOW_MISSING_PERMISSIONS_DIALOG, true)) }
    var isAdjustingPriorities by remember { mutableStateOf(false) }
    var isListenerServiceInitialized by remember { mutableStateOf(NotificationListener.isServiceInitialized) }
    LaunchedEffect(Unit) {
        while (!isListenerServiceInitialized) {
            isListenerServiceInitialized = NotificationListener.isServiceInitialized
            delay(1.seconds)
        }
        runCatching {
            PendingNotificationRegistry.reconcile(
                context.applicationContext,
                NotificationListener.instance.snoozedNotifications,
            )
        }.onFailure {
            Logger.e("FiltersScreen", "Failed to refresh pending notifications", it)
        }
    }

    Scaffold(
        topBar = { AppBar(stringResource(R.string.app_name), false) {
            IconButton(goToSettingsScreen) { Icon(painterResource(R.drawable.settings), stringResource(R.string.settings), tint = MaterialTheme.colorScheme.onSurface) }
            IconButton(goToPendingNotificationsScreen) { Text("⏳", style = MaterialTheme.typography.titleMedium) }
            IconButton(goToNotificationsScreen) { Icon(painterResource(R.drawable.history), stringResource(R.string.history), tint = MaterialTheme.colorScheme.onSurface) }
        } },
        floatingActionButton = { FloatingActionButton({ goToUpsertFilterScreen("null") }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) { Icon(painterResource(R.drawable.add), stringResource(R.string.add_filter)) } },
    ) { paddingValues ->
        when {
            state.value.filters == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            !isListenerServiceInitialized -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text(AnnotatedString.fromHtml(stringResource(R.string.listener_uninitialized), TextLinkStyles(SpanStyle(MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge) }
            state.value.filters!!.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text(stringResource(R.string.no_filters), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge) }
            else -> LazyColumn(Modifier.padding(paddingValues).padding(dimensionResource(R.dimen.padding_small)).fillMaxSize()) {
                items(state.value.filters!!, { it.id }) { filter ->
                    val pendingCount = PendingNotificationRegistry.countForFilter(filter.id)
                    Tile(
                        filter.title, filter.action.verb(), if (filter.app == Any) stringResource(R.string.any_app) else filter.app.name.getFirst(30),
                        if (!filter.enabled) stringResource(R.string.filter_disabled) else if (!filter.historyEnabled) stringResource(R.string.history_disabled) else pluralStringResource(R.plurals.hit, filter.hits, filter.hits),
                        filter.schedule.description,
                        { viewModel.selectedFilter = if (viewModel.selectedFilter == filter) null else filter }, null,
                        {
                            if (!isAdjustingPriorities) {
                                IconButton({ viewModel.normalizePriorities(); isAdjustingPriorities = true }) { Icon(painterResource(R.drawable.format_line_spacing), stringResource(R.string.adjust_priorities)) }
                                if (filter.action is Action.REPLACE) IconButton({ NotificationListener.createReplaceNotificationChannel(filter.id, true) }) { Icon(painterResource(R.drawable.notification_settings), stringResource(R.string.adjust_priorities)) }
                                IconButton({ viewModel.dialogState = FilterDialogState.TOGGLE_HISTORY }) { Icon(painterResource(R.drawable.manage_history), stringResource(R.string.toggle_history, filter.historyEnabled.getToggleString())) }
                                IconButton({ viewModel.dialogState = FilterDialogState.TOGGLE_FILTER }) { Icon(if (filter.enabled) painterResource(R.drawable.archive) else painterResource(R.drawable.unarchive), stringResource(R.string.toggle_filter, filter.enabled.getToggleString())) }
                                IconButton({ goToUpsertFilterScreen(Json.encodeToString(filter)) }) { Icon(painterResource(R.drawable.edit), stringResource(R.string.edit_filter)) }
                                IconButton({ goToUpsertFilterScreen(Json.encodeToString(filter.copy(id = 0, hits = 0))) }) { Icon(painterResource(R.drawable.duplicate), stringResource(R.string.duplicate_filter)) }
                                IconButton({ viewModel.dialogState = FilterDialogState.DELETE }, colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)) { Icon(painterResource(R.drawable.delete), stringResource(R.string.delete)) }
                            } else {
                                IconButton({ viewModel.updatePriority(filter, true) }) { Icon(painterResource(R.drawable.up), stringResource(R.string.move_up)) }
                                IconButton({ viewModel.updatePriority(filter, false) }) { Icon(painterResource(R.drawable.down), stringResource(R.string.move_down)) }
                                IconButton({ isAdjustingPriorities = false }) { Icon(painterResource(R.drawable.check), stringResource(R.string.save)) }
                            }
                        },
                        isAdjustingPriorities || viewModel.selectedFilter == filter, true,
                        if (filter.action.hasMeaningfulPendingNotifications) stringResource(R.string.pending_notification_count, pendingCount) else null,
                        if (filter.action.hasMeaningfulPendingNotifications) ({ goToFilterPendingNotificationsScreen(filter.id) }) else null,
                    )
                }
                item { Box(Modifier.height(100.dp)) {} }
            }
        }
        if (viewModel.selectedFilter != null && viewModel.dialogState != null) ManageFilterDialog(viewModel)
        if (!hasPermissions.all { it.value } && showMissingPermissionsDialog) MissingPermissionsDialog(hasPermissions.filter { !it.value }.keys, { showMissingPermissionsDialog = false }, { showMissingPermissionsDialog = false; context.getSharedPreferences(SETTINGS, MODE_PRIVATE).edit { putBoolean(SHOW_MISSING_PERMISSIONS_DIALOG, false) } })
    }
}
