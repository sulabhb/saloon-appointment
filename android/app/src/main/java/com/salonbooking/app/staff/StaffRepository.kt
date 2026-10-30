package com.salonbooking.app.staff

import com.salonbooking.app.core.network.AgendaItemDto
import com.salonbooking.app.core.network.ApiResult
import com.salonbooking.app.core.network.AppointmentDto
import com.salonbooking.app.core.network.NoteDto
import com.salonbooking.app.core.network.StaffApi
import com.salonbooking.app.core.network.StatusChangeDto
import com.salonbooking.app.core.network.apiCall
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/** Appointments from the staff side (APP-21, APP-22). Permissions are enforced by the server. */
interface StaffRepository {
    suspend fun agenda(date: String): ApiResult<List<AgendaItemDto>>
    suspend fun appointment(id: String): ApiResult<AppointmentDto>
    suspend fun changeStatus(id: String, status: String, reason: String?): ApiResult<AppointmentDto>
    suspend fun saveNote(id: String, note: String): ApiResult<AppointmentDto>
}

@Singleton
class RemoteStaffRepository @Inject constructor(private val api: StaffApi, private val json: Json) : StaffRepository {
    override suspend fun agenda(date: String) = apiCall(json) { api.agenda(date) }
    override suspend fun appointment(id: String) = apiCall(json) { api.appointment(id) }
    override suspend fun changeStatus(id: String, status: String, reason: String?) =
        apiCall(json) { api.changeStatus(id, StatusChangeDto(status, reason)) }
    override suspend fun saveNote(id: String, note: String) = apiCall(json) { api.saveNote(id, NoteDto(note)) }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class StaffRepositoryModule {
    @Binds
    abstract fun staffRepository(remote: RemoteStaffRepository): StaffRepository
}

/** Labels shared by the agenda and the appointment screen; status is never shown by colour alone (DSH-7). */
object StaffLabels {
    fun status(status: String) = when (status) {
        "CONFIRMED" -> "Confirmed"
        "CHECKED_IN" -> "Checked in"
        "COMPLETED" -> "Completed"
        "CANCELLED" -> "Cancelled"
        "NO_SHOW" -> "No-show"
        else -> status
    }

    /** The button for moving from [from] to [to], as on the web console. */
    fun action(from: String, to: String) = when (to) {
        "CHECKED_IN" -> if (from == "COMPLETED") "Undo complete" else "Check in"
        "CONFIRMED" -> "Undo no-show"
        "COMPLETED" -> "Complete"
        "NO_SHOW" -> "No-show"
        else -> "Cancel appointment"
    }

    fun role(role: String) = when (role) {
        "OWNER" -> "Owner"
        "MANAGER" -> "Manager"
        "RECEPTION" -> "Reception"
        "STYLIST" -> "Stylist"
        else -> role
    }
}
