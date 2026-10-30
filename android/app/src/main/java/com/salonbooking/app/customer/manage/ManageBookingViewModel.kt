package com.salonbooking.app.customer.manage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salonbooking.app.core.network.ApiResult
import com.salonbooking.app.core.network.BookingDto
import com.salonbooking.app.core.network.RescheduleOptionsDto
import com.salonbooking.app.customer.booking.BookingFlowViewModel
import com.salonbooking.app.customer.booking.BookingRepository
import com.salonbooking.app.customer.booking.Formats
import com.salonbooking.app.customer.booking.TimeState
import com.salonbooking.app.customer.mybookings.DeviceStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ManageMode { VIEW, CONFIRM_CANCEL, MOVE }

data class MoveState(
    val options: RescheduleOptionsDto? = null,
    /** Null means anyone free. */
    val staffId: String? = null,
    val time: TimeState = TimeState(),
    val date: String? = null,
    val start: String? = null,
    val moving: Boolean = false,
    val timeGone: Boolean = false,
)

data class ManageState(
    val booking: BookingDto? = null,
    /** Showing the copy saved on this phone because the service could not be reached. */
    val offline: Boolean = false,
    /** The link no longer works (expired, or replaced by a newer one). */
    val expired: Boolean = false,
    val loading: Boolean = true,
    val mode: ManageMode = ManageMode.VIEW,
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val move: MoveState = MoveState(),
)

/** One booking through its private token: view, move and cancel (APP-13, MGT-2..4). */
@HiltViewModel
class ManageBookingViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val bookings: BookingRepository,
    private val device: DeviceStore,
) : ViewModel() {

    /** Key matches [com.salonbooking.app.ManageRoute]. */
    private val token: String = checkNotNull(savedState["token"])

    private val _state = MutableStateFlow(ManageState(booking = device.data.value.bookings.find { it.token == token }?.booking))
    val state: StateFlow<ManageState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = bookings.booking(token)) {
                is ApiResult.Ok -> show(result.value)
                is ApiResult.Failed -> if (result.status == 404) {
                    device.forgetBooking(token)
                    _state.update { it.copy(loading = false, expired = true) }
                } else {
                    _state.update { it.copy(loading = false, error = result.message) }
                }
                ApiResult.Offline -> _state.update { it.copy(loading = false, offline = true) }
            }
        }
    }

    private fun show(booking: BookingDto) {
        device.saveBooking(token, booking)
        _state.update { it.copy(booking = booking, loading = false, offline = false, busy = false) }
    }

    fun setMode(mode: ManageMode) {
        _state.update { it.copy(mode = mode, error = null, notice = null) }
        if (mode == ManageMode.MOVE) startMove()
    }

    fun cancel() {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val result = bookings.cancel(token)) {
                is ApiResult.Ok -> {
                    show(result.value)
                    _state.update { it.copy(mode = ManageMode.VIEW, notice = "Your booking is cancelled. We've emailed you a confirmation.") }
                }
                is ApiResult.Failed -> _state.update { it.copy(busy = false, error = result.message) }
                ApiResult.Offline -> _state.update { it.copy(busy = false, error = BookingFlowViewModel.OFFLINE) }
            }
        }
    }

    // ---- Moving (MGT-4) -------------------------------------------------------------------------

    private fun startMove() {
        _state.update { it.copy(move = MoveState()) }
        viewModelScope.launch {
            when (val result = bookings.rescheduleOptions(token)) {
                is ApiResult.Ok -> {
                    // Stay with their stylist if they can still be booked online.
                    val keep = result.value.currentStaffId.takeIf { id -> result.value.stylists.any { it.id == id } }
                    _state.update { it.copy(move = it.move.copy(options = result.value, staffId = keep)) }
                    loadDays(today())
                }
                is ApiResult.Failed -> _state.update { it.copy(error = result.message) }
                ApiResult.Offline -> _state.update { it.copy(error = BookingFlowViewModel.OFFLINE) }
            }
        }
    }

    private fun today() = Formats.today(_state.value.booking?.branch?.timeZone ?: "Australia/Sydney")

    fun selectStylist(staffId: String?) {
        _state.update { it.copy(move = it.move.copy(staffId = staffId, date = null, start = null)) }
        loadDays(_state.value.move.time.from ?: today())
    }

    fun loadDays(from: String) {
        val to = LocalDate.parse(from).plusDays(BookingFlowViewModel.DAYS_SHOWN - 1L).toString()
        _state.update { it.copy(move = it.move.copy(time = it.move.time.copy(from = from, error = null))) }
        viewModelScope.launch {
            val move = _state.value.move
            when (val result = bookings.rescheduleDays(token, move.staffId, from, to)) {
                is ApiResult.Ok -> {
                    _state.update {
                        it.copy(move = it.move.copy(time = it.move.time.copy(days = result.value.days, lastBookableDate = result.value.lastBookableDate)))
                    }
                    result.value.days.firstOrNull { it.status == "AVAILABLE" }?.let { selectDate(it.date) }
                        ?: _state.update { it.copy(move = it.move.copy(time = it.move.time.copy(slots = null))) }
                }
                is ApiResult.Failed -> _state.update { it.copy(move = it.move.copy(time = it.move.time.copy(error = result.message))) }
                ApiResult.Offline -> _state.update { it.copy(move = it.move.copy(time = it.move.time.copy(error = BookingFlowViewModel.OFFLINE))) }
            }
        }
    }

    fun selectDate(date: String) {
        _state.update { it.copy(move = it.move.copy(date = date, start = null, time = it.move.time.copy(loadingSlots = true, slots = null))) }
        viewModelScope.launch {
            val result = bookings.rescheduleSlots(token, _state.value.move.staffId, date)
            _state.update {
                val time = it.move.time
                it.copy(
                    move = it.move.copy(
                        time = when (result) {
                            is ApiResult.Ok -> time.copy(slots = result.value.slots, loadingSlots = false)
                            is ApiResult.Failed -> time.copy(error = result.message, loadingSlots = false)
                            ApiResult.Offline -> time.copy(error = BookingFlowViewModel.OFFLINE, loadingSlots = false)
                        },
                    ),
                )
            }
        }
    }

    fun selectStart(start: String) = _state.update { it.copy(move = it.move.copy(start = start, timeGone = false)) }

    /** The original time stands if the new one has gone in the meantime. */
    fun confirmMove() {
        val move = _state.value.move
        val start = move.start ?: return
        _state.update { it.copy(move = it.move.copy(moving = true), error = null) }
        viewModelScope.launch {
            when (val result = bookings.reschedule(token, start, move.staffId)) {
                is ApiResult.Ok -> {
                    show(result.value)
                    val tz = result.value.branch.timeZone
                    _state.update {
                        it.copy(
                            mode = ManageMode.VIEW,
                            notice = "Moved to ${Formats.longDate(result.value.startAt, tz)} at ${Formats.clock(result.value.startAt, tz)}. We've emailed you the new details.",
                        )
                    }
                }
                is ApiResult.Failed -> if (result.code == "SLOT_UNAVAILABLE") {
                    _state.update { it.copy(move = it.move.copy(moving = false, start = null, timeGone = true)) }
                    move.date?.let(::selectDate)
                } else {
                    _state.update { it.copy(move = it.move.copy(moving = false), error = result.message) }
                }
                ApiResult.Offline -> _state.update { it.copy(move = it.move.copy(moving = false), error = BookingFlowViewModel.OFFLINE) }
            }
        }
    }
}
