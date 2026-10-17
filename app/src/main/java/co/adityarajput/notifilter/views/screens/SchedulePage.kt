package co.adityarajput.notifilter.views.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.adityarajput.notifilter.R
import co.adityarajput.notifilter.data.models.*
import co.adityarajput.notifilter.viewmodels.*
import co.adityarajput.notifilter.views.components.ErrorText
import java.text.DateFormatSymbols
import java.util.Locale

private const val DAY = 1440
private const val WEEK = 10080
private val dayNumbers = listOf(2, 3, 4, 5, 6, 7, 1)

private fun label(minute: Int, days: List<String>): String {
    val m = minute % WEEK
    return "${days[m / DAY]} %02d:%02d".format(Locale.getDefault(), m % DAY / 60, m % 60)
}

@Composable
fun SchedulePage(viewModel: UpsertFilterViewModel) {
    val context = LocalContext.current
    val schedule = viewModel.state.values.schedule
    val slots = schedule.normalizedSlots().sortedBy { it.start }
    val names = remember {
        val weekdays = DateFormatSymbols.getInstance(Locale.getDefault()).weekdays
        dayNumbers.map { weekdays[it] }
    }
    var editing by remember { mutableIntStateOf(-2) }
    var startDay by remember { mutableIntStateOf(0) }
    var endDay by remember { mutableIntStateOf(1) }
    var startMinute by remember { mutableIntStateOf(18 * 60 + 30) }
    var endMinute by remember { mutableIntStateOf(8 * 60 + 30) }
    var warning by remember { mutableStateOf<String?>(null) }

    fun open(index: Int) {
        editing = index
        val slot = slots.getOrNull(index)
        startDay = slot?.start?.div(DAY) ?: 0
        startMinute = slot?.start?.rem(DAY) ?: 1110
        endDay = slot?.end?.rem(WEEK)?.div(DAY) ?: 1
        endMinute = slot?.end?.rem(DAY) ?: 510
        warning = null
    }

    fun save() {
        val start = startDay * DAY + startMinute
        var end = endDay * DAY + endMinute
        if (end <= start) end += WEEK
        if (end - start >= WEEK) {
            warning = "A slot cannot cover the whole week."
            return
        }
        val next = slots.toMutableList()
        val slot = WeeklySlot(start, end)
        if (editing == -1) next.add(slot) else if (editing in next.indices) next[editing] = slot
        val updated = Schedule(slots = next.sortedBy { it.start })
        if (!updated.isRangeValid()) {
            warning = "At least one minute per week must remain free of snoozing."
            return
        }
        viewModel.updateForm(viewModel.state.page, viewModel.state.values.copy(schedule = updated))
        editing = -2
    }

    Text(stringResource(R.string.schedule_page_title), style = MaterialTheme.typography.titleMedium)
    Text("Weekly timeline · tap a colored period to edit", style = MaterialTheme.typography.bodySmall)
    val primary = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.surfaceVariant
    val selected = MaterialTheme.colorScheme.tertiary
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        names.forEachIndexed { day, name ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name.take(3), Modifier.width(48.dp), style = MaterialTheme.typography.labelSmall)
                Box(Modifier.weight(1f).height(24.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRoundRect(background, cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f))
                        slots.forEachIndexed { index, slot ->
                            listOf(slot.start to minOf(slot.end, WEEK),
                                0 to (slot.end - WEEK).coerceAtLeast(0)).forEach { (a,b) ->
                                val left = maxOf(a, day * DAY)
                                val right = minOf(b, (day + 1) * DAY)
                                if (right > left) drawRect(
                                    if (editing == index) selected else primary,
                                    topLeft = Offset(size.width * (left - day * DAY) / DAY, 0f),
                                    size = Size(size.width * (right - left) / DAY, size.height),
                                )
                            }
                        }
                    }
                    Row(Modifier.fillMaxSize()) {
                        repeat(24) { hour ->
                            val index = slots.indexOfFirst { slot ->
                                val minute = day * DAY + hour * 60
                                minute in slot.start until slot.end || minute + WEEK in slot.start until slot.end
                            }
                            Box(Modifier.weight(1f).fillMaxHeight().clickable(enabled = index >= 0) { open(index) })
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("00:00", style = MaterialTheme.typography.labelSmall)
            Text("06:00", style = MaterialTheme.typography.labelSmall)
            Text("12:00", style = MaterialTheme.typography.labelSmall)
            Text("18:00", style = MaterialTheme.typography.labelSmall)
            Text("24:00", style = MaterialTheme.typography.labelSmall)
        }
    }
    slots.forEachIndexed { index, slot ->
        val touching = slots.withIndex().any { (otherIndex, other) -> otherIndex != index &&
            (slot.end % WEEK == other.start || other.end % WEEK == slot.start) }
        val overlapping = slots.withIndex().any { (otherIndex, other) -> otherIndex != index &&
            listOf(-WEEK, 0, WEEK).any { shift ->
                slot.start < other.end + shift && other.start + shift < slot.end
            } }
        OutlinedCard(Modifier.fillMaxWidth().clickable { open(index) }) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${label(slot.start, names)} → ${label(slot.end, names)}")
                    if (touching) Text("⚠ Touches another slot", style = MaterialTheme.typography.bodySmall)
                    if (overlapping) Text("⚠ Overlaps another slot", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = {
                    viewModel.updateForm(viewModel.state.page,
                        viewModel.state.values.copy(schedule = Schedule(slots = slots.filterIndexed { i, _ -> i != index })))
                    editing = -2
                }) { Text("×") }
            }
        }
    }
    OutlinedButton(onClick = { open(-1) }) { Text("+ Add a slot") }

    if (editing != -2) {
        HorizontalDivider()
        Text(if (editing == -1) "New slot" else "Edit slot", style = MaterialTheme.typography.titleSmall)
        @Composable
        fun DaySelector(current: Int, update: (Int) -> Unit) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }) { Text(names[current]) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    names.forEachIndexed { i, day ->
                        DropdownMenuItem(text = { Text(day) }, onClick = { update(i); expanded = false })
                    }
                }
            }
        }
        @Composable
        fun TimeSelector(value: Int, update: (Int) -> Unit) {
            OutlinedButton(onClick = {
                TimePickerDialog(context, { _, h, m -> update(h * 60 + m) },
                    value / 60, value % 60, true).show()
            }) { Text("%02d:%02d".format(Locale.getDefault(), value / 60, value % 60)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("From")
            DaySelector(startDay) { startDay = it }
            TimeSelector(startMinute) { startMinute = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("To")
            DaySelector(endDay) { endDay = it }
            TimeSelector(endMinute) { endMinute = it }
        }
        warning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row {
            Button(onClick = { save() }) { Text("Save slot") }
            TextButton(onClick = { editing = -2 }) { Text("Cancel") }
        }
    }
    if (viewModel.state.error == FormError.INVALID_TIME_RANGE) ErrorText(R.string.invalid_time_range)
    if (viewModel.state.error == FormError.BLANK_FIELDS) ErrorText(R.string.empty_active_days)
}
