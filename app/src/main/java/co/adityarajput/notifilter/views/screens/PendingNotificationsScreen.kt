package co.adityarajput.notifilter.views.screens

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
import co.adityarajput.notifilter.R
import co.adityarajput.notifilter.data.AppContainer
import co.adityarajput.notifilter.data.Cache
import co.adityarajput.notifilter.data.models.PendingNotification
import co.adityarajput.notifilter.services.PendingNotificationRegistry
import co.adityarajput.notifilter.utils.*
import co.adityarajput.notifilter.views.components.*
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingNotificationsScreen(filterId: Int?, goBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { AppContainer(context.applicationContext).repository }
    val filters by repository.filters().collectAsState(initial = emptyList())
    val pending by PendingNotificationRegistry.entries.collectAsState()
    var selectedFilterId by remember(filterId) { mutableStateOf(filterId) }
    var filterMenu by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    var toOpen by remember { mutableStateOf<PendingNotification?>(null) }
    val relevantFilters = filters.filter { f -> pending.values.any { it.filterId == f.id } }
    val shown = pending.values.filter { selectedFilterId == null || it.filterId == selectedFilterId }.sortedBy { it.committedUntil }

    Scaffold(topBar = { AppBar(stringResource(R.string.pending_notifications), true, goBack) { IconButton({ help = true }) { Text("?", style = MaterialTheme.typography.titleLarge) } } }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ExposedDropdownMenuBox(filterMenu, { filterMenu = !filterMenu }, Modifier.padding(horizontal = dimensionResource(R.dimen.padding_large), vertical = dimensionResource(R.dimen.padding_small))) {
                OutlinedTextField(
                    value = selectedFilterId?.let { id -> filters.firstOrNull { it.id == id }?.title } ?: stringResource(R.string.pending_all_filters),
                    onValueChange = {}, readOnly = true, label = { Text(stringResource(R.string.pending_filter_by_filter)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(filterMenu) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(filterMenu, { filterMenu = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.pending_all_filters)) }, { selectedFilterId = null; filterMenu = false })
                    relevantFilters.forEach { f -> DropdownMenuItem({ Text(f.title) }, { selectedFilterId = f.id; filterMenu = false }) }
                }
            }
            if (shown.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Text(stringResource(R.string.no_pending_notifications), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge) }
            else LazyColumn(Modifier.padding(horizontal = dimensionResource(R.dimen.padding_small)).fillMaxSize()) {
                items(shown, { it.key }) { item ->
                    val n = item.notification
                    val release = predictPendingRelease(n, item.committedUntil, filters)
                    val releaseText = if (release == null) stringResource(R.string.pending_release_never) else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(release))
                    Tile(n.title, n.content, n.origin.getFirst(30), stringResource(R.string.pending_release_planned, releaseText), null, { toOpen = item }, null, {}, true)
                }
            }
        }
    }

    if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text(stringResource(R.string.pending_help_title)) }, text = { Text(stringResource(R.string.pending_help_text)) }, confirmButton = { TextButton({ help = false }) { Text("OK") } })
    toOpen?.let { item -> AlertDialog(
        onDismissRequest = { toOpen = null }, title = { Text(stringResource(R.string.open_notification_title)) }, text = { Text(stringResource(R.string.open_notification_warning)) },
        dismissButton = { TextButton({ toOpen = null }) { Text(stringResource(R.string.open_notification_cancel)) } },
        confirmButton = { TextButton({ try { val intents = Cache.intents[item.notification.data.hashCode()]; if (intents?.main != null) intents.launchMain() else context.packageManager.getLaunchIntentForPackage(item.notification.origin)?.let(context::startActivity) } catch (e: Exception) { Logger.e("PendingNotificationsScreen", "Error opening pending notification", e) }; toOpen = null }) { Text(stringResource(R.string.open_notification_confirm)) } },
    ) }
}
