package com.salonbooking.app.customer.mybookings

import android.content.Context
import androidx.core.content.edit
import com.salonbooking.app.core.network.BookingDto
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A booking made on, or opened in, this device: its private token and the last copy seen (APP-12). */
@Serializable
data class SavedBooking(val token: String, val booking: BookingDto)

/** A salon the customer opened, for quick rebooking (APP-11). */
@Serializable
data class SavedSalon(val slug: String, val displayName: String, val accentColor: String? = null)

/** Contact details used for the last booking on this device, to pre-fill the next one (BKG-7). */
@Serializable
data class ContactDetails(val fullName: String = "", val email: String = "", val phone: String = "")

@Serializable
data class DeviceData(
    val bookings: List<SavedBooking> = emptyList(),
    val salons: List<SavedSalon> = emptyList(),
    val lastDetails: ContactDetails? = null,
)

/** Pure list rules, kept apart from storage so they can be unit tested. */
object DeviceDataRules {

    /** Adds or refreshes a booking; a booking is identified by its reference, not by which link opened it. */
    fun saveBooking(data: DeviceData, token: String, booking: BookingDto): DeviceData =
        data.copy(bookings = listOf(SavedBooking(token, booking)) + data.bookings.filter { it.booking.reference != booking.reference })

    /** Most recent first, at most [MAX_SALONS]. */
    fun saveSalon(data: DeviceData, salon: SavedSalon): DeviceData =
        data.copy(salons = (listOf(salon) + data.salons.filter { it.slug != salon.slug }).take(MAX_SALONS))

    /** Upcoming first (soonest at the top), then past and cancelled (most recent first). */
    fun upcomingAndPast(bookings: List<SavedBooking>, now: Instant): Pair<List<SavedBooking>, List<SavedBooking>> {
        val (upcoming, past) = bookings.partition {
            it.booking.status == "CONFIRMED" && Instant.parse(it.booking.endAt).isAfter(now)
        }
        return upcoming.sortedBy { it.booking.startAt } to past.sortedByDescending { it.booking.startAt }
    }

    const val MAX_SALONS = 8
}

/** Bookings, salons and contact details kept on this device only (no account). */
interface DeviceStore {
    val data: StateFlow<DeviceData>

    fun update(change: (DeviceData) -> DeviceData)

    fun saveBooking(token: String, booking: BookingDto) = update { DeviceDataRules.saveBooking(it, token, booking) }

    fun forgetBooking(token: String) = update { d -> d.copy(bookings = d.bookings.filter { it.token != token }) }

    fun saveSalon(salon: SavedSalon) = update { DeviceDataRules.saveSalon(it, salon) }

    fun saveDetails(details: ContactDetails) = update { it.copy(lastDetails = details) }
}

/**
 * Stores [DeviceData] as JSON in the app's private preferences. Private storage is not backed up
 * (allowBackup is off), so management tokens do not leave the device.
 */
@Singleton
class PrefsDeviceStore @Inject constructor(@ApplicationContext context: Context, private val json: Json) : DeviceStore {

    private val prefs = context.getSharedPreferences("customer_device_data", Context.MODE_PRIVATE)
    private val _data = MutableStateFlow(load())
    override val data: StateFlow<DeviceData> = _data.asStateFlow()

    override fun update(change: (DeviceData) -> DeviceData) {
        synchronized(this) {
            val next = change(_data.value)
            prefs.edit { putString(KEY, json.encodeToString(DeviceData.serializer(), next)) }
            _data.value = next
        }
    }

    private fun load(): DeviceData = prefs.getString(KEY, null)
        ?.let { runCatching { json.decodeFromString(DeviceData.serializer(), it) }.getOrNull() }
        ?: DeviceData()

    private companion object {
        const val KEY = "data"
    }
}
