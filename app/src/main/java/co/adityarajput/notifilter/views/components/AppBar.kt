package co.adityarajput.notifilter.views.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.adityarajput.notifilter.R
import co.adityarajput.notifilter.views.Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBar(
    title: String,
    canNavigateBack: Boolean,
    leadingIconOnClick: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = when {
                        canNavigateBack -> MaterialTheme.typography.headlineMedium.fontSize
                        else -> MaterialTheme.typography.headlineLarge.fontSize
                    },
                ),
            )
        },
        colors = TopAppBarDefaults.topAppBarColors().copy(
            containerColor = MaterialTheme.colorScheme.primary,
        ),
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(leadingIconOnClick) {
                    Icon(
                        painterResource(R.drawable.arrow_back),
                        stringResource(R.string.alttext_back_button),
                    )
                }
            } else {
                Icon(
                    painterResource(R.drawable.notiflow_icon_main),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = androidx.compose.ui.graphics.Color.Unspecified,
                )
            }
        },
        actions = actions,
    )
}

@Preview
@Composable
private fun HomeAppBarPreview() {
    Theme {
        AppBar(stringResource(R.string.app_name), false)
    }
}

@Preview
@Composable
private fun AppBarPreview() {
    Theme {
        AppBar("Page Title", true)
    }
}
