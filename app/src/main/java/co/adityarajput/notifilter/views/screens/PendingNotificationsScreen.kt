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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import co.adityarajput.notifilter.R
import co.adityarajput.notifilter.data.Cache
import co.adityarajput.notifilter.data.models.Filter
import co.adityarajput.notifilter.services.PendingNotificationRegistry
import co.adityarajput.notifilter.utils.Logger
import co.adityarajput.notifilter.utils.getFirst
import co.adityarajput.notifilter.views.components.AppBar
import co.adityarajput.notifilter.views.components.Tile
import java.text.DateFormat
import java.util.Date

@Composable
fun PendingNotificationsScreen(filterId: Int?, goBack: () -> Unit) {
    val context = LocalContext.current
    val pending by PendingNotificationRegistry.entries.collectAsState()
    var help by remember { mutableStateOf(false) }
    var toOpen by remember { mutableStateOf<co.adityarajput.notifilter.data.models.PendingNotification?>(null) }
    val filters = remember { mutableStateOf<List<Filter>>(emptyList()) }
    val shown = pending.values.filter { filterId == null || it.filterId == filterId }.sortedBy { it.committedUntil }

    Scaffold(topBar = {
        AppBar(stringResource(R.string.pending_notifications), true, goBack) {
            IconButton({ help = true }) { Text("?", style = MaterialTheme.typography.titleLarge) }
        }
    }) { padding ->
        if (shown.isEmpty()) Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
            Text(stringResource(R.string.no_pending_notifications), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        } else LazyColumn(Modifier.padding(padding).padding(dimensionResource(R.dimen.padding_small)).fillMaxSize()) {
            items(shown, { it.key }) { item ->
                val n = item.notification
                val filter = filters.value.firstOrNull { it.id == item.filterId }
                val release = filter?.schedule?.predictedRelease(item.committedUntil)
                val releaseText = if (filter != null && release == null) stringResource(R.string.pending_release_never)
                else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(release ?: item.committedUntil))
                Tile(n.title, n.content, n.origin.getFirst(30), stringResource(R.string.pending_release_planned, releaseText), null, { toOpen = item }, null, {}, true)
            }
        }
    }

    if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text(stringResource(R.string.pending_help_title)) }, text = { Text(stringResource(R.string.pending_help_text)) }, confirmButton = { TextButton({ help = false }) { Text("OK") } })
    toOpen?.let { item ->
        AlertDialog(
            onDismissRequest = { toOpen = null },
            title = { Text(stringResource(R.string.open_notification_title)) },
            text = { Text(stringResource(R.string.open_notification_warning)) },
            dismissButton = { TextButton({ toOpen = null }) { Text(stringResource(R.string.open_notification_cancel)) } },
            confirmButton = { TextButton({
                try {
                    val intents = Cache.intents[item.notification.data.hashCode()]
                    if (intents?.main != null) intents.launchMain() else context.packageManager.getLaunchIntentForPackage(item.notification.origin)?.let(context::startActivity)
                } catch (e: Exception) { Logger.e("PendingNotificationsScreen", "Error opening pending notification", e) }
                toOpen = null
            }) { Text(stringResource(R.string.open_notification_confirm)) } },
        )
    }
}
