package com.salonbooking.app.customer.booking

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salonbooking.app.R
import com.salonbooking.app.core.design.SalonTheme
import com.salonbooking.app.core.network.BookingDto
import java.time.Instant

/** Book at one branch (APP-10, BKG-2..11): services, stylist, time, details, review, confirmation. */
@Composable
fun BookingFlowScreen(onClose: () -> Unit, onOpenBooking: (token: String) -> Unit, viewModel: BookingFlowViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.created == null && state.draft.step != Step.SERVICES) { viewModel.back() }

    SalonTheme(accentHex = state.salon?.accentColor) {
        val created = state.created
        if (created != null) {
            Confirmation(created.booking, onDone = onClose, onOpen = { onOpenBooking(created.manageToken) })
            return@SalonTheme
        }
        BookingScaffold(state, viewModel, onBack = { if (!viewModel.back()) onClose() }) {
            when {
                state.loadError != null -> item { ErrorMessage(state.loadError!!, viewModel::retry) }
                state.catalog == null -> item { Box(Modifier.fillMaxWidth().padding(48.dp)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) } }
                else -> when (state.draft.step) {
                    Step.SERVICES -> servicesStep(state, viewModel)
                    Step.STYLIST -> stylistStep(state, viewModel)
                    Step.TIME -> timeStep(state, viewModel)
                    Step.DETAILS -> detailsStep(state, viewModel)
                    Step.REVIEW -> reviewStep(state, viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingScaffold(state: BookingFlowState, vm: BookingFlowViewModel, onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.salon?.displayName.orEmpty(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        state.branch?.let {
                            Text(
                                Formats.branch(it.name, it.suburb, it.state),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back)) }
                },
            )
        },
        bottomBar = { BottomBar(state, vm) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (state.catalog != null && state.loadError == null) {
                item { StepHeader(state.draft.step) }
            }
            content()
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** The summary and next action for the current step (BKG-4); nothing until there is something to show. */
@Composable
private fun BottomBar(state: BookingFlowState, vm: BookingFlowViewModel) {
    if (state.catalog == null || state.loadError != null) return
    val tz = state.branch?.timeZone ?: return
    val services = state.services
    val summary = "${services.size} ${if (services.size == 1) "service" else "services"}, ${Formats.duration(services.sumOf { it.durationMin })}"
    val price = (if (services.any { it.priceIsFrom }) "From " else "") + Formats.money(services.sumOf { it.priceCents })
    val start = state.draft.start
    when (state.draft.step) {
        Step.SERVICES -> if (services.isNotEmpty()) ActionBar(summary, price, "Continue", { vm.goTo(Step.STYLIST) })
        Step.STYLIST -> if (state.qualifiedStylists.isNotEmpty()) ActionBar(summary, price, "Choose a time", { vm.goTo(Step.TIME) })
        Step.TIME -> if (start == null) {
            ActionBar(summary, "Choose a time", "Continue", {}, enabled = false)
        } else {
            ActionBar("${Formats.shortDate(start, tz)}, ${Formats.clock(start, tz)}", "$summary. $price", "Continue", { vm.goTo(Step.DETAILS) })
        }
        Step.DETAILS -> ActionBar(summary, price, "Review", vm::continueFromDetails)
        Step.REVIEW -> if (start != null) {
            ActionBar("${Formats.shortDate(start, tz)}, ${Formats.clock(start, tz)}", price, "Confirm booking", vm::confirm, busy = state.submitting)
        }
    }
}

@Composable
private fun StepHeader(step: Step) {
    val (number, title) = when (step) {
        Step.SERVICES -> 1 to "What would you like?"
        Step.STYLIST -> 2 to "Who would you like?"
        Step.TIME -> 3 to "When suits you?"
        Step.DETAILS -> 4 to "Your details"
        Step.REVIEW -> 5 to "Check and confirm"
    }
    Column(Modifier.padding(bottom = 8.dp)) {
        LinearProgressIndicator(progress = { number / 5f }, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), drawStopIndicator = {})
        Text("Step $number of 5", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    }
}

/* ---------------------------------------------------------------------------- Steps */

private fun LazyListScope.servicesStep(state: BookingFlowState, vm: BookingFlowViewModel) {
    val catalog = state.catalog ?: return
    if (catalog.categories.isEmpty()) {
        item { Notice("This branch has no services to book online yet. Please call the salon.") }
        return
    }
    catalog.categories.forEach { category ->
        item(key = "cat-${category.name}") {
            Text(category.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        }
        items(category.services, key = { it.id }) { service ->
            val checked = service.id in state.draft.serviceIds
            ChoiceRow(
                checked = checked,
                onClick = { vm.toggleService(service.id) },
                aside = {
                    Text((if (service.priceIsFrom) "from " else "") + Formats.money(service.priceCents), fontWeight = FontWeight.SemiBold)
                    Text(Formats.duration(service.durationMin), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
            ) {
                Text(service.name, style = MaterialTheme.typography.titleMedium)
                service.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (checked && service.customerNotice != null) {
                    Spacer(Modifier.height(8.dp))
                    Notice(service.customerNotice)
                }
            }
        }
    }
}

private fun LazyListScope.stylistStep(state: BookingFlowState, vm: BookingFlowViewModel) {
    val stylists = state.qualifiedStylists
    if (stylists.isEmpty()) {
        item { Notice("No one here does all of these in one visit. Go back and remove a service, or call the salon.") }
        return
    }
    item {
        ChoiceRow(checked = state.draft.staffId == null, radio = true, onClick = { vm.selectStylist(null) }) {
            Text("Anyone available", style = MaterialTheme.typography.titleMedium)
            Text("Shows the most times", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    items(stylists, key = { it.id }) { s ->
        ChoiceRow(checked = state.draft.staffId == s.id, radio = true, onClick = { vm.selectStylist(s.id) }) {
            Text(s.displayName, style = MaterialTheme.typography.titleMedium)
            s.bio?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private fun LazyListScope.timeStep(state: BookingFlowState, vm: BookingFlowViewModel) {
    val branch = state.branch ?: return
    if (state.timeGone) item { Notice("That time was just booked by someone else. Your other choices are kept; pick another time.", error = true) }
    state.time.error?.let { item { Notice(it, error = true) } }
    item {
        TimePicker(
            timeZone = branch.timeZone,
            from = state.time.from,
            days = state.time.days,
            lastBookableDate = state.time.lastBookableDate,
            selectedDate = state.draft.date,
            slots = state.time.slots,
            loadingSlots = state.time.loadingSlots,
            selectedStart = state.draft.start,
            onPage = vm::loadDays,
            onDate = vm::selectDate,
            onStart = vm::selectStart,
        )
    }
}

private fun LazyListScope.detailsStep(state: BookingFlowState, vm: BookingFlowViewModel) {
    val errors = if (state.showErrors) state.detailErrors else emptyMap()
    val d = state.draft
    item {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field("Name", d.fullName, errors["fullName"], KeyboardOptions(capitalization = KeyboardCapitalization.Words)) { vm.editDetails(fullName = it) }
            Field("Email", d.email, errors["email"], KeyboardOptions(keyboardType = KeyboardType.Email), hint = "For your booking details.") { vm.editDetails(email = it) }
            Field("Mobile", d.phone, errors["phone"], KeyboardOptions(keyboardType = KeyboardType.Phone), hint = "So the salon can reach you about this booking.") { vm.editDetails(phone = it) }
            OutlinedTextField(
                value = d.note,
                onValueChange = { vm.editDetails(note = it) },
                label = { Text("Note for the salon (optional)") },
                supportingText = { Text("${500 - d.note.length} characters left") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            val cutoff = Formats.cutoff(state.salon?.cancelCutoffMin ?: 1440)
            Row(
                Modifier.fillMaxWidth().toggleable(value = d.acceptsPolicy, role = Role.Checkbox, onValueChange = { vm.editDetails(acceptsPolicy = it) }),
                verticalAlignment = Alignment.Top,
            ) {
                Checkbox(checked = d.acceptsPolicy, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
                Text("I agree to the cancellation policy: change or cancel online up to $cutoff before the appointment. After that, call the salon.")
            }
            errors["policy"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Text(
                "${state.salon?.displayName} uses your details only to manage this booking and contact you about it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Field(label: String, value: String, error: String?, keyboard: KeyboardOptions, hint: String? = null, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = (error ?: hint)?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = keyboard,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun LazyListScope.reviewStep(state: BookingFlowState, vm: BookingFlowViewModel) {
    val branch = state.branch ?: return
    val start = state.draft.start ?: run {
        item { Notice("Choose a time first.") }
        return
    }
    val tz = branch.timeZone
    val end = Instant.parse(start).plusSeconds(state.services.sumOf { it.durationMin } * 60L).toString()
    val stylist = state.catalog?.stylists?.find { it.id == state.draft.staffId }?.displayName ?: "Anyone available"
    val rows = listOf(
        Triple("When", "${Formats.longDate(start, tz)}\n${Formats.clock(start, tz)} to about ${Formats.clock(end, tz)}", Step.TIME),
        Triple("Where", Formats.branch(branch.name, branch.suburb, branch.state), null),
        Triple("Services", state.services.joinToString("\n") { "${it.name}  ${if (it.priceIsFrom) "from " else ""}${Formats.money(it.priceCents)}" }, Step.SERVICES),
        Triple("With", stylist, Step.STYLIST),
        Triple("Your details", "${state.draft.fullName}\n${state.draft.email}\n${state.draft.phone}", Step.DETAILS),
    )
    items(rows, key = { it.first }) { (label, value, edit) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(100.dp))
            Text(value, modifier = Modifier.weight(1f))
            edit?.let { step -> TextButton(onClick = { vm.goTo(step) }) { Text("Change") } }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    item {
        Text(
            "You can change or cancel online up to ${Formats.cutoff(state.salon?.cancelCutoffMin ?: 1440)} before the appointment.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
    state.submitError?.let { item { Notice(it, error = true) } }
}

@Composable
private fun ErrorMessage(text: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

/* --------------------------------------------------------------------- Confirmation */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Confirmation(booking: BookingDto, onDone: () -> Unit, onOpen: () -> Unit) {
    val context = LocalContext.current
    val tz = booking.branch.timeZone
    Scaffold(topBar = { TopAppBar(title = { Text(booking.businessName, fontWeight = FontWeight.SemiBold) }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text("You're booked in", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text("${Formats.longDate(booking.startAt, tz)} at ${Formats.clock(booking.startAt, tz)}", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            item { BookingSummary(booking) }
            item {
                Text(
                    "We've emailed the details to ${booking.email ?: "you"}. This booking is also saved in My bookings on this phone.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { addToCalendar(context, booking) }, modifier = Modifier.fillMaxWidth()) { Text("Add to calendar") }
                    OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("View booking") }
                    TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                }
            }
        }
    }
}

/** What, when, where and with whom; shared with the booking detail screen. */
@Composable
fun BookingSummary(booking: BookingDto) {
    val tz = booking.branch.timeZone
    val b = booking.branch
    val minutes = booking.services.sumOf { it.durationMin }
    val rows = listOfNotNull(
        "When" to "${Formats.longDate(booking.startAt, tz)}\n${Formats.clock(booking.startAt, tz)} to about ${Formats.clock(booking.endAt, tz)} (${Formats.duration(minutes)})",
        "Services" to booking.services.joinToString("\n") { "${it.name}  ${if (it.priceIsFrom) "from " else ""}${Formats.money(it.priceCents)}" },
        "With" to booking.staffName,
        "Where" to listOfNotNull(b.name, b.addressLine1, b.addressLine2, "${b.suburb} ${b.state} ${b.postcode}").joinToString("\n"),
        b.phone?.let { "Phone" to it },
        "Reference" to booking.reference,
    )
    Column {
        rows.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(100.dp))
                Text(value, modifier = Modifier.weight(1f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** Opens the phone's calendar with the appointment filled in (BKG-11). */
fun addToCalendar(context: Context, booking: BookingDto) {
    val b = booking.branch
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, Instant.parse(booking.startAt).toEpochMilli())
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, Instant.parse(booking.endAt).toEpochMilli())
        .putExtra(CalendarContract.Events.TITLE, "${booking.services.joinToString(", ") { it.name }} at ${booking.businessName}")
        .putExtra(CalendarContract.Events.EVENT_LOCATION, "${b.name}, ${b.addressLine1}, ${b.suburb} ${b.state}")
        .putExtra(CalendarContract.Events.DESCRIPTION, "With ${booking.staffName}. Reference ${booking.reference}.")
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No calendar app: nothing to open. The booking is still saved in My bookings.
    }
}
