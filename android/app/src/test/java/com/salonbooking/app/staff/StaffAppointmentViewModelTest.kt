package com.salonbooking.app.staff

import androidx.lifecycle.SavedStateHandle
import com.salonbooking.app.core.network.AgendaItemDto
import com.salonbooking.app.core.network.ApiResult
import com.salonbooking.app.core.network.AppointmentDto
import com.salonbooking.app.core.network.CustomerRefDto
import com.salonbooking.app.staff.appointment.StaffAppointmentViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StaffAppointmentViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private fun appointment(status: String, allowed: List<String>, note: String? = null) = AppointmentDto(
        id = "a1", reference = "REF12345", status = status, startAt = "2026-11-03T00:00:00Z", endAt = "2026-11-03T01:00:00Z",
        locationId = "l1", locationName = "Fortitude Valley", timeZone = "Australia/Brisbane", staffId = "s1", staffName = "Sam",
        customer = CustomerRefDto("c1", "Riley Test"), source = "web", allowedTransitions = allowed, canEditNote = true, internalNote = note,
    )

    private inner class FakeStaff : StaffRepository {
        var current = appointment("CONFIRMED", listOf("CHECKED_IN", "CANCELLED"))
        val changes = mutableListOf<Pair<String, String?>>()
        override suspend fun agenda(date: String): ApiResult<List<AgendaItemDto>> = ApiResult.Ok(emptyList())
        override suspend fun appointment(id: String) = ApiResult.Ok(current)
        override suspend fun changeStatus(id: String, status: String, reason: String?): ApiResult<AppointmentDto> {
            changes += status to reason
            current = appointment(status, if (status == "CHECKED_IN") listOf("COMPLETED") else emptyList())
            return ApiResult.Ok(current)
        }
        override suspend fun saveNote(id: String, note: String): ApiResult<AppointmentDto> {
            current = current.copy(internalNote = note)
            return ApiResult.Ok(current)
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `checking in happens at once and offers the next step`() = runTest(dispatcher) {
        val staff = FakeStaff()
        val vm = StaffAppointmentViewModel(SavedStateHandle(mapOf("id" to "a1")), staff)
        advanceUntilIdle()
        vm.change("CHECKED_IN")
        advanceUntilIdle()
        assertEquals(listOf("CHECKED_IN" to null), staff.changes)
        assertEquals(listOf("COMPLETED"), vm.state.value.appointment?.allowedTransitions)
        assertEquals("Checked in", vm.state.value.notice)
    }

    @Test
    fun `cancelling asks first and sends the reason`() = runTest(dispatcher) {
        val staff = FakeStaff()
        val vm = StaffAppointmentViewModel(SavedStateHandle(mapOf("id" to "a1")), staff)
        advanceUntilIdle()
        vm.change("CANCELLED")
        assertTrue(vm.state.value.confirmingCancel)
        assertTrue(staff.changes.isEmpty())
        vm.editReason("  Called to cancel ")
        vm.change("CANCELLED")
        advanceUntilIdle()
        assertEquals(listOf("CANCELLED" to "Called to cancel"), staff.changes)
        assertEquals(false, vm.state.value.confirmingCancel)
    }

    @Test
    fun `the staff note is saved only when changed`() = runTest(dispatcher) {
        val vm = StaffAppointmentViewModel(SavedStateHandle(mapOf("id" to "a1")), FakeStaff())
        advanceUntilIdle()
        assertEquals(false, vm.state.value.noteChanged)
        vm.editNote("Prefers a quiet chair")
        assertTrue(vm.state.value.noteChanged)
        vm.saveNote()
        advanceUntilIdle()
        assertEquals("Prefers a quiet chair", vm.state.value.appointment?.internalNote)
        assertEquals(false, vm.state.value.noteChanged)
    }
}
