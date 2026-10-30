package com.salonbooking.app.customer.booking

import com.salonbooking.app.core.network.ApiResult
import com.salonbooking.app.core.network.BookingDto
import com.salonbooking.app.core.network.CatalogDto
import com.salonbooking.app.core.network.CreatedBookingDto
import com.salonbooking.app.core.network.DaysDto
import com.salonbooking.app.core.network.NewBookingDto
import com.salonbooking.app.core.network.PublicApi
import com.salonbooking.app.core.network.RescheduleDto
import com.salonbooking.app.core.network.RescheduleOptionsDto
import com.salonbooking.app.core.network.SlotsDto
import com.salonbooking.app.core.network.apiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/** Booking a new appointment (BKG) and managing one through its private token (MGT). */
interface BookingRepository {
    suspend fun catalog(salon: String, branch: String): ApiResult<CatalogDto>
    suspend fun days(salon: String, branch: String, serviceIds: List<String>, staffId: String?, from: String, to: String): ApiResult<DaysDto>
    suspend fun slots(salon: String, branch: String, serviceIds: List<String>, staffId: String?, date: String): ApiResult<SlotsDto>
    suspend fun book(salon: String, booking: NewBookingDto): ApiResult<CreatedBookingDto>

    suspend fun booking(token: String): ApiResult<BookingDto>
    suspend fun cancel(token: String): ApiResult<BookingDto>
    suspend fun rescheduleOptions(token: String): ApiResult<RescheduleOptionsDto>
    suspend fun rescheduleDays(token: String, staffId: String?, from: String, to: String): ApiResult<DaysDto>
    suspend fun rescheduleSlots(token: String, staffId: String?, date: String): ApiResult<SlotsDto>
    suspend fun reschedule(token: String, start: String, staffId: String?): ApiResult<BookingDto>
}

@Singleton
class RemoteBookingRepository @Inject constructor(private val api: PublicApi, private val json: Json) : BookingRepository {

    override suspend fun catalog(salon: String, branch: String) = apiCall(json) { api.catalog(salon, branch) }

    override suspend fun days(salon: String, branch: String, serviceIds: List<String>, staffId: String?, from: String, to: String) =
        apiCall(json) { api.days(salon, branch, serviceIds.joinToString(","), staffId, from, to) }

    override suspend fun slots(salon: String, branch: String, serviceIds: List<String>, staffId: String?, date: String) =
        apiCall(json) { api.slots(salon, branch, serviceIds.joinToString(","), staffId, date) }

    override suspend fun book(salon: String, booking: NewBookingDto) = apiCall(json) { api.book(salon, booking) }

    override suspend fun booking(token: String) = apiCall(json) { api.booking(token) }

    override suspend fun cancel(token: String) = apiCall(json) { api.cancel(token) }

    override suspend fun rescheduleOptions(token: String) = apiCall(json) { api.rescheduleOptions(token) }

    override suspend fun rescheduleDays(token: String, staffId: String?, from: String, to: String) =
        apiCall(json) { api.rescheduleDays(token, staffId, from, to) }

    override suspend fun rescheduleSlots(token: String, staffId: String?, date: String) =
        apiCall(json) { api.rescheduleSlots(token, staffId, date) }

    override suspend fun reschedule(token: String, start: String, staffId: String?) =
        apiCall(json) { api.reschedule(token, RescheduleDto(start, staffId)) }
}
