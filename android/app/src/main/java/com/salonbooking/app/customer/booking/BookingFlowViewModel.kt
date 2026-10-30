package com.salonbooking.app.customer.booking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salonbooking.app.core.network.ApiResult
import com.salonbooking.app.core.network.BusinessDto
import com.salonbooking.app.core.network.CatalogDto
import com.salonbooking.app.core.network.CreatedBookingDto
import com.salonbooking.app.core.network.CustomerDto
import com.salonbooking.app.core.network.DayDto
import com.salonbooking.app.core.network.LocationSummaryDto
import com.salonbooking.app.core.network.NewBookingDto
import com.salonbooking.app.core.network.ServiceDto
import com.salonbooking.app.core.network.SlotDto
import com.salonbooking.app.customer.mybookings.ContactDetails
import com.salonbooking.app.customer.mybookings.DeviceStore
import com.salonbooking.app.customer.mybookings.SavedSalon
import com.salonbooking.app.customer.salon.SalonRepository
import com.salonbooking.app.customer.salon.SalonResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class Step { SERVICES, STYLIST, TIME, DETAILS, REVIEW }

/** Everything the customer has chosen; kept in saved state so rotation and process death keep it (BKG-17). */
@Serializable
data class Draft(
    val step: Step = Step.SERVICES,
    val serviceIds: List<String> = emptyList(),
    val staffId: String? = null,
    val date: String? = null,
    val start: String? = null,
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val note: String = "",
    val acceptsPolicy: Boolean = false,
    /** Made when the review opens, so a double tap or retry books once (BKG-10). */
    val idempotencyKey: String? = null,
)

/** Days shown at once in the date strip, and which window is shown. */
data class TimeState(
    val from: String? = null,
    val days: List<DayDto> = emptyList(),
    val lastBookableDate: String? = null,
    val slots: List<SlotDto>? = null,
    val loadingSlots: Boolean = false,
    val error: String? = null,
)

data class BookingFlowState(
    val salon: BusinessDto? = null,
    val branch: LocationSummaryDto? = null,
    val catalog: CatalogDto? = null,
    val loadError: String? = null,
    val draft: Draft = Draft(),
    val time: TimeState = TimeState(),
    val showErrors: Boolean = false,
    val submitting: Boolean = false,
    val submitError: String? = null,
    val timeGone: Boolean = false,
    val created: CreatedBookingDto? = null,
) {
    val services: List<ServiceDto>
        get() = catalog?.categories.orEmpty().flatMap { it.services }.let { all -> draft.serviceIds.mapNotNull { id -> all.find { it.id == id } } }

    /** Stylists bookable online who do every chosen service (AVL-3). */
    val qualifiedStylists get() = catalog?.stylists.orEmpty().filter { s -> draft.serviceIds.all { it in s.serviceIds } }

    val detailErrors: Map<String, String>
        get() = buildMap {
            if (draft.fullName.isBlank()) put("fullName", "Enter your name.")
            if (!EMAIL.matches(draft.email.trim())) put("email", "Enter an email address, like name@example.com.")
            if (!PHONE.matches(draft.phone.trim())) put("phone", "Enter a mobile number, like 0412 345 678.")
            if (!draft.acceptsPolicy) put("policy", "Agree to the cancellation policy to continue.")
        }

    private companion object {
        val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
        val PHONE = Regex("^\\+?[0-9][0-9\\s().-]{7,}$")
    }
}

@HiltViewModel
class BookingFlowViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val salons: SalonRepository,
    private val bookings: BookingRepository,
    private val device: DeviceStore,
    private val json: Json,
) : ViewModel() {

    /** Keys match [com.salonbooking.app.BookRoute]. */
    private val salonCode: String = checkNotNull(savedState["code"])
    private val branchSlug: String = checkNotNull(savedState["branch"])

    private val _state = MutableStateFlow(BookingFlowState(draft = restoredDraft()))
    val state: StateFlow<BookingFlowState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun restoredDraft(): Draft {
        savedState.get<String>(DRAFT)?.let { saved -> runCatching { return json.decodeFromString(Draft.serializer(), saved) } }
        val last = device.data.value.lastDetails ?: ContactDetails()
        return Draft(fullName = last.fullName, email = last.email, phone = last.phone)
    }

    private fun load() {
        _state.update { it.copy(loadError = null) }
        viewModelScope.launch {
            val salon = when (val result = salons.loadSalon(salonCode)) {
                is SalonResult.Found -> result.salon
                SalonResult.NotFound -> return@launch fail("We couldn't find this salon.")
                SalonResult.Unavailable -> return@launch fail("Online booking for this salon is unavailable right now. Please call them.")
                SalonResult.NetworkError -> return@launch fail(OFFLINE)
            }
            val branch = salon.locations.find { it.slug == branchSlug } ?: return@launch fail("This branch is not taking online bookings.")
            device.saveSalon(SavedSalon(salon.slug, salon.displayName, salon.accentColor))
            when (val catalog = bookings.catalog(salonCode, branchSlug)) {
                is ApiResult.Ok -> _state.update { it.copy(salon = salon, branch = branch, catalog = catalog.value) }
                is ApiResult.Failed -> fail(catalog.message)
                ApiResult.Offline -> fail(OFFLINE)
            }
            if (_state.value.draft.step == Step.TIME) loadDays(_state.value.time.from ?: Formats.today(branch.timeZone))
        }
    }

    private fun fail(message: String) = _state.update { it.copy(loadError = message) }

    private fun editDraft(change: (Draft) -> Draft) {
        _state.update { it.copy(draft = change(it.draft)) }
        savedState[DRAFT] = json.encodeToString(Draft.serializer(), _state.value.draft)
    }

    // ---- Steps ----------------------------------------------------------------------------------

    fun toggleService(id: String) = editDraft { d ->
        d.copy(serviceIds = if (id in d.serviceIds) d.serviceIds - id else d.serviceIds + id, staffId = null, date = null, start = null)
    }

    fun selectStylist(id: String?) = editDraft { it.copy(staffId = id, date = null, start = null) }

    fun goTo(step: Step) {
        editDraft { it.copy(step = step) }
        when (step) {
            Step.TIME -> {
                val branch = _state.value.branch ?: return
                loadDays(_state.value.draft.date?.takeIf { it >= Formats.today(branch.timeZone) } ?: Formats.today(branch.timeZone))
            }
            Step.REVIEW -> editDraft { it.copy(idempotencyKey = it.idempotencyKey ?: UUID.randomUUID().toString()) }
            else -> Unit
        }
    }

    /** Back within the flow; returns false at the first step, so the screen closes. */
    fun back(): Boolean {
        val index = _state.value.draft.step.ordinal
        if (index == 0) return false
        goTo(Step.entries[index - 1])
        return true
    }

    fun continueFromDetails() {
        _state.update { it.copy(showErrors = true) }
        if (_state.value.detailErrors.isEmpty()) goTo(Step.REVIEW)
    }

    fun editDetails(fullName: String? = null, email: String? = null, phone: String? = null, note: String? = null, acceptsPolicy: Boolean? = null) =
        editDraft {
            it.copy(
                fullName = fullName ?: it.fullName,
                email = email ?: it.email,
                phone = phone ?: it.phone,
                note = note?.take(500) ?: it.note,
                acceptsPolicy = acceptsPolicy ?: it.acceptsPolicy,
            )
        }

    // ---- Date and time --------------------------------------------------------------------------

    fun loadDays(from: String) {
        val branch = _state.value.branch ?: return
        val to = LocalDate.parse(from).plusDays(DAYS_SHOWN - 1L).toString()
        _state.update { it.copy(time = it.time.copy(from = from, error = null)) }
        viewModelScope.launch {
            val draft = _state.value.draft
            when (val result = bookings.days(salonCode, branch.slug, draft.serviceIds, draft.staffId, from, to)) {
                is ApiResult.Ok -> {
                    _state.update { it.copy(time = it.time.copy(days = result.value.days, lastBookableDate = result.value.lastBookableDate)) }
                    val chosen = draft.date?.takeIf { d -> result.value.days.any { it.date == d && it.status == "AVAILABLE" } }
                        ?: result.value.days.firstOrNull { it.status == "AVAILABLE" }?.date
                    if (chosen != null) selectDate(chosen) else _state.update { it.copy(time = it.time.copy(slots = null)) }
                }
                is ApiResult.Failed -> _state.update { it.copy(time = it.time.copy(error = result.message)) }
                ApiResult.Offline -> _state.update { it.copy(time = it.time.copy(error = OFFLINE)) }
            }
        }
    }

    fun selectDate(date: String) {
        val branch = _state.value.branch ?: return
        if (_state.value.draft.date != date) editDraft { it.copy(date = date, start = null) }
        _state.update { it.copy(time = it.time.copy(loadingSlots = true, slots = null)) }
        viewModelScope.launch {
            val draft = _state.value.draft
            val result = bookings.slots(salonCode, branch.slug, draft.serviceIds, draft.staffId, date)
            _state.update {
                it.copy(
                    time = when (result) {
                        is ApiResult.Ok -> it.time.copy(slots = result.value.slots, loadingSlots = false)
                        is ApiResult.Failed -> it.time.copy(error = result.message, loadingSlots = false)
                        ApiResult.Offline -> it.time.copy(error = OFFLINE, loadingSlots = false)
                    },
                )
            }
        }
    }

    fun selectStart(start: String) {
        _state.update { it.copy(timeGone = false) }
        editDraft { it.copy(start = start) }
    }

    // ---- Confirm --------------------------------------------------------------------------------

    fun confirm() {
        val s = _state.value
        val branch = s.branch ?: return
        val start = s.draft.start ?: return
        if (s.submitting) return
        _state.update { it.copy(submitting = true, submitError = null) }
        viewModelScope.launch {
            val d = s.draft
            val booking = NewBookingDto(
                locationSlug = branch.slug,
                serviceIds = d.serviceIds,
                staffId = d.staffId,
                start = start,
                customer = CustomerDto(d.fullName.trim(), d.email.trim(), d.phone.trim()),
                note = d.note.trim().ifEmpty { null },
                acceptsPolicy = d.acceptsPolicy,
                idempotencyKey = d.idempotencyKey ?: UUID.randomUUID().toString(),
            )
            when (val result = bookings.book(salonCode, booking)) {
                is ApiResult.Ok -> {
                    device.saveBooking(result.value.manageToken, result.value.booking)
                    device.saveDetails(ContactDetails(d.fullName.trim(), d.email.trim(), d.phone.trim()))
                    savedState.remove<String>(DRAFT)
                    _state.update { it.copy(submitting = false, created = result.value) }
                }
                is ApiResult.Failed -> if (result.code == "SLOT_UNAVAILABLE") {
                    // The time went to someone else: back to times, keeping everything else (BKG-9).
                    _state.update { it.copy(submitting = false, timeGone = true) }
                    editDraft { it.copy(start = null, idempotencyKey = null) }
                    goTo(Step.TIME)
                } else {
                    _state.update { it.copy(submitting = false, submitError = result.message) }
                }
                ApiResult.Offline -> _state.update { it.copy(submitting = false, submitError = OFFLINE) }
            }
        }
    }

    companion object {
        const val DAYS_SHOWN = 14
        private const val DRAFT = "draft"
        const val OFFLINE = "We couldn't reach the booking service. Check your connection and try again."
    }
}
