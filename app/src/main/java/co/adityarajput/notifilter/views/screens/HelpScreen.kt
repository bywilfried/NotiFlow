package co.adityarajput.notifilter.views.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import co.adityarajput.notifilter.R
import co.adityarajput.notifilter.views.components.AppBar

private data class HelpSection(@StringRes val title: Int, @StringRes val body: Int)
private val helpSections = listOf(
    HelpSection(R.string.help_getting_started_title, R.string.help_getting_started_body),
    HelpSection(R.string.help_matching_title, R.string.help_matching_body),
    HelpSection(R.string.help_regex_title, R.string.help_regex_body),
    HelpSection(R.string.help_actions_title, R.string.help_actions_body),
    HelpSection(R.string.help_schedule_title, R.string.help_schedule_body),
    HelpSection(R.string.help_pending_title, R.string.help_pending_body),
    HelpSection(R.string.help_android_limits_title, R.string.help_android_limits_body),
    HelpSection(R.string.help_history_title, R.string.help_history_body),
    HelpSection(R.string.help_priority_title, R.string.help_priority_body),
    HelpSection(R.string.help_display_title, R.string.help_display_body),
    HelpSection(R.string.help_permissions_title, R.string.help_permissions_body),
    HelpSection(R.string.help_examples_title, R.string.help_examples_body),
    HelpSection(R.string.help_privacy_title, R.string.help_privacy_body),
)

@Composable
fun HelpScreen(goBack: () -> Unit) {
    Scaffold(topBar = { AppBar(stringResource(R.string.help), true, goBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(dimensionResource(R.dimen.padding_small)),
        ) {
            Text(stringResource(R.string.help_intro), Modifier.padding(dimensionResource(R.dimen.padding_large)), style = MaterialTheme.typography.bodyLarge)
            helpSections.forEach { section ->
                Card(Modifier.fillMaxWidth().padding(dimensionResource(R.dimen.padding_small))) {
                    Column(Modifier.padding(dimensionResource(R.dimen.padding_large))) {
                        Text(stringResource(section.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(dimensionResource(R.dimen.padding_small)))
                        Text(stringResource(section.body), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(dimensionResource(R.dimen.padding_large)))
        }
    }
}
