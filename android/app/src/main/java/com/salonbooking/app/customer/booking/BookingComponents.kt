package com.salonbooking.app.customer.booking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.salonbooking.app.core.network.DayDto
import com.salonbooking.app.core.network.SlotDto
import java.time.LocalDate

/**
 * The always-visible summary and next action (BKG-4), in the salon's colour, clear of the
 * navigation bar.
 */
@Composable
fun ActionBar(summary: String, detail: String?, action: String, onAction: () -> Unit, enabled: Boolean = true, busy: Boolean = false) {
    Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(summary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Button(
                onClick = onAction,
                enabled = enabled && !busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    contentColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                    disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                ),
                modifier = Modifier.height(48.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text(action, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** A full-width choice: checkbox for services, radio for one-of choices. */
@Composable
fun ChoiceRow(checked: Boolean, onClick: () -> Unit, radio: Boolean = false, aside: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    val modifier = if (radio) {
        Modifier.selectable(selected = checked, onClick = onClick, role = Role.RadioButton)
    } else {
        Modifier.toggleable(value = checked, onValueChange = { onClick() }, role = Role.Checkbox)
    }
    // Without their own click handler the controls drop the 48dp touch target and are about 24dp
    // tall, so the first line of text lines up with them with just a little top padding. The whole
    // row is the touch target.
    Row(modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (radio) RadioButton(selected = checked, onClick = null) else Checkbox(checked = checked, onCheckedChange = null)
        Column(Modifier.weight(1f).padding(top = 1.dp)) { content() }
        aside?.let { Column(Modifier.padding(top = 1.dp), horizontalAlignment = Alignment.End) { it() } }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun Notice(text: String, error: Boolean = false) {
    Surface(
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium) }
}

/**
 * Two weeks of days (closed and full ones crossed out) and the free times of the chosen day,
 * grouped by part of the day (BKG-6, AVL-6). Used for new bookings and for moving one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimePicker(
    timeZone: String,
    from: String?,
    days: List<DayDto>,
    lastBookableDate: String?,
    selectedDate: String?,
    slots: List<SlotDto>?,
    loadingSlots: Boolean,
    selectedStart: String?,
    onPage: (from: String) -> Unit,
    onDate: (String) -> Unit,
    onStart: (String) -> Unit,
) {
    val today = Formats.today(timeZone)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Formats.month(selectedDate ?: from ?: today), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            val start = from ?: today
            TextButton(
                onClick = { onPage(maxOf(today, LocalDate.parse(start).minusDays(BookingFlowViewModel.DAYS_SHOWN.toLong()).toString())) },
                enabled = start > today,
            ) { Text("Earlier") }
            TextButton(
                onClick = { onPage(LocalDate.parse(start).plusDays(BookingFlowViewModel.DAYS_SHOWN.toLong()).toString()) },
                enabled = lastBookableDate == null || LocalDate.parse(start).plusDays(BookingFlowViewModel.DAYS_SHOWN - 1L).toString() < lastBookableDate,
            ) { Text("Later") }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(days, key = { it.date }) { day ->
                val open = day.status == "AVAILABLE"
                val selected = day.date == selectedDate
                val label = "${Formats.weekday(day.date)} ${day.date.takeLast(2).toInt()}" +
                    when (day.status) { "FULL" -> ", fully booked"; "CLOSED" -> ", closed"; else -> "" }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = when {
                        selected -> MaterialTheme.colorScheme.onPrimary
                        open -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                    border = if (selected || !open) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .width(56.dp)
                        .height(72.dp)
                        .semantics { contentDescription = label }
                        .selectable(selected = selected, enabled = open, role = Role.RadioButton, onClick = { onDate(day.date) }),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(Formats.weekday(day.date), style = MaterialTheme.typography.labelMedium)
                        Text(
                            "${day.date.takeLast(2).toInt()}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = if (open) null else TextDecoration.LineThrough,
                        )
                    }
                }
            }
        }
        when {
            loadingSlots -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
            slots != null && slots.isEmpty() -> Notice("No times left on this day. Choose another day.")
            days.isNotEmpty() && days.none { it.status == "AVAILABLE" } -> Notice("Nothing free in these two weeks. Try later dates.")
        }
        val groups = listOf(
            "Morning" to slots.orEmpty().filter { it.time < "12:00" },
            "Afternoon" to slots.orEmpty().filter { it.time >= "12:00" && it.time < "17:00" },
            "Evening" to slots.orEmpty().filter { it.time >= "17:00" },
        ).filter { it.second.isNotEmpty() }
        groups.forEach { (label, items) ->
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
                items.forEach { slot ->
                    val selected = slot.start == selectedStart
                    val text = Formats.clock(slot.start, timeZone)
                    if (selected) {
                        Button(onClick = { onStart(slot.start) }, modifier = Modifier.weight(1f).height(48.dp)) { Text(text) }
                    } else {
                        OutlinedButton(onClick = { onStart(slot.start) }, modifier = Modifier.weight(1f).height(48.dp)) {
                            Text(text, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                // Keep the last row's buttons the same width as the others.
                repeat((3 - items.size % 3) % 3) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
