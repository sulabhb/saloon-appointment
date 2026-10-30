package com.salonbooking.app.customer.salon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salonbooking.app.R
import com.salonbooking.app.core.design.SalonTheme
import com.salonbooking.app.core.network.BusinessDto
import com.salonbooking.app.core.network.LocationSummaryDto

@Composable
fun SalonScreen(onBack: () -> Unit, onBook: (branch: String) -> Unit, viewModel: SalonViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SalonContent(state = state, onBack = onBack, onRetry = viewModel::retry, onBook = onBook)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SalonContent(state: SalonUiState, onBack: () -> Unit, onRetry: () -> Unit, onBook: (branch: String) -> Unit) {
    val accent = (state as? SalonUiState.Loaded)?.salon?.accentColor
    SalonTheme(accentHex = accent) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text((state as? SalonUiState.Loaded)?.salon?.displayName.orEmpty()) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                        }
                    },
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (state) {
                    SalonUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    is SalonUiState.Loaded -> SalonDetails(state.salon, onBook)
                    is SalonUiState.NotFound -> Message(stringResource(R.string.salon_not_found, state.code))
                    SalonUiState.Unavailable -> Message(stringResource(R.string.salon_unavailable))
                    SalonUiState.NetworkError -> Message(stringResource(R.string.salon_network_error), onRetry)
                }
            }
        }
    }
}

@Composable
private fun SalonDetails(salon: BusinessDto, onBook: (branch: String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        salon.description?.let { description ->
            item { Text(description, style = MaterialTheme.typography.bodyLarge) }
        }
        item {
            Text(
                stringResource(R.string.salon_branches),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (salon.locations.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.salon_no_branches),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(salon.locations, key = { it.slug }) { BranchCard(it, onClick = { onBook(it.slug) }) }
    }
}

/** Selecting a branch starts the booking flow there (BKG-2). */
@Composable
private fun BranchCard(location: LocationSummaryDto, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(location.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${location.suburb}, ${location.state}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            location.phone?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun Message(text: String, onRetry: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        if (onRetry != null) {
            Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SalonLoadedPreview() {
    SalonContent(
        state = SalonUiState.Loaded(
            BusinessDto(
                slug = "glow-hair",
                displayName = "Glow Hair",
                description = "Colour specialists in Sydney.",
                accentColor = "#9C5B6B",
                locations = listOf(
                    LocationSummaryDto("sydney-cbd", "Sydney CBD", "Sydney", "NSW", "02 9000 0000", "Australia/Sydney"),
                    LocationSummaryDto("parramatta", "Parramatta", "Parramatta", "NSW", null, "Australia/Sydney"),
                ),
            )
        ),
        onBack = {},
        onRetry = {},
        onBook = {},
    )
}
