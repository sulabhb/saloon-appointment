package com.salonbooking.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salonbooking.app.core.design.SalonTheme
import com.salonbooking.app.core.link.AppLink
import com.salonbooking.app.core.update.AppUpdateViewModel
import com.salonbooking.app.core.update.UpdateRequiredScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val link = mutableStateOf<AppLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A restored activity already shows the screen the link led to.
        if (savedInstanceState == null) handleLink(intent)
        setContent {
            SalonTheme {
                val update: AppUpdateViewModel = hiltViewModel()
                val storeUrl by update.updateRequired.collectAsStateWithLifecycle()
                storeUrl?.let { UpdateRequiredScreen(it) }
                    ?: AppNavHost(link = link.value, onLinkHandled = { link.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    private fun handleLink(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        link.value = intent.dataString?.let { AppLink.parse(it, BuildConfig.BOOKING_HOST) }
    }
}
