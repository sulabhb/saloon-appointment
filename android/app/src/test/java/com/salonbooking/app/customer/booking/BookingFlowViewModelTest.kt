package com.salonbooking.app.customer.booking

import androidx.lifecycle.SavedStateHandle
import com.salonbooking.app.core.network.ApiResult
import com.salonbooking.app.core.network.BookingDto
import com.salonbooking.app.core.network.BusinessDto
import com.salonbooking.app.core.network.CatalogDto
import com.salonbooking.app.core.network.CategoryDto
import com.salonbooking.app.core.network.CreatedBookingDto
import com.salonbooking.app.core.network.DayDto
import com.salonbooking.app.core.network.DaysDto
import com.salonbooking.app.core.network.LocationSummaryDto
import com.salonbooking.app.core.network.NewBookingDto
import com.salonbooking.app.core.network.RescheduleOptionsDto
import com.salonbooking.app.core.network.ServiceDto
import com.salonbooking.app.core.network.SlotDto
import com.salonbooking.app.core.network.SlotsDto
import com.salonbooking.app.core.network.StylistDto
import com.salonbooking.app.customer.mybookings.ContactDetails
import com.salonbooking.app.customer.mybookings.DeviceData
import com.salonbooking.app.customer.salon.SalonRepository
import com.salonbooking.app.customer.salon.SalonResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookingFlowViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true }
    private val start = "2026-11-03T00:00:00Z"

    private val salon = BusinessDto(
        slug = "luna-hair-co",
        displayName = "Luna Hair Co",
        locations = listOf(LocationSummaryDto("fortitude-valley", "Fortitude Valley", "Fortitude Valley", "QLD", null, "Australia/Brisbane")),
    )
    private val catalog = CatalogDto(
        categories = listOf(CategoryDto("Cuts", listOf(ServiceDto("cut", "Cut", durationMin = 60, priceCents = 8500), ServiceDto("colour", "Colour", durationMin = 120, priceCents = 16000)))),
        stylists = listOf(StylistDto("jo", "Jo", serviceIds = listOf("cut", "colour")), StylistDto("sam", "Sam", serviceIds = listOf("cut"))),
    )

    private class FakeSalons(private val salon: BusinessDto) : SalonRepository {
        override suspend fun loadSalon(code: String) = SalonResult.Found(salon)
    }

    private inner class FakeBookings : BookingRepository {
        var nextBook: ApiResult<CreatedBookingDto> = ApiResult.Ok(CreatedBookingDto(booking("REF12345", start), "secret-token"))
        val booked = mutableListOf<NewBookingDto>()
        override suspend fun catalog(salon: String, branch: String) = ApiResult.Ok(catalog)
        override suspend fun days(salon: String, branch: String, serviceIds: List<String>, staffId: String?, from: String, to: String) =
            ApiResult.Ok(DaysDto(listOf(DayDto("2026-11-02", "CLOSED"), DayDto("2026-11-03", "AVAILABLE")), "2026-12-31"))
        override suspend fun slots(salon: String, branch: String, serviceIds: List<String>, staffId: String?, date: String) =
            ApiResult.Ok(SlotsDto(date, "Australia/Brisbane", 60, listOf(SlotDto(start, "10:00:00"))))
        override suspend fun book(salon: String, booking: NewBookingDto): ApiResult<CreatedBookingDto> {
            booked += booking
            return nextBook
        }
        override suspend fun booking(token: String): ApiResult<BookingDto> = error("unused")
        override suspend fun cancel(token: String): ApiResult<BookingDto> = error("unused")
        override suspend fun rescheduleOptions(token: String): ApiResult<RescheduleOptionsDto> = error("unused")
        override suspend fun rescheduleDays(token: String, staffId: String?, from: String, to: String): ApiResult<DaysDto> = error("unused")
        override suspend fun rescheduleSlots(token: String, staffId: String?, date: String): ApiResult<SlotsDto> = error("unused")
        override suspend fun reschedule(token: String, start: String, staffId: String?): ApiResult<BookingDto> = error("unused")
    }

    private val bookings = FakeBookings()
    private val device = InMemoryDeviceStore(DeviceData(lastDetails = ContactDetails("Riley Test", "riley@example.test", "0412 345 678")))
    private val savedState = SavedStateHandle(mapOf("code" to "luna-hair-co", "branch" to "fortitude-valley"))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = BookingFlowViewModel(savedState, FakeSalons(salon), bookings, device, json)

    @Test
    fun `only stylists who do every chosen service are offered`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.toggleService("cut")
        assertEquals(listOf("Jo", "Sam"), vm.state.value.qualifiedStylists.map { it.displayName })
        vm.toggleService("colour")
        assertEquals(listOf("Jo"), vm.state.value.qualifiedStylists.map { it.displayName })
    }

    @Test
    fun `the time step opens on the first free day, and details are pre-filled from last time`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.toggleService("cut")
        vm.goTo(Step.TIME)
        advanceUntilIdle()
        assertEquals("2026-11-03", vm.state.value.draft.date)
        assertEquals(1, vm.state.value.time.slots?.size)
        assertEquals("Riley Test", vm.state.value.draft.fullName)
    }

    @Test
    fun `details must be complete and the policy agreed before review`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.goTo(Step.DETAILS)
        vm.continueFromDetails()
        assertEquals(Step.DETAILS, vm.state.value.draft.step)
        assertEquals(setOf("policy"), vm.state.value.detailErrors.keys)
        vm.editDetails(acceptsPolicy = true)
        vm.continueFromDetails()
        assertEquals(Step.REVIEW, vm.state.value.draft.step)
        assertNotNull(vm.state.value.draft.idempotencyKey)
    }

    @Test
    fun `confirming saves the booking on this phone with its private link`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.toggleService("cut")
        vm.selectStart(start)
        vm.editDetails(acceptsPolicy = true)
        vm.goTo(Step.REVIEW)
        vm.confirm()
        advanceUntilIdle()
        assertNotNull(vm.state.value.created)
        assertEquals("secret-token", device.data.value.bookings.single().token)
        assertEquals(vm.state.value.draft.idempotencyKey ?: bookings.booked.single().idempotencyKey, bookings.booked.single().idempotencyKey)
    }

    @Test
    fun `a time taken in the meantime goes back to times and keeps everything else`() = runTest(dispatcher) {
        bookings.nextBook = ApiResult.Failed(409, "SLOT_UNAVAILABLE", "Sorry, that time was just taken.")
        val vm = viewModel()
        advanceUntilIdle()
        vm.toggleService("cut")
        vm.selectStylist("sam")
        vm.selectStart(start)
        vm.editDetails(acceptsPolicy = true)
        vm.goTo(Step.REVIEW)
        vm.confirm()
        advanceUntilIdle()
        val state = vm.state.value
        assertEquals(Step.TIME, state.draft.step)
        assertTrue(state.timeGone)
        assertNull(state.draft.start)
        assertEquals("sam", state.draft.staffId)
        assertEquals(listOf("cut"), state.draft.serviceIds)
    }

    @Test
    fun `choices survive the screen being recreated`() = runTest(dispatcher) {
        val first = viewModel()
        advanceUntilIdle()
        first.toggleService("colour")
        first.goTo(Step.STYLIST)
        val second = viewModel()
        assertEquals(Step.STYLIST, second.state.value.draft.step)
        assertEquals(listOf("colour"), second.state.value.draft.serviceIds)
    }
}
