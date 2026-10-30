package com.salonbooking.app.customer.manage

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.salonbooking.app.core.design.SalonTheme
import com.salonbooking.app.core.network.BookingDto
import com.salonbooking.app.customer.booking.ActionBar
import com.salonbooking.app.customer.booking.BookingSummary
import com.salonbooking.app.customer.booking.ChoiceRow
import com.salonbooking.app.customer.booking.Formats
import com.salonbooking.app.customer.booking.Notice
import com.salonbooking.app.customer.booking.TimePicker
import com.salonbooking.app.customer.booking.addToCalendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageBookingScreen(onBack: () -> Unit, onBookAgain: (salon: String) -> Unit, viewModel: ManageBookingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.mode != ManageMode.VIEW) { viewModel.setMode(ManageMode.VIEW) }
    val booking = state.booking

    SalonTheme(accentHex = booking?.accentColor) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(booking?.businessName ?: "Your booking", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = { if (state.mode != ManageMode.VIEW) viewModel.setMode(ManageMode.VIEW) else onBack() }) {
                            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                        }
                    },
                )
            },
            bottomBar = {
                if (state.mode == ManageMode.MOVE && booking != null) {
                    val start = state.move.start
                    val tz = booking.branch.timeZone
                    val stylist = state.move.options?.stylists?.find { it.id == state.move.staffId }?.displayName
                    if (start == null) {
                        ActionBar("Choose a new time", null, "Move booking", {}, enabled = false)
                    } else {
                        ActionBar(
                            "${Formats.shortDate(start, tz)}, ${Formats.clock(start, tz)}",
                            if (stylist != null) "With $stylist" else "With whoever is free",
                            "Move booking",
                            viewModel::confirmMove,
                            busy = state.move.moving,
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when {
                    state.expired -> Message("This link no longer works. It may have expired, or a newer link was sent to your email.")
                    booking == null && state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    booking == null -> Message(state.error ?: "We couldn't load this booking.", viewModel::refresh)
                    state.mode == ManageMode.MOVE -> MoveContent(state, booking, viewModel)
                    else -> ViewContent(state, booking, viewModel, onBookAgain)
                }
            }
        }
    }
}

@Composable
private fun ViewContent(state: ManageState, booking: BookingDto, vm: ManageBookingViewModel, onBookAgain: (String) -> Unit) {
    val context = LocalContext.current
    val tz = booking.branch.timeZone
    val cancelled = booking.status == "CANCELLED"
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(if (cancelled) "This booking is cancelled" else "Your booking", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                if (cancelled) "The time has been released." else "Booked for ${booking.customerName}.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.offline) item { Notice("You're offline. This is the copy saved on this phone; changes need a connection.") }
        state.notice?.let { item { Notice(it) } }
        state.error?.let { item { Notice(it, error = true) } }
        item { BookingSummary(booking) }
        if (!cancelled) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Need to change it?", style = MaterialTheme.typography.titleMedium)
                    if (booking.canCancel) {
                        Text(
                            "You can move or cancel online until ${Formats.longDate(booking.cancelBefore, tz)} at ${Formats.clock(booking.cancelBefore, tz)}.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.mode == ManageMode.CONFIRM_CANCEL) {
                            Text("Cancel this booking?", style = MaterialTheme.typography.titleSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { vm.setMode(ManageMode.VIEW) }) { Text("Keep it") }
                                Button(
                                    onClick = vm::cancel,
                                    enabled = !state.busy && !state.offline,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                                ) { Text(if (state.busy) "Cancelling…" else "Yes, cancel") }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { vm.setMode(ManageMode.MOVE) }, enabled = !state.offline) { Text("Change time") }
                                OutlinedButton(onClick = { vm.setMode(ManageMode.CONFIRM_CANCEL) }, enabled = !state.offline) { Text("Cancel booking") }
                            }
                        }
                    } else {
                        Text("It's too late to change this online.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        booking.branch.phone?.let { phone ->
                            OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, "tel:${phone.replace(" ", "")}".toUri())) }) {
                                Text("Call ${booking.branch.name}")
                            }
                        }
                    }
                    TextButton(onClick = { addToCalendar(context, booking) }) { Text("Add to calendar") }
                }
            }
        } else {
            item { Button(onClick = { onBookAgain(booking.businessSlug) }) { Text("Book again") } }
        }
    }
}

@Composable
private fun MoveContent(state: ManageState, booking: BookingDto, vm: ManageBookingViewModel) {
    val tz = booking.branch.timeZone
    val move = state.move
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Choose a new time", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Now: ${Formats.longDate(booking.startAt, tz)} at ${Formats.clock(booking.startAt, tz)} with ${booking.staffName}. Same services and branch.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.error?.let { item { Notice(it, error = true) } }
        val options = move.options
        if (options == null) {
            item { Box(Modifier.fillMaxWidth().padding(24.dp)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) } }
            return@LazyColumn
        }
        if (options.stylists.size > 1) {
            item { Text("Stylist", style = MaterialTheme.typography.titleSmall) }
            item {
                ChoiceRow(checked = move.staffId == null, radio = true, onClick = { vm.selectStylist(null) }) { Text("Anyone available") }
            }
            items(options.stylists, key = { it.id }) { s ->
                ChoiceRow(checked = move.staffId == s.id, radio = true, onClick = { vm.selectStylist(s.id) }) {
                    Text(if (s.id == options.currentStaffId) "${s.displayName} (your stylist)" else s.displayName)
                }
            }
        }
        if (move.timeGone) item { Notice("That time was just booked by someone else. Your booking hasn't changed; pick another time.", error = true) }
        move.time.error?.let { item { Notice(it, error = true) } }
        item {
            TimePicker(
                timeZone = tz,
                from = move.time.from,
                days = move.time.days,
                lastBookableDate = move.time.lastBookableDate,
                selectedDate = move.date,
                slots = move.time.slots,
                loadingSlots = move.time.loadingSlots,
                selectedStart = move.start,
                onPage = vm::loadDays,
                onDate = vm::selectDate,
                onStart = vm::selectStart,
            )
        }
        item { TextButton(onClick = { vm.setMode(ManageMode.VIEW) }) { Text("Keep my booking as it is") } }
    }
}

@Composable
private fun Message(text: String, onRetry: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        onRetry?.let { OutlinedButton(onClick = it) { Text(stringResource(R.string.retry)) } }
    }
}
