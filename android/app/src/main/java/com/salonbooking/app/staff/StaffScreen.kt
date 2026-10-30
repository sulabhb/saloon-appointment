package com.salonbooking.app.staff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salonbooking.app.R
import com.salonbooking.app.core.network.AgendaItemDto
import com.salonbooking.app.core.network.MeDto
import com.salonbooking.app.customer.booking.Formats
import com.salonbooking.app.customer.booking.Notice
import com.salonbooking.app.staff.session.SessionState
import com.salonbooking.app.staff.signin.SignInForm
import com.salonbooking.app.staff.signin.StaffGateViewModel
import com.salonbooking.app.staff.today.TodayState
import com.salonbooking.app.staff.today.TodayViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Staff mode: sign in, choose a business, then the day (APP-20, APP-21). */
@Composable
fun StaffScreen(onBack: () -> Unit, onOpenAppointment: (id: String) -> Unit, gate: StaffGateViewModel = hiltViewModel()) {
    val state by gate.state.collectAsStateWithLifecycle()
    val restoreError by gate.restoreError.collectAsStateWithLifecycle()
    when (val s = state) {
        SessionState.Restoring -> Box(Modifier.fillMaxSize()) {
            if (restoreError == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                Column(Modifier.align(Alignment.Center).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(restoreError!!)
                    OutlinedButton(onClick = gate::restore) { Text(stringResource(R.string.retry)) }
                }
            }
        }
        SessionState.SignedOut -> {
            val form by gate.form.collectAsStateWithLifecycle()
            SignInContent(form, onBack, gate::edit, gate::signIn)
        }
        is SessionState.SignedIn -> {
            val selected = s.me.selected
            if (selected == null) {
                ChooseBusiness(s.me, restoreError, gate::selectBusiness, gate::signOut)
            } else {
                // A new view model per business, so nothing from one business shows in another.
                val today: TodayViewModel = hiltViewModel(key = "today-${selected.businessId}")
                val todayState by today.state.collectAsStateWithLifecycle()
                TodayContent(
                    me = s.me,
                    state = todayState,
                    onBack = onBack,
                    onRefresh = today::refresh,
                    onShiftDay = today::shiftDay,
                    onToday = today::goToToday,
                    onOpen = onOpenAppointment,
                    onSwitchBusiness = { gate.selectBusiness(it) },
                    onSignOut = gate::signOut,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignInContent(form: SignInForm, onBack: () -> Unit, onEdit: (String?, String?) -> Unit, onSignIn: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Staff sign in") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("For salon staff. Customers don't need an account to book.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = form.email,
                onValueChange = { onEdit(it, null) },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.EmailAddress },
            )
            OutlinedTextField(
                value = form.password,
                onValueChange = { onEdit(null, it) },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onSignIn() }),
                modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
            )
            form.error?.let { Notice(it, error = true) }
            Button(onClick = onSignIn, enabled = !form.busy && form.email.isNotBlank() && form.password.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                if (form.busy) CircularProgressIndicator(Modifier.padding(2.dp).width(18.dp), strokeWidth = 2.dp) else Text("Sign in")
            }
            Text("Forgot your password? Reset it from the sign-in page of the web console.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChooseBusiness(me: MeDto, error: String?, onChoose: (String) -> Unit, onSignOut: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Choose a business") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Hi ${me.user.fullName.substringBefore(' ')}, you work in more than one. You can switch later from the menu.") }
            error?.let { item { Notice(it, error = true) } }
            if (me.businesses.isEmpty()) item { Notice("Your account is not part of a business.") }
            items(me.businesses, key = { it.businessId }) { b ->
                Card(onClick = { onChoose(b.businessId) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(b.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(StaffLabels.role(b.role), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { TextButton(onClick = onSignOut) { Text("Sign out") } }
        }
    }
}

private val DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.forLanguageTag("en-AU"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayContent(
    me: MeDto,
    state: TodayState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onShiftDay: (Long) -> Unit,
    onToday: () -> Unit,
    onOpen: (String) -> Unit,
    onSwitchBusiness: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    val business = me.businesses.find { it.businessId == me.selected?.businessId }
    var menu by remember { mutableStateOf(false) }
    // Back from an appointment (or the app) the day may have changed: show it fresh.
    LifecycleResumeEffect(Unit) {
        onRefresh()
        onPauseOrDispose { }
    }
    val isToday = state.date == LocalDate.now().toString()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(business?.displayName.orEmpty(), fontWeight = FontWeight.SemiBold)
                        Text(
                            "${me.user.fullName}, ${StaffLabels.role(me.selected?.role.orEmpty()).lowercase()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back)) } },
                actions = {
                    TextButton(onClick = { menu = true }) { Text("Account") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        me.businesses.filter { it.businessId != business?.businessId }.forEach { other ->
                            DropdownMenuItem(text = { Text("Switch to ${other.displayName}") }, onClick = {
                                menu = false
                                onSwitchBusiness(other.businessId)
                            })
                        }
                        DropdownMenuItem(text = { Text("Sign out") }, onClick = {
                            menu = false
                            onSignOut()
                        })
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.loading && state.items != null, onRefresh = onRefresh, modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                item {
                    Column(Modifier.padding(bottom = 8.dp)) {
                        Text(
                            if (isToday) "Today, ${DAY.format(LocalDate.parse(state.date))}" else DAY.format(LocalDate.parse(state.date)),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            state.items?.let {
                                val n = state.active.size
                                Text(
                                    "$n ${if (n == 1) "appointment" else "appointments"}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                            } ?: Box(Modifier.weight(1f))
                            TextButton(onClick = { onShiftDay(-1) }) { Text("Previous") }
                            if (!isToday) TextButton(onClick = onToday) { Text("Today") }
                            TextButton(onClick = { onShiftDay(1) }) { Text("Next") }
                        }
                    }
                }
                state.error?.let { item { Notice(it, error = true) } }
                if (state.items == null && state.loading) {
                    item { Box(Modifier.fillMaxWidth().padding(32.dp)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) } }
                }
                if (state.items?.isEmpty() == true) {
                    item { Notice("No appointments. Online bookings appear here as soon as customers make them.") }
                }
                items(state.items.orEmpty(), key = { it.id }) { AgendaRow(it, onOpen) }
            }
        }
    }
}

@Composable
private fun AgendaRow(item: AgendaItemDto, onOpen: (String) -> Unit) {
    val cancelled = item.status == "CANCELLED"
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Card(onClick = { onOpen(item.id) }, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.width(72.dp)) {
                    Text(Formats.clock(item.startAt, item.timeZone), fontWeight = FontWeight.SemiBold)
                    Text(Formats.clock(item.endAt, item.timeZone), style = MaterialTheme.typography.bodySmall, color = muted)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        item.customerName,
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (cancelled) TextDecoration.LineThrough else null,
                        color = if (cancelled) muted else MaterialTheme.colorScheme.onSurface,
                    )
                    Text("${item.services.orEmpty()} with ${item.staffName}", style = MaterialTheme.typography.bodyMedium, color = muted)
                    if (item.status != "CONFIRMED") Text(StaffLabels.status(item.status), style = MaterialTheme.typography.labelLarge)
                    item.customerNote?.let { Text("Note: $it", style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 2) }
                }
            }
        }
    }
}
