package co.adityarajput.notifilter.views.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.adityarajput.notifilter.BuildConfig
import co.adityarajput.notifilter.R
import co.adityarajput.notifilter.views.Theme
import co.adityarajput.notifilter.views.components.AppBar

@Composable
fun AboutScreen(goBack: () -> Unit) {
    Scaffold(topBar = { AppBar(stringResource(R.string.about_notiflow), true, goBack) }) { paddingValues ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(dimensionResource(R.dimen.padding_small)),
                Arrangement.Top,
                Alignment.CenterHorizontally,
            ) {
                AboutCard {
                    Image(
                        painterResource(R.drawable.notiflow_icon_main),
                        stringResource(R.string.alttext_app_logo),
                        Modifier
                            .size(112.dp)
                            .padding(top = dimensionResource(R.dimen.padding_large))
                            .align(Alignment.CenterHorizontally),
                    )
                    Text(
                        stringResource(R.string.app_name),
                        Modifier
                            .padding(top = dimensionResource(R.dimen.padding_medium))
                            .align(Alignment.CenterHorizontally),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        stringResource(R.string.notiflow_description),
                        Modifier.padding(dimensionResource(R.dimen.padding_large)),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(R.string.notiflow_origin),
                        Modifier.padding(
                            start = dimensionResource(R.dimen.padding_large),
                            end = dimensionResource(R.dimen.padding_large),
                            bottom = dimensionResource(R.dimen.padding_large),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                AboutCard {
                    Text(
                        AnnotatedString.fromHtml(
                            stringResource(R.string.notiflow_links),
                            TextLinkStyles(
                                SpanStyle(
                                    MaterialTheme.colorScheme.primary,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ),
                        ),
                        Modifier.padding(dimensionResource(R.dimen.padding_large)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                AboutCard {
                    Text(
                        AnnotatedString.fromHtml(stringResource(R.string.app_permissions)),
                        Modifier.padding(dimensionResource(R.dimen.padding_large)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Column(
                    Modifier.fillMaxWidth(),
                    Arrangement.Bottom,
                    Alignment.CenterHorizontally,
                ) {
                    Text(
                        "v${BuildConfig.VERSION_NAME}",
                        Modifier.padding(
                            top = dimensionResource(R.dimen.padding_large),
                            bottom = dimensionResource(R.dimen.padding_small),
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        AnnotatedString.fromHtml(
                            stringResource(R.string.notiflow_maintainer),
                            TextLinkStyles(
                                SpanStyle(
                                    MaterialTheme.colorScheme.primary,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ),
                        ),
                        Modifier.padding(bottom = dimensionResource(R.dimen.padding_large)),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(dimensionResource(R.dimen.padding_small))
    ) { content() }
}

@Preview
@Composable
private fun AboutScreenPreview() = Theme { AboutScreen {} }
