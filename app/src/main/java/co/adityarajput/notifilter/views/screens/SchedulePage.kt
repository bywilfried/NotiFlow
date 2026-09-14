package co.adityarajput.notifilter.views.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import co.adityarajput.notifilter.R
import co.adityarajput.notifilter.data.models.Action
import co.adityarajput.notifilter.data.models.TimeRange
import co.adityarajput.notifilter.viewmodels.FormError
import co.adityarajput.notifilter.viewmodels.UpsertFilterViewModel
import co.adityarajput.notifilter.views.components.ErrorText
import java.util.Locale

@Composable
fun SchedulePage(viewModel: UpsertFilterViewModel) {
    val context = LocalContext.current
    val schedule = viewModel.state.values.schedule

    var pickerTarget by remember {
        mutableStateOf<Triple<Int, Int, Boolean>?>(null)
    }

    LaunchedEffect(pickerTarget) {
        val target = pickerTarget ?: return@LaunchedEffect
        val (day, rangeIndex, isEnd) = target
        val range = schedule.ranges[day]?.getOrNull(rangeIndex)

        if (range == null) {
            pickerTarget = null
            return@LaunchedEffect
        }

        val initialMinutes = if (isEnd) {
            range.end.coerceAtMost(1439)
        } else {
            range.start
        }

        TimePickerDialog(
            context,
            { _, hour, minute ->
                val newMinutes = if (isEnd && hour == 0 && minute == 0) 1440 else hour * 60 + minute
                val newRanges = schedule.ranges.toMutableMap()
                val ranges = newRanges[day].orEmpty().toMutableList()

                if (rangeIndex in ranges.indices) {
                    val current = ranges[rangeIndex]
                    ranges[rangeIndex] = if (isEnd) {
                        current.copy(end = newMinutes)
                    } else {
                        current.copy(start = newMinutes)
                    }
                    newRanges[day] = ranges
                    viewModel.updateForm(
                        viewModel.state.page,
                        viewModel.state.values.copy(
                            schedule = schedule.copy(ranges = newRanges),
                        ),
                    )
                }
                pickerTarget = null
            },
            initialMinutes / 60,
            initialMinutes % 60,
            true,
        ).apply {
            setOnCancelListener {
                pickerTarget = null
            }
        }.show()
    }

    Text(
        stringResource(R.string.schedule_page_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Normal,
    )

    if (viewModel.state.error == FormError.INVALID_TIME_RANGE) {
        ErrorText(R.string.invalid_time_range)
    }

    Column(
        Modifier.fillMaxWidth(),
    ) {
        listOf(2, 3, 4, 5, 6, 7, 1).forEach { day ->
            val dayName = java.text.DateFormatSymbols
                .getInstance(Locale.getDefault())
                .weekdays[day]
            val ranges = schedule.ranges[day].orEmpty()

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimensionResource(R.dimen.padding_small)),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .background(
                                if (ranges.isNotEmpty()) MaterialTheme.colorScheme.primary
                                else Color.Transparent,
                                CircleShape,
                            )
                            .padding(dimensionResource(R.dimen.padding_small))
                            .selectable(ranges.isNotEmpty()) {
                                if (ranges.isEmpty()) {
                                    val newRanges = schedule.ranges.toMutableMap()
                                    newRanges[day] = listOf(TimeRange(0, 60))
                                    viewModel.updateForm(
                                        viewModel.state.page,
                                        viewModel.state.values.copy(
                                            schedule = schedule.copy(ranges = newRanges),
                                        ),
                                    )
                                } else {
                                    val newRanges = schedule.ranges.toMutableMap()
                                    newRanges.remove(day)
                                    viewModel.updateForm(
                                        viewModel.state.page,
                                        viewModel.state.values.copy(
                                            schedule = schedule.copy(ranges = newRanges),
                                        ),
                                    )
                                }
                            },
                    ) {
                        Text(
                            dayName,
                            color = if (ranges.isNotEmpty())
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    Spacer(
                        Modifier.width(
                            dimensionResource(R.dimen.padding_medium)
                        )
                    )

                    Text(
                        if (ranges.isEmpty()) {
                            stringResource(R.string.no_time_ranges)
                        } else {
                            pluralStringResource(
                                R.plurals.time_range_count,
                                ranges.size,
                                ranges.size,
                            )
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                ranges.forEachIndexed { rangeIndex, range ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                start = dimensionResource(R.dimen.padding_large)
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            String.format(
                                Locale.getDefault(),
                                "%02d:%02d",
                                range.start / 60,
                                range.start % 60,
                            ),
                            Modifier.clickable {
                                pickerTarget = Triple(day, rangeIndex, false)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            textDecoration = TextDecoration.Underline,
                        )

                        Text(
                            stringResource(R.string.to),
                            Modifier.padding(
                                horizontal = dimensionResource(R.dimen.padding_small)
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )

                        Text(
                            if (range.end == 1440) {
                                "24:00"
                            } else {
                                String.format(
                                    Locale.getDefault(),
                                    "%02d:%02d",
                                    range.end / 60,
                                    range.end % 60,
                                )
                            },
                            Modifier.clickable {
                                pickerTarget = Triple(day, rangeIndex, true)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            textDecoration = TextDecoration.Underline,
                        )

                        Text(
                            "×",
                            Modifier
                                .padding(
                                    start = dimensionResource(
                                        R.dimen.padding_small
                                    )
                                )
                                .clickable {
                                    val newRanges = schedule.ranges.toMutableMap()
                                    val updatedRanges = newRanges[day]
                                        .orEmpty()
                                        .toMutableList()
                                    updatedRanges.removeAt(rangeIndex)

                                    if (updatedRanges.isEmpty()) {
                                        newRanges.remove(day)
                                    } else {
                                        newRanges[day] = updatedRanges
                                    }

                                    viewModel.updateForm(
                                        viewModel.state.page,
                                        viewModel.state.values.copy(
                                            schedule = schedule.copy(
                                                ranges = newRanges
                                            ),
                                        ),
                                    )
                                },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                Text(
                    stringResource(R.string.add_time_range),
                    Modifier
                        .padding(
                            start = dimensionResource(R.dimen.padding_large)
                        )
                        .clickable {
                            val newRanges = schedule.ranges.toMutableMap()
                            val updatedRanges = newRanges[day].orEmpty().toMutableList()
                            updatedRanges.add(TimeRange(0, 60))
                            newRanges[day] = updatedRanges
                            viewModel.updateForm(
                                viewModel.state.page,
                                viewModel.state.values.copy(
                                    schedule = schedule.copy(
                                        ranges = newRanges
                                    ),
                                ),
                            )
                        },
                    style = MaterialTheme.typography.labelLarge,
                    textDecoration = TextDecoration.Underline,
                )
            }
        }
    }

    if (viewModel.state.error == FormError.BLANK_FIELDS) {
        ErrorText(R.string.empty_active_days)
    }

    if (viewModel.state.values.action.let {
            it is Action.DELAY && it.delayLength == null
        }) {
        Text(
            stringResource(R.string.delay_action_reminder),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Normal,
        )
    }
}
