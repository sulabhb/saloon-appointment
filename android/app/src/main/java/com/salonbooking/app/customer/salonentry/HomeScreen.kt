package com.salonbooking.app.customer.salonentry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salonbooking.app.core.design.BrandColor
import com.salonbooking.app.customer.booking.Formats
import com.salonbooking.app.customer.mybookings.HomeState
import com.salonbooking.app.customer.mybookings.HomeViewModel
import com.salonbooking.app.customer.mybookings.SavedBooking
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.salonbooking.app.R
import com.salonbooking.app.core.design.SalonTheme
import com.salonbooking.app.core.link.SalonCode

/** Customer entry point: open a salon by its code, saved salons and my bookings (TEN-6, APP-3, APP-11, APP-12). */
@Composable
fun HomeScreen(
    onOpenSalon: (code: String) -> Unit,
    onOpenBooking: (token: String) -> Unit,
    onStaff: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val saved by viewModel.state.collectAsStateWithLifecycle()
    HomeContent(saved, onOpenSalon, onOpenBooking, onStaff)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeContent(saved: HomeState, onOpenSalon: (code: String) -> Unit, onOpenBooking: (token: String) -> Unit, onStaff: () -> Unit) {
    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (saved.upcoming.isNotEmpty()) {
                item { SectionTitle("My bookings") }
                items(saved.upcoming, key = { it.booking.reference }) { BookingRow(it, onOpenBooking) }
            }
            item {
                Text(
                    stringResource(R.string.home_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(top = if (saved.upcoming.isEmpty()) 0.dp else 16.dp),
                )
            }
            item {
                Text(
                    stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (saved.salons.isNotEmpty()) {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        saved.salons.forEach { salon ->
                            AssistChip(onClick = { onOpenSalon(salon.slug) }, label = { Text(salon.displayName) })
                        }
                    }
                }
            }
            item { CodeEntry(onOpenSalon) }
            if (saved.past.isNotEmpty()) {
                item { SectionTitle("Past and cancelled") }
                items(saved.past, key = { it.booking.reference }) { BookingRow(it, onOpenBooking) }
            }
            item {
                TextButton(onClick = onStaff, modifier = Modifier.padding(top = 24.dp)) { Text("Staff sign in") }
            }
        }
    }
}

@Composable
private fun CodeEntry(onOpenSalon: (code: String) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(false) }

    fun submit() {
        val code = SalonCode.normalize(input)
        showError = code == null
        if (code != null) onOpenSalon(code)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                showError = false
            },
            label = { Text(stringResource(R.string.home_code_label)) },
            isError = showError,
            supportingText = if (showError) {
                { Text(stringResource(R.string.home_code_invalid)) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = ::submit, enabled = input.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.home_open))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

/** One saved booking; the salon's colour marks it, with the status as text, not colour alone. */
@Composable
private fun BookingRow(saved: SavedBooking, onOpen: (token: String) -> Unit) {
    val b = saved.booking
    val tz = b.branch.timeZone
    val accent = BrandColor.parse(b.accentColor)?.let { Color(it.toLong() or 0xFF000000L) } ?: MaterialTheme.colorScheme.primary
    Card(onClick = { onOpen(saved.token) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(accent))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${Formats.longDate(b.startAt, tz)}, ${Formats.clock(b.startAt, tz)}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${b.services.joinToString(", ") { it.name }} with ${b.staffName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    buildString {
                        append(b.businessName)
                        when (b.status) {
                            "CANCELLED" -> append(". Cancelled")
                            "COMPLETED" -> append(". Completed")
                            "NO_SHOW" -> append(". Missed")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    SalonTheme { HomeContent(HomeState(), onOpenSalon = {}, onOpenBooking = {}, onStaff = {}) }
}
