package co.adityarajput.notifilter.views.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.sp
import co.adityarajput.notifilter.R

@Composable
fun Tile(
    title: String,
    content: String,
    leading: String,
    trailing: String? = null,
    preContent: String? = null,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    buttons: @Composable RowScope.() -> Unit = {},
    expanded: Boolean = false,
    dividerBetweenTitleAndContent: Boolean = false,
    prominentStatus: String? = null,
    onProminentStatusClick: (() -> Unit)? = null,
    prominentStatusColor: Color? = null,
) {
    Card(Modifier.fillMaxWidth().padding(dimensionResource(R.dimen.padding_small)).animateContentSize(tween(300, easing = LinearOutSlowInEasing)).combinedClickable(onClick = onClick, onLongClick = onLongClick ?: onClick)) {
        Column(Modifier.fillMaxWidth().padding(dimensionResource(R.dimen.padding_large)), Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(leading, style = MaterialTheme.typography.bodySmall.copy(MaterialTheme.colorScheme.onSurfaceVariant, 11.sp))
                if (trailing != null) Text(trailing, style = MaterialTheme.typography.bodySmall.copy(MaterialTheme.colorScheme.onSurfaceVariant, 8.sp))
            }
            if (prominentStatus != null) Text(
                prominentStatus,
                Modifier.then(if (onProminentStatusClick != null) Modifier.clickable(onClick = onProminentStatusClick) else Modifier),
                style = MaterialTheme.typography.titleSmall,
                color = prominentStatusColor ?: MaterialTheme.colorScheme.primary,
            )
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (dividerBetweenTitleAndContent) HorizontalDivider()
            if (!preContent.isNullOrEmpty()) Text(preContent, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
            Text(content, style = MaterialTheme.typography.bodySmall)
            if (expanded) Row(Modifier.fillMaxWidth(), Arrangement.End, Alignment.CenterVertically) { buttons() }
        }
    }
}
