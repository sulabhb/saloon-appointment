package com.salonbooking.app.core.update

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salonbooking.app.core.network.PublicApi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Checks the version rules once per launch; a failed check never blocks the app. */
@HiltViewModel
class AppUpdateViewModel @Inject constructor(private val update: AppUpdate, private val api: PublicApi) : ViewModel() {

    val updateRequired: StateFlow<String?> = update.updateRequired

    init {
        viewModelScope.launch {
            try {
                update.apply(api.appConfig())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Offline or an older server: carry on, the 426 check still applies.
            }
        }
    }
}

/** Shown instead of the app when this build is no longer supported. */
@Composable
fun UpdateRequiredScreen(storeUrl: String) {
    val context = LocalContext.current
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text("Time for an update", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "This version of the app is no longer supported. Update it to keep booking and managing appointments. Your saved bookings stay on this phone.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, storeUrl.toUri())) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("Update the app") }
        }
    }
}
