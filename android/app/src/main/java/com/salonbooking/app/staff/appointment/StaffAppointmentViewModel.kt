package com.salonbooking.app.staff.appointment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salonbooking.app.core.network.ApiResult
import com.salonbooking.app.core.network.AppointmentDto
import com.salonbooking.app.customer.booking.BookingFlowViewModel
import com.salonbooking.app.staff.StaffLabels
import com.salonbooking.app.staff.StaffRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StaffAppointmentState(
    val appointment: AppointmentDto? = null,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val confirmingCancel: Boolean = false,
    val cancelReason: String = "",
    val note: String = "",
) {
    val noteChanged get() = note.trim() != appointment?.internalNote.orEmpty()
}

/** One appointment for staff (APP-22): status changes allowed by the server, and the staff note. */
@HiltViewModel
class StaffAppointmentViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val staff: StaffRepository,
) : ViewModel() {

    /** Key matches [com.salonbooking.app.StaffAppointmentRoute]. */
    private val id: String = checkNotNull(savedState["id"])

    private val _state = MutableStateFlow(StaffAppointmentState())
    val state: StateFlow<StaffAppointmentState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch { apply(staff.appointment(id)) }
    }

    /** Cancelling asks first, with an optional reason; other changes happen at once. */
    fun change(status: String) {
        if (status == "CANCELLED" && !_state.value.confirmingCancel) {
            _state.update { it.copy(confirmingCancel = true, notice = null) }
            return
        }
        val reason = _state.value.cancelReason.trim().ifEmpty { null }
        _state.update { it.copy(busy = true, error = null, notice = null) }
        viewModelScope.launch {
            apply(staff.changeStatus(id, status, if (status == "CANCELLED") reason else null), notice = StaffLabels.status(status))
        }
    }

    fun keepAppointment() = _state.update { it.copy(confirmingCancel = false, cancelReason = "") }

    fun editReason(reason: String) = _state.update { it.copy(cancelReason = reason.take(500)) }

    fun editNote(note: String) = _state.update { it.copy(note = note.take(2000)) }

    fun saveNote() {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch { apply(staff.saveNote(id, _state.value.note.trim()), notice = "Note saved") }
    }

    private fun apply(result: ApiResult<AppointmentDto>, notice: String? = null) {
        _state.update {
            when (result) {
                is ApiResult.Ok -> it.copy(
                    appointment = result.value,
                    note = result.value.internalNote.orEmpty(),
                    loading = false,
                    busy = false,
                    confirmingCancel = false,
                    cancelReason = "",
                    notice = notice,
                )
                is ApiResult.Failed -> it.copy(loading = false, busy = false, error = result.message)
                ApiResult.Offline -> it.copy(loading = false, busy = false, error = BookingFlowViewModel.OFFLINE)
            }
        }
    }
}
