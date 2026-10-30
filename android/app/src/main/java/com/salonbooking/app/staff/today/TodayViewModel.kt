package com.salonbooking.app.staff.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salonbooking.app.core.network.AgendaItemDto
import com.salonbooking.app.core.network.ApiResult
import com.salonbooking.app.customer.booking.BookingFlowViewModel
import com.salonbooking.app.staff.StaffRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TodayState(
    val date: String,
    val items: List<AgendaItemDto>? = null,
    val loading: Boolean = true,
    val error: String? = null,
) {
    val active get() = items.orEmpty().filter { it.status != "CANCELLED" }
}

/**
 * The day's appointments as an agenda list (APP-21): stylists see their own, everyone else their
 * branches'. The server decides which; the date is read in each branch's own time zone.
 */
@HiltViewModel
class TodayViewModel @Inject constructor(private val staff: StaffRepository) : ViewModel() {

    private val _state = MutableStateFlow(TodayState(date = LocalDate.now().toString()))
    val state: StateFlow<TodayState> = _state.asStateFlow()

    fun refresh() {
        val date = _state.value.date
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = staff.agenda(date)
            _state.update {
                if (it.date != date) return@update it
                when (result) {
                    is ApiResult.Ok -> it.copy(items = result.value, loading = false)
                    is ApiResult.Failed -> it.copy(loading = false, error = result.message)
                    ApiResult.Offline -> it.copy(loading = false, error = BookingFlowViewModel.OFFLINE)
                }
            }
        }
    }

    fun shiftDay(days: Long) {
        _state.update { TodayState(date = LocalDate.parse(it.date).plusDays(days).toString()) }
        refresh()
    }

    fun goToToday() {
        _state.update { TodayState(date = LocalDate.now().toString()) }
        refresh()
    }
}
