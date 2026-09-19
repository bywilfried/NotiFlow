package co.adityarajput.notifilter.views.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.*
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import co.adityarajput.notifilter.R
import co.adityarajput.notifilter.data.Cache
import co.adityarajput.notifilter.data.models.Notification
import co.adityarajput.notifilter.utils.*
import co.adityarajput.notifilter.viewmodels.*
import co.adityarajput.notifilter.views.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(goBack: () -> Unit, viewModel: NotificationsViewModel = viewModel(factory = Provider.Factory)) {
    val context = LocalContext.current
    val state = viewModel.state.collectAsState()
    val filters by viewModel.repository.filters().collectAsState(initial = emptyList())
    var selectedFilterId by remember { mutableStateOf<Int?>(null) }
    var filterMenu by remember { mutableStateOf(false) }
    var toOpen by remember { mutableStateOf<Notification?>(null) }
    val history = state.value.notifications
    val shown = history?.filter { n -> selectedFilterId == null || n.filterId == selectedFilterId }

    Scaffold(topBar = { AppBar(stringResource(R.string.history), true, goBack) { IconButton({ viewModel.dialogState = NotificationDialogState.CLEAR_HISTORY }) { Icon(painterResource(R.drawable.clear_all), stringResource(R.string.clear_history), tint = MaterialTheme.colorScheme.onSurface) } } }) { paddingValues ->
        Column(Modifier.padding(paddingValues).fillMaxSize()) {
            ExposedDropdownMenuBox(filterMenu, { filterMenu = !filterMenu }, Modifier.padding(horizontal = dimensionResource(R.dimen.padding_large), vertical = dimensionResource(R.dimen.padding_small))) {
                OutlinedTextField(value = selectedFilterId?.let { id -> filters.firstOrNull { it.id == id }?.title }.orEmpty().ifEmpty { stringResource(R.string.pending_all_filters) }, onValueChange = {}, readOnly = true, label = { Text(stringResource(R.string.pending_filter_by_filter)) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(filterMenu) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
                ExposedDropdownMenu(filterMenu, { filterMenu = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.pending_all_filters)) }, { selectedFilterId = null; filterMenu = false })
                    filters.forEach { f -> DropdownMenuItem({ Text(f.title) }, { selectedFilterId = f.id; filterMenu = false }) }
                }
            }
            if (history == null || viewModel.allPackages.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            else if (shown.isNullOrEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Text(stringResource(R.string.no_notifications), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge) }
            else LazyColumn(Modifier.padding(dimensionResource(R.dimen.padding_small)).fillMaxSize()) {
                items(shown, { it.id }) { n ->
                    val intents = Cache.intents[n.data.hashCode()]
                    Tile(n.title, n.content, n.appNameFrom(viewModel.allPackages).getFirst(30), n.timestamp.toDelta(), null, { toOpen = n }, { viewModel.delete(n) }, { intents?.actions?.forEach { (title, intent) -> Text(title, Modifier.padding(horizontal = dimensionResource(R.dimen.padding_small)).padding(top = dimensionResource(R.dimen.padding_small)).weight(1f).clickable { intent?.send() }, style = MaterialTheme.typography.titleSmall.copy(color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)) } }, true)
                }
            }
        }
        if (viewModel.dialogState != null) ManageHistoryDialog(viewModel)
    }
    toOpen?.let { n -> AlertDialog(onDismissRequest = { toOpen = null }, title = { Text(stringResource(R.string.open_notification_title)) }, text = { Text(stringResource(R.string.open_notification_warning_history)) }, dismissButton = { TextButton({ toOpen = null }) { Text(stringResource(R.string.open_notification_cancel)) } }, confirmButton = { TextButton({ try { val intents = Cache.intents[n.data.hashCode()]; if (intents?.main != null) intents.launchMain() else context.packageManager.getLaunchIntentForPackage(n.origin)?.let(context::startActivity) } catch (e: Exception) { Logger.e("NotificationsScreen", "Error clicking $n", e) }; toOpen = null }) { Text(stringResource(R.string.open_notification_confirm)) } }) }
}
