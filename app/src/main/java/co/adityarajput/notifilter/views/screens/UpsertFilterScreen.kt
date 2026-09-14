package co.adityarajput.notifilter.views.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import co.adityarajput.notifilter.R
import co.adityarajput.notifilter.data.models.*
import co.adityarajput.notifilter.utils.filterFirst
import co.adityarajput.notifilter.utils.getFirst
import co.adityarajput.notifilter.viewmodels.FormPage
import co.adityarajput.notifilter.viewmodels.Provider
import co.adityarajput.notifilter.viewmodels.UpsertFilterViewModel
import co.adityarajput.notifilter.views.components.AppBar
import co.adityarajput.notifilter.views.components.Tile
import kotlinx.coroutines.launch

@Composable
fun UpsertFilterScreen(
    filterString: String,
    goBack: () -> Unit,
    viewModel: UpsertFilterViewModel = viewModel(factory = Provider.createUFVM(filterString)),
) {
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            AppBar(
                stringResource(
                    if (viewModel.state.values.filterId == 0) R.string.add_filter
                    else R.string.edit_filter,
                ),
                true,
                goBack,
            )
        },
    ) { paddingValues ->
        Column(
            Modifier.padding(paddingValues),
            Arrangement.SpaceBetween,
        ) {
            AnimatedContent(
                viewModel.state.page,
                Modifier
                    .weight(1f)
                    .padding(dimensionResource(R.dimen.padding_small))
                    .padding(
                        dimensionResource(R.dimen.padding_large),
                        dimensionResource(R.dimen.padding_medium),
                    ),
                { fadeIn() togetherWith fadeOut() },
            ) {
                Column(
                    Modifier.fillMaxWidth().run {
                        if (it == FormPage.PATTERN || it == FormPage.ACTION || it == FormPage.SCHEDULE) {
                            this.verticalScroll(rememberScrollState())
                        } else {
                            this
                        }
                    },
                    Arrangement.spacedBy(dimensionResource(R.dimen.padding_medium)),
                ) {
                    when (it) {
                        FormPage.ZAPPER -> ZapperPage(viewModel)
                        FormPage.PACKAGE -> PackagePage(viewModel)
                        FormPage.PATTERN -> PatternPage(viewModel)
                        FormPage.ACTION -> ActionPage(viewModel)
                        FormPage.SCHEDULE -> SchedulePage(viewModel)
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimensionResource(R.dimen.padding_large)),
                Arrangement.Center,
                Alignment.Bottom,
            ) {
                TextButton(
                    {
                        if (viewModel.state.page.isFirstPage()) {
                            goBack()
                        } else {
                            viewModel.updateForm(
                                viewModel.state.page.previous(),
                                viewModel.state.values,
                            )
                        }
                    },
                    Modifier
                        .fillMaxWidth(0.5f)
                        .padding(end = dimensionResource(R.dimen.padding_small)),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text(
                        if (viewModel.state.page.isFirstPage()) stringResource(R.string.cancel)
                        else stringResource(R.string.back),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Normal,
                    )
                }
                TextButton(
                    {
                        if (!viewModel.state.page.isFinalPage()) {
                            viewModel.updateForm(
                                viewModel.state.page.next(),
                                viewModel.state.values,
                            )
                        } else {
                            coroutineScope.launch {
                                viewModel.submitForm()
                                goBack()
                            }
                        }
                    },
                    Modifier
                        .fillMaxWidth()
                        .padding(start = dimensionResource(R.dimen.padding_small)),
                    viewModel.state.error == null,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                ) {
                    Text(
                        if (viewModel.state.page.isFirstPage()) stringResource(R.string.skip)
                        else if (viewModel.state.page.isFinalPage()) {
                            if (viewModel.state.values.filterId == 0) stringResource(R.string.add)
                            else stringResource(R.string.save)
                        } else stringResource(R.string.next),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun ZapperPage(viewModel: UpsertFilterViewModel) {
    if (viewModel.allPackages.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
    } else if (viewModel.activeNotifications.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(
                stringResource(R.string.no_active_notifications),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    } else {
        Text(
            stringResource(R.string.zapper_page_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Normal,
        )
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = dimensionResource(R.dimen.padding_small)),
        ) {
            items(viewModel.activeNotifications, { it.id }) {
                val appName = it.appNameFrom(viewModel.allPackages)
                Tile(
                    it.title,
                    it.content,
                    appName.getFirst(30),
                    onClick = {
                        viewModel.updateForm(
                            FormPage.PATTERN,
                            viewModel.state.values.copy(
                                app = App(appName, it.origin),
                                notification = it,
                            ),
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PackagePage(viewModel: UpsertFilterViewModel) {
    var searchString by remember {
        mutableStateOf(
            if (viewModel.state.values.app == Any) ""
            else viewModel.state.values.app.name,
        )
    }
    var visibleItemsCount by remember { mutableIntStateOf(10) }
    var showAdvancedOptions by remember { mutableStateOf(viewModel.state.values.app == Any) }
    var showSystemPackages by remember { mutableStateOf(false) }

    val (apps, searchFinished) = (if (showSystemPackages) viewModel.allPackages else viewModel.visibleApps)
        .filterFirst(visibleItemsCount) { it.toString().contains(searchString, true) }

    Text(
        stringResource(R.string.package_page_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Normal,
    )
    OutlinedTextField(
        searchString,
        { searchString = it },
        Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.package_name)) },
        placeholder = { Text(stringResource(R.string.package_name_placeholder)) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        singleLine = true,
    )
    Column(
        Modifier.padding(dimensionResource(R.dimen.padding_medium)),
        Arrangement.spacedBy(dimensionResource(R.dimen.padding_medium)),
    ) {
        Row(
            Modifier.toggleable(showAdvancedOptions) { showAdvancedOptions = it },
            Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
            Alignment.CenterVertically,
        ) {
            Checkbox(showAdvancedOptions, null)
            Text(
                stringResource(R.string.advanced_options),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Normal,
            )
        }
        if (showAdvancedOptions) {
            Row(
                Modifier
                    .padding(horizontal = dimensionResource(R.dimen.padding_small))
                    .toggleable(viewModel.state.values.app == Any) {
                        viewModel.updateForm(
                            FormPage.PACKAGE,
                            viewModel.state.values.copy(app = if (it) Any else None),
                        )
                    },
                Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
                Alignment.CenterVertically,
            ) {
                Checkbox(viewModel.state.values.app == Any, null)
                Column {
                    Text(
                        stringResource(R.string.target_all_apps),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Normal,
                    )
                    Text(
                        stringResource(R.string.all_apps_warning),
                        Modifier.padding(top = dimensionResource(R.dimen.padding_small)),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
            Row(
                Modifier
                    .padding(horizontal = dimensionResource(R.dimen.padding_small))
                    .toggleable(showSystemPackages) { showSystemPackages = it },
                Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
                Alignment.CenterVertically,
            ) {
                Checkbox(showSystemPackages, null)
                Column {
                    Text(
                        stringResource(R.string.show_system_packages),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Normal,
                    )
                    Text(
                        stringResource(R.string.system_packages_warning),
                        Modifier.padding(top = dimensionResource(R.dimen.padding_small)),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
        }
    }
    FlowRow(
        Modifier.verticalScroll(rememberScrollState()),
        Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
        Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
    ) {
        apps.forEach {
            FilterChip(
                it == viewModel.state.values.app,
                {
                    viewModel.updateForm(
                        FormPage.PATTERN,
                        viewModel.state.values.copy(app = it),
                    )
                },
                { Text(it.name) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        }
        if (!searchFinished)
            FilterChip(
                false,
                { visibleItemsCount += 10 },
                { Text("...") },
                colors = FilterChipDefaults.filterChipColors(
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
    }
}
