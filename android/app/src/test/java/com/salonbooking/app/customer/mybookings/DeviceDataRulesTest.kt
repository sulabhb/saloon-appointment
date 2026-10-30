package com.salonbooking.app.customer.mybookings

import com.salonbooking.app.customer.booking.booking
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceDataRulesTest {

    @Test
    fun `a booking is kept once, with its latest copy and link`() {
        var data = DeviceData()
        data = DeviceDataRules.saveBooking(data, "token-1", booking("AAAA1111", "2026-11-03T00:00:00Z"))
        data = DeviceDataRules.saveBooking(data, "token-2", booking("AAAA1111", "2026-11-04T00:00:00Z"))
        assertEquals(1, data.bookings.size)
        assertEquals("token-2", data.bookings.single().token)
        assertEquals("2026-11-04T00:00:00Z", data.bookings.single().booking.startAt)
    }

    @Test
    fun `upcoming bookings come first, soonest at the top, then past and cancelled ones`() {
        val now = Instant.parse("2026-11-01T00:00:00Z")
        val bookings = listOf(
            SavedBooking("a", booking("LATER111", "2026-11-10T00:00:00Z")),
            SavedBooking("b", booking("SOONER11", "2026-11-02T00:00:00Z")),
            SavedBooking("c", booking("PAST1111", "2026-10-20T00:00:00Z")),
            SavedBooking("d", booking("CANCEL11", "2026-11-05T00:00:00Z", status = "CANCELLED")),
        )
        val (upcoming, past) = DeviceDataRules.upcomingAndPast(bookings, now)
        assertEquals(listOf("SOONER11", "LATER111"), upcoming.map { it.booking.reference })
        assertEquals(listOf("CANCEL11", "PAST1111"), past.map { it.booking.reference })
    }

    @Test
    fun `recent salons are remembered without duplicates`() {
        var data = DeviceData()
        (1..10).forEach { data = DeviceDataRules.saveSalon(data, SavedSalon("salon-$it", "Salon $it")) }
        data = DeviceDataRules.saveSalon(data, SavedSalon("salon-5", "Salon 5"))
        assertEquals(DeviceDataRules.MAX_SALONS, data.salons.size)
        assertEquals("salon-5", data.salons.first().slug)
        assertEquals(1, data.salons.count { it.slug == "salon-5" })
    }
}
