package com.salonbooking.app.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Staff sign-in and the staff API (`/api/v1/auth`, `/api/v1/staff`). The session token is added by
 * [StaffAuthInterceptor]; Android sessions use a bearer token, not a cookie.
 */
interface StaffApi {

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginDto): LoginResponseDto

    @GET("api/v1/auth/me")
    suspend fun me(): MeDto

    @POST("api/v1/auth/business")
    suspend fun selectBusiness(@Body request: SelectBusinessDto): MeDto

    @POST("api/v1/auth/logout")
    suspend fun logout()

    @GET("api/v1/staff/agenda")
    suspend fun agenda(@Query("date") date: String, @Query("locationId") locationId: String? = null): List<AgendaItemDto>

    @GET("api/v1/staff/appointments/{id}")
    suspend fun appointment(@Path("id") id: String): AppointmentDto

    @POST("api/v1/staff/appointments/{id}/status")
    suspend fun changeStatus(@Path("id") id: String, @Body request: StatusChangeDto): AppointmentDto

    @PUT("api/v1/staff/appointments/{id}/note")
    suspend fun saveNote(@Path("id") id: String, @Body request: NoteDto): AppointmentDto
}

/** No default for [client]: kotlinx.serialization leaves out properties that still have their default value. */
@Serializable
data class LoginDto(val email: String, val password: String, val client: String, val deviceName: String)

@Serializable
data class LoginResponseDto(val token: String, val me: MeDto)

@Serializable
data class UserDto(val id: String, val email: String, val fullName: String, val platformAdmin: Boolean = false)

/** role: OWNER, MANAGER, RECEPTION or STYLIST. */
@Serializable
data class MembershipDto(val businessId: String, val slug: String, val displayName: String, val role: String)

@Serializable
data class SelectedBusinessDto(val businessId: String, val role: String, val locationIds: List<String>? = null, val staffId: String? = null)

@Serializable
data class MeDto(val user: UserDto, val businesses: List<MembershipDto> = emptyList(), val selected: SelectedBusinessDto? = null)

@Serializable
data class SelectBusinessDto(val businessId: String)

@Serializable
data class AgendaItemDto(
    val id: String,
    val reference: String,
    val status: String,
    val startAt: String,
    val endAt: String,
    val locationId: String,
    val locationName: String,
    val timeZone: String,
    val staffId: String,
    val staffName: String,
    val customerName: String,
    val customerPhone: String? = null,
    val customerEmail: String? = null,
    val services: String? = null,
    val customerNote: String? = null,
    val source: String,
)

@Serializable
data class CustomerRefDto(val id: String, val fullName: String, val email: String? = null, val phone: String? = null)

@Serializable
data class ServiceLineDto(val serviceId: String, val name: String, val durationMin: Int, val priceCents: Long, val priceIsFrom: Boolean = false)

@Serializable
data class EmailRecordDto(val type: String, val status: String, val recipient: String, val attempts: Int = 0, val lastError: String? = null, val sentAt: String? = null)

@Serializable
data class AppointmentDto(
    val id: String,
    val reference: String,
    val status: String,
    val startAt: String,
    val endAt: String,
    val locationId: String,
    val locationName: String,
    val timeZone: String,
    val staffId: String,
    val staffName: String,
    val customer: CustomerRefDto,
    val services: List<ServiceLineDto> = emptyList(),
    val totalPriceCents: Long = 0,
    val customerNote: String? = null,
    val internalNote: String? = null,
    val source: String,
    val createdByName: String? = null,
    val cancelledBy: String? = null,
    val cancelReason: String? = null,
    val lateCancel: Boolean = false,
    val allowedTransitions: List<String> = emptyList(),
    val canEditNote: Boolean = false,
    val emails: List<EmailRecordDto> = emptyList(),
)

@Serializable
data class StatusChangeDto(val status: String, val reason: String? = null)

@Serializable
data class NoteDto(val internalNote: String)
