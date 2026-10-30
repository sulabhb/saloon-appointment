package com.salonbooking.app.customer.mybookings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeState(
    val upcoming: List<SavedBooking> = emptyList(),
    val past: List<SavedBooking> = emptyList(),
    val salons: List<SavedSalon> = emptyList(),
)

/** Bookings and salons saved on this device (APP-11, APP-12). */
@HiltViewModel
class HomeViewModel @Inject constructor(device: DeviceStore) : ViewModel() {

    val state: StateFlow<HomeState> = device.data
        .map { data ->
            val (upcoming, past) = DeviceDataRules.upcomingAndPast(data.bookings, Instant.now())
            HomeState(upcoming, past, data.salons)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())
}
