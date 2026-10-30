package com.salonbooking.app.core.network

import com.salonbooking.app.core.update.AppConfigDto
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Public booking API (`/api/v1/public`). Hand-written for now; to be replaced by the client
 * generated from api/openapi.yaml once the backend publishes it.
 */
interface PublicApi {

    @GET("api/v1/public/app-config")
    suspend fun appConfig(): AppConfigDto

    @GET("api/v1/public/b/{businessSlug}")
    suspend fun business(@Path("businessSlug") businessSlug: String): BusinessDto

    @GET("api/v1/public/b/{businessSlug}/locations/{branch}/catalog")
    suspend fun catalog(@Path("businessSlug") businessSlug: String, @Path("branch") branch: String): CatalogDto

    @GET("api/v1/public/b/{businessSlug}/locations/{branch}/availability/days")
    suspend fun days(
        @Path("businessSlug") businessSlug: String,
        @Path("branch") branch: String,
        @Query("services") services: String,
        @Query("staff") staff: String?,
        @Query("from") from: String,
        @Query("to") to: String,
    ): DaysDto

    @GET("api/v1/public/b/{businessSlug}/locations/{branch}/availability")
    suspend fun slots(
        @Path("businessSlug") businessSlug: String,
        @Path("branch") branch: String,
        @Query("services") services: String,
        @Query("staff") staff: String?,
        @Query("date") date: String,
    ): SlotsDto

    @POST("api/v1/public/b/{businessSlug}/bookings")
    suspend fun book(@Path("businessSlug") businessSlug: String, @Body booking: NewBookingDto): CreatedBookingDto

    @GET("api/v1/public/manage/{token}")
    suspend fun booking(@Path("token") token: String): BookingDto

    @POST("api/v1/public/manage/{token}/cancel")
    suspend fun cancel(@Path("token") token: String): BookingDto

    @GET("api/v1/public/manage/{token}/options")
    suspend fun rescheduleOptions(@Path("token") token: String): RescheduleOptionsDto

    @GET("api/v1/public/manage/{token}/availability/days")
    suspend fun rescheduleDays(
        @Path("token") token: String,
        @Query("staff") staff: String?,
        @Query("from") from: String,
        @Query("to") to: String,
    ): DaysDto

    @GET("api/v1/public/manage/{token}/availability")
    suspend fun rescheduleSlots(@Path("token") token: String, @Query("staff") staff: String?, @Query("date") date: String): SlotsDto

    @POST("api/v1/public/manage/{token}/reschedule")
    suspend fun reschedule(@Path("token") token: String, @Body request: RescheduleDto): BookingDto
}

@Serializable
data class BusinessDto(
    val slug: String,
    val displayName: String,
    val description: String? = null,
    val accentColor: String? = null,
    val cancelCutoffMin: Int = 1440,
    val locations: List<LocationSummaryDto> = emptyList(),
)

@Serializable
data class LocationSummaryDto(
    val slug: String,
    val name: String,
    val suburb: String,
    val state: String,
    val phone: String? = null,
    val timeZone: String,
)

@Serializable
data class CatalogDto(val categories: List<CategoryDto> = emptyList(), val stylists: List<StylistDto> = emptyList())

@Serializable
data class CategoryDto(val name: String, val services: List<ServiceDto> = emptyList())

@Serializable
data class ServiceDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val durationMin: Int,
    val priceCents: Long,
    val priceIsFrom: Boolean = false,
    val customerNotice: String? = null,
)

@Serializable
data class StylistDto(val id: String, val displayName: String, val bio: String? = null, val serviceIds: List<String> = emptyList())

/** status: AVAILABLE, FULL or CLOSED. */
@Serializable
data class DayDto(val date: String, val status: String)

@Serializable
data class DaysDto(val days: List<DayDto> = emptyList(), val lastBookableDate: String)

/** start: an instant; time: branch-local "HH:mm:ss". */
@Serializable
data class SlotDto(val start: String, val time: String)

@Serializable
data class SlotsDto(val date: String, val timeZone: String, val durationMin: Int, val slots: List<SlotDto> = emptyList())

@Serializable
data class CustomerDto(val fullName: String, val email: String, val phone: String)

@Serializable
data class NewBookingDto(
    val locationSlug: String,
    val serviceIds: List<String>,
    val staffId: String?,
    val start: String,
    val customer: CustomerDto,
    val note: String? = null,
    val acceptsPolicy: Boolean,
    val idempotencyKey: String,
)

@Serializable
data class BranchContactDto(
    val name: String,
    val addressLine1: String,
    val addressLine2: String? = null,
    val suburb: String,
    val state: String,
    val postcode: String,
    val phone: String? = null,
    val timeZone: String,
)

@Serializable
data class BookedServiceDto(val name: String, val durationMin: Int, val priceCents: Long, val priceIsFrom: Boolean = false)

@Serializable
data class BookingDto(
    val reference: String,
    val status: String,
    val businessName: String,
    val businessSlug: String,
    val accentColor: String? = null,
    val branch: BranchContactDto,
    val startAt: String,
    val endAt: String,
    val services: List<BookedServiceDto> = emptyList(),
    val totalPriceCents: Long = 0,
    val staffName: String,
    val customerName: String,
    val email: String? = null,
    val phone: String? = null,
    val canCancel: Boolean = false,
    val cancelBefore: String,
)

@Serializable
data class CreatedBookingDto(val booking: BookingDto, val manageToken: String)

@Serializable
data class StylistOptionDto(val id: String, val displayName: String)

@Serializable
data class RescheduleOptionsDto(val currentStaffId: String, val stylists: List<StylistOptionDto> = emptyList())

@Serializable
data class RescheduleDto(val start: String, val staffId: String?)

/** Every API error: `{"error": {"code": "...", "message": "..."}}`. */
@Serializable
data class ApiErrorDto(val error: ApiErrorBody)

@Serializable
data class ApiErrorBody(val code: String, val message: String)
