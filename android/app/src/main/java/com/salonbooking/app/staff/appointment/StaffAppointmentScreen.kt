package com.salonbooking.app.staff.appointment

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salonbooking.app.R
import com.salonbooking.app.core.network.AppointmentDto
import com.salonbooking.app.customer.booking.Formats
import com.salonbooking.app.customer.booking.Notice
import com.salonbooking.app.staff.StaffLabels

/** One appointment for staff (APP-22): details, allowed status changes and the staff-only note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffAppointmentScreen(onBack: () -> Unit, viewModel: StaffAppointmentViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val a = state.appointment
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(a?.customer?.fullName ?: "Appointment", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                a == null && state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                a == null -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(state.error ?: "We couldn't load this appointment.")
                    OutlinedButton(onClick = viewModel::refresh) { Text(stringResource(R.string.retry)) }
                }
                else -> Content(a, state, viewModel)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Content(a: AppointmentDto, state: StaffAppointmentState, vm: StaffAppointmentViewModel) {
    val context = LocalContext.current
    val tz = a.timeZone
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(
                "${Formats.longDate(a.startAt, tz)}, ${Formats.clock(a.startAt, tz)} to ${Formats.clock(a.endAt, tz)}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                buildString {
                    append(StaffLabels.status(a.status))
                    if (a.status == "CANCELLED") {
                        append(if (a.cancelledBy == "customer") " by the customer" else " by staff")
                        if (a.lateCancel) append(", after the cut-off")
                        a.cancelReason?.let { append(": $it") }
                    }
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        state.notice?.let { item { Notice(it) } }
        state.error?.let { item { Notice(it, error = true) } }

        val actions = a.allowedTransitions.filter { it != "CANCELLED" }
        val canCancel = "CANCELLED" in a.allowedTransitions
        if (state.confirmingCancel) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.cancelReason,
                        onValueChange = vm::editReason,
                        label = { Text("Reason (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = vm::keepAppointment) { Text("Keep it") }
                        Button(
                            onClick = { vm.change("CANCELLED") },
                            enabled = !state.busy,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                        ) { Text("Cancel appointment") }
                    }
                }
            }
        } else if (actions.isNotEmpty() || canCancel) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions.forEach { to ->
                        if (to == "NO_SHOW") {
                            OutlinedButton(onClick = { vm.change(to) }, enabled = !state.busy) { Text(StaffLabels.action(a.status, to)) }
                        } else {
                            Button(onClick = { vm.change(to) }, enabled = !state.busy) { Text(StaffLabels.action(a.status, to)) }
                        }
                    }
                    if (canCancel) TextButton(onClick = { vm.change("CANCELLED") }, enabled = !state.busy) { Text("Cancel appointment") }
                }
            }
        }

        item {
            val rows = listOfNotNull(
                "Services" to a.services.joinToString("\n") { "${it.name}  ${Formats.duration(it.durationMin)}, ${if (it.priceIsFrom) "from " else ""}${Formats.money(it.priceCents)}" },
                "Stylist" to a.staffName,
                "Branch" to a.locationName,
                "Reference" to a.reference,
                a.customer.email?.let { "Email" to it },
                "Booked" to when (a.source) {
                    "web" -> "Online"
                    "android" -> "In the app"
                    "walk_in" -> "Walk-in" + (a.createdByName?.let { ", added by $it" } ?: "")
                    else -> a.createdByName?.let { "By $it" } ?: "By staff"
                },
            )
            Column {
                rows.forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
                        Text(value, modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        a.customer.phone?.let { phone ->
            item {
                OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$phone".toUri())) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Call ${a.customer.fullName.substringBefore(' ')}, $phone")
                }
            }
        }
        a.customerNote?.let { item { Notice("From the customer: $it") } }
        item {
            if (a.canEditNote) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.note,
                        onValueChange = vm::editNote,
                        label = { Text("Staff note") },
                        supportingText = { Text("Only staff see this.") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.noteChanged) OutlinedButton(onClick = vm::saveNote, enabled = !state.busy) { Text("Save note") }
                }
            } else if (!a.internalNote.isNullOrBlank()) {
                Text("Staff note", style = MaterialTheme.typography.titleSmall)
                Text(a.internalNote, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
