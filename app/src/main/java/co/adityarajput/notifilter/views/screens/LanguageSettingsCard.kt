package co.adityarajput.notifilter.views.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import co.adityarajput.notifilter.R
import co.adityarajput.notifilter.utils.LANGUAGE_ENGLISH
import co.adityarajput.notifilter.utils.LANGUAGE_FRENCH
import co.adityarajput.notifilter.utils.LANGUAGE_SYSTEM
import co.adityarajput.notifilter.utils.appLanguage
import co.adityarajput.notifilter.utils.setAppLanguage
import androidx.compose.ui.platform.LocalContext

@Composable
fun LanguageSettingsCard() {
    val context = LocalContext.current
    var selectedLanguage by remember { mutableStateOf(context.appLanguage()) }
    val languages = listOf(
        LANGUAGE_SYSTEM to R.string.language_system,
        LANGUAGE_ENGLISH to R.string.language_english,
        LANGUAGE_FRENCH to R.string.language_french,
    )

    Card(
        Modifier
            .fillMaxWidth()
            .padding(dimensionResource(R.dimen.padding_small)),
    ) {
        Text(
            stringResource(R.string.settings_section_language),
            Modifier.padding(
                dimensionResource(R.dimen.padding_large),
                dimensionResource(R.dimen.padding_medium),
            ),
            fontWeight = FontWeight.Medium,
        )
        Text(
            stringResource(R.string.language_description),
            Modifier.padding(horizontal = dimensionResource(R.dimen.padding_large)),
            style = MaterialTheme.typography.bodySmall,
        )
        languages.forEach { (languageTag, label) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selectedLanguage == languageTag) {
                        if (selectedLanguage != languageTag) {
                            selectedLanguage = languageTag
                            context.setAppLanguage(languageTag)
                        }
                    }
                    .padding(
                        horizontal = dimensionResource(R.dimen.padding_large),
                        vertical = dimensionResource(R.dimen.padding_small),
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selectedLanguage == languageTag,
                    onClick = null,
                )
                Spacer(Modifier.width(dimensionResource(R.dimen.padding_small)))
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
        Spacer(Modifier.height(dimensionResource(R.dimen.padding_small)))
    }
}
